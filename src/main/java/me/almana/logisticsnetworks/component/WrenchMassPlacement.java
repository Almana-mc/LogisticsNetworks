package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record WrenchMassPlacement(
        Optional<Area> area,
        Optional<ResourceLocation> selectedBlock,
        List<GlobalPos> selections) {

    public static final WrenchMassPlacement EMPTY = new WrenchMassPlacement(
            Optional.empty(), Optional.empty(), List.of());
    public static final Codec<WrenchMassPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Area.CODEC.optionalFieldOf("area").forGetter(WrenchMassPlacement::area),
            ResourceLocation.CODEC.optionalFieldOf("selected_block").forGetter(WrenchMassPlacement::selectedBlock),
            GlobalPos.CODEC.listOf().optionalFieldOf("selections", List.of()).forGetter(WrenchMassPlacement::selections)
    ).apply(instance, WrenchMassPlacement::new));

    public WrenchMassPlacement {
        area = area == null ? Optional.empty() : area;
        selectedBlock = selectedBlock == null ? Optional.empty() : selectedBlock;
        selections = List.copyOf(selections);
    }

    public boolean isEmpty() {
        return area.isEmpty() && selectedBlock.isEmpty() && selections.isEmpty();
    }

    public record Area(GlobalPos first, Optional<BlockPos> second) {
        public static final Codec<Area> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GlobalPos.CODEC.fieldOf("first").forGetter(Area::first),
                BlockPos.CODEC.optionalFieldOf("second").forGetter(Area::second)
        ).apply(instance, Area::new));

        public Area {
            second = second == null ? Optional.empty() : second;
        }
    }
}
