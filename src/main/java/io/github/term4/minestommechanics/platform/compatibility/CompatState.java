package io.github.term4.minestommechanics.platform.compatibility;

import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.AttackRange;
import net.minestom.server.network.packet.server.LazyPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Per-player cross-version compat state plus the pose/hitbox/eye/attack-box logic it drives. Pushed from
 * {@link CompatConfig} by {@code PlayerConfigApplier} and consulted by {@code OptimizedPlayer}'s overrides, which only
 * route here - the compat policy lives in this package, not on the player class. One instance per {@code OptimizedPlayer},
 * exposed via {@code OptimizedPlayer.compat()}. Unset margin / empty pose-set = unmanaged.
 */
public final class CompatState {

    private @NotNull Set<EntityPose> disabledPoses = Set.of();
    private boolean restrictMovement = false;
    private boolean legacyHitbox = false;
    private @Nullable Float attackHitboxMargin;
    private boolean disableOffhand = false;
    private boolean restrictSprintSneak = false;
    private boolean restrictSprintUse = false;
    private boolean restrictSwimSpeed = false;
    private @Nullable Double blockPlaceReach;
    private boolean oldPlacement = false;
    /** Whether {@code CompatMovement} forced sprint off (sneak/use) and still owes a restore - so it restores only ITS strip, not a combat sprint-reset (which must keep the 1.8 w-tap requirement). */
    private boolean sprintStripped = false;
    /** The disabled pose Minestom computed this tick (e.g. crawl = {@code SWIMMING}), or {@code null} - reset each updatePose, captured in setPose. The client believes it's in this pose while the server pose is forced to STANDING. */
    private @Nullable EntityPose interceptedPose;
    /** Features this player's Animatium client applies 1.8 behaviour for natively (set by {@code CompatAnimatium} on the {@code animatium:info} handshake) - the enforcers skip the matching server hack for them. */
    private @NotNull Set<AnimatiumFeature> nativeFeatures = Set.of();
    /** Whether this player is an Animatium client (sent the {@code animatium:info} handshake). Gates the feature re-send on profile/instance changes, and distinguishes a feature-less Animatium client from a non-Animatium one (both have an empty {@link #nativeFeatures}). */
    private boolean animatiumClient = false;

    /** Poses rewritten to {@code STANDING} in {@code setPose}. */
    public void setDisabledPoses(@NotNull Set<EntityPose> poses) { this.disabledPoses = poses; }
    public @NotNull Set<EntityPose> disabledPoses() { return disabledPoses; }

    /** Whether moves into hitbox-block collision are rejected (enforced by {@code CompatMovement}). */
    public void setRestrictMovement(boolean v) { this.restrictMovement = v; }
    public boolean restrictMovement() { return restrictMovement; }

    /** Whether the server hitbox/eye height stay at 1.8 dimensions regardless of pose (no crouch shrink). */
    public void setLegacyHitbox(boolean v) { this.legacyHitbox = v; }
    public boolean legacyHitbox() { return legacyHitbox; }

    /** {@code attack_range.hitbox_margin} stamped on the client's view of held items (1.8 = {@code 0.1f}); {@code null} = untouched. */
    public void setAttackHitboxMargin(@Nullable Float margin) { this.attackHitboxMargin = margin; }
    public @Nullable Float attackHitboxMargin() { return attackHitboxMargin; }

    /** Whether the offhand is disabled (F-swap + offhand-slot clicks cancelled; enforced by {@code CompatOffhand}). */
    public void setDisableOffhand(boolean v) { this.disableOffhand = v; }
    public boolean disableOffhand() { return disableOffhand; }

    /** Speed restrictions enforced by {@code CompatMovement} (1.8 parity): clamp the sprint move while sneaking / using an item / in water. */
    public void setRestrictSprintSneak(boolean v) { this.restrictSprintSneak = v; }
    public boolean restrictSprintSneak() { return restrictSprintSneak; }
    public void setRestrictSprintUse(boolean v) { this.restrictSprintUse = v; }
    public boolean restrictSprintUse() { return restrictSprintUse; }
    public void setRestrictSwimSpeed(boolean v) { this.restrictSwimSpeed = v; }
    public boolean restrictSwimSpeed() { return restrictSwimSpeed; }

    /** Max distance (blocks) from the server eye to a placement's clicked point before it's cancelled (enforced by {@code CompatPlacement}); {@code null} = unmanaged. */
    public void setBlockPlaceReach(@Nullable Double v) { this.blockPlaceReach = v; }
    public @Nullable Double blockPlaceReach() { return blockPlaceReach; }

    /** Whether 1.8 placement is enforced: refuse a placement whose clicked cell is air (the creative "quick replace" floating block); enforced by {@code CompatPlacement}. */
    public void setOldPlacement(boolean v) { this.oldPlacement = v; }
    public boolean oldPlacement() { return oldPlacement; }

    /** Whether any speed restriction is enabled (lets {@code CompatMovement} skip players with none). */
    public boolean anySpeedRestriction() { return restrictSprintSneak || restrictSprintUse || restrictSwimSpeed; }

    /** Whether {@code CompatMovement} forced sprint off and owes a restore (so a combat sprint-reset isn't undone). */
    public boolean sprintStripped() { return sprintStripped; }
    public void setSprintStripped(boolean v) { this.sprintStripped = v; }

