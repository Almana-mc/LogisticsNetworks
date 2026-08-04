package me.almana.logisticsnetworks.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
//? if <26
/*import net.minecraft.nbt.Tag;*/

import java.util.UUID;

/**
 * 26.x turned the CompoundTag getters into Optional-returning methods and added
 * the {@code *Or} family; older versions return the primitive directly and take
 * an element type. Every read of a stored tag goes through here so the callers
 * stay version-agnostic.
 *
 * <p>26.x also dropped {@code putUUID}/{@code getUUID} entirely, so each version
 * keeps its own on-disk shape for UUIDs — a dashed string on 26.x, the four-int
 * array everywhere else. Existing saves therefore still read back on every target.
 */
public final class NbtAccess {

    private NbtAccess() {
    }

    public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        //? if <26 {
        /*return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
        *///?} else {
        return tag.getBooleanOr(key, fallback);
        //?}
    }

    public static int getInt(CompoundTag tag, String key, int fallback) {
        //? if <26 {
        /*return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
        *///?} else {
        return tag.getIntOr(key, fallback);
        //?}
    }

    public static long getLong(CompoundTag tag, String key, long fallback) {
        //? if <26 {
        /*return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : fallback;
        *///?} else {
        return tag.getLongOr(key, fallback);
        //?}
    }

    public static String getString(CompoundTag tag, String key, String fallback) {
        //? if <26 {
        /*return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
        *///?} else {
        return tag.getStringOr(key, fallback);
        //?}
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        //? if <26 {
        /*return tag.getCompound(key);
        *///?} else {
        return tag.getCompoundOrEmpty(key);
        //?}
    }

    public static ListTag getList(CompoundTag tag, String key, int elementType) {
        //? if <26 {
        /*return tag.getList(key, elementType);
        *///?} else {
        return tag.getListOrEmpty(key);
        //?}
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        //? if <26 {
        /*return tag.contains(key, Tag.TAG_INT_ARRAY) ? tag.getIntArray(key) : new int[0];
        *///?} else {
        return tag.getIntArray(key).orElse(new int[0]);
        //?}
    }

    public static void putUuid(CompoundTag tag, String key, UUID value) {
        //? if <26 {
        /*tag.putUUID(key, value);
        *///?} else {
        tag.putString(key, value.toString());
        //?}
    }

    public static UUID getUuidOrNull(CompoundTag tag, String key) {
        //? if <26 {
        /*return tag.hasUUID(key) ? tag.getUUID(key) : null;
        *///?} else {
        return parseUuid(tag.getStringOr(key, null));
        //?}
    }

    public static UUID getUuidOrRandom(CompoundTag tag, String key) {
        UUID stored = getUuidOrNull(tag, key);
        return stored != null ? stored : UUID.randomUUID();
    }

    //? if >=26 {
    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
    //?}
}
