package net.cervlogger.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Helpers to serialize/deserialize block states and registry ids.
 * Replaces the Bukkit Material/BlockData handling of the original plugin.
 */
public final class BlockUtils {

    private BlockUtils() {
    }

    /**
     * Serializes a block state to a string like
     * "minecraft:oak_stairs[facing=east,half=bottom]".
     */
    public static String serialize(BlockState state) {
        try {
            return BlockStateParser.serialize(state);
        }
        catch (Exception e) {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }
    }

    /**
     * Parses a serialized block state string back into a BlockState.
     * Falls back to air if parsing fails.
     */
    public static BlockState deserialize(String serialized, ServerLevel level) {
        if (serialized == null || serialized.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        try {
            BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(
                    level.holderLookup(Registries.BLOCK), new StringReader(serialized), true);
            return result.blockState();
        }
        catch (CommandSyntaxException e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    /** Registry id of a block state's block, e.g. "minecraft:stone". */
    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Registry id of an item stack's item, e.g. "minecraft:diamond". */
    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Registry id of an entity type, e.g. "minecraft:zombie". */
    public static String entityId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    /**
     * Strips the block state properties from a serialized state:
     * "minecraft:oak_stairs[facing=east]" -> "minecraft:oak_stairs".
     */
    public static String baseId(String serialized) {
        int bracket = serialized.indexOf('[');
        return bracket >= 0 ? serialized.substring(0, bracket) : serialized;
    }

    /** Normalizes a user-provided id: "stone" -> "minecraft:stone". */
    public static String normalizeId(String id) {
        String value = id.trim().toLowerCase();
        return value.contains(":") ? value : "minecraft:" + value;
    }

    /** True if the given id refers to a known block or item. */
    public static boolean isKnownId(String id) {
        try {
            ResourceLocation location = ResourceLocation.parse(id);
            return BuiltInRegistries.BLOCK.containsKey(location) || BuiltInRegistries.ITEM.containsKey(location)
                    || BuiltInRegistries.ENTITY_TYPE.containsKey(location);
        }
        catch (Exception e) {
            return false;
        }
    }
}
