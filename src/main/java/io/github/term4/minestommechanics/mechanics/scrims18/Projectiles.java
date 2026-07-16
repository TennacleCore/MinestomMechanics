package io.github.term4.minestommechanics.mechanics.scrims18;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.FishingBobber;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;

/**
 * Scrims 1.8 projectiles: the {@link Vanilla18} 1.8 baseline, but every projectile is FULLY client-predicted -
 * spawn + velocity, then it never synchronizes again (some competitive servers do this; captured as spawn_entity +
 * one entity_velocity, no follow-up teleports). {@code syncInterval(0)} drops the periodic position sync,
 * {@code velocitySyncInterval(0)} the per-tick velocity. Everything else is vanilla18.
 */
public final class Projectiles {

    private Projectiles() {}

    /** Vanilla 1.8 projectiles with the periodic wire sync turned off on every type. */
    public static ProjectileConfig config() {
        ProjectileConfig base = Vanilla18.projectiles();
        return ProjectileConfig.builder(base)
                // generic base covers snowball / egg / pearl / splash (they inherit it)
                .defaults(silent(ProjectileTypeConfig.builder(base.defaults())))
                // arrow + bobber set their own interval, so silence them explicitly
                .typeConfigs(
                        silent(ProjectileTypeConfig.builder(base.typeConfig(Arrow.KEY))),
                        silent(ProjectileTypeConfig.builder(base.typeConfig(FishingBobber.KEY))))
                .build();
    }

    private static ProjectileTypeConfig silent(ProjectileTypeConfig.Builder b) {
        return b.syncInterval(0).velocitySyncInterval(0).build();
    }
}
