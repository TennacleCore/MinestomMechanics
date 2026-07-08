package io.github.term4.minestommechanics.platform.compatibility;

import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.AttackRange;
import io.github.term4.minestommechanics.platform.PacketShapes;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Per-player cross-version compat state plus the pose/hitbox/eye/attack-box logic it drives. The policy is the held
 * {@link CompatConfig} (swapped whole by {@link #apply} - a profile push is a clean mode SWITCH, never a sticky merge);
 * the rest is operational/identity state. One instance per {@code OptimizedPlayer}, exposed via {@code compat()}.
 */
public final class CompatState {

    /** All-off policy (a modern client left untouched). */
    private static final CompatConfig OFF = CompatConfig.builder().build();

    private @NotNull CompatConfig policy = OFF;
    private boolean attackCooldownRemoved = false;
    private boolean sprintStripped = false;
    private @Nullable EntityPose interceptedPose;
    private @NotNull Set<AnimatiumFeature> nativeFeatures = Set.of();
    private boolean animatiumClient = false;
    private @NotNull Set<AnimatiumFeature> supportedFeatures = Set.of();
    private boolean legacyClient;

    /**
     * Swaps the whole policy; {@code null} = all off (modern). Operational/identity state is left untouched; the
     * {@code attackHitboxMargin} re-send + attack-cooldown attribute are the caller's job (they touch the player).
     */
    public void apply(@Nullable CompatConfig config) {
        this.policy = config != null ? config : OFF;
    }

    public @NotNull Set<EntityPose> disabledPoses() { return policy.disabledPoses != null ? policy.disabledPoses : Set.of(); }
    /** Whether moves into hitbox-block collision are rejected (enforced by {@code CompatMovement}). */
    public boolean restrictMovement() { return on(policy.restrictMovement); }
    /** Whether the server hitbox/eye height stay at 1.8 dimensions regardless of pose (no crouch shrink). */
    public boolean legacyHitbox() { return on(policy.legacyHitbox); }
    /** {@code attack_range.hitbox_margin} stamped on the client's view of held items; {@code null} = untouched. */
    public @Nullable Float attackHitboxMargin() { return policy.attackHitboxMargin; }
    /** Whether the offhand is disabled (enforced by {@code CompatOffhand}). */
    public boolean disableOffhand() { return on(policy.disableOffhand); }
    /** Sprint clamps while sneaking / using an item / in water (enforced by {@code CompatMovement}). */
    public boolean restrictSprintSneak() { return on(policy.restrictSprintSneak); }
    public boolean restrictSprintUse() { return on(policy.restrictSprintUse); }
    public boolean restrictSwimSpeed() { return on(policy.restrictSwimSpeed); }
    /** Swim-dampen divisors for {@link #restrictSwimSpeed()} (horizontal / vertical); defaults when unset. */
    public double swimFactor() { return policy.swimFactor != null ? policy.swimFactor : 1.25; }
    public double swimVerticalFactor() { return policy.swimVerticalFactor != null ? policy.swimVerticalFactor : 3.0; }
    /** Max blocks from the server eye to a placement's clicked point (enforced by {@code CompatPlacement}); {@code null} = unmanaged. */
    public @Nullable Double blockPlaceReach() { return policy.blockPlaceReach; }
    /** 1.8 placement: refuse a placement whose clicked cell is air (enforced by {@code CompatPlacement}). */
    public boolean oldPlacement() { return on(policy.oldPlacement); }

    private static boolean on(@Nullable Boolean v) { return Boolean.TRUE.equals(v); }

    /** Whether compat removed the modern attack cooldown; tracked so a profile swap restores the default base only on a real change. */
    public boolean attackCooldownRemoved() { return attackCooldownRemoved; }
    public void setAttackCooldownRemoved(boolean v) { this.attackCooldownRemoved = v; }

    /** Whether any speed restriction is enabled (lets {@code CompatMovement} skip players with none). */
    public boolean anySpeedRestriction() { return restrictSprintSneak() || restrictSprintUse() || restrictSwimSpeed(); }

    /** Whether {@code CompatMovement} forced sprint off and owes a restore (so a combat sprint-reset isn't undone). */
    public boolean sprintStripped() { return sprintStripped; }
    public void setSprintStripped(boolean v) { this.sprintStripped = v; }

    /** Whether {@code pose} is disabled (forced to {@code STANDING}). */
    public boolean isPoseDisabled(@NotNull EntityPose pose) { return disabledPoses().contains(pose); }

    /** Records the Animatium features this client applies natively (sent by {@code CompatAnimatium}); the enforcers gate the matching hack off via {@link #handlesNatively}. */
    public void setNativeFeatures(@NotNull Set<AnimatiumFeature> features) { this.nativeFeatures = features; }
    /** The Animatium features this client applies natively (empty for non-Animatium clients). */
    public @NotNull Set<AnimatiumFeature> nativeFeatures() { return nativeFeatures; }

    /** Whether the {@code animatium:info} handshake arrived - distinguishes a feature-less Animatium client from a non-Animatium one (both have empty {@link #nativeFeatures}). */
    public boolean isAnimatiumClient() { return animatiumClient; }
    public void setAnimatiumClient(boolean v) { this.animatiumClient = v; }

    /** Records the features the client's Animatium build advertised it can natively handle (from the {@code animatium:info} capability bits). */
    public void setSupportedFeatures(@NotNull Set<AnimatiumFeature> features) { this.supportedFeatures = features; }
    /** Whether the client advertised native support for {@code feature} (decoder present) - required before sending a wire-format feature like {@link AnimatiumFeature#SHORTS_VELOCITY}. */
    public boolean supports(@NotNull AnimatiumFeature feature) { return supportedFeatures.contains(feature); }

    /** Whether this player's client applies {@code feature} (or {@link AnimatiumFeature#ALL}) natively, so the matching server-side hack should be skipped for it. */
    public boolean handlesNatively(@NotNull AnimatiumFeature feature) {
        return nativeFeatures.contains(AnimatiumFeature.ALL) || nativeFeatures.contains(feature);
    }

    /** Confirmed legacy (&le;1.8) client: skips the {@code attack_range} stamp - already native, and it only round-trips through Via as junk NBT. */
    public boolean legacyClient() { return legacyClient; }
    public void setLegacyClient(boolean v) { this.legacyClient = v; }

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
        return legacyHitbox() && pose == EntityPose.SNEAKING ? ClientEye.LEGACY_SNEAKING : nativeEye;
    }

    /** Whether this client receives the {@code attack_range} stamp - so it's also the only client that can echo it back through creative ({@link #sanitizeInboundItem}). */
    public boolean stampsAttackRange() {
        return policy.attackHitboxMargin != null && !legacyClient && !handlesNatively(AnimatiumFeature.PICK_INFLATION);
    }

    /**
     * Stamps {@code attack_range} (the configured margin + reach) onto the client's VIEW of its items - the server items
     * stay clean; bare-hand keeps the client's hardcoded 0. Must cover all three item-carrying packets or a slot update
     * (pickup, held swap) reaches the client unstamped until the next full {@link WindowItemsPacket}. Skipped when the
     * client grows the attack box natively ({@link AnimatiumFeature#PICK_INFLATION}).
     */
    public @NotNull SendablePacket stampAttackRange(@NotNull SendablePacket packet) {
        if (!stampsAttackRange()) return packet;
        ServerPacket sp = PacketShapes.unwrapStateless(packet);
        return switch (sp) {
            case SetSlotPacket p -> new SetSlotPacket(p.windowId(), p.stateId(), p.slot(), withAttackRange(p.itemStack()));
            case SetPlayerInventorySlotPacket p -> new SetPlayerInventorySlotPacket(p.slot(), withAttackRange(p.itemStack()));
            case WindowItemsPacket p -> new WindowItemsPacket(p.windowId(), p.stateId(), p.items().stream().map(this::withAttackRange).toList(), withAttackRange(p.carriedItem()));
            case null, default -> packet;
        };
    }

    /**
     * Inbound counterpart to {@link #stampAttackRange}: for a stamped client, drops any {@code attack_range} it echoes
     * back through creative (client-authoritative slots), so the client-view stamp never becomes server state - from
     * where it would spread to drops and other viewers. Non-stamped clients and already-clean items pass through.
     */
    public @NotNull ItemStack sanitizeInboundItem(@NotNull ItemStack item) {
        return stampsAttackRange() && item.get(DataComponents.ATTACK_RANGE) != null ? item.without(DataComponents.ATTACK_RANGE) : item;
    }

    private ItemStack withAttackRange(ItemStack item) {
        // respect an item's own attack_range (minigame weapon, spear); vanilla non-spear items ship none, so 1.8-parity
        // items still get the compat box. Also keeps a creative-echoed stamp from being re-stamped onto itself.
        if (item.isAir() || item.get(DataComponents.ATTACK_RANGE) != null) return item;
        float reach = policy.attackReach != null ? policy.attackReach : 3f;
        return item.with(DataComponents.ATTACK_RANGE, new AttackRange(0f, reach, 0f, 5f, policy.attackHitboxMargin, 1f));
    }
}
