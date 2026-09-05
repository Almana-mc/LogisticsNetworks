package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record WrenchClipboard(boolean valid, Optional<ClipboardSnapshot> snapshot) {

    public static final Codec<WrenchClipboard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("valid").forGetter(WrenchClipboard::valid),
            ClipboardSnapshot.CODEC.optionalFieldOf("snapshot").forGetter(WrenchClipboard::snapshot)
    ).apply(instance, WrenchClipboard::new));

    public WrenchClipboard {
        snapshot = snapshot == null ? Optional.empty() : snapshot;
    }

    public static WrenchClipboard valid(ClipboardSnapshot snapshot) {
        return new WrenchClipboard(true, Optional.of(snapshot));
    }

    public static WrenchClipboard invalid() {
        return new WrenchClipboard(false, Optional.empty());
    }
}
