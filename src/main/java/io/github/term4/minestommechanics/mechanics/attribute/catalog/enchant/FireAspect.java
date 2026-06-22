package io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant;

import io.github.term4.minestommechanics.mechanics.attribute.source.ItemSource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import io.github.term4.minestommechanics.mechanics.attribute.combat.HitContext;
import io.github.term4.minestommechanics.mechanics.attribute.combat.OnHit;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * Fire Aspect (enchant) - ignites the victim on a melee hit. Vanilla (1.8 + 26 identical): {@code level × 4} seconds of
 * fire ({@code EntityHuman.attack} {@code setOnFire(level*4)}). An {@link ItemSource} with no attribute modifiers - it's
 * an {@link OnHit} side effect; the burn itself is the existing {@code on_fire} damage type once the victim is alight.
 * Triggered by the damage system's post-hit weapon dispatch (a damage-domain combat enchant).
 */
public final class FireAspect {

    public static final Key KEY = Key.key("minecraft:fire_aspect");
    /** Fire ticks per enchant level: 4 seconds × 20 ticks. */
    private static final int TICKS_PER_LEVEL = 4 * 20;

    public static final Source INSTANCE = new FireAspectSource();

    private FireAspect() {}

    private static final class FireAspectSource extends ItemSource implements OnHit {
        private FireAspectSource() { super(KEY); }

        @Override public List<Mod> modifiers(int level) { return List.of(); }

        @Override public void onHit(HitContext ctx) {
            if (ctx.level() <= 0) return;
            // fire duration is real-time; Minestom decrements fireTicks at server TPS, so scale it (identity at 20)
            ctx.victim().setFireTicks(TickScaler.duration(ctx.level() * TICKS_PER_LEVEL, DamageSystem.KEY));
        }
    }
}
