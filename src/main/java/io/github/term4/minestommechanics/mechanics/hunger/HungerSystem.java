package io.github.term4.minestommechanics.mechanics.hunger;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.util.tick.TickContext;
import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hunger subsystem: food / saturation / exhaustion depletion, natural regen, and starvation. Mirrors the other systems;
 * per-scope config via {@code HUNGER}; drives periodic work off the shared {@link TickSystem}.
 *
 * <p><b>Stub.</b> The per-instance tick is installed and scope-gated but does nothing yet; depletion/regen and the
 * starvation route through {@code DamageSystem} land with the hunger logic.
 */
public final class HungerSystem implements MechanicsModule {

    private final MinestomMechanics mm;
    private final HungerConfig config; // install config (the resolution fallback)
    private final EventNode<@NotNull Event> node;
    /** The per-instance hunger tick is installed once for the JVM ({@link TickSystem} has no removal); it reads the live system each tick. */
    private static final AtomicBoolean TICK_HOOK = new AtomicBoolean();

    public HungerSystem(MinestomMechanics mm, HungerConfig config) {
        this.mm = mm;
        this.config = config;
        this.node = EventNode.all("mm:hunger");
    }

    public EventNode<@NotNull Event> node() { return node; }
    public HungerConfig config() { return config; }

    /** Effective hunger config for {@code subject}: the scoped profile (player -&gt; instance -&gt; global), else the install config. */
    public HungerConfig configFor(@Nullable Entity subject) {
        HungerConfig scoped = mm.profiles().resolve(subject, MechanicsKeys.HUNGER);
        return scoped != null ? scoped : config;
    }

    /** Whether hunger is enabled for {@code subject} - active by default (an installed config is on unless it sets {@code enabled(false)}). */
    public boolean enabled(@Nullable Entity subject) {
        return !Boolean.FALSE.equals(configFor(subject).enabled());
    }

    /**
     * Restores {@code nutrition} food points and {@code saturation} to {@code player} - the entry point food consumables
     * call on finish (e.g. a golden apple's +4 / +9.6). <b>Stub:</b> a no-op until the hunger logic lands.
     */
    public void restore(Player player, int nutrition, float saturation) {
        if (!enabled(player)) return;
        // TODO(hunger): add to food level (cap 20) and saturation (capped at the food level).
    }

    /** Per-instance hunger pass over each enabled player. <b>Stub:</b> a no-op until the hunger logic lands. */
    private void tick(TickContext ctx) {
        for (Player p : ctx.instance().getPlayers()) {
            if (!enabled(p)) continue;
            // TODO(hunger): step exhaustion -> saturation -> food, natural regen, and starvation (route through DamageSystem).
        }
    }

    /** Installs the system active (a per-scope {@code MechanicsProfile.hunger} config can disable it). */
    public static HungerSystem install(MinestomMechanics mm) {
        return install(mm, HungerConfig.builder().build());
    }

    public static HungerSystem install(MinestomMechanics mm, HungerConfig cfg) {
        HungerSystem system = new HungerSystem(mm, cfg);
        mm.register(system);
        mm.install(system.node);
        // Registered once for the JVM (TickSystem has no removal); dispatches through the live registry so a re-install is picked up.
        if (TICK_HOOK.compareAndSet(false, true)) {
            TickSystem.register(TickPhase.DEFAULT, ctx -> {
                HungerSystem live = mm.module(HungerSystem.class);
                if (live != null) live.tick(ctx);
            });
        }
        return system;
    }
}
