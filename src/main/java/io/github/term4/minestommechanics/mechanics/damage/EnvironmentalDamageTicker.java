package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatcher for self-driven environmental damage producers (cactus, burning). Installs one per-instance
 * {@link TickSystem} hook on first {@link #bind}, and each instance tick runs every registered
 * {@link EnvironmentalTickProducer} against each living, non-exempt entity in that instance - the shared guards every
 * producer used to repeat. Producers {@link #register}/{@link #unregister} as their damage types enable/disable; the hook
 * is installed once for the JVM ({@link TickSystem} has no removal) and goes inert when no producers remain.
 */
public final class EnvironmentalDamageTicker {

    private static final EnvironmentalDamageTicker INSTANCE = new EnvironmentalDamageTicker();

    private final Set<EnvironmentalTickProducer> producers = new CopyOnWriteArraySet<>();
    private final AtomicBoolean hooked = new AtomicBoolean();
    private volatile @Nullable DamageSystem system;

    private EnvironmentalDamageTicker() {}

    public static EnvironmentalDamageTicker instance() { return INSTANCE; }

    /** Binds the active damage system and installs the per-instance tick hook once for the JVM. */
    public void bind(DamageSystem system) {
        this.system = system;
        if (hooked.compareAndSet(false, true)) {
            TickSystem.register(TickPhase.DEFAULT, ctx -> tickInstance(ctx.instance()));
        }
    }

    public void register(EnvironmentalTickProducer producer) { producers.add(producer); }

    public void unregister(EnvironmentalTickProducer producer) { producers.remove(producer); }

    private void tickInstance(Instance instance) {
        DamageSystem sys = this.system;
        if (sys == null || producers.isEmpty()) return;
        for (Entity entity : instance.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) continue;
            if (DamageProducers.exempt(living)) continue;
            for (EnvironmentalTickProducer producer : producers) producer.tick(living, sys);
        }
    }
}
