package io.github.term4.minestommechanics.mechanics.attribute.combat;

/**
 * An item-borne enchant that fires a side effect when its holder lands a hit (e.g. Fire Aspect's ignite). An
 * {@link io.github.term4.minestommechanics.mechanics.attribute.source.ItemSource} that also implements this; the damage
 * system dispatches it after a landed hit, and the effect routes via {@link HitContext}'s services. Knockback enchants
 * feed the KB computation instead, so they aren't {@code OnHit}.
 */
public interface OnHit {

    /** Fire this enchant's on-hit side effect for {@code ctx} (its holder just hit {@code ctx.victim()}). */
    void onHit(HitContext ctx);
}
