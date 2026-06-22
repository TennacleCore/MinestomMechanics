package io.github.term4.minestommechanics.mechanics.attribute.combat;

/**
 * An item-borne enchant that fires a side effect when its holder lands a hit (Fire Aspect's ignite, later Thorns'
 * reflect, etc.). The enchant is an {@link io.github.term4.minestommechanics.mechanics.attribute.source.ItemSource} that also
 * implements this; the relevant system triggers it (the damage system dispatches weapon on-hit after a fresh landed
 * hit), and the effect routes to its own domain via the {@link HitContext}'s services. Knockback-domain enchants instead
 * feed the knockback computation, so they are not {@code OnHit}.
 */
public interface OnHit {

    /** Fire this enchant's on-hit side effect for {@code ctx} (its holder just hit {@code ctx.victim()}). */
    void onHit(HitContext ctx);
}
