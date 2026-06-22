package io.github.term4.minestommechanics.mechanics.attribute.defense;

/**
 * The specialized armor-enchant protection categories the EPF stage gates on. A {@link io.github.term4.minestommechanics.mechanics.damage.types.DamageType}
 * declares which apply to it; general Protection always applies. Mirrors the vanilla damage-source flags (1.8
 * {@code DamageSource}) / damage-type tags (26 {@code is_fire}/{@code is_fall}/{@code is_explosion}/{@code is_projectile}).
 */
public enum ProtectionCategory {
    /** Fire-source damage (in-fire / on-fire / lava): Fire Protection applies. */
    FIRE,
    /** Fall damage: Feather Falling applies. */
    FALL,
    /** Explosion damage: Blast Protection applies. */
    EXPLOSION,
    /** Projectile damage (arrows, thrown): Projectile Protection applies. */
    PROJECTILE
}
