package net.cervlogger.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.cervlogger.CervLogger;
import net.cervlogger.command.Params;
import net.cervlogger.util.BlockUtils;
import net.cervlogger.util.Chat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Rollback / restore engine.
 *
 * Rollback undoes logged changes (newest first):
 *   - placed blocks are replaced by what was there before
 *   - broken/exploded/burned blocks are placed back
 *   - container additions are removed, removals are put back
 *   - killed entities are respawned
 *
 * Restore re-applies previously rolled back changes (oldest first).
 */
public final class Rollback {

    private Rollback() {
    }

    public static void run(CommandSourceStack source, Params params, boolean restore) {
        Thread thread = new Thread(() -> execute(source, params, restore), "CervLogger-Rollback");
        thread.setDaemon(true);
        thread.start();
    }

    private static void execute(CommandSourceStack source, Params params, boolean restore) {
        String verb = restore ? "Restore" : "Rollback";
        try {
            List<Lookup.Row> rows = Lookup.lookup(params, restore ? 1 : 0, -1, 0, restore);
            if (rows.isEmpty()) {
                source.getServer().execute(() -> Chat.sendPrefixed(source,
                        Chat.text("No data found to " + verb.toLowerCase() + ".", ChatFormatting.WHITE)));
                return;
            }
            MinecraftServer server = source.getServer();
            server.execute(() -> {
                int changed = 0;
                List<Long> processed = new ArrayList<>();
                for (Lookup.Row row : rows) {
                    try {
                        boolean applied = switch (params.table) {
                            case BLOCK -> applyBlock(server, row, restore);
                            case CONTAINER -> applyContainer(server, row, restore);
                            case KILL -> !restore && applyKill(server, row);
                            default -> false;
                        };
                        if (applied) {
                            changed++;
                            processed.add(row.rowid());
                        }
                    }
                    catch (Exception e) {
                        CervLogger.LOGGER.error("[CervLogger] {} failed for row {}", verb, row.rowid(), e);
                    }
                }
                final int totalChanged = changed;
                // Mark rows async, then report back on the main thread.
                ConsumerThread.queue(conn -> Lookup.setRolledBack(params.table.tableName, processed, !restore));
                Component message = Chat.prefix()
                        .append(Chat.text(verb + " completed. ", ChatFormatting.WHITE))
                        .append(Chat.text("Modified " + totalChanged + " " + (params.table == Params.LogTable.BLOCK ? "block(s)" : params.table == Params.LogTable.CONTAINER ? "item(s)" : "entit(ies)") + ".", ChatFormatting.WHITE));
                Chat.send(source, message);
            });
        }
        catch (Exception e) {
            CervLogger.LOGGER.error("[CervLogger] {} failed", verb, e);
            source.getServer().execute(() -> Chat.sendPrefixed(source,
                    Chat.text(verb + " failed: " + e.getMessage(), ChatFormatting.RED)));
        }
    }

    private static ServerLevel level(MinecraftServer server, String world) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(world)) {
                return level;
            }
        }
        return null;
    }

    private static boolean applyBlock(MinecraftServer server, Lookup.Row row, boolean restore) {
        ServerLevel level = level(server, row.world());
        if (level == null) {
            return false;
        }
        BlockPos pos = new BlockPos(row.x(), row.y(), row.z());
        BlockState target;
        if (!restore) {
            // Undo the change.
            target = switch (row.action()) {
                case 1 -> BlockUtils.deserialize(row.oldType(), level); // placed -> put back what was replaced (often air)
                case 0, 3, 4 -> BlockUtils.deserialize(row.type(), level); // removed -> put it back
                default -> null;
            };
        }
        else {
            // Re-apply the change.
            target = switch (row.action()) {
                case 1 -> BlockUtils.deserialize(row.type(), level);
                case 0, 3, 4 -> BlockUtils.deserialize(row.oldType() == null ? "minecraft:air" : row.oldType(), level);
                default -> null;
            };
        }
        if (target == null) {
            return false;
        }
        // If we are about to remove/replace a container block, clear its inventory
        // first: otherwise vanilla drops the whole contents on the ground when the
        // block is removed, duplicating the items the rollback re-inserts elsewhere.
        if (!level.getBlockState(pos).is(target.getBlock())
                && level.getBlockEntity(pos) instanceof Container container) {
            container.clearContent();
        }
        return level.setBlock(pos, target, 3);
    }

    private static boolean applyContainer(MinecraftServer server, Lookup.Row row, boolean restore) {
        ServerLevel level = level(server, row.world());
        if (level == null) {
            return false;
        }
        BlockPos pos = new BlockPos(row.x(), row.y(), row.z());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            return false;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(BlockUtils.baseId(row.type())));
        if (item.isEmpty()) {
            return false;
        }
        // rollback: undo -> removed items (action 0) go back in, added items (action 1) come out
        // restore:  redo -> the opposite
        boolean addToContainer = restore ? row.action() == 1 : row.action() == 0;
        if (addToContainer) {
            return addItems(container, item.get(), row.amount());
        }
        return removeItems(container, item.get(), row.amount());
    }

    private static boolean addItems(Container container, Item item, int amount) {
        int remaining = amount;
        int maxStack = new ItemStack(item).getMaxStackSize();
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                int put = Math.min(remaining, maxStack);
                container.setItem(slot, new ItemStack(item, put));
                remaining -= put;
            }
            else if (existing.getItem() == item && existing.getCount() < existing.getMaxStackSize()) {
                int put = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
                existing.grow(put);
                container.setItem(slot, existing);
                remaining -= put;
            }
        }
        container.setChanged();
        return remaining < amount;
    }

    private static boolean removeItems(Container container, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && existing.getItem() == item) {
                int take = Math.min(remaining, existing.getCount());
                existing.shrink(take);
                container.setItem(slot, existing.isEmpty() ? ItemStack.EMPTY : existing);
                remaining -= take;
            }
        }
        container.setChanged();
        return remaining < amount;
    }

    private static boolean applyKill(MinecraftServer server, Lookup.Row row) {
        ServerLevel level = level(server, row.world());
        if (level == null) {
            return false;
        }
        Optional<EntityType<?>> type = EntityType.byString(row.type());
        if (type.isEmpty()) {
            return false;
        }
        Entity entity = type.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.moveTo(row.x() + 0.5D, row.y(), row.z() + 0.5D, 0.0F, 0.0F);
        return level.addFreshEntity(entity);
    }
}
