package io.github.term4.minestommechanics.platform.player;

import io.github.term4.minestommechanics.platform.compatibility.CompatState;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyEquipmentFix;
import io.github.term4.minestommechanics.platform.fixes.client.LegacyViewDistanceFix;
import io.github.term4.minestommechanics.platform.fixes.client.SelfMetaFilter;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.player.ClientSettings;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The library's custom {@link Player}: self-echo suppression ({@link SelfMetaFilter}), a position-broadcast
 * throttle, the self-placement exclusion, and the cross-version compat overrides (pose/hitbox/eye/attack-box,
 * state in {@link CompatState}). Each behavior is opt-in and wired by {@code MinestomMechanics}.
 */
public class OptimizedPlayer extends Player {

    private @Nullable SelfMetaFilter selfMetaFilter = SelfMetaFilter.defaultPlayerFilter();
    private boolean processingClientInput = false;
    private int positionBroadcastInterval = 1;
    private boolean selfPlacing = false;
    private final CompatState compat = new CompatState();

    public OptimizedPlayer(PlayerConnection connection, GameProfile gameProfile) {
        super(connection, gameProfile);
        // vanilla scans item pickups every tick; Minestom's default 5-tick cooldown adds up to 250ms of pickup lag
        this.itemPickupCooldown = new net.minestom.server.utils.time.Cooldown(java.time.Duration.ZERO);
    }

    /** Set by {@code MetaFix} around the client-input listeners; while {@code true}, self-bound echoes are filtered. */
    public void setProcessingClientInput(boolean value) {
        this.processingClientInput = value;
    }

    /** The active self-metadata filter, or {@code null} when filtering is disabled. */
    public @Nullable SelfMetaFilter getSelfMetaFilter() {
        return selfMetaFilter;
    }

    public void setSelfMetaFilter(@Nullable SelfMetaFilter filter) {
        this.selfMetaFilter = filter;
    }

    /**
     * Updates player state without sending the change to this player; viewers still receive it.
     * <pre>{@code player.suppressSelf(() -> player.setSneaking(true)); }</pre>
     */
    public void suppressSelf(@NotNull Runnable action) {
        processingClientInput = true;
        try {
            action.run();
        } finally {
            processingClientInput = false;
        }
    }

    @Override
    public void sendPacketToViewersAndSelf(@NotNull SendablePacket packet) {
        if (processingClientInput && selfMetaFilter != null) {
            // client-predicted metadata (crouch, use item, elytra): viewers get the full packet, self only the rest
            if (packet instanceof EntityMetaDataPacket(int entityId, Map<Integer, Metadata.Entry<?>> entries) && entityId == getEntityId()) {
                Map<Integer, Metadata.Entry<?>> filtered = selfMetaFilter.filter(entries);
                if (filtered != null) {
                    if (!filtered.isEmpty()) {
                        sendPacket(new EntityMetaDataPacket(entityId, filtered));
                    }
                    sendPacketToViewers(packet);
                    return;
                }
            }
            if (packet instanceof EntityAttributesPacket attr
                    && attr.entityId() == getEntityId()
                    && selfMetaFilter.suppressAttributes()) {
                sendPacketToViewers(packet);
                return;
            }
        }
        super.sendPacketToViewersAndSelf(packet);
    }

    /** Outgoing transforms: {@code attack_range} stamped onto seen items + empty equipment slots stripped. Both no-op when off. */
    @Override
    public void sendPacket(@NotNull SendablePacket packet) {
        super.sendPacket(LegacyEquipmentFix.rewrite(compat.stampAttackRange(packet)));
    }

    // bulk equipment resends (respawn/teleport) group into a CachedPacket the per-viewer transform can't unwrap,
    // so strip empty slots before grouping - else BODY=AIR reaches ViaBackwards and hides the chestplate
    @Override
    public void sendPacketToViewers(@NotNull SendablePacket packet) {
        super.sendPacketToViewers(LegacyEquipmentFix.rewrite(packet));
    }

    /**
     * {@link Player#updatePose()} recomputes the pose every tick (crawl enter/exit), not only in packet listeners -
     * without arming the echo guard here the self-bound pose echo slips through (the crawl/stand stutter).
     * Save/restore so it nests; a direct {@code setPose} doesn't route here, so server-authoritative poses still echo.
     */
    @Override
    protected void updatePose() {
        boolean previous = processingClientInput;
        processingClientInput = true;
        compat.resetInterceptedPose(); // setPose (via super.updatePose) re-captures any disabled pose computed this tick
        try {
            super.updatePose();
        } finally {
            processingClientInput = previous;
        }
    }

    /**
     * A compat-disabled pose is rewritten to {@code STANDING} before it reaches metadata (1.8 parity). Sent with the
     * echo guard cleared so the correction reaches the mispredicting client; the metadata layer dedups the repeat.
     */
    @Override
    public void setPose(@NotNull EntityPose pose) {
        if (compat.isPoseDisabled(pose)) {
            compat.recordInterceptedPose(pose); // what the client believes it's in
            boolean previous = processingClientInput;
            processingClientInput = false;
            try {
                super.setPose(EntityPose.STANDING);
            } finally {
                processingClientInput = previous;
            }
            return;
        }
        super.setPose(pose);
    }

    /** This player's cross-version compat state + logic (pose/hitbox/eye/attack-box); pushed from {@code CompatConfig}. */
    public @NotNull CompatState compat() { return compat; }

    // legacyHitbox: the server box stays standing regardless of pose (1.8 parity; CompatMovement blocks the sneak gap)
    @Override
    public BoundingBox getBoundingBox() {
        return compat.legacyHitbox() ? boundingBox : super.getBoundingBox();
    }

    /** The SERVER-treated eye height (projectile spawn, drowning); what the client believes is {@code ClientEye}. */
    @Override
    public double getEyeHeight() {
        return compat.eyeHeight(super.getEyeHeight(), getPose());
    }

    /** Position-broadcast cadence to viewers: 1 = every tick (Minestom), 2 = every other (Spigot). */
    public void setPositionBroadcastInterval(int interval) {
        if (interval < 1) throw new IllegalArgumentException("interval must be >= 1");
        this.positionBroadcastInterval = interval;
    }

    public int getPositionBroadcastInterval() {
        return positionBroadcastInterval;
    }

    @Override
    public void refreshPosition(Pos newPosition, boolean ignoreView, boolean sendPackets) {
        if (sendPackets) {
            // identity at 20 TPS, throttled toward ~client Hz as server TPS rises
            int cadence = TickScaler.clientCadence(positionBroadcastInterval);
            if (cadence > 1 && getAliveTicks() % cadence != 0) sendPackets = false;
        }
        super.refreshPosition(newPosition, ignoreView, sendPackets); // api-internal override
    }

    // clamp the reported view distance to the instance's - Minestom's refreshSettings over-sends chunks past the cap
    // (the legacy-client "invisibility band"); temporary until the upstream fix lands
    @Override
    public void refreshSettings(@NotNull ClientSettings settings) {
        super.refreshSettings(LegacyViewDistanceFix.clamp(getInstance(), settings));
    }

    /** Armed by {@code SelfPlacementFix} while this player's own placement is processed; lets a passable block into their body. */
    public void setSelfPlacing(boolean value) {
        this.selfPlacing = value;
    }

    @Override
    public boolean preventBlockPlacement() {
        return !selfPlacing && super.preventBlockPlacement();
    }
}
