package io.github.term4.minestommechanics.platform.player;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfiles;
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
    /** Vanilla generic.attack_speed base, restored when compat stops removing the cooldown. */
    private static final double DEFAULT_ATTACK_SPEED = 4.0;

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
        // Reads two members of the same player's profile - resolve its scope chain once.
        MechanicsProfiles.Resolved profile = mm.profiles().resolved(player);
        PlayerConfig cfg = profile.get(MechanicsKeys.PLAYER);
        if (cfg != null && cfg.positionBroadcastInterval != null) {
            op.setPositionBroadcastInterval(Math.max(1, cfg.positionBroadcastInterval));
        }
        // Compat is set in one pass from the resolved config (or all-off when no scope sets it), so a profile swap is a clean
        // mode SWITCH - the previous mode's state never sticks. Operational/identity state (Animatium features) is untouched.
        CompatConfig compat = profile.get(MechanicsKeys.COMPAT);
        var state = op.compat();
        Float prevMargin = state.attackHitboxMargin();
        boolean prevCooldownRemoved = state.attackCooldownRemoved();
        state.apply(compat);
        // Re-send items only when the attack_range stamp actually changed (margin set / cleared / retuned) - the join inventory
        // is sent before this applies, so a change wouldn't reach the client until the next inventory packet otherwise.
        if (!java.util.Objects.equals(prevMargin, state.attackHitboxMargin())) op.getInventory().update();
        // Attack cooldown: a huge ATTACK_SPEED removes the modern cooldown (1.8-style, hits never weaken). Touch the attribute
        // only on a real change, so a non-compat server's attack speed is never clobbered; restore the default base on switch-off.
        boolean nowCooldownRemoved = compat != null && Boolean.TRUE.equals(compat.removeAttackCooldown);
        if (nowCooldownRemoved != prevCooldownRemoved) {
            var attackSpeed = op.getAttribute(Attribute.ATTACK_SPEED);
            if (attackSpeed != null) attackSpeed.setBaseValue(nowCooldownRemoved ? REMOVED_ATTACK_COOLDOWN_SPEED : DEFAULT_ATTACK_SPEED);
            state.setAttackCooldownRemoved(nowCooldownRemoved);
        }
        // Re-push the Animatium feature set on every apply - the client handshakes only once on join, so a kit/profile swap or
        // instance/world change (this also runs on PlayerSpawnEvent) must re-send it. No-op for non-Animatium clients, and runs
        // even when compat is null so a profile that drops compat clears the client's features.
        CompatAnimatium.applyFeatures(mm, player);
    }
}
