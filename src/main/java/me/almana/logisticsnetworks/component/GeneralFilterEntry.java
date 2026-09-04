package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record GeneralFilterEntry(
        int slot,
        @Nullable StackSnapshot item,
        @Nullable String fluidId,
        @Nullable String chemicalId,
        @Nullable String tag,
        EntryCounts counts,
        SlotMapping slotMapping,
        @Nullable Boolean enchanted,
        NbtConstraints nbt,
        @Nullable DurabilityConstraint durability) {

    public static final Codec<GeneralFilterEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(GeneralFilterEntry::slot),
            StackSnapshot.CODEC.optionalFieldOf("item").forGetter(entry -> Optional.ofNullable(entry.item)),
            Codec.STRING.optionalFieldOf("fluid").forGetter(entry -> Optional.ofNullable(entry.fluidId)),
            Codec.STRING.optionalFieldOf("chemical").forGetter(entry -> Optional.ofNullable(entry.chemicalId)),
            Codec.STRING.optionalFieldOf("tag").forGetter(entry -> Optional.ofNullable(entry.tag)),
            EntryCounts.CODEC.optionalFieldOf("counts", EntryCounts.EMPTY).forGetter(GeneralFilterEntry::counts),
            SlotMapping.CODEC.optionalFieldOf("slot_mapping", SlotMapping.EMPTY).forGetter(GeneralFilterEntry::slotMapping),
            Codec.BOOL.optionalFieldOf("enchanted").forGetter(entry -> Optional.ofNullable(entry.enchanted)),
            NbtConstraints.CODEC.optionalFieldOf("nbt", NbtConstraints.EMPTY).forGetter(GeneralFilterEntry::nbt),
            DurabilityConstraint.CODEC.optionalFieldOf("durability")
                    .forGetter(entry -> Optional.ofNullable(entry.durability))
    ).apply(instance, (slot, item, fluid, chemical, tag, counts, mapping, enchanted, nbt, durability) ->
            new GeneralFilterEntry(slot, item.orElse(null), fluid.orElse(null), chemical.orElse(null),
                    tag.orElse(null), counts, mapping, enchanted.orElse(null), nbt, durability.orElse(null))));

    public GeneralFilterEntry {
        counts = counts == null ? EntryCounts.EMPTY : counts;
        slotMapping = slotMapping == null ? SlotMapping.EMPTY : slotMapping;
        nbt = nbt == null ? NbtConstraints.EMPTY : nbt;
    }

    public static GeneralFilterEntry empty(int slot) {
        return new GeneralFilterEntry(slot, null, null, null, null, EntryCounts.EMPTY, SlotMapping.EMPTY,
                null, NbtConstraints.EMPTY, null);
    }

    public GeneralFilterEntry withNbt(NbtConstraints value) {
        return new GeneralFilterEntry(slot, item, fluidId, chemicalId, tag, counts, slotMapping, enchanted,
                value, durability);
    }

    public GeneralFilterEntry withSlotMapping(SlotMapping value) {
        return new GeneralFilterEntry(slot, item, fluidId, chemicalId, tag, counts, value, enchanted,
                nbt, durability);
    }

    public record EntryCounts(int amount, int batch, int stock) {
        public static final EntryCounts EMPTY = new EntryCounts(0, 0, 0);
        public static final Codec<EntryCounts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("amount", 0).forGetter(EntryCounts::amount),
                Codec.INT.optionalFieldOf("batch", 0).forGetter(EntryCounts::batch),
                Codec.INT.optionalFieldOf("stock", 0).forGetter(EntryCounts::stock)
        ).apply(instance, EntryCounts::new));
    }

    public record SlotMapping(List<Integer> slots, String expression) {
        public static final SlotMapping EMPTY = new SlotMapping(List.of(), "");
        public static final Codec<SlotMapping> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.listOf().optionalFieldOf("slots", List.of()).forGetter(SlotMapping::slots),
                Codec.STRING.optionalFieldOf("expression", "").forGetter(SlotMapping::expression)
        ).apply(instance, SlotMapping::new));

        public SlotMapping {
            slots = List.copyOf(slots);
            expression = expression == null ? "" : expression;
        }
    }

    public record NbtConstraints(List<NbtCriterion> rules, boolean matchAny, Optional<Boolean> strict, String raw) {
        public static final NbtConstraints EMPTY = new NbtConstraints(List.of(), false, Optional.empty(), "");
        public static final Codec<NbtConstraints> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NbtCriterion.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(NbtConstraints::rules),
                Codec.BOOL.optionalFieldOf("match_any", false).forGetter(NbtConstraints::matchAny),
                Codec.BOOL.optionalFieldOf("strict").forGetter(NbtConstraints::strict),
                Codec.STRING.optionalFieldOf("raw", "").forGetter(NbtConstraints::raw)
        ).apply(instance, NbtConstraints::new));

        public NbtConstraints {
            rules = List.copyOf(rules);
            strict = strict == null ? Optional.empty() : strict;
            raw = raw == null ? "" : raw;
        }
    }

    public record DurabilityConstraint(DurabilityFilterData.Operator operator, int value) {
        private static final Codec<DurabilityFilterData.Operator> OPERATOR_CODEC = Codec.STRING.xmap(
                DurabilityFilterData.Operator::fromId,
                DurabilityFilterData.Operator::id);
        public static final Codec<DurabilityConstraint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                OPERATOR_CODEC.fieldOf("operator").forGetter(DurabilityConstraint::operator),
                Codec.INT.fieldOf("value").forGetter(DurabilityConstraint::value)
        ).apply(instance, DurabilityConstraint::new));

        public DurabilityConstraint {
            operator = operator == null ? DurabilityFilterData.Operator.GREATER_OR_EQUAL : operator;
            value = Math.max(0, Math.min(3000, value));
        }
    }
}
