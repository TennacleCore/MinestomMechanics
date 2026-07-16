package io.github.term4.minestommechanics.mechanics.vanilla18;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.effect.Effects;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.death.DeathConfig;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.platform.player.PlayerConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;

/**
 * Vanilla 1.8 preset - the composed, pure-mechanics {@link MechanicsProfile}. The individual 1.8 configs live in the
 * {@code mechanics.vanilla18} subpackage ({@link Attack}, {@link Damage}, {@link Knockback}, {@link Explosion},
 * {@link Projectiles}, {@link Attributes}, {@link Consumables}, {@link Blocking}, {@link Movement}, {@link Player}, and
 * {@code Items}), which are also the canonical defaults the systems fall back to. Assign with {@code mm.profiles().setGlobal(Vanilla18.profile())}.
 *
 * <p>Carries mechanics only - no compat or fixes (those install separately). Includes the item registry ({@link Items}).
 */
public final class Vanilla18 {

    private Vanilla18() {}

    /** The vanilla 1.8 mechanics profile. */
    public static MechanicsProfile profile() {
        return MechanicsProfile.builder()
                .set(MechanicsKeys.ATTACK, Attack.config())
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.DEATH, Death.config())
                .set(MechanicsKeys.KNOCKBACK, Knockback.melee())
                .set(MechanicsKeys.PLAYER, Player.config())
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.ITEM_PHYSICS, io.github.term4.minestommechanics.vri.DroppedItemEntity.Model.LEGACY)
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                .set(MechanicsKeys.ATTRIBUTES, Attributes.config())
                .set(MechanicsKeys.CONSUMABLES, Consumables.config())
                .set(MechanicsKeys.BLOCKING, Blocking.config())
                .set(MechanicsKeys.EXPLOSION, Explosion.config())
                .set(MechanicsKeys.ITEMS, Items.registry())
                .set(MechanicsKeys.EFFECTS, Effects.vanilla18())
                .build();
    }

    // Individual 1.8 base configs, for presets layering deltas on top - their classes share simple names with the
    // preset's own, so this facade is the importable seam.
    public static AttackConfig attack() { return Attack.config(); }
    public static AttackEvent.AttackRule.Ruleset attackRuleset() { return Attack.ruleset(); }
    public static DamageConfig damage() { return Damage.config(); }
    public static DeathConfig death() { return Death.config(); }
    public static KnockbackConfig knockback() { return Knockback.melee(); }
    public static PlayerConfig player() { return Player.config(); }
    public static ExplosionConfig explosion() { return Explosion.config(); }
    public static ProjectileConfig projectiles() { return Projectiles.config(); }
}
