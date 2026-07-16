package io.github.term4.minestommechanics.mechanics.hunger;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.util.tick.TickContext;
import io.github.term4.minestommechanics.util.tick.TickPhase;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hunger subsystem: natural regen + the exhaustion drain it feeds, per-scope config via {@code HUNGER}. Mirrors
 * vanilla's food tick ({@code FoodMetaData.a} / {@code FoodData.tick}): drain exhaustion first (4 points take a
 * saturation point, else a food point), then regen - the modern saturation fast path (food 20, heal
 * {@code min(sat,6)/6} every 10 ticks at the spent saturation's cost), else 1 heart-half per {@code regenInterval} at
 * {@code regenFoodThreshold}+. Difficulty gates are dropped (Minestom has no server difficulty).
 *
 * <p>Action exhaustion costs (sprint/jump/combat) and starvation land with the depletion logic; until then the only
 * exhaustion source is regen itself, which correctly eats saturation, then food, down to the regen threshold.
 */
public final class HungerSystem implements MechanicsModule {

    public static final Key KEY = Key.key("mm:hunger");
    /** The 80-tick regen heal; quantity 1 per heal (1.8 preset: {@code flat(3)}, modern: {@code flat(6)}). */
    public static final Key REGEN_COST = Key.key("mm:regen");
    /** The modern saturation fast-regen heal; quantity = the spent saturation (modern preset: {@code dynamic()}). */
    public static final Key SATURATION_REGEN_COST = Key.key("mm:regen-saturation");

    /** Accumulated exhaustion (vanilla {@code foodExhaustionLevel}); absent = 0. */
    private static final Tag<Float> EXHAUSTION = Tag.Transient("mm:exhaustion");
    /** The shared regen/starvation timer (vanilla {@code foodTickTimer}); absent = 0. */
    private static final Tag<Integer> FOOD_TIMER = Tag.Transient("mm:food-timer");

    // modern saturation fast regen (FoodData.tick): fixed cadence + per-heal saturation cap
    private static final int SATURATION_REGEN_INTERVAL = 10;
    private static final float SATURATION_REGEN_CAP = 6.0f;

    private final MinestomMechanics mm;
    private final HungerConfig config;
    private final EventNode<@NotNull Event> node;
    /** The per-instance hunger tick is installed once for the JVM ({@link TickSystem} has no removal); it reads the live system each tick. */
    private static final AtomicBoolean TICK_HOOK = new AtomicBoolean();

    public HungerSystem(MinestomMechanics mm, HungerConfig config) {
        this.mm = mm;
        this.config = config;
        this.node = EventNode.all("mm:hunger");
        // vanilla respawns with a fresh FoodMetaData; Minestom resets food/saturation, these tags are ours to clear
        node.addListener(PlayerRespawnEvent.class, e -> {
            e.getPlayer().removeTag(EXHAUSTION);
            e.getPlayer().removeTag(FOOD_TIMER);
        });
    }

    public EventNode<@NotNull Event> node() { return node; }
    public HungerConfig config() { return config; }

    /** Effective config for {@code subject}: the scoped profile, else the install config. */
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
     * call on finish (e.g. a golden apple's +4 / +9.6). Saturation caps at the food level (vanilla).
     */
    public void restore(Player player, int nutrition, float saturation) {
        if (!enabled(player)) return;
        player.setFood(Math.min(player.getFood() + nutrition, 20));
        player.setFoodSaturation(Math.min(player.getFoodSaturation() + saturation, player.getFood()));
    }

    /**
     * Adds exhaustion from {@code source}: the source's {@link ExhaustionCost} rule maps {@code quantity} to the cost
     * (no entry = the quantity as-is), and the global {@code exhaustionScale} multiplies the result. Every source -
     * the lib's and CUSTOM ones alike - goes through here: an ability spends
     * {@code exhaust(p, Key.key("game:dash"), 2.0f)} and is tunable per scope like any vanilla cost.
     */
    public void exhaust(Player player, Key source, float quantity) {
        if (!enabled(player)) return;
        exhaust(player, configFor(player), source, quantity);
    }

    private static void exhaust(Player player, HungerConfig cfg, Key source, float quantity) {
        ExhaustionCost rule = cfg.exhaustionCost(source);
        float global = cfg.exhaustionScale() != null ? cfg.exhaustionScale() : 1f;
        float cost = (rule != null ? rule.cost(quantity) : quantity) * global;
        if (cost <= 0) return;
        player.setTag(EXHAUSTION, exhaustion(player) + cost);
    }

    /** The player's accumulated exhaustion. */
    public static float exhaustion(Player player) {
        Float v = player.getTag(EXHAUSTION);
        return v != null ? v : 0f;
    }

    /** Per-instance hunger pass: exhaustion drain then natural regen, per enabled player. */
    private void tick(TickContext ctx) {
        for (Player p : ctx.world().players()) {
            if (!ctx.owns(p) || !enabled(p)) continue;
            HungerConfig cfg = configFor(p);
            drainExhaustion(p);
            if (!Boolean.FALSE.equals(cfg.naturalRegen())) regen(p, cfg);
            else p.removeTag(FOOD_TIMER);
        }
    }

    /** 4 exhaustion points take a saturation point, else a food point (identical in 1.8 and modern; peaceful gate dropped). */
    private static void drainExhaustion(Player p) {
        float ex = exhaustion(p);
        if (ex <= 4.0f) return;
        p.setTag(EXHAUSTION, ex - 4.0f);
        float sat = p.getFoodSaturation();
        if (sat > 0) p.setFoodSaturation(Math.max(sat - 1.0f, 0f));
        else p.setFood(Math.max(p.getFood() - 1, 0));
    }

    private void regen(Player p, HungerConfig cfg) {
        float max = (float) p.getAttributeValue(Attribute.MAX_HEALTH);
        boolean hurt = p.getHealth() < max;
        var scaling = mm.profiles().resolve(p, MechanicsKeys.TICK_SCALING);
        if (Boolean.TRUE.equals(cfg.saturationRegen()) && p.getFoodSaturation() > 0 && hurt && p.getFood() >= 20) {
            int t = timer(p) + 1;
            if (t >= TickScaler.duration(SATURATION_REGEN_INTERVAL, scaling, KEY)) {
                float spent = Math.min(p.getFoodSaturation(), SATURATION_REGEN_CAP);
                p.setHealth(Math.min(p.getHealth() + spent / 6.0f, max));
                exhaust(p, cfg, SATURATION_REGEN_COST, spent); // quantity = the spent saturation
                t = 0;
            }
            p.setTag(FOOD_TIMER, t);
        } else if (hurt && p.getFood() >= (cfg.regenFoodThreshold() != null ? cfg.regenFoodThreshold() : 18)) {
            int t = timer(p) + 1;
            if (t >= TickScaler.duration(cfg.regenInterval() != null ? cfg.regenInterval() : 80, scaling, KEY)) {
                p.setHealth(Math.min(p.getHealth() + 1.0f, max));
                exhaust(p, cfg, REGEN_COST, 1.0f); // quantity = one heal; the preset's cost rule prices it
                t = 0;
            }
            p.setTag(FOOD_TIMER, t);
        } else {
            p.removeTag(FOOD_TIMER); // vanilla resets the shared timer; the starvation branch slots here later
        }
    }

    private static int timer(Player p) {
        Integer v = p.getTag(FOOD_TIMER);
        return v != null ? v : 0;
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
