package test.presets.mmc18;

import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.Instance;

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
        return AttackConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla18.Attack.config())
                .ruleset(ruleset())
                .fullHitScale(1.0) // mmc18: a landed hit does NOT slow the attacker's tracked velocity
                .build();
    }

    /**
     * mmc18 attack processing: vanilla legacy combat behind a {@link #HIT_QUEUE_BUFFER 1-tick} hit queue against the
     * damage-invul window - a hit landing within the last tick of the target's window is buffered (last-wins per target,
     * collapsing high-ping bursts) and applied the moment the window expires, instead of being eaten by it.
     * TODO(swing-hits): arm-swing-animation hits (swing packet -> raytrace + obstacle check -> emulated attack,
     * hit-distance logging) belong here too, not in AttackConfig.
     */
    public static AttackEvent.AttackRule.Ruleset ruleset() {
        return services -> {
            ensureFlushListener();
            AttackEvent.AttackRule vanilla = io.github.term4.minestommechanics.mechanics.vanilla18.Attack.ruleset().create(services);
            return event -> {
                if (event.target() instanceof LivingEntity le) {
                    // the buffer is a vanilla-tick window, so scale it to live TPS (identity at 20) to keep the same real-time slop
                    if (DamageSystem.isInvulnerableToDamage(le)
                            && DamageSystem.remainingDamageInvul(le) <= TickScaler.duration(HIT_QUEUE_BUFFER, services.profiles().scalingFor(le), DamageSystem.KEY)) {
                        pendingHit.put(le, PendingHit.capture(event, vanilla));
                        return;
                    }
                    // Drain an already-expired queued hit first so the older hit lands before this one.
                    if (flushPending(le)) return;
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

    /** A buffered hit: the fired event plus the processing rule to apply it with on flush. */
    private record PendingHit(AttackEvent event, AttackEvent.AttackRule rule) {
        private static PendingHit capture(AttackEvent event, AttackEvent.AttackRule rule) {
            // AttackEvent reads crit/item lazily; freeze them so the delayed hit is the queued swing, not a later state.
            event.overrideCritical(event.critical());
            event.overrideItem(event.item());
            return new PendingHit(event, rule);
        }
    }

    /**
     * Queue expiry runs as a {@link TickPhase#PRE_DISPATCH} {@link TickSystem} tickable - right after the instance's
     * combat tick advances, strictly before the dispatcher processes that tick's attack packets. Documented order per
     * tick: advance clock -> flush expired -> attacks.
     */
    private static void ensureFlushListener() {
        if (!flushListenerStarted.compareAndSet(false, true)) return;
        TickSystem.register(TickPhase.PRE_DISPATCH, ctx -> flushExpired(ctx.instance()));
    }

    private static void flushExpired(Instance instance) {
        for (LivingEntity target : pendingHit.keySet()) {
            if (target.isRemoved()) pendingHit.remove(target);
            else if (target.getInstance() == instance) flushPending(target);
        }
    }

    /** Applies the pending hit for {@code le} once its window has expired, claiming the slot atomically. */
    private static boolean flushPending(LivingEntity le) {
        PendingHit p = pendingHit.get(le);
        if (p == null) return false;
        if (DamageSystem.isInvulnerableToDamage(le)) return false; // window still open
        if (!pendingHit.remove(le, p)) return false; // claimed elsewhere
        p.rule().processAttack(p.event());
        return true;
    }
}
