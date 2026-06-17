package io.github.term4.minestommechanics.mechanics.projectile;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The "arrows stuck in an entity" count - the cosmetic arrows poking out of a hit mob/player, carried by vanilla's
 * {@code LivingEntity} arrow-count metadata. A damaging arrow hit adds one; they fall out over {@code 20 * (30 - count)}
 * ticks per arrow. Public API to read/set/clear; the decay runs on one lazily-started global task.
 */
public final class StuckArrows {

    private StuckArrows() {}

    /** Per-entity countdown (ticks) to the next arrow falling out; transient - not persisted, dies with the entity. */
    private static final Tag<Integer> REMOVE_TIME = Tag.Transient("mm:arrow-remove-time");
    /** Living entities with at least one stuck arrow - the only entities the decay task touches. */
    private static final Set<LivingEntity> tracked = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean decayStarted = new AtomicBoolean();

    /** The number of arrows currently stuck in {@code entity}. */
    public static int count(LivingEntity entity) {
        return entity.getEntityMeta() instanceof LivingEntityMeta m ? m.getArrowCount() : 0;
    }

    /** Sets the stuck-arrow count (clamped to {@code >= 0}); {@code 0} clears it. Starts the fall-out decay if needed. */
    public static void set(LivingEntity entity, int count) {
        if (!(entity.getEntityMeta() instanceof LivingEntityMeta m)) return;
        int c = Math.max(0, count);
        m.setArrowCount(c);
        if (c > 0) {
            if (tracked.add(entity)) entity.setTag(REMOVE_TIME, 0); // (re)tracked: arm the timer (recomputed next tick)
            ensureDecayTask();
        } else {
            tracked.remove(entity);
            entity.removeTag(REMOVE_TIME);
        }
    }

    /** Adds {@code delta} stuck arrows (the vanilla on-hit increment is {@code add(entity, 1)}). */
    public static void add(LivingEntity entity, int delta) { set(entity, count(entity) + delta); }

    /** Removes all stuck arrows from {@code entity}. */
    public static void clear(LivingEntity entity) { set(entity, 0); }

    /** Lazily starts the single global fall-out task (vanilla cadence {@code 20 * (30 - count)} ticks per arrow). */
    private static void ensureDecayTask() {
        if (!decayStarted.compareAndSet(false, true)) return;
        MinecraftServer.getSchedulerManager().buildTask(StuckArrows::tickDecay).repeat(TaskSchedule.tick(1)).schedule();
    }

    private static void tickDecay() {
        tracked.removeIf(entity -> {
            if (entity.isRemoved() || !(entity.getEntityMeta() instanceof LivingEntityMeta m)) return true;
            int count = m.getArrowCount();
            if (count <= 0) return true;
            Integer t = entity.getTag(REMOVE_TIME);
            int time = (t == null || t <= 0) ? 20 * (30 - count) : t; // recompute when the previous arrow fell out
            if (--time <= 0) {
                m.setArrowCount(count - 1);
                entity.setTag(REMOVE_TIME, 0);
            } else {
                entity.setTag(REMOVE_TIME, time);
            }
            return m.getArrowCount() <= 0;
        });
    }
}
