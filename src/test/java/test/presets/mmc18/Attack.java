package test.presets.mmc18;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.attack.AttackEvent;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attack.AttackLog;
import io.github.term4.minestommechanics.mechanics.attack.AttackSnapshot;
import io.github.term4.minestommechanics.mechanics.attack.AttackSystem;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.projectile.ProjectileDamage;
import io.github.term4.minestommechanics.util.tick.TickContext;
import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import io.github.term4.minestommechanics.world.WorldPolicy;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerMoveEvent;

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
     * The setup also arms the swing fake-hits ({@link #ensureSwingHits}); {@link #recordSwingVictim} fires on LANDED
     * hits only (incl. queued flushes), and only from this ruleset, so eligibility follows the active profile's scope.
     */
    public static AttackEvent.AttackRule.Ruleset ruleset() {
        return services -> {
            ensureFlushListener();
            ensureSwingHits(services);
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
                    processAndArm(vanilla, event, le);
                    return;
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

    /**
     * Victim of the queued hit currently flushing as non-sprint; {@link Knockback}'s sprint gate asks (same call stack).
     * ThreadLocal on purpose: "came through the queue" is call-stack context that no entity/damage state can recompute,
     * domains flush concurrently (a plain field would cross-contaminate), and the lib's attack/KB snapshots have no
     * field to carry a preset quirk.
     */
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
            try { processAndArm(p.rule(), p.event(), le); } finally { flushingNonSprintFor.remove(); }
        } else {
            processAndArm(p.rule(), p.event(), le);
        }
        return true;
    }

    // swing fake-hits (armed per-scope by the ruleset)

    /** Reach for a swing fake-hit (blocks) - 1.8 melee entity reach; the target box is already grown by the attacker's hit margin. */
    private static final double SWING_REACH = 3.0;
    /**
     * Swing-arming window around the victim's i-frame expiry: the last {@code before} i-frame ticks + the first
     * {@code after} hittable ticks. {@code 1/1} = the two ticks straddling expiry (the expiry is the transition between
     * them, not a third tick). Bounding {@code after} is what keeps a swing at a long-idle victim from arming.
     */
    private record SwingWindow(int before, int after) {}
    private static final SwingWindow SWING_WINDOW = new SwingWindow(1, 1);
    /** After a swing, the ray is checked on this many ticks of move/look packets - each carries the current look. */
    private static final int LOOK_WINDOW = 1;

    private static final AtomicBoolean swingListenersStarted = new AtomicBoolean();
    /** Per-attacker swing bookkeeping; an entry exists only for players this ruleset landed a hit for, so activation is per-scope. */
    private static final Map<Player, Swing> swingState = new ConcurrentHashMap<>();

    // volatile: normally all same-domain, but a queued flush can arm from the victim's thread after the attacker hops domains
    private static final class Swing {
        volatile Player lastVictim;
        volatile long victimExpiryTick = Long.MIN_VALUE; // combat tick the last-hit victim leaves i-frames (victim clock)
        volatile long swingTick = Long.MIN_VALUE;        // MIN_VALUE = no armed window (attacker clock)
        volatile long lastPacketTick = Long.MIN_VALUE;   // the client's own attack (interact-entity) packet - a real hit, no fake needed
    }

    /** Runs the hit and, if it LANDED (i-frames opened across it = fresh damage; eaten hits leave the window unchanged), arms the attacker's swing fake-hit. */
    private static void processAndArm(AttackEvent.AttackRule rule, AttackEvent event, LivingEntity target) {
        long invulBefore = DamageSystem.remainingDamageInvul(target);
        rule.processAttack(event);
        if (DamageSystem.remainingDamageInvul(target) > invulBefore) recordSwingVictim(event);
    }

    private static void recordSwingVictim(AttackEvent event) {
        if (event.attacker() instanceof Player atk && event.target() instanceof Player victim && victim != atk) {
            Swing s = swingState.computeIfAbsent(atk, k -> new Swing());
            s.lastVictim = victim;
            // the hit just opened i-frames, so remaining == full duration -> this IS the expiry tick
            s.victimExpiryTick = TickSystem.tick(victim) + DamageSystem.remainingDamageInvul(victim);
        }
    }

    /**
     * MMC swing fake-hits: an arm-swing whose attack packet missed still lands on the LAST player the attacker hit, if
     * the look ray falls on them within {@link #SWING_REACH reach} - the MineMen way of closing the modern-vs-1.8
     * hit-rate gap. The swing only OPENS the window ({@link #SWING_WINDOW}); the ray is read off the following move/look
     * packets. Listeners are server-wide but inert for players without a {@link #swingState} entry, so activation stays
     * per-scope; a granted hit runs the normal pipeline, so the {@link #ruleset() hit queue} + invul apply.
     */
    private static void ensureSwingHits(Services services) {
        if (!swingListenersStarted.compareAndSet(false, true)) return;
        MinestomMechanics mm = services.mm();
        AttackLog.installDigTracking(mm);
        EventNode<Event> node = EventNode.all("mm:mmc18-swing-hits");
        // the client's own attack packet this tick is the real hit; the fake hit only fills a miss
        node.addListener(EntityAttackEvent.class, e -> {
            if (e.getEntity() instanceof Player atk) {
                Swing s = swingState.get(atk);
                if (s != null) s.lastPacketTick = TickSystem.tick(atk);
            }
        });
        node.addListener(PlayerHandAnimationEvent.class, e -> {
            Player atk = e.getPlayer();
            Swing s = swingState.get(atk);
            Player victim = s != null ? s.lastVictim : null;
            if (victim == null) return;
            var scaling = mm.profiles().resolve(victim, MechanicsKeys.TICK_SCALING);
            long before = TickScaler.duration(SWING_WINDOW.before(), scaling, DamageSystem.KEY);
            long after = TickScaler.duration(SWING_WINDOW.after(), scaling, DamageSystem.KEY);
            // clocks are per-domain: the expiry compares on the VICTIM's clock (where it was stamped), the look window on the attacker's
            long now = TickSystem.tick(victim);
            if (now >= s.victimExpiryTick - before && now < s.victimExpiryTick + after) s.swingTick = TickSystem.tick(atk);
        });
        // fires PRE-move: getNewPosition() carries the incoming look for the ray while the hit reads the attacker's
        // normal state - knockback unchanged
        node.addListener(PlayerMoveEvent.class, e -> {
            Player atk = e.getPlayer();
            Swing s = swingState.get(atk);
            if (s == null || s.swingTick == Long.MIN_VALUE) return;
            if (TickSystem.tick(atk) - s.swingTick > LOOK_WINDOW) s.swingTick = Long.MIN_VALUE;
            else if (tryFakeHit(mm, atk, s, e.getNewPosition())) s.swingTick = Long.MIN_VALUE;
        });
        node.addListener(PlayerDisconnectEvent.class, e -> swingState.remove(e.getPlayer()));
        mm.install(node);
    }

    /** Feeds a fake hit through the pipeline iff affectable, not a modern client mid-break, and the ray (eye at {@code from}) is on the victim's box within reach. */
    private static boolean tryFakeHit(MinestomMechanics mm, Player atk, Swing s, Pos from) {
        Player victim = s.lastVictim;
        if (victim == null || victim.isRemoved() || !WorldPolicy.canAffect(atk, victim)) return false;
        if (s.lastPacketTick == TickSystem.tick(atk)) return false; // real hit this tick - only fill a miss
        // a modern client doesn't swing-ATTACK while breaking a block (the left-click drives the break); a 1.8 client does
        if (!mm.clientInfo().isLegacy(atk) && AttackLog.digging(atk)) return false;
        if (AttackLog.rayReach(atk, victim, mm.clientInfo(), SWING_REACH, from) < 0) return false;
        AttackSystem attack = mm.module(AttackSystem.class);
        if (attack == null) return false;
        // no invul gate: a fake hit inside the last i-frame tick BUFFERS in the ruleset's hit queue like a real one
        attack.apply(new AttackSnapshot(atk, victim, null));
        return true;
    }
}
