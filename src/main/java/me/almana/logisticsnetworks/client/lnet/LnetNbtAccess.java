package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
//? if <26
/*import net.minecraft.nbt.Tag;*/

final class LnetNbtAccess {

    private LnetNbtAccess() {
    }

    static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        //? if <26
        /*return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;*/
        //? if >=26
        return tag.getBooleanOr(key, fallback);
    }

    static int getInt(CompoundTag tag, String key, int fallback) {
        //? if <26
        /*return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;*/
        //? if >=26
        return tag.getIntOr(key, fallback);
    }

    static String getString(CompoundTag tag, String key, String fallback) {
        //? if <26
        /*return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;*/
        //? if >=26
        return tag.getStringOr(key, fallback);
    }

    static ListTag getList(CompoundTag tag, String key) {
        //? if <26
        /*return tag.getList(key, Tag.TAG_COMPOUND);*/
        //? if >=26
        return tag.getListOrEmpty(key);
    }

    static CompoundTag parse(String value) throws Exception {
        //? if <26
        /*return TagParser.parseTag(value);*/
        //? if >=26
        return TagParser.parseCompoundFully(value);
    }
}
