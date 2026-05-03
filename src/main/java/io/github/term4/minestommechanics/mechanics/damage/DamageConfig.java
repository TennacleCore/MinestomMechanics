package io.github.term4.minestommechanics.mechanics.damage;

// TODO: A. make this a mutable config, B. make tick based values scale automatically with server TPS, C. better api

/** Configuration for the damage system */
public final class DamageConfig {

    /** Ticks an entity is "invulnerable" for after taking damage */
    public int invulTicks = 9;    // default 10 ticks, 500ms

    public DamageConfig() {}

    public DamageConfig copy() {
        var c = new DamageConfig();
        c.invulTicks = this.invulTicks;
        return c;
    }

    public static DamageConfig defaultConfig() {
        return new DamageConfig();
    }
}
