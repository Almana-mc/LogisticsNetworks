package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.IoSide;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class EntryFaces<T> {
    private static final int SIDES = IoSide.values().length;

    private final IoSide side;
    private final boolean imports;
    private final boolean exports;
    private final Function<IoSide, T> resolver;
    private final Object[] faces = new Object[SIDES];
    private int resolved;

    public EntryFaces(IoSide side, boolean imports, boolean exports, Function<IoSide, T> resolver) {
        this.side = side;
        this.imports = imports;
        this.exports = exports;
        this.resolver = resolver;
    }

    public boolean imports() {
        return imports;
    }

    public boolean exports() {
        return exports;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public T face(@Nullable IoSide wanted, T channelFace) {
        if (wanted == null || wanted == side)
            return channelFace;
        int bit = 1 << wanted.ordinal();
        if ((resolved & bit) == 0) {
            faces[wanted.ordinal()] = resolver.apply(wanted);
            resolved |= bit;
        }
        return (T) faces[wanted.ordinal()];
    }
}
