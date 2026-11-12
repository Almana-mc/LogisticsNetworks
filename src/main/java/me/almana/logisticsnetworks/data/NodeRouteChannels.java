package me.almana.logisticsnetworks.data;

public final class NodeRouteChannels {
    private static final int CHANNEL_COUNT = 9;
    private static final int BITS_PER_CHANNEL = 6;
    private static final int EXPORT_BIT = 1 << 5;

    private NodeRouteChannels() {
    }

    public static long encode(ChannelData[] channels, boolean mounted, int available) {
        long bits = 0L;
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            bits |= (long) descriptor(channels[channel], mounted, available) << channel * BITS_PER_CHANNEL;
        }
        return bits;
    }

    public static boolean matches(long bits, int channel, ChannelType type, ChannelMode mode) {
        if (channel < 0 || channel >= CHANNEL_COUNT) {
            return false;
        }
        int descriptor = (int) (bits >>> channel * BITS_PER_CHANNEL & 0x3FL);
        return type.in(descriptor) && ((descriptor & EXPORT_BIT) != 0) == (mode == ChannelMode.EXPORT);
    }

    private static int descriptor(ChannelData channel, boolean mounted, int available) {
        if (!channel.isEnabled() || mounted && channel.getRedstoneMode() == RedstoneMode.HIGH) {
            return 0;
        }
        int types = channel.getTypes() & available;
        if (types == 0) {
            return 0;
        }
        return types | (channel.getMode() == ChannelMode.EXPORT ? EXPORT_BIT : 0);
    }
}
