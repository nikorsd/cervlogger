package net.cervlogger.listener;

import net.cervlogger.config.ConfigHandler;
import net.cervlogger.database.Logs;
import net.cervlogger.inspector.Inspector;
import net.cervlogger.util.BlockUtils;
import net.cervlogger.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Logs block breaking, placing and explosions.
 * Replaces the Bukkit BlockBreakEvent/BlockPlaceEvent/EntityExplodeEvent listeners.
 */
public final class BlockListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player breaker = event.getPlayer();
        if (breaker instanceof ServerPlayer player && Inspector.isInspecting(player)) {
            event.setCanceled(true);
            Inspector.showHistory(player, level, event.getPos());
            return;
        }
        if (!ConfigHandler.logBlocks) {
            return;
        }
        String user = breaker != null ? breaker.getGameProfile().getName() : "#environment";
        BlockState state = event.getState();
        Logs.logBlock(user, level, event.getPos(), 0, BlockUtils.serialize(state), "minecraft:air");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !ConfigHandler.logBlocks) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        String user;
        if (entity instanceof ServerPlayer player) {
            user = player.getGameProfile().getName();
        }
        else if (entity != null) {
            if (!ConfigHandler.logNonPlayerActions) {
                return;
            }
            user = "#" + BlockUtils.entityId(entity.getType()).replace("minecraft:", "");
        }
        else {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        BlockState replaced;
        try {
            replaced = event.getBlockSnapshot().getState();
        }
        catch (Exception e) {
            replaced = null;
        }
        String oldType = replaced != null ? BlockUtils.serialize(replaced) : "minecraft:air";
        Logs.logBlock(user, level, event.getPos(), 1, BlockUtils.serialize(placed), oldType);

        // If a sign was placed, capture its text a few seconds later (after the edit GUI closes).
        if (ConfigHandler.logSigns && entity instanceof ServerPlayer player) {
            BlockPos pos = event.getPos();
            String name = player.getGameProfile().getName();
            Scheduler.schedule(100, () -> {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof SignBlockEntity sign) {
                    String text = SignHelper.readText(sign);
                    if (!text.isBlank()) {
                        Logs.logSign(name, level, pos, text);
                    }
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!ConfigHandler.logExplosions) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity source = event.getExplosion().getIndirectSourceEntity();
        String user;
        if (source instanceof Player player) {
            user = player.getGameProfile().getName();
        }
        else if (source != null) {
            user = "#" + BlockUtils.entityId(source.getType()).replace("minecraft:", "");
        }
        else {
            user = "#explosion";
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            Logs.logBlock(user, level, pos, 3, BlockUtils.serialize(state), "minecraft:air");
        }
    }

    /** Small helper to read all four lines of a sign's front text. */
    static final class SignHelper {
        static String readText(SignBlockEntity sign) {
            StringBuilder text = new StringBuilder();
            for (int line = 0; line < 4; line++) {
                String value = sign.getFrontText().getMessage(line, false).getString();
                if (!value.isBlank()) {
                    if (text.length() > 0) {
                        text.append(" | ");
                    }
                    text.append(value);
                }
            }
            return text.toString();
        }
    }
}
