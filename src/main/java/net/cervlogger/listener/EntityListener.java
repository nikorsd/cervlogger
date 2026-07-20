package net.cervlogger.listener;

import net.cervlogger.config.ConfigHandler;
import net.cervlogger.database.Logs;
import net.cervlogger.util.BlockUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Logs entity kills (co_entity_kill), replacing the Bukkit EntityDeathEvent listener.
 */
public final class EntityListener {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!ConfigHandler.logEntityKills) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (victim instanceof ServerPlayer) {
            return; // player deaths are not entity kills in CervLogger
        }
        Entity killer = event.getSource().getEntity();
        String user;
        if (killer instanceof ServerPlayer player) {
            user = player.getGameProfile().getName();
        }
        else if (killer != null) {
            if (!ConfigHandler.logNonPlayerActions) {
                return;
            }
            user = "#" + BlockUtils.entityId(killer.getType()).replace("minecraft:", "");
        }
        else {
            return;
        }
        Logs.logEntityKill(user, victim.level(), victim.blockPosition(), BlockUtils.entityId(victim.getType()));
    }
}
