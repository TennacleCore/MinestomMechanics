package io.github.term4.minestommechanics.mechanics.explosion;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfigResolver.ExplosionContext;
import io.github.term4.minestommechanics.entity.DroppedItemEntity;
import io.github.term4.minestommechanics.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla's block-destruction pass. The 16³-shell ray is IDENTICAL in 1.8 and 26.1 (verified in
 * {@code docs/HANDOFF-explosion-block-breaking.md}); the versions differ only in what resists a ray and
 * whether it stops at the world border, so one implementation serves both via {@link BlockBreaking.Model}.
 *
 * <p>Selection must run BEFORE entity damage and destruction AFTER it - exposure rays have to meet intact
 * geometry, or a blast shields itself.
 */
final class ExplosionBlocks {

    /** Ray lattice edge; only its shell is cast (1352 of 4096). */
    private static final int GRID = 16;
    private static final float STEP = 0.3F;
    /** Per-step intensity loss, independent of what the ray passes through. */
    private static final float STEP_DECAY = 0.22500001F;
    /** Water and lava; a modern ray reads the fluid's resistance alongside the block's. */
    private static final double FLUID_RESISTANCE = 100.0;
    private static final int PICKUP_DELAY_TICKS = 10;

    private ExplosionBlocks() {}

    /** The positions this explosion destroys, in no particular order. */
    static @NotNull List<Point> select(MechanicsWorld world, Point center, float power,
                                       BlockBreaking cfg, ExplosionContext ctx) {
        return cfg.model() == BlockBreaking.Model.SPHERE
                ? sphere(world, center, power, cfg, ctx)
                : rays(world, center, power, cfg, ctx);
    }

    private static List<Point> rays(MechanicsWorld world, Point center, float power,
                                    BlockBreaking cfg, ExplosionContext ctx) {
        boolean modern = cfg.model() == BlockBreaking.Model.RAY_MODERN;
        Set<Point> hit = new HashSet<>();
        var rnd = ThreadLocalRandom.current();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                for (int z = 0; z < GRID; z++) {
                    if (x != 0 && x != GRID - 1 && y != 0 && y != GRID - 1 && z != 0 && z != GRID - 1) continue;
                    double dx = x / 15.0F * 2.0F - 1.0F, dy = y / 15.0F * 2.0F - 1.0F, dz = z / 15.0F * 2.0F - 1.0F;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= len; dy /= len; dz /= len;

                    float intensity = power * (0.7F + rnd.nextFloat() * 0.6F);
                    double px = center.x(), py = center.y(), pz = center.z();
                    while (intensity > 0.0F) {
                        BlockVec pos = new BlockVec(Math.floor(px), Math.floor(py), Math.floor(pz));
                        if (modern && !inBounds(world, pos)) break;
                        Block block = world.getBlock(pos);
                        double resistance = resistance(block, cfg, ctx, modern);
                        if (resistance >= 0) intensity -= (float) ((resistance + 0.3F) * 0.3F);
                        if (intensity > 0.0F && cfg.canBreak(block, pos, ctx)) hit.add(pos);
                        px += dx * STEP; py += dy * STEP; pz += dz * STEP;
                        intensity -= STEP_DECAY;
                    }
                }
            }
        }
        return new ArrayList<>(hit);
    }

    /** No shadowing: every breakable block whose centre is within {@code power}. */
    private static List<Point> sphere(MechanicsWorld world, Point center, float power,
                                      BlockBreaking cfg, ExplosionContext ctx) {
        List<Point> hit = new ArrayList<>();
        int r = (int) Math.ceil(power);
        double rSq = power * power;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) continue;
                    BlockVec pos = new BlockVec(Math.floor(center.x()) + dx, Math.floor(center.y()) + dy,
                            Math.floor(center.z()) + dz);
                    Block block = world.getBlock(pos);
                    if (block.isAir() || Double.isInfinite(cfg.resistance(block, ctx))) continue;
                    if (cfg.canBreak(block, pos, ctx)) hit.add(pos);
                }
            }
        }
        return hit;
    }

    /** {@code -1} = contributes nothing (1.8: air; modern: air with no fluid). */
    private static double resistance(Block block, BlockBreaking cfg, ExplosionContext ctx, boolean modern) {
        double fluid = modern && isFluid(block) ? FLUID_RESISTANCE : -1;
        if (block.isAir()) return fluid;
        double own = cfg.resistance(block, ctx);
        return Math.max(own, fluid);
    }

    private static boolean isFluid(Block block) {
        return block.compare(Block.WATER) || block.compare(Block.LAVA)
                || "true".equals(block.getProperty("waterlogged"));
    }

    private static boolean inBounds(MechanicsWorld world, Point pos) {
        var dim = world.instance().getCachedDimensionType();
        return pos.y() >= dim.minY() && pos.y() < dim.minY() + dim.height();
    }

    /** Clears the non-air {@code blocks}, spawns their drops, and returns the cells actually broken. Runs AFTER entity damage; empty under {@code KEEP}. */
    static List<Point> destroy(MechanicsWorld world, List<Point> blocks, float power, BlockBreaking cfg) {
        if (!cfg.interaction().destroys()) return List.of();
        List<Point> broken = new ArrayList<>();
        var rnd = ThreadLocalRandom.current();
        for (Point pos : blocks) {
            Block block = world.getBlock(pos);
            if (block.isAir()) continue;
            if (cfg.interaction() != BlockBreaking.Interaction.DESTROY_NO_DROPS) {
                for (ItemStack stack : BlockBreaking.dropsOf(block)) {
                    // vanilla decay is a per-ITEM roll at 1/power, not one roll for the stack
                    int kept = cfg.interaction() == BlockBreaking.Interaction.DESTROY_WITH_DROPS
                            ? stack.amount() : survivors(stack.amount(), power, rnd);
                    if (kept > 0) drop(world, pos, stack.withAmount(kept));
                }
            }
            world.setBlock(pos, Block.AIR);
            broken.add(pos);
        }
        return broken;
    }

    /**
     * Vanilla's incendiary pass over the SELECTED set (not the destroyed one - {@code KEEP} still lights fires):
     * 1 in 3, the cell must be air, and what is under it must be solid. Runs last.
     *
     * <p>1.8 tests the RNG after those two checks and modern before, so the odds match but the random streams do
     * not; only seed-exact reproduction could tell.
     */
    static void placeFire(MechanicsWorld world, List<Point> blocks) {
        var rnd = ThreadLocalRandom.current();
        for (Point pos : blocks) {
            if (rnd.nextInt(3) != 0) continue;
            if (!world.getBlock(pos).isAir()) continue;
            if (!world.getBlock(pos.sub(0, 1, 0)).isSolid()) continue;
            world.setBlock(pos, Block.FIRE);
        }
    }

    private static int survivors(int amount, float power, ThreadLocalRandom rnd) {
        float chance = 1.0F / power;
        int kept = 0;
        for (int i = 0; i < amount; i++) if (rnd.nextFloat() <= chance) kept++;
        return kept;
    }

    private static void drop(MechanicsWorld world, Point pos, ItemStack stack) {
        DroppedItemEntity.spawn(world, new Pos(pos.blockX() + 0.5, pos.blockY() + 0.5, pos.blockZ() + 0.5),
                Vec.ZERO, stack, null, PICKUP_DELAY_TICKS);
    }
}
