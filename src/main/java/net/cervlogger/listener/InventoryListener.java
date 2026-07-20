package net.cervlogger.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.cervlogger.config.ConfigHandler;
import net.cervlogger.database.Logs;
import net.cervlogger.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Container transaction logging.
 *
 * The Bukkit plugin hooks InventoryClickEvent/InventoryMoveItemEvent; NeoForge
 * has no direct equivalent without mixins, so this port snapshots the container
 * contents when a player opens it and diffs the contents when it is closed.
 * The net difference is logged as "added" / "removed" rows, which is what
 * rollback needs.
 */
public final class InventoryListener {

    private record Snapshot(BlockPos pos, ServerLevel level, Map<String, Integer> counts) {
    }

    private static final Map<UUID, Snapshot> OPEN_CONTAINERS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> LAST_INTERACTED = new ConcurrentHashMap<>();

    /** Called by PlayerListener when a player right-clicks a container block. */
    static void rememberInteractedContainer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LAST_INTERACTED.put(player.getUUID(), pos.immutable());
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!ConfigHandler.logContainers) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Snapshot snapshot = snapshot(player, event.getContainer());
        if (snapshot != null) {
            OPEN_CONTAINERS.put(player.getUUID(), snapshot);
        }
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Snapshot before = OPEN_CONTAINERS.remove(player.getUUID());
        if (before == null) {
            return;
        }
        Snapshot after = snapshot(player, event.getContainer());
        if (after == null) {
            return;
        }
        String user = player.getGameProfile().getName();
        Set<String> itemIds = new HashSet<>(before.counts().keySet());
        itemIds.addAll(after.counts().keySet());
        for (String itemId : itemIds) {
            int delta = after.counts().getOrDefault(itemId, 0) - before.counts().getOrDefault(itemId, 0);
            if (delta > 0) {
                Logs.logContainer(user, before.level(), before.pos(), 1, itemId, delta);
            }
            else if (delta < 0) {
                Logs.logContainer(user, before.level(), before.pos(), 0, itemId, -delta);
            }
        }
    }

    /**
     * Builds a snapshot of all non-player-inventory slots of the open menu.
     * Returns null when the menu has no block-backed container (e.g. crafting).
     */
    private static Snapshot snapshot(ServerPlayer player, AbstractContainerMenu menu) {
        Map<String, Integer> counts = new HashMap<>();
        BlockPos pos = null;
        boolean hasExternalSlots = false;
        for (Slot slot : menu.slots) {
            Container container = slot.container;
            if (container instanceof Inventory) {
                continue; // player's own inventory rows in the menu
            }
            hasExternalSlots = true;
            if (pos == null && container instanceof BlockEntity blockEntity) {
                pos = blockEntity.getBlockPos();
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                counts.merge(BlockUtils.itemId(stack), stack.getCount(), Integer::sum);
            }
        }
        if (!hasExternalSlots) {
            return null;
        }
        if (pos == null) {
            // Double chests use CompoundContainer (not a BlockEntity) -> fall back to
            // the block the player last right-clicked.
            pos = LAST_INTERACTED.get(player.getUUID());
        }
        if (pos == null) {
            return null;
        }
        return new Snapshot(pos, player.serverLevel(), counts);
    }
}
