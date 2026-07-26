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
import org.jetbrains.annotations.Nullable;

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

    private static final float STEP = 0.3F;
    /** Unit shell directions per lattice edge (16 -> 1352 rays, 8 -> 296). */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, double[][]> LATTICES = new java.util.concurrent.ConcurrentHashMap<>();
    /** Per-step intensity loss, independent of what the ray passes through. */
    private static final float STEP_DECAY = 0.22500001F;
    /** Furthest a ray reaches per unit power: max intensity (1.3) / the min per-step decay, in blocks. Bounds the seal scan. */
    private static final float MAX_REACH_PER_POWER = 1.3F / STEP_DECAY * STEP;
    /** Water and lava; a modern ray reads the fluid's resistance alongside the block's. */
    private static final double FLUID_RESISTANCE = 100.0;
    private static final int PICKUP_DELAY_TICKS = 10;

    private ExplosionBlocks() {}

    /** The positions this explosion destroys, in no particular order. */
    static @NotNull List<Point> select(MechanicsWorld world, Point center, float power,
                                       BlockBreaking cfg, ExplosionContext ctx) {
        if (cfg.model() == BlockBreaking.Model.SPHERE) return sphere(world, center, power, cfg, ctx);
        List<Point> hit = rays(world, center, power, cfg, ctx);
        if (cfg.shielding() == BlockBreaking.Shielding.OCCLUSION) {
            // hard shadow (Hypixel): drop a selected cell if the line from the blast centre to its centre crosses a
            // blast-proof block. The ray itself is untouched - it passes straight through glass; only this line sees it.
            Seal seal = scanSeals(world, center, power, cfg, ctx);
            if (seal != null) hit.removeIf(p -> seal.occludes(center.x(), center.y(), center.z(), p));
        }
        return hit;
    }

    /** The blast-proof (shadow-casting) cells within reach, box-indexed for O(1) lookup; {@code null} when there are
     *  none. Reads the world directly, not where random rays landed, so the shadow is deterministic across detonations. */
    private static @Nullable Seal scanSeals(MechanicsWorld world, Point center, float power,
                                            BlockBreaking cfg, ExplosionContext ctx) {
        int cx = (int) Math.floor(center.x()), cy = (int) Math.floor(center.y()), cz = (int) Math.floor(center.z());
        int r = (int) Math.ceil(power * MAX_REACH_PER_POWER) + 1, side = 2 * r + 1, rSq = r * r;
        int minX = cx - r, minY = cy - r, minZ = cz - r;
        boolean[] mask = null; // allocated on the first shadow-caster, so a glass-free blast keeps just the scan
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) continue;
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    Block block = loadedBlock(world, new BlockVec(x, y, z));
                    if (block == null || !cfg.castsShadow(block)) continue;
                    if (mask == null) mask = new boolean[side * side * side];
                    mask[(x - minX) + side * ((y - minY) + side * (z - minZ))] = true;
                }
            }
        }
        return mask == null ? null : new Seal(mask, minX, minY, minZ, side);
    }

    /** The vanilla NxNxN shell (the 16 lattice is byte-exact 1.8; MineMen casts an 8). */
    private static double[][] lattice(int grid) {
        return LATTICES.computeIfAbsent(grid, n -> {
            List<double[]> out = new ArrayList<>();
            int m = n - 1;
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    for (int z = 0; z < n; z++) {
                        if (x != 0 && x != m && y != 0 && y != m && z != 0 && z != m) continue;
                        double dx = (double) x / m * 2.0F - 1.0F, dy = (double) y / m * 2.0F - 1.0F, dz = (double) z / m * 2.0F - 1.0F;
                        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        out.add(new double[]{dx / len, dy / len, dz / len});
                    }
                }
            }
            return out.toArray(double[][]::new);
        });
    }

    private static List<Point> rays(MechanicsWorld world, Point center, float power,
                                    BlockBreaking cfg, ExplosionContext ctx) {
        boolean modern = cfg.model() == BlockBreaking.Model.RAY_MODERN;
        boolean perStep = cfg.charging() == BlockBreaking.Charging.PER_STEP;
        Set<Point> hit = new HashSet<>();
        var rnd = ThreadLocalRandom.current();
        for (double[] dir : lattice(cfg.rayGrid())) {
            double dx = dir[0], dy = dir[1], dz = dir[2];
            float intensity = (float) cfg.rollIntensity(power, rnd);
            double px = center.x(), py = center.y(), pz = center.z();
            BlockVec charged = null; // PER_BLOCK: the last cell that paid resistance
            while (intensity > 0.0F) {
                BlockVec pos = new BlockVec(Math.floor(px), Math.floor(py), Math.floor(pz));
                if (modern && !inBounds(world, pos)) break;
                Block block = loadedBlock(world, pos);
                if (block == null) break; // unloaded reads as solid: the ray stops
                double resistance = resistance(block, cfg, ctx, modern);
                if (resistance >= 0 && (perStep || !pos.equals(charged))) {
                    if (cfg.charging() == BlockBreaking.Charging.THRESHOLD) {
                        // gate, not a cost: a stronger block stops the ray (shields what is behind); the per-ray roll
                        // keeps the reach continuous - under a FIXED intensity the 0.3 sampling quantizes it to rungs
                        if (cfg.charge(resistance) > intensity) break;
                    } else {
                        intensity -= (float) cfg.charge(resistance);
                    }
                    charged = pos;
                }
                if (intensity > 0.0F && cfg.canBreak(block, pos, ctx)) hit.add(pos);
                px += dx * STEP; py += dy * STEP; pz += dz * STEP;
                intensity -= STEP_DECAY;
            }
        }
        return new ArrayList<>(hit);
    }

    /** Blast-proof cells within reach, box-indexed for O(1) lookup. */
    private static final class Seal {
        /** A line clipping a shared block edge counts as a graze, not a crossing: two axis boundaries within this many
         *  blocks of each other are treated as simultaneous and resolved by a fixed axis order, so which cell the line
         *  is deemed to enter never turns on sub-block float jitter in the blast centre (capture-verified vs Hypixel). */
        private static final double GRAZE_EPS = 1e-3;

        private final boolean[] mask;
        private final int minX, minY, minZ, side;

        Seal(boolean[] mask, int minX, int minY, int minZ, int side) {
            this.mask = mask; this.minX = minX; this.minY = minY; this.minZ = minZ; this.side = side;
        }

        private boolean sealAt(int x, int y, int z) {
            int lx = x - minX, ly = y - minY, lz = z - minZ;
            if (lx < 0 || lx >= side || ly < 0 || ly >= side || lz < 0 || lz >= side) return false;
            return mask[lx + side * (ly + side * lz)];
        }

        /** Center-occlusion: does the straight line from the blast centre to {@code target}'s centre cross a blast-proof
         *  cell before reaching it? A voxel walk over the cells strictly between the two - origin and target excluded.
         *  Distances are in blocks (unit direction), so {@link #GRAZE_EPS} is a real edge-clip tolerance. */
        boolean occludes(double cx, double cy, double cz, Point target) {
            int tx = target.blockX(), ty = target.blockY(), tz = target.blockZ();
            double dx = tx + 0.5 - cx, dy = ty + 0.5 - cy, dz = tz + 0.5 - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1e-9) return false;
            dx /= dist; dy /= dist; dz /= dist;
            int x = (int) Math.floor(cx), y = (int) Math.floor(cy), z = (int) Math.floor(cz);
            int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
            double tMaxX = dx != 0 ? (dx > 0 ? x + 1 - cx : cx - x) / Math.abs(dx) : Double.POSITIVE_INFINITY;
            double tMaxY = dy != 0 ? (dy > 0 ? y + 1 - cy : cy - y) / Math.abs(dy) : Double.POSITIVE_INFINITY;
            double tMaxZ = dz != 0 ? (dz > 0 ? z + 1 - cz : cz - z) / Math.abs(dz) : Double.POSITIVE_INFINITY;
            double tDeltaX = dx != 0 ? 1 / Math.abs(dx) : Double.POSITIVE_INFINITY;
            double tDeltaY = dy != 0 ? 1 / Math.abs(dy) : Double.POSITIVE_INFINITY;
            double tDeltaZ = dz != 0 ? 1 / Math.abs(dz) : Double.POSITIVE_INFINITY;
            while (Math.min(tMaxX, Math.min(tMaxY, tMaxZ)) < dist) {
                double m = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
                if (tMaxX <= m + GRAZE_EPS) { x += stepX; tMaxX += tDeltaX; }
                else if (tMaxY <= m + GRAZE_EPS) { y += stepY; tMaxY += tDeltaY; }
                else { z += stepZ; tMaxZ += tDeltaZ; }
                if (x == tx && y == ty && z == tz) return false; // reached the target with a clear line
                if (sealAt(x, y, z)) return true;
            }
            return false;
        }
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
                    Block block = loadedBlock(world, pos);
                    if (block == null || block.isAir() || Double.isInfinite(cfg.resistance(block, ctx))) continue;
                    if (cfg.canBreak(block, pos, ctx)) hit.add(pos);
                }
            }
        }
        return hit;
    }

    /** {@code null} when the chunk is not loaded - a blast must read it as solid, never reach through or edit it.
     *  Minestom's {@code getBlock} throws there even on the nullable {@code Condition} overload. */
    private static @Nullable Block loadedBlock(MechanicsWorld world, Point pos) {
        return world.instance().isChunkLoaded(pos) ? world.getBlock(pos) : null;
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
