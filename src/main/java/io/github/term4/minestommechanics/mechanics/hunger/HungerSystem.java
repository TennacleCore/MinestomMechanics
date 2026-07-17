package io.github.term4.minestommechanics.mechanics.hunger;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.starvation.StarvationDamage;
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
 * Hunger: the vanilla food tick (exhaustion drain, natural regen, starvation - {@code FoodMetaData.a} /
 * {@code FoodData.tick}) plus every exhaustion source, per-scope config via {@code HUNGER}. Action sources ride
 * {@link ExhaustionSources}, the hunger effect the attribute catalog; docs/exhaustion-sources.md has the vanilla
 * catalog. Difficulty gates are dropped (Minestom has no server difficulty).
 */
public final class HungerSystem implements MechanicsModule {

    /** This system's identity for per-module TPS scaling (its {@code referenceTps} feel-baseline). */
    public static final Key KEY = Key.key("mm:hunger");
    // Source keys, priced per preset. Lib sources charge ONLY when the scope prices them (no entry = inert);
    // quantities: per-meter sources pass meters, per-event pass 1, damage-taken passes the type's exhaustion value.
    /** The 80-tick regen heal; quantity 1 per heal (1.8 preset: {@code flat(3)}, modern: {@code flat(6)}). */
    public static final Key REGEN_COST = Key.key("mm:regen");
    /** The modern saturation fast-regen heal; quantity = the spent saturation (modern preset: {@code dynamic()}). */
    public static final Key SATURATION_REGEN_COST = Key.key("mm:regen-saturation");
    /** A damaging hit taken; quantity = the damage type's {@code exhaustion} value (both presets: {@code dynamic()}). */
    public static final Key DAMAGE_TAKEN_COST = Key.key("mm:damage-taken");
    /** A landed melee attack, charged to the attacker (1.8: {@code flat(0.3)}, modern: {@code flat(0.1)}). */
    public static final Key ATTACK_COST = Key.key("mm:attack");
    /** A jump (1.8: {@code flat(0.2)}, modern: {@code flat(0.05)}). */
    public static final Key JUMP_COST = Key.key("mm:jump");
    /** A sprint-jump (1.8: {@code flat(0.8)}, modern: {@code flat(0.2)}). */
    public static final Key SPRINT_JUMP_COST = Key.key("mm:sprint-jump");
    /** Eye-underwater movement, quantity = 3D meters (1.8: {@code scaled(0.015)}, modern: {@code scaled(0.01)}). */
    public static final Key DIVE_COST = Key.key("mm:dive");
    /** Surface water movement, quantity = horizontal meters (1.8: {@code scaled(0.015)}, modern: {@code scaled(0.01)}). */
    public static final Key SWIM_COST = Key.key("mm:swim");
    /** Ground sprint, quantity = horizontal meters ({@code scaled(0.1)} both). */
    public static final Key SPRINT_COST = Key.key("mm:sprint");
    /** Ground walk/sneak, quantity = horizontal meters (1.8: {@code scaled(0.01)}; modern free - unpriced). */
    public static final Key WALK_COST = Key.key("mm:walk");
    /** A survival block harvest (1.8: {@code flat(0.025)}, modern: {@code flat(0.005)}). */
    public static final Key BLOCK_BREAK_COST = Key.key("mm:block-break");
    /** The hunger effect, quantity = amplifier+1 per tick (1.8: {@code scaled(0.025)}, modern: {@code scaled(0.005)}). */
    public static final Key HUNGER_EFFECT_COST = Key.key("mm:hunger-effect");

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
     * Adds exhaustion from {@code source}: its {@link ExhaustionCost} rule maps {@code quantity} to the cost (no entry
     * = as-is), times the global {@code exhaustionScale}. Custom costs use their own key - {@code exhaust(p,
     * Key.key("game:dash"), 2.0f)} - and are scope-tunable like the vanilla ones.
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

    /** Lib-source charge: only when the scope PRICES the key - the presets own the vanilla values, unpriced = inert. */
    public void chargePriced(Player player, Key source, float quantity) {
        if (!enabled(player)) return;
        HungerConfig cfg = configFor(player);
        if (cfg.exhaustionCost(source) != null) exhaust(player, cfg, source, quantity);
    }

    /** The player's accumulated exhaustion. */
    public static float exhaustion(Player player) {
        Float v = player.getTag(EXHAUSTION);
        return v != null ? v : 0f;
    }

    /** Per-instance hunger pass: exhaustion drain then the food tick (regen/starvation), per enabled player. */
    private void tick(TickContext ctx) {
        for (Player p : ctx.world().players()) {
            if (!ctx.owns(p) || !enabled(p)) continue;
            drainExhaustion(p);
            foodTick(p, configFor(p));
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

    /** The vanilla food tick: regen (gated by {@code naturalRegen}), else starvation, on the SHARED timer. */
    private void foodTick(Player p, HungerConfig cfg) {
        float max = (float) p.getAttributeValue(Attribute.MAX_HEALTH);
        boolean hurt = p.getHealth() < max;
        boolean regen = !Boolean.FALSE.equals(cfg.naturalRegen());
        if (regen && Boolean.TRUE.equals(cfg.saturationRegen()) && p.getFoodSaturation() > 0 && hurt && p.getFood() >= 20) {
            int t = timer(p) + 1;
            if (t >= scaled(p, SATURATION_REGEN_INTERVAL)) {
                float spent = Math.min(p.getFoodSaturation(), SATURATION_REGEN_CAP);
                p.setHealth(Math.min(p.getHealth() + spent / 6.0f, max));
                exhaust(p, cfg, SATURATION_REGEN_COST, spent); // quantity = the spent saturation
                t = 0;
            }
            p.setTag(FOOD_TIMER, t);
        } else if (regen && hurt && p.getFood() >= (cfg.regenFoodThreshold() != null ? cfg.regenFoodThreshold() : 18)) {
            int t = timer(p) + 1;
            if (t >= scaled(p, interval(cfg))) {
                p.setHealth(Math.min(p.getHealth() + 1.0f, max));
                exhaust(p, cfg, REGEN_COST, 1.0f); // quantity = one heal; the preset's cost rule prices it
                t = 0;
            }
            p.setTag(FOOD_TIMER, t);
        } else if (p.getFood() <= 0) {
            int t = timer(p) + 1;
            if (t >= scaled(p, interval(cfg))) { // vanilla starves on the same cadence + timer as regen
                starve(p);
                t = 0;
            }
            p.setTag(FOOD_TIMER, t);
        } else {
            p.removeTag(FOOD_TIMER);
        }
    }

    private static int interval(HungerConfig cfg) {
        return cfg.regenInterval() != null ? cfg.regenInterval() : 80;
    }

    private int scaled(Player p, int ticks) {
        return TickScaler.duration(ticks, mm.profiles().resolve(p, MechanicsKeys.TICK_SCALING), KEY);
    }

    /** Difficulty floors are EASY 10 / NORMAL 1 / HARD none; no server difficulty, so NORMAL's applies. */
    private void starve(Player p) {
        DamageSystem damage = mm.module(DamageSystem.class);
        if (damage == null || p.getHealth() <= 1.0f) return;
        DamageSnapshot snap = DamageSnapshot.of(p, StarvationDamage.INSTANCE);
        var ctx = damage.contextFor(snap);
        if (ctx.typeConfig().enabled(ctx)) damage.apply(snap);
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
        ExhaustionSources.install(mm);
        return system;
    }
}
