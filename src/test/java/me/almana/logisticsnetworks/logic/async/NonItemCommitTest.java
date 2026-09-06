package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NonItemCommitTest extends SnapshotFixture {
    @Test
    void persistentTopologyPayloadKeepsItsExistingWireShape() {
        var sourceId = java.util.UUID.randomUUID();
        var targetId = java.util.UUID.randomUUID();
        var source = new BlockPos(1, 2, 3);
        var target = new BlockPos(4, 5, 6);
        var path = new me.almana.logisticsnetworks.network.TransferVisualPayload.Path(sourceId, targetId, 11, 12,
                source, target, ChannelType.ITEM.ordinal(),
                me.almana.logisticsnetworks.network.TransferVisualPayload.Shape.FULL);
        var payload = new me.almana.logisticsnetworks.network.TransferVisualPayload(0x112233, List.of(path));
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            me.almana.logisticsnetworks.network.TransferVisualPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(0x112233, buffer.readInt());
            assertEquals(1, buffer.readVarInt());
            assertEquals(sourceId, buffer.readUUID());
            assertEquals(targetId, buffer.readUUID());
            assertEquals(11, buffer.readInt());
            assertEquals(12, buffer.readInt());
            assertEquals(source, buffer.readBlockPos());
            assertEquals(target, buffer.readBlockPos());
            assertEquals(ChannelType.ITEM.ordinal(), buffer.readVarInt());
            assertEquals(0, buffer.readByte());
            assertEquals(0, buffer.readableBytes());
            buffer.readerIndex(0);
            assertEquals(payload, me.almana.logisticsnetworks.network.TransferVisualPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void followingItemCommitProcessesEnergyOnceWithoutRepeatingItems() throws Exception {
        var items = inventory(20);
        var destination = inventory(0);
        var energy = new SimpleEnergyHandler(100, 100, 100, 100);
        var receiver = new SimpleEnergyHandler(100);
        try (var f = new CommitFixture(items, destination)) {
            var exportEnergy = CommitFixture.channel(ChannelMode.EXPORT);
            exportEnergy.setType(ChannelType.ENERGY);
            var importEnergy = CommitFixture.channel(ChannelMode.IMPORT);
            importEnergy.setType(ChannelType.ENERGY);
            when(f.source.getChannel(1)).thenReturn(exportEnergy);
            when(f.targets.getFirst().getChannel(1)).thenReturn(importEnergy);
            when(f.source.capabilities().findEnergyHandler(any())).thenReturn(energy);
            when(f.targets.getFirst().capabilities().findEnergyHandler(any())).thenReturn(receiver);
            List<NodeRef>[] refs = new List[9];
            Arrays.fill(refs, List.of());
            refs[1] = List.of(new NodeRef(f.targets.getFirst().getUUID(), new BlockPos(2, 0, 0), 0));
            when(f.network.getEnergyImports()).thenReturn(refs);
            assertEquals(8, f.commit(f.plan()).moved());
            assertEquals(0, receiver.getAmountAsLong());
            f.lastExecution[0] = 0;
            assertEquals(0, TransferEngine.processSynchronousNonItems(f.network, f.server));
            assertEquals(8, receiver.getAmountAsLong());
            assertEquals(92, energy.getAmountAsLong());
            assertEquals(8, exportEnergy.getTelemetry().drainFlow());
            assertEquals(8, f.export.getTelemetry().drainFlow());
            assertEquals(12, items.getAmountAsInt(0));
            assertEquals(8, destination.getAmountAsInt(0));
        }
    }
}
