package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

final class LnetNbtAccess {

    private LnetNbtAccess() {
    }

    static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
    }

    static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
    }

    static String getString(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    static ListTag getList(CompoundTag tag, String key) {
        return tag.getList(key, Tag.TAG_COMPOUND);
    }

    static CompoundTag parse(String value) throws Exception {
        return TagParser.parseTag(value);
    }
}
