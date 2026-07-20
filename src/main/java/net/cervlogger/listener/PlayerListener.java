package net.cervlogger.listener;

import net.cervlogger.config.ConfigHandler;
import net.cervlogger.database.Logs;
import net.cervlogger.inspector.Inspector;
import net.cervlogger.util.BlockUtils;
import net.cervlogger.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Logs sessions (join/quit), chat, commands, interactions, bucket usage,
 * and drives the inspector's right-click behaviour.
 */
public final class PlayerListener {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (ConfigHandler.logSessions) {
            Logs.logSession(player.getGameProfile().getName(), player.getStringUUID(),
                    player.serverLevel(), player.blockPosition(), 1);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Inspector.remove(player);
        if (ConfigHandler.logSessions) {
            Logs.logSession(player.getGameProfile().getName(), player.getStringUUID(),
                    player.serverLevel(), player.blockPosition(), 0);
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!ConfigHandler.logChat) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        Logs.logChat(player.getGameProfile().getName(), event.getMessage().getString());
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!ConfigHandler.logCommands) {
            return;
        }
        try {
            var source = event.getParseResults().getContext().getSource();
            if (source.getEntity() instanceof ServerPlayer player) {
                String command = "/" + event.getParseResults().getReader().getString();
                Logs.logCommand(player.getGameProfile().getName(), command);
            }
        }
        catch (Exception ignored) {
        }
    }

    /**
     * Inspector handling runs at HIGHEST priority so the click is cancelled before
     * other mods (Create GUIs, etc.) react to it.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (Inspector.isInspecting(player)) {
            event.setCanceled(true);
            // The event fires once per hand; only handle the main hand, and rate-limit
            // lookups to one per ~0.5s so the check message isn't spammed.
            if (event.getHand() == InteractionHand.MAIN_HAND && Inspector.tryAcquireCooldown(player)) {
                Inspector.showHistory(player, level, event.getPos());
            }
        }
    }

    /**
     * Logging runs at LOWEST priority so that mods which cancel right-clicks
     * (protection mods, Item Obliterator, etc.) run first: cancelled interactions
     * are then never logged as if they happened.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void logRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (Inspector.isInspecting(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        String user = player.getGameProfile().getName();

        // Remember the container block a player is about to open (used by InventoryListener
        // to attribute double-chest transactions to a position).
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container) {
            InventoryListener.rememberInteractedContainer(player, level, pos);
        }

        // Interaction logging (doors, buttons, levers, trapdoors, fence gates).
        if (ConfigHandler.logInteractions && (isInteractable(state) || ConfigHandler.isWatchedInteractBlock(BlockUtils.blockId(state)))) {
            Logs.logBlock(user, level, pos, 2, BlockUtils.serialize(state), null);
        }

        // Sign edits: right-clicking an existing sign opens the edit GUI in 1.20+.
        if (ConfigHandler.logSigns && blockEntity instanceof SignBlockEntity) {
            Scheduler.schedule(100, () -> {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SignBlockEntity sign) {
                    String text = BlockListener.SignHelper.readText(sign);
                    if (!text.isBlank()) {
                        Logs.logSign(user, level, pos, text);
                    }
                }
            });
        }

        // Bucket logging.
        if (ConfigHandler.logBuckets) {
            ItemStack stack = event.getItemStack();
            if (stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)) {
                BlockPos placePos = state.canBeReplaced() ? pos : pos.relative(event.getFace());
                String fluid = stack.is(Items.WATER_BUCKET) ? "minecraft:water" : "minecraft:lava";
                Logs.logBlock(user, level, placePos, 1, fluid, BlockUtils.serialize(level.getBlockState(placePos)));
            }
            else if (stack.is(Items.BUCKET)) {
                // Scooping a fluid: check both the clicked block and the adjacent one next tick.
                BlockPos adjacent = pos.relative(event.getFace());
                BlockState clicked = level.getBlockState(pos);
                BlockState neighbour = level.getBlockState(adjacent);
                if (!clicked.getFluidState().isEmpty()) {
                    Logs.logBlock(user, level, pos, 0, BlockUtils.serialize(clicked), "minecraft:air");
                }
                else if (!neighbour.getFluidState().isEmpty()) {
                    Logs.logBlock(user, level, adjacent, 0, BlockUtils.serialize(neighbour), "minecraft:air");
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (Inspector.isInspecting(player)) {
            event.setCanceled(true);
            // LeftClickBlock re-fires while the button is held; apply the same ~0.5s rate limit.
            if (event.getHand() == InteractionHand.MAIN_HAND && Inspector.tryAcquireCooldown(player)) {
                Inspector.showHistory(player, level, event.getPos());
            }
        }
    }

    private static boolean isInteractable(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof LeverBlock
                || (state.getBlock() instanceof BaseEntityBlock && !ConfigHandler.logContainers);
    }
}
