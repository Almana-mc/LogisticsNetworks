package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.network.TransferVisualPayload;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.render.LogisticsNodeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class TransferVisuals {

    private static final int LIFETIME = 24;
    private static final int FADE_TICKS = 8;
    private static final Map<Key, ActivePath> ACTIVE = new HashMap<>();

    private static ClientLevel activeLevel;

    private TransferVisuals() {
    }

    public static void accept(TransferVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ready(minecraft)) {
            return;
        }
        if (activeLevel != minecraft.level) {
            ACTIVE.clear();
            activeLevel = minecraft.level;
        }
        long tick = minecraft.level.getGameTime();
        for (TransferVisualPayload.Path path : payload.paths()) {
            if (!eligible(path)) {
                continue;
            }
            Key key = new Key(path.sourceId(), path.targetId(), path.typeOrdinal(), path.shape());
            ActivePath current = ACTIVE.get(key);
            if (current != null) {
                current.refresh(path, payload.color(), tick);
                continue;
            }
            ActivePath added = new ActivePath(path, payload.color(), tick);
            if (ACTIVE.size() < ClientConfig.maxTransferVisuals) {
                ACTIVE.put(key, added);
            } else {
                replaceFarthest(minecraft, key, added);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ready(minecraft)) {
            ACTIVE.clear();
            activeLevel = minecraft.level;
            return;
        }
        if (activeLevel != minecraft.level) {
            ACTIVE.clear();
            activeLevel = minecraft.level;
        }

        long tick = minecraft.level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> tick - entry.getValue().lastRefresh >= LIFETIME
                || !eligible(entry.getValue().path));
        trimToLimit(minecraft);
        for (ActivePath path : ACTIVE.values()) {
            spawn(minecraft.level, path, tick);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        activeLevel = null;
    }

    private static boolean ready(Minecraft minecraft) {
        return ClientConfig.showTransferVisuals && minecraft.level != null && minecraft.player != null
                && minecraft.player.isHolding(Registration.WRENCH.get());
    }

    private static boolean eligible(TransferVisualPayload.Path path) {
        return switch (path.shape()) {
            case FULL -> LogisticsNodeRenderer.isWithinWrenchRenderLimit(path.sourceEntityId())
                    || LogisticsNodeRenderer.isWithinWrenchRenderLimit(path.targetEntityId());
            case OUTBOUND -> LogisticsNodeRenderer.isWithinWrenchRenderLimit(path.sourceEntityId());
            case INBOUND -> LogisticsNodeRenderer.isWithinWrenchRenderLimit(path.targetEntityId());
        };
    }

    private static void replaceFarthest(Minecraft minecraft, Key key, ActivePath added) {
        Map.Entry<Key, ActivePath> farthest = null;
        double farthestDistance = -1.0;
        for (Map.Entry<Key, ActivePath> entry : ACTIVE.entrySet()) {
            double distance = entry.getValue().distanceTo(minecraft.player.position());
            if (distance > farthestDistance) {
                farthest = entry;
                farthestDistance = distance;
            }
        }
        if (farthest != null && added.distanceTo(minecraft.player.position()) < farthestDistance) {
            ACTIVE.remove(farthest.getKey());
            ACTIVE.put(key, added);
        }
    }

    private static void trimToLimit(Minecraft minecraft) {
        while (ACTIVE.size() > ClientConfig.maxTransferVisuals) {
            Key farthest = null;
            double distance = -1.0;
            for (Map.Entry<Key, ActivePath> entry : ACTIVE.entrySet()) {
                double candidate = entry.getValue().distanceTo(minecraft.player.position());
                if (candidate > distance) {
                    farthest = entry.getKey();
                    distance = candidate;
                }
            }
            ACTIVE.remove(farthest);
        }
    }

    private static void spawn(ClientLevel level, ActivePath active, long tick) {
        ChannelType type = ChannelType.values()[active.path.typeOrdinal()];
        int cycle = switch (type) {
            case ENERGY -> 8;
            case FLUID, SOURCE -> 18;
            case CHEMICAL -> 20;
            default -> 16;
        };
        if ((type == ChannelType.ITEM || type == ChannelType.CHEMICAL || type == ChannelType.SOURCE)
                && ((tick + active.keyHash) & 1L) != 0L) {
            return;
        }

        double progress = (tick - active.createdTick) % cycle / (double) cycle;
        Vec3 point = active.point(progress, type);
        float fade = Math.min(1.0f, (LIFETIME - (tick - active.lastRefresh)) / (float) FADE_TICKS);
        float scale = switch (type) {
            case FLUID -> 0.55f;
            case ENERGY -> 0.45f + ((tick & 1L) == 0L ? 0.2f : 0.0f);
            case CHEMICAL -> 0.7f;
            case SOURCE -> 0.9f + (float) Math.sin(progress * Math.PI * 2.0) * 0.2f;
            default -> 0.65f;
        };
        float pulse = 0.85f + (float) Math.sin(progress * Math.PI * 2.0) * 0.2f;
        level.addParticle(new DustParticleOptions(active.color, Math.max(0.1f, scale * pulse * fade)),
                point.x, point.y, point.z, 0.0, 0.0, 0.0);
        if (active.path.shape() != TransferVisualPayload.Shape.FULL && (tick + active.keyHash) % 3L == 0L) {
            Vec3 portal = active.portalPoint(tick);
            level.addParticle(new DustParticleOptions(active.color, Math.max(0.1f, 0.75f * fade)),
                    portal.x, portal.y, portal.z, 0.0, 0.0, 0.0);
        }
    }

    private record Key(UUID sourceId, UUID targetId, int typeOrdinal, TransferVisualPayload.Shape shape) {
    }

    private static final class ActivePath {
        private TransferVisualPayload.Path path;
        private int color;
        private long lastRefresh;
        private final long createdTick;
        private final int keyHash;

        private ActivePath(TransferVisualPayload.Path path, int color, long tick) {
            this.path = path;
            this.color = color;
            this.lastRefresh = tick;
            this.createdTick = tick;
            this.keyHash = path.sourceId().hashCode() * 31 + path.targetId().hashCode() * 7 + path.typeOrdinal();
        }

        private void refresh(TransferVisualPayload.Path path, int color, long tick) {
            this.path = path;
            this.color = color;
            this.lastRefresh = tick;
        }

        private double distanceTo(Vec3 player) {
            Vec3 source = Vec3.atBottomCenterOf(path.sourcePos());
            Vec3 target = Vec3.atBottomCenterOf(path.targetPos());
            return switch (path.shape()) {
                case FULL -> Math.min(player.distanceToSqr(source), player.distanceToSqr(target));
                case OUTBOUND -> player.distanceToSqr(source);
                case INBOUND -> player.distanceToSqr(target);
            };
        }

        private Vec3 point(double progress, ChannelType type) {
            Vec3 source = Vec3.atBottomCenterOf(path.sourcePos()).add(0.0, 0.35, 0.0);
            Vec3 target = Vec3.atBottomCenterOf(path.targetPos()).add(0.0, 0.35, 0.0);
            if (path.shape() != TransferVisualPayload.Shape.FULL) {
                Vec3 local = path.shape() == TransferVisualPayload.Shape.OUTBOUND ? source : target;
                Vec3 portal = portalCenter(local);
                source = path.shape() == TransferVisualPayload.Shape.OUTBOUND ? local : portal;
                target = path.shape() == TransferVisualPayload.Shape.OUTBOUND ? portal : local;
            }
            Vec3 delta = target.subtract(source);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            Vec3 side = horizontal > 0.001 ? new Vec3(-delta.z / horizontal, 0.0, delta.x / horizontal)
                    : new Vec3(1.0, 0.0, 0.0);
            double offset = (path.typeOrdinal() - 2) * 0.09;
            double height = path.shape() == TransferVisualPayload.Shape.FULL
                    ? Math.max(1.5, Math.min(6.0, source.distanceTo(target) * 0.2)) : 0.7;
            Vec3 control = source.add(target).scale(0.5).add(side.scale(offset)).add(0.0, height, 0.0);
            double inverse = 1.0 - progress;
            Vec3 point = source.scale(inverse * inverse).add(control.scale(2.0 * inverse * progress))
                    .add(target.scale(progress * progress));
            if (type == ChannelType.CHEMICAL) {
                point = point.add(side.scale(Math.sin(progress * Math.PI * 6.0) * 0.12));
            }
            return point;
        }

        private Vec3 portalPoint(long tick) {
            Vec3 local = path.shape() == TransferVisualPayload.Shape.OUTBOUND
                    ? Vec3.atBottomCenterOf(path.sourcePos()).add(0.0, 0.35, 0.0)
                    : Vec3.atBottomCenterOf(path.targetPos()).add(0.0, 0.35, 0.0);
            Vec3 center = portalCenter(local);
            double angle = (tick + keyHash) * 0.7;
            return center.add(Math.cos(angle) * 0.28, Math.sin(angle * 1.7) * 0.12, Math.sin(angle) * 0.28);
        }

        private Vec3 portalCenter(Vec3 local) {
            double angle = keyHash * 0.017;
            return local.add(Math.cos(angle) * 0.8, 2.5, Math.sin(angle) * 0.8);
        }
    }
}
