package io.github.term4.minestommechanics.vri;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.tracking.motion.FluidFlow;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link ItemEntity} with the vanilla environment response Minestom omits: fluid current/buoyancy, lava pop,
 * push-out-of-blocks. {@link Model#LEGACY} = 1.8 (items sink, the current slides them along the bottom);
 * {@link Model#MODERN} = 26.1 (items float). Stock physics stay in charge; {@link #update} nudges velocity
 * post-move, where both vanillas apply their fluid steps.
 */
public class DroppedItemEntity extends ItemEntity {

    /** Which vanilla item physics to run. */
    public enum Model { LEGACY, MODERN }

    private static final double WATER_DRAG_MODERN = 0.99;
    private static final double LAVA_DRAG_MODERN = 0.95;
    private static final double BUOYANCY = 5.0E-4;         // 26.1: +lift while vy < 0.06
    private static final double BUOYANCY_CAP = 0.06;
    private static final double FLOAT_HEIGHT = 0.1;        // 26.1: fluid height gating float vs gravity
    private static final double FLOW_SCALE = 0.014;
    private static final double LAVA_FLOW_SCALE = 0.0023333333333333335; // 26.1 overworld (nether FAST_LAVA: 0.007)
    private static final double TPS = ServerFlag.SERVER_TICKS_PER_SECOND;

    private final @Nullable Model model;
    private @Nullable Model resolved;
    // lava-pop cadence: vanilla pops on int-cast cell change or every 25 ticks
    private int cellX = Integer.MIN_VALUE, cellY, cellZ;
    // last tick's sample chose fluid motion - vanilla skips gravity that tick; Minestom applied it anyway
    private boolean fluidMotion;

    /** {@code model} null = resolve from the profile ({@code MechanicsKeys.ITEM_PHYSICS}), falling back to LEGACY. */
    public DroppedItemEntity(@NotNull ItemStack itemStack, @Nullable Model model) {
        super(itemStack);
        this.model = model;
    }

    /** Spawns a drop ({@code velocity} in b/t) after firing {@link ItemSpawnEvent}; {@code null} if cancelled. */
    public static @Nullable DroppedItemEntity spawn(@NotNull Instance instance, @NotNull Pos pos, @NotNull Vec velocity,
                                                    @NotNull ItemStack stack, @Nullable Model physics, int pickupDelayTicks,
                                                    ItemSpawnEvent.@NotNull Cause cause, @NotNull Player player) {
        DroppedItemEntity item = new DroppedItemEntity(stack, physics);
        item.setPickupDelay(pickupDelayTicks, TimeUnit.SERVER_TICK);
        item.setVelocity(velocity.mul(TPS));
        ItemSpawnEvent event = new ItemSpawnEvent(item, cause, player, instance, pos);
        EventDispatcher.call(event);
        if (event.isCancelled()) return null;
        item.setInstance(instance, pos);
        return item;
    }

    @Override
    public void update(long time) {
        super.update(time); // merging
        Instance instance = getInstance();
        if (instance == null || isRemoved()) return;
        Pos pos = getPosition();
        Vec v0 = getVelocity().div(TPS); // b/t; Minestom already applied gravity + drag this tick

        Vec v = effectiveModel() == Model.LEGACY ? legacyTick(instance, pos, v0) : modernTick(instance, pos, v0);
        if (buried(instance, pos)) v = pushOutOfBlocks(instance, pos, v);

        // silent: setVelocity broadcasts, fighting the client's own item sim
        if (!v.samePoint(v0)) this.velocity = v.mul(TPS);
    }

    private Vec legacyTick(Instance instance, Pos pos, Vec v) {
        int bx = (int) pos.x(), by = (int) pos.y(), bz = (int) pos.z();
        boolean cellChanged = bx != cellX || by != cellY || bz != cellZ;
        cellX = bx;
        cellY = by;
        cellZ = bz;
        if ((cellChanged || getAliveTicks() % 25 == 0)
                && instance.getBlock(pos, Block.Getter.Condition.TYPE).compare(Block.LAVA)) {
            var rnd = ThreadLocalRandom.current();
            v = new Vec((rnd.nextFloat() - rnd.nextFloat()) * 0.2, 0.2, (rnd.nextFloat() - rnd.nextFloat()) * 0.2);
            instance.playSound(Sound.sound(SoundEvent.BLOCK_FIRE_EXTINGUISH.key(), Sound.Source.NEUTRAL,
                    0.4f, 2.0f + rnd.nextFloat() * 0.4f), pos.x(), pos.y(), pos.z());
        }
        // twice per tick (Entity.onEntityUpdate + the EntityItem tail); half-speed sliding rubber-bands
        // against the client's own sim
        Vec flow = FluidFlow.itemLegacyFlow(instance, pos, getBoundingBox());
        return flow.isZero() ? v : v.add(flow.mul(2 * FLOW_SCALE));
    }

    private Vec modernTick(Instance instance, Pos pos, Vec v) {
        var water = FluidFlow.itemModernSample(instance, pos, getBoundingBox(), Block.WATER);
        var lava = water.height() > 0 ? FluidFlow.ItemSample.EMPTY
                : FluidFlow.itemModernSample(instance, pos, getBoundingBox(), Block.LAVA);

        // vanilla skipped gravity this tick - invert exactly what Minestom's aerodynamics (registry item 0.04/0.98) applied
        if (fluidMotion) {
            Aerodynamics aero = getAerodynamics();
            v = v.withY(v.y() + aero.gravity() * aero.verticalAirResistance());
        }
        // twice per tick (baseTick + the item tail); sequential - the near-still floor reads v between pushes
        for (int push = 0; push < 2; push++) {
            if (water.height() > 0) v = v.add(water.current(FLOW_SCALE, v.x(), v.z()));
            else if (lava.height() > 0) v = v.add(lava.current(LAVA_FLOW_SCALE, v.x(), v.z()));
        }

        fluidMotion = water.height() > FLOAT_HEIGHT || lava.height() > FLOAT_HEIGHT;
        if (fluidMotion) {
            double drag = water.height() > FLOAT_HEIGHT ? WATER_DRAG_MODERN : LAVA_DRAG_MODERN;
            double vy = v.y() + (v.y() < BUOYANCY_CAP ? BUOYANCY : 0.0);
            v = new Vec(v.x() * drag, vy, v.z() * drag);
        }
        return v;
    }

    private boolean buried(Instance instance, Pos pos) {
        double cy = pos.y() + getBoundingBox().height() / 2.0;
        return instance.getBlock((int) Math.floor(pos.x()), (int) Math.floor(cy), (int) Math.floor(pos.z()),
                Block.Getter.Condition.TYPE).isSolid();
    }

    private Model effectiveModel() {
        if (model != null) return model;
        if (resolved == null && MinestomMechanics.getInstance().isInitialized()) {
            Model r = MinestomMechanics.getInstance().profiles().resolve(this, MechanicsKeys.ITEM_PHYSICS);
            resolved = r != null ? r : Model.LEGACY; // once; profile swaps don't retarget live items
        }
        return resolved != null ? resolved : Model.LEGACY;
    }

    /** Vanilla {@code Entity.pushOutOfBlocks}: inside a solid block, shove {@code 0.1..0.3} toward the nearest free face (up wins ties). */
    private Vec pushOutOfBlocks(Instance instance, Pos pos, Vec v) {
        double cy = pos.y() + getBoundingBox().height() / 2.0;
        int bx = (int) Math.floor(pos.x()), by = (int) Math.floor(cy), bz = (int) Math.floor(pos.z());

        double dx = pos.x() - bx, dy = cy - by, dz = pos.z() - bz;
        double best = 9999.0;
        int dir = 3; // up default, like vanilla
        if (free(instance, bx - 1, by, bz) && dx < best) { best = dx; dir = 0; }
        if (free(instance, bx + 1, by, bz) && 1.0 - dx < best) { best = 1.0 - dx; dir = 1; }
        if (free(instance, bx, by + 1, bz) && 1.0 - dy < best) { best = 1.0 - dy; dir = 3; }
        if (free(instance, bx, by, bz - 1) && dz < best) { best = dz; dir = 4; }
        if (free(instance, bx, by, bz + 1) && 1.0 - dz < best) { dir = 5; }
        double f = ThreadLocalRandom.current().nextFloat() * 0.2 + 0.1;
        return switch (dir) {
            case 0 -> v.withX(-f);
            case 1 -> v.withX(f);
            case 4 -> v.withZ(-f);
            case 5 -> v.withZ(f);
            default -> v.withY(f);
        };
    }

    private static boolean free(Instance instance, int x, int y, int z) {
        return !instance.getBlock(x, y, z, Block.Getter.Condition.TYPE).isSolid();
    }
}
