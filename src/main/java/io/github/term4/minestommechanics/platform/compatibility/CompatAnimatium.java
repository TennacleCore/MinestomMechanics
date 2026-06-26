package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;

/**
 * Animatium integration: an Animatium client applies 1.8 behaviour natively client-side, so rather than rubber-banding it
 * with our server-side hacks we tell it which features to apply and skip those hacks. Detection is the {@code animatium:info}
 * plugin message the client sends on join (routed here by {@link io.github.term4.minestommechanics.tracking.ClientInfoTracker});
 * on receipt we resolve the feature set for the player's {@link CompatConfig}, send {@code animatium:set_server_features}, and
 * record the set on {@link CompatState} so the enforcers (attack-box stamp, sneak/use sprint strip) gate themselves off for it.
 *
 * <p>The feature set is <em>derived</em> from the compat knobs ({@link #derive}), or fully overridden by
 * {@code CompatConfig.animatiumFeatures}. Features Animatium can't yet do natively (pose disabling, fluids, typed eye height)
 * stay server-side hacks for everyone - that's Track 2 (a fork/PR).
 */
public final class CompatAnimatium {

    /** C2S: sent by an Animatium client on play join ({@code double version} + optional dev string); our detection signal. */
    public static final String INFO_CHANNEL = "animatium:info";
    /** S2C: the {@link BitSet} of enabled {@link AnimatiumFeature} bits (raw bytes, no length prefix). */
    public static final String SET_FEATURES_CHANNEL = "animatium:set_server_features";

    private CompatAnimatium() {}

    /** Routes the {@code animatium:info} handshake through {@link io.github.term4.minestommechanics.tracking.ClientInfoTracker}. Needs the player provider (the feature set is recorded on {@code OptimizedPlayer.compat()}). */
    public static void install(MinestomMechanics mm) {
        mm.clientInfo().onPluginMessage(INFO_CHANNEL, (player, data) -> onInfo(mm, player));
    }

    /** An Animatium client identified itself: mark it, then resolve + send its feature set (re-sent later by {@link #applyFeatures}). */
    private static void onInfo(MinestomMechanics mm, Player player) {
        if (!(player instanceof OptimizedPlayer op)) return;
        op.compat().setAnimatiumClient(true);
        applyFeatures(mm, player);
    }

    /**
     * (Re)resolves and sends an Animatium client's feature set, recording it on {@link CompatState} for the enforcers to gate
     * on. Called on the {@code animatium:info} handshake and again whenever {@code PlayerConfigApplier} re-applies the player's
     * config (kit/profile swap, instance/world change) - the client only handshakes once on join, so the server must re-push
     * the set itself. No-op for non-Animatium clients (so a vanilla client is never wrongly marked as handling features natively).
     */
    public static void applyFeatures(MinestomMechanics mm, Player player) {
        if (!(player instanceof OptimizedPlayer op) || !op.compat().isAnimatiumClient()) return;
        CompatConfig cfg = mm.profiles().resolve(player, MechanicsKeys.COMPAT);
        Set<AnimatiumFeature> features = cfg == null ? Set.of() : resolve(cfg);
        op.compat().setNativeFeatures(features);
        player.sendPluginMessage(SET_FEATURES_CHANNEL, encode(features));
    }

    /** The features to send: the explicit {@code animatiumFeatures} override if set, else the knob-derived set. */
    static Set<AnimatiumFeature> resolve(CompatConfig cfg) {
        return cfg.animatiumFeatures != null ? cfg.animatiumFeatures : derive(cfg);
    }

