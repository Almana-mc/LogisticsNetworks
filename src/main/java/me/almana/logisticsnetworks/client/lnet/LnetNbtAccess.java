package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;

final class LnetNbtAccess {

    private LnetNbtAccess() {
    }

    static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.getBooleanOr(key, fallback);
    }

    static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.getIntOr(key, fallback);
    }

    static String getString(CompoundTag tag, String key, String fallback) {
        return tag.getStringOr(key, fallback);
    }

    static ListTag getList(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key);
    }

    static CompoundTag parse(String value) throws Exception {
        return TagParser.parseCompoundFully(value);
    }
}
