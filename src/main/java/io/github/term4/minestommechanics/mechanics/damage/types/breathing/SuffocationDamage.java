package io.github.term4.minestommechanics.mechanics.damage.types.breathing;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.EnvironmentalDamageTicker;
import io.github.term4.minestommechanics.mechanics.damage.EnvironmentalTickProducer;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.VanillaTypes;
import io.github.term4.minestommechanics.util.BlockContact;
import net.kyori.adventure.key.Key;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.Instance;

/**
 * Suffocation ({@code minecraft:in_wall}). Vanilla 1.8 ({@code inBlock} -&gt; {@code STUCK}) and 26 ({@code isInWall} -&gt;
 * {@code inWall()}) are behaviourally identical: while the head sits in a full occluding cube, deal {@code baseAmount}
 * (1.0) per tick, gated by the invul window (so it lands at the hurt cadence). Self-driven via
 * {@link EnvironmentalDamageTicker} (creative/spectator excluded).
 */
public final class SuffocationDamage extends DamageType implements EnvironmentalTickProducer {

    public static final Key KEY = Key.key("minecraft:in_wall");
    public static final SuffocationDamage INSTANCE = new SuffocationDamage();

    private boolean registered;

    private SuffocationDamage() {
        super(KEY, "Suffocation", VanillaTypes.IN_WALL, DamageTypeConfig.builder(KEY).baseAmount(1.0).build());
    }

    @Override
    public void enable(DamageSystem system, MinestomMechanics mm) {
        EnvironmentalDamageTicker.instance().bind(system);
        if (!registered) {
            EnvironmentalDamageTicker.instance().register(this);
            registered = true;
        }
    }

    @Override
    public void disable() {
        if (registered) {
            EnvironmentalDamageTicker.instance().unregister(this);
            registered = false;
        }
    }

    @Override
    public void tick(LivingEntity living, DamageSystem sys) {
        Instance inst = living.getInstance();
        if (inst == null || !inWall(living, inst)) return;

        DamageSnapshot snap = DamageSnapshot.of(living, this);
        DamageContext ctx = sys.contextFor(snap);
        if (!ctx.typeConfig().enabled(ctx)) return;
        if (DamageSystem.absorbedByWindow(living, ctx.baseAmount())) return;
        sys.apply(snap);
    }

    /**
     * Vanilla 26 {@code Entity.isInWall} - the cleaner, cheaper expression of the same check 1.8 does with its 8-point
     * ring. Build a flat eye-plane box ({@code width * 0.8} wide on X/Z, ~0 tall) centred on the eye and suffocate if any
     * cell it overlaps is a full occluding cube. That's 1-4 block lookups (vs the 1.8 ring's 9) and is exact: the box
     * spans only the cells the head actually occupies. Pose-aware via {@link LivingEntity#getEyeHeight()} (crawling/
     * swimming drop the eye to 0.4, so a wall at standing-head height correctly doesn't suffocate a crawling player).
     *
     * <p>We approximate vanilla's per-block {@code isSuffocating} flag with {@link BlockContact#isFullCube} (collision
     * shape covers all six faces). For a full cube the modern {@code collisionShape ∩ eyeBox} test is always true and the
     * default {@code isSuffocating} (= {@code blocksMotion() && fullBlock}) holds, so these agree - except where vanilla
     * overrides {@code isSuffocating} per block, which Minestom doesn't expose: a transparent full cube (glass, which sets
     * {@code isSuffocating(never)}) wrongly suffocates here, and a non-full-cube block marked {@code isSuffocating(always)}
     * wrongly doesn't. {@code blocksMotion} (PR merged upstream) won't close this gap - glass blocks motion and is a full
     * cube, so only the per-block {@code isSuffocating} override would.
     */
    private static boolean inWall(LivingEntity living, Instance inst) {
        double eye = living.getEyeHeight();
        double half = living.getBoundingBox().width() * 0.4; // width * 0.8, split either side of the eye centre
        BoundingBox eyePlane = new BoundingBox(
                new Vec(-half, eye - 5.0E-7, -half),
                new Vec(half, eye + 5.0E-7, half));
        return BlockContact.scanCells(inst, living.getPosition(), eyePlane, 0, BlockContact::isFullCube);
    }
}