    /** Maps the enabled compat knobs to their native Animatium equivalents (the default, when no override is set). */
    static Set<AnimatiumFeature> derive(CompatConfig cfg) {
        EnumSet<AnimatiumFeature> set = EnumSet.noneOf(AnimatiumFeature.class);
        if (cfg.attackHitboxMargin != null) set.add(AnimatiumFeature.PICK_INFLATION);
        if (Boolean.TRUE.equals(cfg.legacyHitbox)) set.add(AnimatiumFeature.OLD_SNEAK_HEIGHT);
        if (Boolean.TRUE.equals(cfg.restrictSprintUse)) set.add(AnimatiumFeature.FIX_SPRINT_ITEM_USE);
        if (Boolean.TRUE.equals(cfg.restrictSprintSneak)) set.add(AnimatiumFeature.FIX_SPRINT_SNEAKING);
        if (cfg.disabledPoses != null) {
            // Minecraft has no crawl pose: in-water swim AND the squeeze-crawl are both Pose.SWIMMING, so disabling SWIMMING
            // server-side maps to both client bits (the mod tells them apart by isSwimming vs the fit fallback).
            if (cfg.disabledPoses.contains(EntityPose.SWIMMING)) {
                set.add(AnimatiumFeature.DISABLE_SWIM_POSE);
                set.add(AnimatiumFeature.DISABLE_CRAWL_POSE);
            }
            if (cfg.disabledPoses.contains(EntityPose.FALL_FLYING)) set.add(AnimatiumFeature.DISABLE_ELYTRA_POSE);
        }
        if (Boolean.TRUE.equals(cfg.legacyFluids)) set.add(AnimatiumFeature.OLD_FLUID_PHYSICS);
        if (Boolean.TRUE.equals(cfg.disableElytraFlight)) set.add(AnimatiumFeature.DISABLE_ELYTRA_FLIGHT);
        if (Boolean.TRUE.equals(cfg.oldFlight)) set.add(AnimatiumFeature.OLD_FLIGHT);
        if (Boolean.TRUE.equals(cfg.leftClickItemUsage)) set.add(AnimatiumFeature.LEFT_CLICK_ITEM_USAGE);
        if (Boolean.TRUE.equals(cfg.disableAutoSneak)) set.add(AnimatiumFeature.DISABLE_AUTO_SNEAK);
        // oldPhysics is the bundle default; each per-aspect knob overrides it (null = follow the bundle).
        boolean physics = Boolean.TRUE.equals(cfg.oldPhysics);
        if (physicsAspect(cfg.oldMomentum, physics)) set.add(AnimatiumFeature.OLD_MOMENTUM);
        if (physicsAspect(cfg.disableBedBounce, physics)) set.add(AnimatiumFeature.DISABLE_BED_BOUNCE);
        if (physicsAspect(cfg.disableHoneyPhysics, physics)) set.add(AnimatiumFeature.DISABLE_HONEY_PHYSICS);
        if (physicsAspect(cfg.disableBubbleColumn, physics)) set.add(AnimatiumFeature.DISABLE_BUBBLE_COLUMN);
        if (Boolean.TRUE.equals(cfg.disableEntityPush)) set.add(AnimatiumFeature.DISABLE_ENTITY_PUSH);
        if (Boolean.TRUE.equals(cfg.oldPlacement)) set.add(AnimatiumFeature.OLD_PLACEMENT);
        if (Boolean.TRUE.equals(cfg.disableOffhand)) set.add(AnimatiumFeature.DISABLE_OFFHAND);
        return set;
    }

    /** A per-aspect physics knob's effective value: explicit {@code true}/{@code false} overrides, {@code null} follows the {@code oldPhysics} bundle. */
    private static boolean physicsAspect(@Nullable Boolean knob, boolean bundle) {
        return knob != null ? knob : bundle;
    }

    /** Packs the feature bits into the {@code set_server_features} payload ({@code BitSet.toByteArray}, the inverse of Animatium's {@code BitSet.valueOf}). */
    private static byte[] encode(Set<AnimatiumFeature> features) {
        BitSet bits = new BitSet();
        for (AnimatiumFeature f : features) bits.set(f.bit);
        return bits.toByteArray();
    }
}
