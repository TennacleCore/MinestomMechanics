package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.mechanics.damage.types.projectile.ProjectileDamage;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.Bow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;

/**
 * Vanilla 1.8 projectile config: the generic {@link #defaults()} baseline plus per-type entries. The canonical 1.8
 * values, consumed both by the {@link Vanilla18} preset profile and by {@code ProjectileTypeConfig}/the flight entities.
 */
public final class Projectiles {

    private Projectiles() {}

    /**
     * Vanilla 1.8 projectile config: the generic {@link #defaults()} baseline plus per-type entries (presence enables a
     * type at install). All three throwables share the baseline; their differences are in the flight entity (egg -&gt;
     * baby chicken, pearl -&gt; teleport).
     */
    public static ProjectileConfig config() {
        return ProjectileConfig.builder()
                .defaults(defaults())
                .typeConfigs(
                        ProjectileTypeConfig.builder(Snowball.KEY).build(),
                        ProjectileTypeConfig.builder(Egg.KEY).build(),
                        pearl(),
                        arrow())
                .shootables(new Bow()) // the bow launcher (item -> arrow); exists in 1.8
                .build();
    }

    /**
     * The generic vanilla 1.8 throwable baseline every type inherits unless it overrides a knob: zero render box
     * (block collision is a point raytrace), aerodynamics (gravity {@code 0.03}, drag {@code 0.99}), speed {@code 1.5},
     * spread {@code 0.0075}, no shooter-momentum inheritance, spawn {@code 0.1} below the eye + {@code 0.16} lateral,
     * 5-tick shooter immunity then normal self-hits (the pearl overrides with {@code selfHit(PASS_THROUGH)}), 20-tick
     * sync, {@link Knockback#projectile() vanilla knockback} from the thrower, and 0 damage routed through {@link ProjectileDamage}
     * (hurt flash + invul gate; a 0-damage hit still lands). Re-base per-type via
     * {@code ProjectileTypeConfig.builder(Projectiles.defaults())...}.
     */
    public static ProjectileTypeConfig defaults() {
        return ProjectileTypeConfig.builder()
                .boundingBox(0, 0, 0)
                .gravity(0.03).horizontalDrag(0.99).verticalDrag(0.99)
                .speed(1.5).spread(0.0075) // momentumHorizontal/Vertical default 0 (1.8 folds no shooter momentum)
                .spawnOffsetVertical(-0.1).spawnOffsetSideways(0.16)
                // 5-tick collision grace, then self-hits land (vanilla has no self-immunity). The pearl overrides with PASS_THROUGH.
                .shooterImmunityTicks(5)
                // entity-hit margin: grow the target bbox 0.3 per side and ray-test the path; too tight = arrows the 1.8 client predicts as hits fly past.
                .entityHitGrow(0.3)
                .syncInterval(20)
                // knockback pushes from the thrower (not the projectile); melee=false so no sprint extra applies.
                .knockback(Knockback.projectile())
                .knockbackSource(ProjectileTypeConfig.KnockbackSource.SHOOTER)
                // 0 damage, but routed through the damage system so the hurt flash + invul gate fire.
                .damage(0.0).damageType(ProjectileDamage.INSTANCE)
                .removeOnEntityHit(true).removeOnBlockHit(true)
                // throwables destroy (break + effect) on any rejected hit; the arrow overrides to DEFLECT/PASS_THROUGH.
                .invulnHit(ProjectileTypeConfig.HitResponse.DESTROY)
                .build();
    }

    /**
     * Vanilla 1.8 ender pearl overrides (on {@link #defaults()}): {@code selfHit(PASS_THROUGH)} - the 1.8 pearl ignores
     * its own thrower and passes through (unlike snowball/egg, which can self-hit after the immunity window). The teleport
     * + 5 fall damage live in {@code PearlEntity}.
     */
    public static ProjectileTypeConfig pearl() {
        return ProjectileTypeConfig.builder(Pearl.KEY)
                .selfHit(ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .build();
    }

    /**
     * Vanilla 1.8 arrow overrides (on {@link #defaults()}): faster + heavier than a throwable (speed {@code 3.0}, gravity
     * {@code 0.05}), velocity-based damage ({@code damage = 2.0} per-speed multiplier), and it sticks in blocks instead of
     * breaking ({@code removeOnBlockHit = false}). Knockback stays shooter-relative (inherited, not overridden to {@code
     * PROJECTILE}): a plain arrow knocks the victim away from the shooter, not along the arrow's flight. Punch rides as the
     * extra-knockback level, scaling {@link Knockback#melee()}'s {@code extra}* knobs in that same shooter-relative
     * direction. Damage routes through {@link ProjectileDamage} for now (a dedicated {@code minecraft:arrow} type is the follow-up).
     */
    public static ProjectileTypeConfig arrow() {
        return ProjectileTypeConfig.builder(Arrow.KEY)
                .gravity(0.05).speed(3.0)
                .damage(2.0).damageType(ProjectileDamage.INSTANCE)
                .removeOnEntityHit(true).removeOnBlockHit(false)
                .invulnHit(ProjectileTypeConfig.HitResponse.DEFLECT, ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .build();
    }
}
