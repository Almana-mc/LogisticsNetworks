package me.almana.logisticsnetworks.data;

public final class NodeRouteChannels {
    private static final int CHANNEL_COUNT = 9;
    private static final int BITS_PER_CHANNEL = 4;

    private NodeRouteChannels() {
    }

    public static long encode(ChannelData[] channels, boolean mounted, boolean chemicalUpgrade,
            boolean sourceUpgrade) {
        long bits = 0L;
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            ChannelData data = channels[channel];
            if (isSupported(data, mounted, chemicalUpgrade, sourceUpgrade)) {
                bits |= (long) descriptor(data.getType(), data.getMode()) << channel * BITS_PER_CHANNEL;
            }
        }
        return bits;
    }

    public static boolean matches(long bits, int channel, ChannelType type, ChannelMode mode) {
        if (channel < 0 || channel >= CHANNEL_COUNT) {
            return false;
        }
        long descriptor = bits >>> channel * BITS_PER_CHANNEL & 0xFL;
        return descriptor == descriptor(type, mode);
    }

    private static boolean isSupported(ChannelData channel, boolean mounted, boolean chemicalUpgrade,
            boolean sourceUpgrade) {
        if (!channel.isEnabled()) {
            return false;
        }
        ChannelType type = channel.getType();
        if (mounted && (channel.getRedstoneMode() != RedstoneMode.ALWAYS_ON
                || type != ChannelType.ITEM && type != ChannelType.FLUID)) {
            return false;
        }
        if (type == ChannelType.CHEMICAL) {
            return chemicalUpgrade;
        }
        return type != ChannelType.SOURCE || sourceUpgrade;
    }

    private static int descriptor(ChannelType type, ChannelMode mode) {
        return type.ordinal() + (mode == ChannelMode.IMPORT ? 1 : 6);
    }
}
