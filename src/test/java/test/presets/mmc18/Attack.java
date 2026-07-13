package test.presets.mmc18;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.projectile.ProjectileDamage;
import io.github.term4.minestommechanics.util.tick.TickContext;
import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mmc18 attack: the vanilla 1.8 attack ({@link io.github.term4.minestommechanics.mechanics.vanilla18.Attack}) behind a
 * 1-tick hit queue against the damage-invul window, with {@code fullHitScale(1.0)} (no attacker self-slowdown).
 */
public final class Attack {

    private Attack() {}

    /** mmc18 AttackConfig: vanilla 1.8 behavior through the {@link #ruleset() hit-queue ruleset}, no self-slowdown. */
    public static AttackConfig config() {
        return AttackConfig.builder(Vanilla18.attack())
                .ruleset(ruleset())
                .fullHitScale(1.0) // mmc18: a landed hit does NOT slow the attacker's tracked velocity
                .build();
    }

    /**
     * mmc18 attack processing: vanilla legacy combat behind a {@link #HIT_QUEUE_BUFFER 1-tick} hit queue against the
     * damage-invul window - a hit landing within the last tick of the target's window is buffered (last-wins per
     * target, collapsing high-ping bursts) and applied the moment the window expires, instead of being eaten by it.
     * Measured (sprintgate duo 2026-07-07): a queued hit flushes as a NON-sprint hit (plain base 0.5274 - sprint
     * bonus, enchant, axial, range all read non-sprint) when the victim's most recent damage is a PROJECTILE; an
     * overdamage replacement overrides that, so the queued hit keeps sprint. Earlier hits fall through to the
     * vanilla pipeline (eaten, or overdamage - which consumes sprint like any landed hit).
     * TODO(swing-hits): arm-swing-animation hits (swing packet -> raytrace + obstacle check -> emulated attack,
     * hit-distance logging) belong here too, not in AttackConfig.
     */
    public static AttackEvent.AttackRule.Ruleset ruleset() {
        return services -> {
            ensureFlushListener();
            AttackEvent.AttackRule vanilla = Vanilla18.attackRuleset().create(services);
            return event -> {
                if (event.target() instanceof LivingEntity le) {
                    // Drain an already-expired queued hit first so the older hit lands before this one.
                    if (flushPending(le)) return;
                    // the buffer is a vanilla-tick window, so scale it to live TPS (identity at 20) to keep the same real-time slop
                    if (DamageSystem.isInvulnerableToDamage(le)
                            && DamageSystem.remainingDamageInvul(le) <= TickScaler.duration(HIT_QUEUE_BUFFER,
                            services.profiles().resolve(le, MechanicsKeys.TICK_SCALING), DamageSystem.KEY)) {
                        pendingHit.put(le, PendingHit.capture(event, vanilla, le));
                        return;
                    }
                }
                vanilla.processAttack(event);
            };
        };
    }

    /** Buffer window (ticks): hits this close to the end of the damage-invul window queue instead of dropping. */
    private static final int HIT_QUEUE_BUFFER = 1;
    /** At most one pending buffered hit per target, applied as a fresh hit when its window expires. */
    private static final Map<LivingEntity, PendingHit> pendingHit = new ConcurrentHashMap<>();
    private static final AtomicBoolean flushListenerStarted = new AtomicBoolean();

    /** A buffered hit + the combat tick its ORIGINAL window ends. */
    private record PendingHit(AttackEvent event, AttackEvent.AttackRule rule, long deadline) {
        private static PendingHit capture(AttackEvent event, AttackEvent.AttackRule rule, LivingEntity target) {
            // crit/item read lazily; freeze them so the delayed hit is the queued swing
            event.overrideCritical(event.critical());
            event.overrideItem(event.item());
            return new PendingHit(event, rule,
                    TickSystem.tick(target) + DamageSystem.remainingDamageInvul(target));
        }
    }

    /**
     * Queue expiry runs as a {@link TickPhase#PRE_DISPATCH} {@link TickSystem} tickable - right after the instance's
     * combat tick advances, strictly before the dispatcher processes that tick's attack packets. Documented order per
     * tick: advance clock -> flush expired -> attacks.
     */
    private static void ensureFlushListener() {
        if (!flushListenerStarted.compareAndSet(false, true)) return;
        TickSystem.register(TickPhase.PRE_DISPATCH, Attack::flushExpired);
    }

    // flush on the victim's own pass: shards share the base instance, and a wrong-pass flush would run the
    // hit on a foreign thread against the owner's combat tick
    private static void flushExpired(TickContext ctx) {
        for (LivingEntity target : pendingHit.keySet()) {
            if (target.isRemoved()) pendingHit.remove(target);
            else if (ctx.owns(target)) flushPending(target);
        }
    }

    /** Victim of the queued hit currently flushing as non-sprint; {@link Knockback}'s sprint gate asks (same call stack). */
    private static final ThreadLocal<LivingEntity> flushingNonSprintFor = new ThreadLocal<>();

    /** Whether {@code target}'s incoming hit is a queued flush applying as a non-sprint hit. */
    static boolean flushingNonSprint(Entity target) {
        return flushingNonSprintFor.get() == target;
    }

    /**
     * Applies the pending hit once its ORIGINAL window ends, claiming the slot atomically. Deadline-based on purpose:
     * a window re-armed in between (fall damage on landing, fire) must not hold the hit - that deferred KB for whole
     * extra windows ("the hit lands when the player reaches the ground"). The pipeline eats/overdamages it like any
     * hit landing at that moment.
     */
    private static boolean flushPending(LivingEntity le) {
        PendingHit p = pendingHit.get(le);
        if (p == null || TickSystem.tick(le) < p.deadline()) return false;
        if (!pendingHit.remove(le, p)) return false; // claimed elsewhere
        // the queued hit applies as a NON-sprint hit iff the victim's most recent damage is still the projectile
        // that opened the window - an overdamage replacement in between overrides it (measured + user-decoded)
        if (DamageSystem.lastDamageType(le) instanceof ProjectileDamage) {
            flushingNonSprintFor.set(le);
            try { p.rule().processAttack(p.event()); } finally { flushingNonSprintFor.remove(); }
        } else {
            p.rule().processAttack(p.event());
        }
        return true;
    }
}
