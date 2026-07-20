package io.github.term4.minestommechanics.tracking.motion;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import io.github.term4.minestommechanics.world.MechanicsWorld;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla fluid <b>flow</b> direction - the current that shoves an entity downstream. Pure geometry over the world's
 * fluid cells; it does <strong>not</strong> move anything (the caller carries it as a friction-bled residual, like
 * vanilla's {@code motX/motY/motZ}). The per-cell slope mirrors 1.8 {@code BlockFluids.h} / 26
 * {@code FlowingFluid.getFlow} (neighbour-level gradient + a falling-water down-term); {@link Model} selects how the
 * summed cells become the push.
 */
public final class FluidFlow {

    /**
     * Pluggable fluid behavior: the current {@link #impulse} plus the per-version water quirks ({@link #waterGravity},
     * {@link #waterFriction}, {@link #zeroesAgainstWall}, {@link #pushesInLava}). Built-ins {@link #LEGACY} (1.8) and
     * {@link #MODERN} (26.1), or a custom impl; selected per preset via {@code VelocityConfig.flowModel}. LEGACY is the
     * flat 1.8 {@code normalize(sum) x scale}; MODERN is 26's averaged + depth-scaled impulse.
     */
    public interface Model {

        /** This tick's fluid-current impulse (b/t, already x {@code scale}) for {@code fluid} (WATER/LAVA). {@code movX/movZ} = horizontal velocity (only MODERN's near-still floor reads it). */
        Vec impulse(MechanicsWorld inst, Point pos, BoundingBox box, Block fluid, double scale, double movX, double movZ);

        /** motY (b/t) subtracted per WATER tick (1.8: flat {@code 0.02}; 26: {@code 0.005}, and {@code 0} while sprinting). Lava gravity is model-agnostic. */
        double waterGravity(boolean sprinting);

        /** Horizontal friction the in-water residual bleeds by (1.8: {@code 0.8}; 26: {@code 0.9} sprint-swimming, else {@code 0.8}). */
        double waterFriction(boolean sprinting);

        /** Whether the current zeroes on a collision-blocked axis, so a current pinning a player against a wall never accumulates (1.8 yes; 26 no). */
        boolean zeroesAgainstWall();

        /** Whether this model pushes in <b>lava</b> at all (1.8 no; 26 yes - still subject to the {@code flowLava} config gate). */
        boolean pushesInLava();

        /**
         * This model with LEGACY (1.8) vertical water gravity ({@code 0.02} flat, no sprint exception) - i.e. the modern
         * horizontal current/friction but the 1.8 sink rate + vertical-knockback feel (e.g. Hypixel: modern flow boost
         * horizontally, legacy for vertical). Only {@link #waterGravity} changes; {@link #impulse} (incl. its falling-water
         * Y) stays this model's.
         */
        default Model withLegacyWaterGravity() {
            Model base = this;
            return new Model() {
                @Override public Vec impulse(MechanicsWorld inst, Point pos, BoundingBox box, Block fluid, double scale, double movX, double movZ) {
                    return base.impulse(inst, pos, box, fluid, scale, movX, movZ);
                }
                @Override public double waterGravity(boolean sprinting) { return LEGACY.waterGravity(sprinting); }
                @Override public double waterFriction(boolean sprinting) { return base.waterFriction(sprinting); }
                @Override public boolean zeroesAgainstWall() { return base.zeroesAgainstWall(); }
                @Override public boolean pushesInLava() { return base.pushesInLava(); }
            };
        }

        /** 1.8: flat impulse, {@code 0.02}/{@code 0.8} water gravity/friction, zeroes against a wall, no lava push. */
        Model LEGACY = new Model() {
            @Override public Vec impulse(MechanicsWorld inst, Point pos, BoundingBox box, Block fluid, double scale, double movX, double movZ) {
                Vec dir = legacyFlow(inst, pos, box, fluid);
                return dir.isZero() ? Vec.ZERO : dir.mul(scale);
            }
            @Override public double waterGravity(boolean sprinting) { return WATER_GRAVITY_LEGACY; }
            @Override public double waterFriction(boolean sprinting) { return WATER_FRICTION; }
            @Override public boolean zeroesAgainstWall() { return true; }
            @Override public boolean pushesInLava() { return false; }
        };

        /** 26.1: averaged + depth-scaled impulse, {@code 0.005}/{@code 0.9} water gravity/friction, no wall-zero, pushes in lava. */
        Model MODERN = new Model() {
            @Override public Vec impulse(MechanicsWorld inst, Point pos, BoundingBox box, Block fluid, double scale, double movX, double movZ) {
                return modernImpulse(inst, pos, box, fluid, scale, movX, movZ);
            }
            @Override public double waterGravity(boolean sprinting) { return sprinting ? 0.0 : WATER_GRAVITY_MODERN; }
            @Override public double waterFriction(boolean sprinting) { return sprinting ? MODERN_SPRINT_FRICTION : WATER_FRICTION; }
            @Override public boolean zeroesAgainstWall() { return false; }
            @Override public boolean pushesInLava() { return true; }
        };
    }

    /** Vanilla {@code AABB.shrink} skin on the water box. */
    private static final double INSET = 0.001;
    /** 26.1 shallow-fluid threshold: below this fluid height the per-cell flow is scaled by the height. */
    private static final double MODERN_SHALLOW = 0.4;
    /** 26.1 near-still minimum impulse (b/t) applied when the player is almost stationary. */
    private static final double MODERN_MIN_IMPULSE = 0.0045;
    /** 26.1 near-still velocity threshold (per axis) gating the minimum impulse. */
    private static final double MODERN_STILL = 0.003;
    /** Vanilla water box vertical inset ({@code grow(0,-0.4,0)}). */
    private static final double Y_SHRINK = 0.4;
    /** {@code BlockFluids.h} falling-water down-term added before the final normalize. */
    private static final double FALL_DOWN = -6.0;
    /** Horizontal neighbour offsets (vanilla {@code EnumDirectionLimit.HORIZONTAL}). */
    private static final int[][] HORIZONTAL = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    // constants
    /** Water horizontal friction ({@code motX/Z *= 0.8} in water) - vanilla {@code EntityLiving.travel}; both 1.8 + 26 walk. */
    private static final double WATER_FRICTION = 0.8;
    /** 26 sprint-swimming horizontal friction ({@code 0.9}, not the {@code 0.8} water slowdown) - {@code travelInWater}. */
    private static final double MODERN_SPRINT_FRICTION = 0.9;
    /** 1.8 water vertical gravity ({@code motY -= 0.02} flat). */
    private static final double WATER_GRAVITY_LEGACY = 0.02;
    /** 26 water vertical gravity ({@code motY -= baseGravity/16 = 0.005}; {@code 0} while sprinting) - {@code getFluidFallingAdjustedMovement}. */
    private static final double WATER_GRAVITY_MODERN = 0.005;

    private FluidFlow() {}

    /**
     * Unit fluid-flow direction (b/t) for the box at {@code pos} (vanilla 1.8 {@code World.a} + {@code BlockFluids.h}),
     * or {@link Vec#ZERO} in still / pure-source fluid (a full source pool has no slope - why static water folds 1:1
     * without this). The summed per-cell unit slopes are re-normalized, so the magnitude is flat regardless of depth.
     */
    private static Vec legacyFlow(MechanicsWorld inst, Point pos, BoundingBox box, Block fluid) {
        // Vanilla water push box
        Vec flow = legacyScan(inst,
                pos.x() + box.minX() + INSET, pos.y() + box.minY() + Y_SHRINK + INSET, pos.z() + box.minZ() + INSET,
                pos.x() + box.maxX() - INSET, pos.y() + box.maxY() - Y_SHRINK - INSET, pos.z() + box.maxZ() - INSET,
                fluid);
        return flow == null ? Vec.ZERO : flow;
    }

    /** 1.8 item water current: {@code EntityItem} scans the RAW box (the player Y-inset would invert a 0.25-tall box and scan nothing). Unit direction or ZERO. */
    public static Vec itemLegacyFlow(MechanicsWorld inst, Point pos, BoundingBox box) {
        Vec flow = legacyScan(inst,
                pos.x() + box.minX(), pos.y() + box.minY(), pos.z() + box.minZ(),
                pos.x() + box.maxX(), pos.y() + box.maxY(), pos.z() + box.maxZ(), Block.WATER);
        return flow == null ? Vec.ZERO : flow;
    }

    /** 1.8 {@code Entity.W()} water contact ({@code bb.grow(0,-0.4,0).shrink(0.001)} - the box every non-item
     *  entity checks, quirks included: it inverts on short boxes, so detection flickers with height exactly like
     *  vanilla). {@code box} is the VANILLA entity box, not the physics box. {@code null} = dry; otherwise the
     *  unit current ({@link Vec#ZERO} in still water). */
    public static @Nullable Vec waterContact(MechanicsWorld inst, Point pos, BoundingBox box) {
        return legacyScan(inst,
                pos.x() + box.minX() + INSET, pos.y() + box.minY() + Y_SHRINK + INSET, pos.z() + box.minZ() + INSET,
                pos.x() + box.maxX() - INSET, pos.y() + box.maxY() - Y_SHRINK - INSET, pos.z() + box.maxZ() - INSET,
                Block.WATER);
    }

    private static @Nullable Vec legacyScan(MechanicsWorld inst, double ax, double ay, double az, double dx, double dy, double dz, Block fluid) {
        int xi = floor(ax), xj = floor(dx + 1.0);
        int yi = floor(ay), yj = floor(dy + 1.0);
        int zi = floor(az), zj = floor(dz + 1.0);

        boolean touched = false;
        double fx = 0, fy = 0, fz = 0;
        for (int x = xi; x < xj; x++) {
            for (int y = yi; y < yj; y++) {
                for (int z = zi; z < zj; z++) {
                    if (effLevel(inst, x, y, z, fluid) < 0) continue; // not this fluid
                    touched = true;
                    Vec slope = slopeAt(inst, x, y, z, fluid);
                    fx += slope.x();
                    fy += slope.y();
                    fz += slope.z();
                }
            }
        }
        if (!touched) return null;
        Vec sum = new Vec(fx, fy, fz);
        return sum.isZero() ? Vec.ZERO : sum.normalize();
    }

    /**
     * 26.1 fluid-flow impulse (b/t, already x {@code scale}) for a PLAYER - {@code EntityFluidInteraction.applyCurrentTo}.
     * Works for {@code fluid} = {@link Block#WATER} ({@code scale 0.014}) or {@link Block#LAVA} ({@code scale 0.0023} overworld
     * / {@code 0.007} nether). Differs from {@link #legacyFlow} (1.8) in two ways: each cell's unit slope is scaled by the fluid
     * {@code height} when shallow ({@code height < 0.4} - the thin-edge decay a 1.8 client never gets), and the cells are
     * <em>averaged</em> over the fluid-cell count (zero-flow source cells in the box dilute it) rather than normalized. A
     * near-still floor nudges a tiny impulse up to {@code 0.0045}. {@code movX/movZ} = the player's current horizontal velocity.
     */
    private static Vec modernImpulse(MechanicsWorld inst, Point pos, BoundingBox box, Block fluid, double scale, double movX, double movZ) {
        double feetY = pos.y() + box.minY();
        int xi = floor(pos.x() + box.minX()), xj = floor(pos.x() + box.maxX());
        int yi = floor(feetY), yj = (int) Math.ceil(pos.y() + box.maxY()) - 1;
        int zi = floor(pos.z() + box.minZ()), zj = floor(pos.z() + box.maxZ());

        // first pass: overall fluid depth above the feet
        double maxHeight = 0.0;
        for (int x = xi; x <= xj; x++)
            for (int y = yi; y <= yj; y++)
                for (int z = zi; z <= zj; z++) {
                    int lvl = rawLevel(inst, x, y, z, fluid);
                    if (lvl < 0) continue;
                    double top = y + fluidHeight(lvl);
                    if (top >= feetY) maxHeight = Math.max(maxHeight, top - feetY);
                }
        if (maxHeight <= 0.0) return Vec.ZERO;
        double heightScale = maxHeight < MODERN_SHALLOW ? maxHeight : 1.0;

        // second pass: average the height-scaled slopes
        double sx = 0, sy = 0, sz = 0;
        int count = 0;
        for (int x = xi; x <= xj; x++)
            for (int y = yi; y <= yj; y++)
                for (int z = zi; z <= zj; z++) {
                    int lvl = rawLevel(inst, x, y, z, fluid);
                    if (lvl < 0) continue;
                    if (y + fluidHeight(lvl) < feetY) continue;
                    count++;
                    Vec slope = slopeAt(inst, x, y, z, fluid); // ZERO for a source cell
                    sx += slope.x() * heightScale;
                    sy += slope.y() * heightScale;
                    sz += slope.z() * heightScale;
                }
        if (count == 0) return Vec.ZERO;
        Vec impulse = new Vec(sx / count, sy / count, sz / count).mul(scale);
        if (Math.abs(movX) < MODERN_STILL && Math.abs(movZ) < MODERN_STILL
                && impulse.length() < MODERN_MIN_IMPULSE && !impulse.isZero()) {
            impulse = impulse.normalize().mul(MODERN_MIN_IMPULSE);
        }
        return impulse;
    }

    /**
     * 26.1 fluid pass for a non-player ({@code EntityFluidInteraction.update}): {@code height} = max fluid top above
     * the box bottom (gates {@code isInFluid > 0} / item float {@code > 0.1}) plus the accumulated current -
     * normalized by {@link ItemSample#current}, not player-averaged.
     */
    public static ItemSample itemModernSample(MechanicsWorld inst, Point pos, BoundingBox box, Block fluid) {
        // vanilla scans bb.deflate(0.001) but measures height from the raw bb bottom
        double minX = pos.x() + box.minX() + INSET, maxX = pos.x() + box.maxX() - INSET;
        double minY = pos.y() + box.minY() + INSET, maxY = pos.y() + box.maxY() - INSET;
        double minZ = pos.z() + box.minZ() + INSET, maxZ = pos.z() + box.maxZ() - INSET;
        int x0 = floor(minX), x1 = (int) Math.ceil(maxX) - 1;
        int y0 = floor(minY), y1 = (int) Math.ceil(maxY) - 1;
        int z0 = floor(minZ), z1 = (int) Math.ceil(maxZ) - 1;
        double feetY = pos.y() + box.minY();

        double height = 0;
        double sx = 0, sy = 0, sz = 0;
        int count = 0;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    int lvl = rawLevel(inst, x, y, z, fluid);
                    if (lvl < 0) continue;
                    // same fluid above -> the cell is brim-full (FlowingFluid.getHeight)
                    double top = y + (rawLevel(inst, x, y + 1, z, fluid) >= 0 ? 1.0 : fluidHeight(lvl));
                    if (top < minY) continue;
                    height = Math.max(top - feetY, height);
                    Vec slope = slopeAt(inst, x, y, z, fluid);
                    double s = height < MODERN_SHALLOW ? height : 1.0; // vanilla scales by the RUNNING max
                    sx += slope.x() * s;
                    sy += slope.y() * s;
                    sz += slope.z() * s;
                    count++;
                }
        return count == 0 ? ItemSample.EMPTY : new ItemSample(height, new Vec(sx, sy, sz), count);
    }

    /** One fluid's {@link #itemModernSample} result: {@code height} above the box bottom + the raw current sum. */
    public record ItemSample(double height, Vec flowSum, int cells) {
        public static final ItemSample EMPTY = new ItemSample(0, Vec.ZERO, 0);

        /** {@code Tracker.applyCurrentTo} non-player branch: normalized sum x {@code scale}, near-still {@code 0.0045} floor. {@code movX/movZ} = horizontal velocity (b/t). */
        public Vec current(double scale, double movX, double movZ) {
            if (cells == 0 || flowSum.lengthSquared() < 1.0E-5F) return Vec.ZERO;
            Vec impulse = flowSum.normalize().mul(scale);
            if (Math.abs(movX) < MODERN_STILL && Math.abs(movZ) < MODERN_STILL && impulse.length() < MODERN_MIN_IMPULSE) {
                impulse = impulse.normalize().mul(MODERN_MIN_IMPULSE);
            }
            return impulse;
        }
    }

    /** Modern fluid render height for a water {@code level} ({@code (8-level)/9}; falling/{@code >=8} ~full). */
    private static double fluidHeight(int level) {
        return level >= 8 ? 0.8888889 : (8 - level) / 9.0;
    }

    /** Per-cell unit slope (vanilla {@code BlockFluids.h} / {@code FlowingFluid.getFlow}): neighbour {@code level} gradient + falling down-term, for the given {@code fluid}. */
    private static Vec slopeAt(MechanicsWorld inst, int x, int y, int z, Block fluid) {
        int raw = rawLevel(inst, x, y, z, fluid);
        int i = raw >= 8 ? 0 : raw; // this cell's flow level (source/falling -> 0)
        double sx = 0, sz = 0;
        for (int[] dir : HORIZONTAL) {
            int nx = x + dir[0], nz = z + dir[1];
            int j = effLevel(inst, nx, y, nz, fluid);
            if (j < 0) {
                // non-fluid neighbour with fluid below pulls toward the drop
                if (!solid(inst, nx, y, nz)) {
                    int below = effLevel(inst, nx, y - 1, nz, fluid);
                    if (below >= 0) {
                        int k = below - (i - 8);
                        sx += dir[0] * k;
                        sz += dir[1] * k;
                    }
                }
            } else {
                int k = j - i; // toward the more-drained neighbour
                sx += dir[0] * k;
                sz += dir[1] * k;
            }
        }

        Vec v = new Vec(sx, 0, sz);
        // falling fluid against a solid face adds the (0,-6,0) waterfall pull
        if (raw >= 8) {
            for (int[] dir : HORIZONTAL) {
                int nx = x + dir[0], nz = z + dir[1];
                if (solid(inst, nx, y, nz) || solid(inst, nx, y + 1, nz)) {
                    v = (v.isZero() ? v : v.normalize()).add(0, FALL_DOWN, 0);
                    break;
                }
            }
        }
        return v.isZero() ? Vec.ZERO : v.normalize();
    }

    /** Vanilla {@code f()}: -1 if the cell is not {@code fluid}, else the flow level ({@code level >= 8 -> 0}). */
    private static int effLevel(MechanicsWorld inst, int x, int y, int z, Block fluid) {
        int raw = rawLevel(inst, x, y, z, fluid);
        if (raw < 0) return -1;
        return raw >= 8 ? 0 : raw;
    }

    /** The {@code fluid}'s {@code level} property (0 source, 1-7 flowing, 8-15 falling), or -1 when the cell is not that fluid. */
    private static int rawLevel(MechanicsWorld inst, int x, int y, int z, Block fluid) {
        if (!inst.isChunkLoaded(x >> 4, z >> 4)) return -1; // unloaded -> not fluid
        Block block = inst.getBlock(x, y, z); // full state so the level property is present
        if (block == null || !block.compare(fluid)) return -1; // by id: matches every fluid level
        String level = block.getProperty("level");
        if (level == null) return 0;
        try {
            return Integer.parseInt(level);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Approximates vanilla's {@code material.isSolid()} / face-occlusion test with the registry solid flag. */
    private static boolean solid(MechanicsWorld inst, int x, int y, int z) {
        if (!inst.isChunkLoaded(x >> 4, z >> 4)) return false; // unloaded -> non-solid
        Block block = inst.getBlock(x, y, z, Block.Getter.Condition.TYPE);
        return block != null && block.isSolid();
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
