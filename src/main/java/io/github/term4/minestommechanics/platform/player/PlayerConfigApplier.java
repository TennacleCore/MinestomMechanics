package io.github.term4.minestommechanics.platform.player;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.platform.compatibility.CompatAnimatium;
import io.github.term4.minestommechanics.platform.compatibility.CompatConfig;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the scoped player-platform configs - {@link PlayerConfig} and {@link CompatConfig} - at spawn
 * (join and instance change) and on every profile assignment change ({@code MechanicsProfiles} set calls
 * re-apply to all online players), so swaps are live without any polling. Each member is applied only when a
 * scope sets it; otherwise the player is left untouched, so the manual {@link OptimizedPlayer} setters
 * ({@code setPositionBroadcastInterval} / {@code compat().setDisabledPoses}) stay authoritative.
 */
public final class PlayerConfigApplier {

    /** Attack-speed base used to remove the modern cooldown: huge, so the cooldown is always full (no indicator, 1.8-style hits). */
    private static final double REMOVED_ATTACK_COOLDOWN_SPEED = 1024.0;

    private PlayerConfigApplier() {}

    /** Installs the spawn listener. Called by {@code MinestomMechanics.init()} when installPlayerProvider is on. */
    public static void install(MinestomMechanics mm) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:player-config", EventFilter.PLAYER);
        node.addListener(PlayerSpawnEvent.class, e -> apply(mm, e.getPlayer()));
        mm.install(node);
    }

    /** Applies the scoped config to every online player (run when profile assignments change). */
    public static void applyAll(MinestomMechanics mm) {
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) apply(mm, p);
    }

    /** Applies the player's scoped platform configs; each member is a no-op when no scope sets it. */
    public static void apply(MinestomMechanics mm, Player player) {
        if (!(player instanceof OptimizedPlayer op)) return;
        PlayerConfig cfg = mm.profiles().playerFor(player);
        if (cfg != null && cfg.positionBroadcastInterval != null) {
            op.setPositionBroadcastInterval(Math.max(1, cfg.positionBroadcastInterval));
        }
        CompatConfig compat = mm.profiles().compatFor(player);
        if (compat != null) {
            var state = op.compat();
            if (compat.disabledPoses != null) state.setDisabledPoses(compat.disabledPoses);
            if (compat.restrictMovement != null) state.setRestrictMovement(compat.restrictMovement);
            if (compat.legacyHitbox != null) state.setLegacyHitbox(compat.legacyHitbox);
            if (compat.attackHitboxMargin != null) {
                state.setAttackHitboxMargin(compat.attackHitboxMargin);
                // the join inventory is sent before this config applies, so the stamp wouldn't reach the client until the
                // next inventory packet (open/close). Resend now that outgoing items get the attack_range stamp.
                op.getInventory().update();
            }
            if (compat.disableOffhand != null) state.setDisableOffhand(compat.disableOffhand);
            if (compat.restrictSprintSneak != null) state.setRestrictSprintSneak(compat.restrictSprintSneak);
            if (compat.restrictSprintUse != null) state.setRestrictSprintUse(compat.restrictSprintUse);
            if (compat.restrictSwimSpeed != null) state.setRestrictSwimSpeed(compat.restrictSwimSpeed);
            if (compat.blockPlaceReach != null) state.setBlockPlaceReach(compat.blockPlaceReach);
            if (compat.oldPlacement != null) state.setOldPlacement(compat.oldPlacement);
            // Remove the modern attack cooldown server-side (any client): a huge ATTACK_SPEED keeps the cooldown always full,
            // so hits never weaken (1.8-style) and the crosshair indicator never shows. Idempotent (setBaseValue).
            if (Boolean.TRUE.equals(compat.removeAttackCooldown)) {
                var attackSpeed = op.getAttribute(Attribute.ATTACK_SPEED);
                if (attackSpeed != null) attackSpeed.setBaseValue(REMOVED_ATTACK_COOLDOWN_SPEED);
            }
        }
        // Re-push the Animatium feature set on every apply - the client handshakes only once on join, so a kit/profile swap or
        // instance/world change (this also runs on PlayerSpawnEvent) must re-send it. No-op for non-Animatium clients, and runs
        // even when compat is null so a profile that drops compat clears the client's features.
        CompatAnimatium.applyFeatures(mm, player);
    }
}
