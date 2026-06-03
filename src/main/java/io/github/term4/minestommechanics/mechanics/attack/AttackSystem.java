package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.mechanics.Cause;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig.HitQueueInvulSource;
import io.github.term4.minestommechanics.mechanics.attack.hitdetection.PacketHit;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.util.TickClock;
import io.github.term4.minestommechanics.util.TickState;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDespawnEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AttackSystem {

    private static final Tag<TickState> INVUL_ATTACK = Tag.Transient("mm:invul-attack");

    private final MinestomMechanics mm;
    private final AttackConfig config;
    private final EventNode<@NotNull Event> apiEvents;
    private final EventNode<@NotNull Event> node;

    /** At most one pending buffered hit per target, applied as a fresh hit when its window opens. */
    private final Map<LivingEntity, AttackEvent> pendingHit = new ConcurrentHashMap<>();

    public AttackSystem(MinestomMechanics mm, AttackConfig config) {
        this.mm = mm;
        this.config = config;
        this.apiEvents = mm.events();
        this.node = EventNode.all("mm:attack");

        var services = mm.services();
        var resolvedStatic = AttackConfigResolver.resolve(config, AttackConfigResolver.AttackContext.of(
                new AttackSnapshot(null, null, Cause.ATTACK_PACKET, config), services));

        if (resolvedStatic.enabled() && resolvedStatic.packetHits()) {
            PacketHit.install(node, config, snap -> handleAttack(snap, services));
        }

        node.addListener(EntityDespawnEvent.class, e -> {
            if (e.getEntity() instanceof LivingEntity le) pendingHit.remove(le);
        });

        MinecraftServer.getSchedulerManager()
                .buildTask(this::tickQueue)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    private void handleAttack(AttackSnapshot snap, io.github.term4.minestommechanics.Services services) {
        AttackEvent api = new AttackEvent(snap, services);
        apiEvents.call(api);

        if (api.cancelled() || !api.process()) return;

        // Buffer a hit that lands within `buffer` ticks of the chosen invul window's end, applying it
        // as a fresh hit when the window opens. Otherwise forward to processing: the damage system
        // (overdamage) and knockback system each self-gate on their own invul windows.
        if (snap.target() instanceof LivingEntity le) {
            var resolved = api.resolvedConfig();
            int buffer = resolved.hitQueueBuffer();
            HitQueueInvulSource src = resolved.hitQueueInvulSource();
            int atkInvul = resolved.atkInvulnTicks();
            if (!api.bypassInvul() && buffer > 0
                    && queueInvulnerable(le, src, atkInvul) && queueRemaining(le, src, atkInvul) <= buffer) {
                pendingHit.put(le, api); // buffer the fired event (last-wins); collapses high-ping bursts
                return;
            }
            // Processing immediately: drain an already-ready queued hit first so the older hit lands
            // before this one, regardless of packet-vs-tickQueue ordering within the tick.
            flushPending(le, services);
        }

        processAttack(api.finalSnap(), services, api);
    }

    /**
     * Applies a pending hit for {@code le} if its buffered window has expired, claiming the slot
     * atomically so a concurrent {@link #tickQueue()} cannot process the same event twice.
     */
    private void flushPending(LivingEntity le, io.github.term4.minestommechanics.Services services) {
        AttackEvent pending = pendingHit.get(le);
        if (pending == null) return;
        var resolved = pending.resolvedConfig();
        if (queueInvulnerable(le, resolved.hitQueueInvulSource(), resolved.atkInvulnTicks())) return; // window still open
        if (!pendingHit.remove(le, pending)) return; // claimed elsewhere
        processAttack(pending.finalSnap(), services, pending);
    }

    /** Whether the target is in the invul window the hit-queue buffers against, per the configured source. */
    private static boolean queueInvulnerable(LivingEntity le, HitQueueInvulSource src, int atkInvul) {
        return switch (src) {
            case ATTACK    -> isInvulnerableToAttack(le);
            case DAMAGE    -> DamageSystem.isInvulnerableToDamage(le);
            case KNOCKBACK -> KnockbackSystem.isInvulnerableToKnockback(le);
            case AUTO      -> atkInvul > 0 ? isInvulnerableToAttack(le) : DamageSystem.isInvulnerableToDamage(le);
        };
    }

    /** Remaining ticks in the invul window the hit-queue buffers against, per the configured source. */
    private static int queueRemaining(LivingEntity le, HitQueueInvulSource src, int atkInvul) {
        return switch (src) {
            case ATTACK    -> remainingAttackInvulTicks(le);
            case DAMAGE    -> DamageSystem.remainingDamageInvulTicks(le);
            case KNOCKBACK -> KnockbackSystem.remainingKnockbackInvulTicks(le);
            case AUTO      -> atkInvul > 0 ? remainingAttackInvulTicks(le) : DamageSystem.remainingDamageInvulTicks(le);
        };
    }

    private void processAttack(AttackSnapshot snap, io.github.term4.minestommechanics.Services services, AttackEvent api) {
        AttackEvent.AttackRule proc = api.processor() != null
                ? api.processor().create(services)
                : api.resolvedConfig().ruleset().create(services);
        proc.processAttack(api);
        Entity target = api.finalSnap().target();
        if (target != null) {
            int duration = api.resolvedConfig().atkInvulnTicks();
            if (duration > 0) setAttackInvulnerable(target, duration);
        }
    }

    private void tickQueue() {
        var services = mm.services();
        for (Map.Entry<LivingEntity, AttackEvent> entry : pendingHit.entrySet()) {
            LivingEntity target = entry.getKey();
            if (target.isRemoved()) {
                pendingHit.remove(target);
                continue;
            }

            AttackEvent api = entry.getValue();
            // Wait until the buffered window (per the configured source) has expired before applying.
            var resolved = api.resolvedConfig();
            if (queueInvulnerable(target, resolved.hitQueueInvulSource(), resolved.atkInvulnTicks())) continue;

            // Window open: claim the slot atomically so a concurrent put isn't lost without being processed.
            // The event was already fired at detection; apply it directly without re-dispatching.
            if (!pendingHit.remove(target, api)) continue;
            processAttack(api.finalSnap(), services, api);
        }
    }

    public static AttackSystem install(MinestomMechanics mm, AttackConfig config) {
        var system = new AttackSystem(mm, config);
        mm.registerAttack(system);
        mm.install(system.node);
        return system;
    }

    public AttackConfig config() { return config; }
    public EventNode<@NotNull Event> node() { return node; }

    public static void setAttackInvulnerable(Entity e, int duration) {
        if (!(e instanceof LivingEntity le) || duration <= 0) return;
        le.setTag(INVUL_ATTACK, new TickState(TickClock.now(), duration));
    }

    public static boolean isInvulnerableToAttack(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        TickState s = le.getTag(INVUL_ATTACK);
        return s != null && s.isActive();
    }

    /** Remaining attack invul ticks for the entity. 0 if not invulnerable. */
    public static int remainingAttackInvulTicks(Entity e) {
        if (!(e instanceof LivingEntity le)) return 0;
        TickState s = le.getTag(INVUL_ATTACK);
        return s != null ? s.remainingTicks() : 0;
    }
}
