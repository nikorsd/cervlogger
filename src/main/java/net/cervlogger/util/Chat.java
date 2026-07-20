package net.cervlogger.util;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat helpers replicating CervLogger's message style.
 */
public final class Chat {

    private Chat() {
    }

    public static MutableComponent prefix() {
        return Component.literal("CervLogger ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("- ").withStyle(ChatFormatting.WHITE));
    }

    public static MutableComponent text(String text, ChatFormatting... styles) {
        return Component.literal(text).withStyle(styles);
    }

    public static void send(CommandSourceStack source, Component component) {
        source.sendSystemMessage(component);
    }

    public static void sendPrefixed(CommandSourceStack source, Component component) {
        source.sendSystemMessage(prefix().append(component));
    }

    public static void send(ServerPlayer player, Component component) {
        player.sendSystemMessage(component);
    }

    public static void sendPrefixed(ServerPlayer player, Component component) {
        player.sendSystemMessage(prefix().append(component));
    }
}