    /** Whether {@code pose} is disabled (forced to {@code STANDING}). */
    public boolean isPoseDisabled(@NotNull EntityPose pose) { return disabledPoses.contains(pose); }

    /** Records the Animatium features this client applies natively (sent by {@code CompatAnimatium}); the enforcers gate the matching hack off via {@link #handlesNatively}. */
    public void setNativeFeatures(@NotNull Set<AnimatiumFeature> features) { this.nativeFeatures = features; }

    /** Whether this player is an Animatium client (handshake received); gates the feature re-send by {@code CompatAnimatium}. */
    public boolean isAnimatiumClient() { return animatiumClient; }
    public void setAnimatiumClient(boolean v) { this.animatiumClient = v; }

    /** Whether this player's client applies {@code feature} (or {@link AnimatiumFeature#ALL}) natively, so the matching server-side hack should be skipped for it. */
    public boolean handlesNatively(@NotNull AnimatiumFeature feature) {
        return nativeFeatures.contains(AnimatiumFeature.ALL) || nativeFeatures.contains(feature);
    }

    /** Records the disabled pose the client believes it's in (called by setPose when intercepting a disabled pose). */
    public void recordInterceptedPose(@NotNull EntityPose pose) { this.interceptedPose = pose; }

    /** Clears the intercepted pose; called once per tick before pose recomputation (updatePose). */
    public void resetInterceptedPose() { this.interceptedPose = null; }

    /**
     * The pose the CLIENT believes it's in: the disabled pose intercepted this tick (e.g. crawl = {@code SWIMMING}), else
     * the authoritative {@code serverPose}. Lets the compat/reach layer read the client's belief while the server stays
     * STANDING - the pose analogue of the echo fix's client-vs-server split.
     */
    public @NotNull EntityPose clientPose(@NotNull EntityPose serverPose) {
        return interceptedPose != null ? interceptedPose : serverPose;
    }

    /**
     * The server-treated eye height (value (b) of the eye model): with {@code legacyHitbox} on, the fixed 1.8 preset
     * ({@link ClientEye#LEGACY_SNEAKING} sneaking; the standing default already matches), so a crouching modern client
     * still spawns/drowns at the 1.8 eye. Off, {@code nativeEye} (Minestom's native value).
     */
    public double eyeHeight(double nativeEye, @NotNull EntityPose pose) {
        return legacyHitbox && pose == EntityPose.SNEAKING ? ClientEye.LEGACY_SNEAKING : nativeEye;
    }

    /**
     * Stamps the 1.8 {@code attack_range} ({@code hitbox_margin = attackHitboxMargin}) onto the items the client sees in an
     * outgoing inventory packet, so a modern 1.21.11+ client attacks with the 1.8 hitbox box. The server's real items stay
     * clean (only the client's view carries it); a bare-hand attack still uses the client's hardcoded 0.0 margin.
     *
     * <p>Covers all three item-carrying packets after unwrapping the {@link SendablePacket} (a single-slot refresh goes out
     * via {@code sendGroupedPacket} wrapped in a {@code CachedPacket}, and the held/hotbar slot uses
     * {@link SetPlayerInventorySlotPacket}, not {@link SetSlotPacket}) - else a slot update (item pickup, held swap, addItem)
     * reaches the client unstamped and the attack box is wrong until the next full {@link WindowItemsPacket} (open/close).
     *
     * <p>Skipped when the client applies {@link AnimatiumFeature#PICK_INFLATION} natively - it already grows the 1.8 attack
     * box client-side, so stamping would be redundant (and would pollute its inventory view).
     */
    public @NotNull SendablePacket stampAttackRange(@NotNull SendablePacket packet) {
        if (attackHitboxMargin == null || handlesNatively(AnimatiumFeature.PICK_INFLATION)) return packet;
        // Resolve only the state-independent shapes (the inventory packets we stamp are always sent bare; LazyPacket is the
        // generic wrapper). NEVER use extractServerPacket here: its CachedPacket branch forces that packet's single,
        // stateless FramedPacket cache for the state we pass, and stamping runs on every outgoing packet - so a hardcoded
        // PLAY would poison a CachedPacket's cache during a modern client's CONFIGURATION join and corrupt the real config
        // send. None of the stamped packets are ever CachedPackets, so skipping that shape loses no coverage.
        ServerPacket sp = switch (packet) {
            case ServerPacket s -> s;
            case LazyPacket lazy -> lazy.packet();
            default -> null;
        };
        return switch (sp) {
            case SetSlotPacket p -> new SetSlotPacket(p.windowId(), p.stateId(), p.slot(), withAttackRange(p.itemStack()));
            case SetPlayerInventorySlotPacket p -> new SetPlayerInventorySlotPacket(p.slot(), withAttackRange(p.itemStack()));
            case WindowItemsPacket p -> new WindowItemsPacket(p.windowId(), p.stateId(), p.items().stream().map(this::withAttackRange).toList(), withAttackRange(p.carriedItem()));
            case null, default -> packet;
        };
    }

    /** Stamps the 1.8 {@code attack_range} (reach 3, the configured {@code hitboxMargin}) onto a non-air item; returns it unchanged otherwise. */
    private ItemStack withAttackRange(ItemStack item) {
        return item.isAir() ? item : item.with(DataComponents.ATTACK_RANGE, new AttackRange(0f, 3f, 0f, 5f, attackHitboxMargin, 1f));
    }
}
