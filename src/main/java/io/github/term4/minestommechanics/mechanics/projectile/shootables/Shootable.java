package io.github.term4.minestommechanics.mechanics.projectile.shootables;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

/**
 * A pluggable launcher: an ITEM that fires a projectile (bow, crossbow, fishing rod), as opposed to a thrown item
 * that IS the projectile (snowball/egg/pearl - those are self-launching {@code ProjectileType}s that wire their own
 * use trigger). Mounts its item listeners under the projectile system's node and launches via
 * {@link ProjectileSystem#launch}. Pass implementations to
 * {@link ProjectileSystem#install(MinestomMechanics, ProjectileConfig, Shootable...)} - the projectile-side analog of
 * the attack system's {@code HitDetection}. Teardown follows the system node's lifecycle, so there is no uninstall.
 */
@FunctionalInterface
public interface Shootable {

    /** Mounts this launcher's listeners on the system's {@code node}, launching through {@code system}. */
    void install(@NotNull EventNode<@NotNull Event> node, @NotNull ProjectileSystem system);
}
