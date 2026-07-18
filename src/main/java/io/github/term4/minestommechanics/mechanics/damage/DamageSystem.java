package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.death.DeathConfig;
import io.github.term4.minestommechanics.mechanics.death.DeathConfig.DeathContext;
import io.github.term4.minestommechanics.presets.vanilla18.Knockback;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingSystem;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeSystem;
import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.attribute.defense.MitigationRequest;
import io.github.term4.minestommechanics.mechanics.attribute.defense.ProtectionCategory;
import io.github.term4.minestommechanics.api.event.damage.DamageEvent;
import io.github.term4.minestommechanics.api.event.damage.PreDamageEvent;
import io.github.term4.minestommechanics.api.event.damage.DamageAppliedEvent;
import io.github.term4.minestommechanics.mechanics.damage.DamageCalculator.DamageResult;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.ResolvedDamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.silent.HurtSuppression;
import io.github.term4.minestommechanics.mechanics.damage.silent.SilentDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.breathing.DrowningDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.explosion.ExplosionDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.projectile.ProjectileDamage;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.projectile.entities.arrow.StuckArrows;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.util.tick.TickState;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.item.ItemStack;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.ListenerHandle;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main damage system: resolves config, computes the amount, fires {@link DamageEvent}, applies the 1.8 overdamage
 * rule, and applies the damage. Mirrors KnockbackSystem.
 *
 * <p>Every fresh hit except drowning broadcasts the victim's server-tracked velocity. Non-melee hits route it through
 * the {@link KnockbackSystem} with {@link DamageConfig#hurtKnockback}; melee's broadcast is its own knockback.
 */
public final class DamageSystem implements MechanicsModule {

    /** This system's identity for per-module TPS scaling (its {@code referenceTps} feel-baseline). */
    public static final Key KEY = Key.key("mm:damage");

    private static final Tag<TickState> INVUL_DAMAGE = Tag.Transient("mm:invul-damage");
    /** Amount of the hit that opened the current invulnerability window (for overdamage replacement). */
    private static final Tag<Float> LAST_DAMAGE = Tag.Transient("mm:last-damage");
    private static final Tag<DamageType> LAST_DAMAGE_TYPE = Tag.Transient("mm:last-damage-type");
    /** Melee weapon that opened the current invulnerability window ({@code null} = fist / non-melee). */
    private static final Tag<ItemStack> OPENING_ITEM = Tag.Transient("mm:opening-hit-item");

    /** Default invul ticks (vanilla 1.8) when nothing resolves; scaled to live TPS at the stamp site. */
    public static final int DEFAULT_INVUL_TICKS = 10;

    /** Fallback death-animation length when a scoped {@link DeathConfig#deathAnimationTicks} is unset (vanilla {@code deathTime} 20). */
    private static final int DEATH_ANIMATION_TICKS = 20;

    // Pre/Applied fire only when listened to; main always fires
    private static final ListenerHandle<PreDamageEvent> PRE_DAMAGE = EventDispatcher.getHandle(PreDamageEvent.class);
    private static final ListenerHandle<DamageAppliedEvent> DAMAGE_APPLIED = EventDispatcher.getHandle(DamageAppliedEvent.class);
    private static final AtomicBoolean CLOCK_RESET = new AtomicBoolean();

    private final MinestomMechanics mm;
    private final DamageConfig config;
    private final DamageCalculator calc;
    private final DamageTypeRegistry registry;
    private final Services services;
    private final EventNode<@NotNull Event> node;

    public DamageSystem(MinestomMechanics mm, DamageConfig config) {
        this.mm = mm;
        this.node = EventNode.all("mm:damage");
        this.config = config;
        this.services = mm.services();
        this.calc = new DamageCalculator(this.services, Vanilla18.damage());
        this.registry = new DamageTypeRegistry(this, mm).registerVanillaDefaults();
        // i-frame window stamps use the victim's per-instance clock; drop the window state on (re)spawn (the TickState
        // future-guard covers most cross-instance carries, but a long-lived target instance could coincide with one).
        this.node.addListener(PlayerSpawnEvent.class, e -> clearDamageWindow(e.getPlayer()));
        if (CLOCK_RESET.compareAndSet(false, true)) {
            TickSystem.onClockChange(e -> {
                if (e instanceof LivingEntity le) clearDamageWindow(le);
            });
        }
        // Death handling lives here (the damage system owns the death/respawn path). Vanilla clears active effects +
        // transient combat state on death; Minestom's kill() does not, so it would leak across respawn (the Player object
        // persists). clearEffects() fires the remove events the AttributeSystem reacts to. Behavior is the victim's scoped
        // DeathConfig; an unset knob defaults to vanilla (on / 20-tick animation).
        this.node.addListener(EntityDeathEvent.class, e -> {
            if (!(e.getEntity() instanceof LivingEntity dead)) return;
            DeathContext ctx = new DeathContext(dead);
            DeathConfig death = effectiveDeath(mm.profiles().resolve(dead, MechanicsKeys.DEATH), ctx);
            if (deathFlag(death != null ? death.clearEffects(ctx) : null)) dead.clearEffects();
            if (deathFlag(death != null ? death.resetCombatState(ctx) : null)) resetCombatState(dead);
            // Minestom keeps the health-0 entity in the world (viewers see a lingering body; 1.8/Via replays the death
            // smoke on chunk reload) - hide it from viewers, but only AFTER the death animation plays (immediate removal
            // yanks the entity mid-animation = instant disappear). Guarded so a fast respawn doesn't hide the live player.
            if (deathFlag(death != null ? death.hideCorpse(ctx) : null) && dead instanceof Player p) {
                Integer knob = death != null ? death.deathAnimationTicks(ctx) : null;
                int ticks = knob != null ? knob : DEATH_ANIMATION_TICKS;
                p.scheduler().buildTask(() -> { if (p.isDead()) p.setAutoViewable(false); })
                        .delay(TaskSchedule.tick(TickScaler.duration(ticks, KEY))).schedule();
            }
        });
        // re-show the respawned player NEXT tick (after the respawn teleport, else viewers re-see them at the death spot)
        this.node.addListener(PlayerRespawnEvent.class, e -> {
            DeathContext ctx = new DeathContext(e.getPlayer());
            DeathConfig death = effectiveDeath(mm.profiles().resolve(e.getPlayer(), MechanicsKeys.DEATH), ctx);
            if (deathFlag(death != null ? death.hideCorpse(ctx) : null)) e.getPlayer().scheduleNextTick(p -> p.setAutoViewable(true));
        });
    }

    /** Effective config for a snapshot carrying none: the victim's scoped profile, else the install config. */
    private DamageConfig configFor(@Nullable Entity target) {
        DamageConfig scoped = mm.profiles().resolve(target, MechanicsKeys.DAMAGE);
        return scoped != null ? scoped : config;
    }

    /**
     * Resolution context for a snapshot, applying the config chain (snapshot -> victim scope -> install) when the
     * snapshot carries none. Producers use this to read per-type knobs before emitting.
     */
    public DamageContext contextFor(DamageSnapshot snap) {
        DamageSnapshot working = snap.config() != null ? snap : snap.withConfig(configFor(snap.target()));
        return DamageContext.of(working, services);
    }

    /** Whether {@code type} is enabled for {@code target} under its effective config chain. */
    public boolean typeEnabled(DamageType type, Entity target) {
        DamageContext ctx = contextFor(DamageSnapshot.of(target, type));
        return ctx.typeConfig().enabled(ctx);
    }

    /**
     * Outcome of {@link #apply}, mirroring vanilla {@code damageEntity}: rulesets gate effects on it (knockback on
     * {@link #FRESH_DAMAGE}, sprint reset on {@link #landed()}).
     */
    public enum DamageOutcome {
        /** Absorbed by the i-frame window, or cancelled / zero / disabled. Distinct from {@link #IMMUNE}. */
        BLOCKED,
        /** Fundamentally immune (creative/spectator): no damage and no knockback. Kept distinct from {@link #BLOCKED} so a
         *  projectile can react differently - a 1.8 arrow passes through an immune target but deflects off an i-frame one. */
        IMMUNE,
        /** Overdamage replacement inside the i-frame window: damage dealt, but the fresh effects are skipped. */
        OVERDAMAGE,
        /** Fresh damage - full effects (knockback, opens the i-frame window). */
        FRESH_DAMAGE;

        /** Damage was dealt (fresh or replacement). */
        public boolean landed() { return this == OVERDAMAGE || this == FRESH_DAMAGE; }
    }

    /**
     * Applies damage from a snapshot. Base amount comes from the {@link DamageCalculator}; type-specific modifiers are
     * baked into the snapshot beforehand. Returns the {@link DamageOutcome} so rulesets can gate effects.
     */
    public DamageOutcome apply(DamageSnapshot snap) {
        if (!(snap.target() instanceof LivingEntity)) return DamageOutcome.BLOCKED;

        // config: snapshot -> victim scope -> install (none = inert, empty = vanilla floor)
        DamageConfig effective = snap.config() != null ? snap.config() : configFor(snap.target());
        if (effective == null) return DamageOutcome.BLOCKED;

        DamageSnapshot working = snap.config() != null ? snap : snap.withConfig(effective);

        if (PRE_DAMAGE.hasListener()) {
            PreDamageEvent pre = new PreDamageEvent(working, services);
            EventDispatcher.call(pre);
            if (pre.isCancelled()) return DamageOutcome.BLOCKED;
            working = pre.finalSnap();
            if (!(working.target() instanceof LivingEntity)) return DamageOutcome.BLOCKED;
        }

        DamageResult result = calc.compute(working);

        float amount = result.amount();

        DamageEvent event = new DamageEvent(working, amount, services);
        EventDispatcher.call(event);
        if (event.isCancelled()) return DamageOutcome.BLOCKED;

        DamageSnapshot finalSnap = event.finalSnap();
        if (!(finalSnap.target() instanceof LivingEntity living)) return DamageOutcome.BLOCKED;

        DamageType type = finalSnap.type();
        DamageContext typeCtx = contextFor(finalSnap);
        DamageTypeConfig typeCfg = typeCtx.typeConfig();
        // per-scope kill switch; read off the final snap so a listener can swap in an enabled config
        if (!typeCfg.enabled(typeCtx)) return DamageOutcome.BLOCKED;
        amount = event.amount();
        boolean bypassImmune = event.bypassImmune() || typeCfg.bypassImmune(typeCtx);
        boolean bypassInvul = event.bypassInvul() || typeCfg.bypassInvul(typeCtx);

        if (!bypassImmune && isImmune(living)) return DamageOutcome.IMMUNE;

        // Fire Resistance: total immunity to fire-source damage. Vanilla blocks it at the hit entry (1.8 damageEntity /
        // 26 hurtServer return false) - before i-frames and mitigation, so it consumes no invul window and never flashes.
        // Keyed on the type's FIRE protection set (in-fire / on-fire / lava / burning); not gated by the i-frame bypass.
        AttributeSystem fireAttrs = services.attributes();
        if (fireAttrs != null && type.protectionCategories().contains(ProtectionCategory.FIRE) && fireAttrs.fireResistant(living)) {
            return DamageOutcome.BLOCKED;
        }

        ResolvedDamageConfig resolved = calc.resolveConfig(
                finalSnap.config() != null ? finalSnap : finalSnap.withConfig(configFor(finalSnap.target())));

        boolean overdamage = Boolean.TRUE.equals(pick(typeCfg.overdamage(typeCtx), resolved.enableOverdamage()));
        boolean generalSilent = Boolean.TRUE.equals(pick(typeCfg.silent(typeCtx), resolved.silent()));

        // In an active i-frame window this is a replacement (overdamage) hit, else fresh. Mitigation (components + blocking +
        // armor/resistance/EPF) applies to the FULL amount FIRST either way - vanilla mitigates, THEN takes the overdamage
        // remainder, so a replacement isn't "true damage" (a fall landing mid-i-frame still gets resistance / Feather Falling).
        boolean replacement = event.invulnerable() && !bypassInvul;
        if (replacement && !overdamage) return DamageOutcome.BLOCKED;

        amount = applyComponents(typeCtx, event, amount, replacement);
        // Blocking (sword/shield): vanilla reduces a blocked hit BEFORE armor (1.8 EntityHuman.damageEntity). The
        // BlockingSystem owns the decision entirely (is the player blocking, the behavior, what's blockable).
        BlockingSystem blocking = services.blocking();
        if (blocking != null) amount = blocking.reduce(living, typeCtx, amount);
        amount = applyMitigation(living, type, typeCfg, typeCtx, amount);
        boolean triggersInvul = typeCfg.triggersInvul(typeCtx);

        if (replacement) {
            // overdamage: only the mitigated amount above the window's stored highwater lands
            float applied = amount > event.stored() ? amount - event.stored() : 0f;
            if (applied <= 0) return DamageOutcome.BLOCKED;
            Boolean odSilent = pick(typeCfg.overdamageSilent(typeCtx), resolved.overdamageSilent());
            boolean replacementSilent = odSilent != null ? odSilent : generalSilent;
            living.setTag(LAST_DAMAGE, Math.max(event.stored(), amount));
            living.setTag(LAST_DAMAGE_TYPE, type);
            applyDamage(living, type, finalSnap, applied, replacementSilent);
            fireDamageApplied(finalSnap, applied, DamageOutcome.OVERDAMAGE);
            return DamageOutcome.OVERDAMAGE;
        }

        // fresh hit: a 0-damage hit still lands when its type triggers invul (snowball/egg); only negative or non-invul 0 is dropped
        if (amount < 0 || (amount == 0f && !triggersInvul)) return DamageOutcome.BLOCKED;

        storeOpeningItem(living, finalSnap.item());
        living.setTag(LAST_DAMAGE, amount);
        living.setTag(LAST_DAMAGE_TYPE, type);
        applyDamage(living, type, finalSnap, amount, generalSilent);
        Boolean ownsFlag = typeCfg.ownsVelocityBroadcast(typeCtx);
        boolean ownsVelocity = ownsFlag != null ? ownsFlag : knockbackOwnsVelocity(type);
        if (Boolean.TRUE.equals(resolved.syncHurtVelocity())
                && !ownsVelocity
                && !DROWN_KEY.equals(type.key())) {
            applyHurtKnockback(living, resolved.hurtKnockback());
        }
        Integer invulTicks = pick(typeCfg.invulTicks(typeCtx), resolved.invulTicks());
        if (triggersInvul && invulTicks != null && invulTicks > 0) {
            // i-frame window is a server-authoritative duration: stretch the vanilla-tick count to live TPS (identity at 20)
            setDamageInvulnerable(living, TickScaler.duration(invulTicks, mm.profiles().resolve(living, MechanicsKeys.TICK_SCALING), KEY));
        }
        dispatchWeaponOnHit(living, finalSnap);
        fireDamageApplied(finalSnap, amount, DamageOutcome.FRESH_DAMAGE);
        return DamageOutcome.FRESH_DAMAGE;
    }

    private void fireDamageApplied(DamageSnapshot snap, float dealt, DamageOutcome outcome) {
        if (DAMAGE_APPLIED.hasListener()) EventDispatcher.call(new DamageAppliedEvent(snap, dealt, outcome, services));
    }

    /**
     * Fires the attacker's weapon on-hit enchant side effects (Fire Aspect, ...) after a fresh landed hit. Damage-domain
     * combat enchants - defined in the attribute catalog, triggered here. No attacker / no weapon = nothing fires.
     */
    private void dispatchWeaponOnHit(LivingEntity victim, DamageSnapshot snap) {
        AttributeSystem attrs = services.attributes();
        if (attrs == null || !(snap.source() instanceof LivingEntity attacker)) return;
        ItemStack weapon = snap.item();
        if (weapon == null || weapon.isAir()) return;
        attrs.dispatchWeaponOnHit(attacker, victim, weapon);
    }

    /** Built-in default for the per-type {@code ownsVelocityBroadcast} knob: melee, thrown, and explosion own the velocity broadcast (the {@code ExplosionSystem} delivers its own KB), so the generic hurt velocity isn't also sent. */
    private static boolean knockbackOwnsVelocity(DamageType type) {
        return MeleeDamage.KEY.equals(type.key()) || ProjectileDamage.KEY.equals(type.key()) || ExplosionDamage.KEY.equals(type.key());
    }

    private static <T> @Nullable T pick(@Nullable T typeValue, @Nullable T globalValue) {
        return typeValue != null ? typeValue : globalValue;
    }

    /** A nullable {@link DeathConfig} toggle: unset (or true) is on; only an explicit {@code false} disables. */
    private static boolean deathFlag(@Nullable Boolean v) { return !Boolean.FALSE.equals(v); }

    /** The scoped config with its {@code subConfig} overlay applied - the resolver-side step the other systems do. */
    private static @Nullable DeathConfig effectiveDeath(@Nullable DeathConfig cfg, DeathContext ctx) {
        if (cfg == null || cfg.subConfig == null) return cfg;
        DeathConfig overlay = cfg.subConfig.apply(ctx);
        return overlay != null ? overlay.fromBase(cfg) : cfg;
    }

    /** Vanilla {@code damageEntity}: drowning is the one source that never triggers {@code ac()}. */
    private static final Key DROWN_KEY = Key.key("minecraft:drown");
    /** Fallback hurt knockback when no config sets one (built once - it is immutable). */
    private static final KnockbackConfig DEFAULT_HURT_KB = Knockback.hurt();

    /**
     * Defense mitigation: hands the hit to the attribute system's {@link AttributeSystem#mitigate pipeline} (armor →
     * resistance → EPF, vanilla order; absorption is Minestom's). The damage side supplies the per-hit gating - the
     * type's protection {@link io.github.term4.minestommechanics.mechanics.damage.types.DamageType#protectionCategories
     * categories} and its "true damage" bypass flags. No attribute system installed = no mitigation.
     */
    private float applyMitigation(LivingEntity living, DamageType type, DamageTypeConfig typeCfg, DamageContext ctx, float amount) {
        AttributeSystem attrs = services.attributes();
        if (attrs == null || amount <= 0) return amount;
        // the damage type contributes the broad stage flags; the snapshot (item/attack) contributes targeted bypass
        Bypass bypass = Bypass.builder()
                .all(typeCfg.bypassAll(ctx))
                .armor(typeCfg.bypassArmor(ctx))
                .effects(typeCfg.bypassEffects(ctx))
                .enchants(typeCfg.bypassEnchants(ctx))
                .build()
                .merge(ctx.snap().bypass());
        MitigationRequest req = MitigationRequest.of(type.protectionCategories(), bypass, ThreadLocalRandom.current());
        return attrs.mitigate(living, amount, req);
    }

    /**
     * The hurt velocity broadcast: a fresh hit broadcasts the victim's server velocity (gated by a knockback-resistance
     * roll), routed through the {@link KnockbackSystem} with a zero-impulse {@link DamageConfig#hurtKnockback} so all
     * velocity sends share one path.
     */
    private void applyHurtKnockback(LivingEntity living, @Nullable KnockbackConfig cfg) {
        KnockbackSystem kb = services.knockback();
        if (kb == null) return;
        // LEGACY rolls to negate (1:1 with vanilla); MODERN's ×(1-resistance) scale folds in with the KB stage list later.
        AttributeSystem attrs = services.attributes();
        double resistance = attrs != null ? attrs.knockbackResistance(living)
                : living.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
        if (ThreadLocalRandom.current().nextDouble() < resistance) return;
        kb.apply(new KnockbackSnapshot(living, false, null,
                living.getPosition(), living.getPosition().direction(),
                cfg != null ? cfg : DEFAULT_HURT_KB));
    }

    private float applyComponents(DamageContext ctx, DamageEvent event, float amount, boolean overdamage) {
        DamageConfig cfg = event.config();
        if (cfg == null) cfg = configFor(event.target());
        if (cfg == null) return amount;
        if (cfg.subConfig != null) {
            DamageConfig sub = cfg.subConfig.apply(ctx);
            if (sub != null) cfg = sub.fromBase(cfg);
        }
        List<DamageComponent> components = cfg.customComponents;
        if (components == null || components.isEmpty()) return amount;
        for (DamageComponent component : components) {
            Float next = component.apply(ctx, event, amount, overdamage);
            if (next != null) amount = next;
        }
        return amount;
    }

    private static void storeOpeningItem(LivingEntity living, @Nullable ItemStack item) {
        if (item != null && !item.isAir()) living.setTag(OPENING_ITEM, item);
        else living.removeTag(OPENING_ITEM);
    }

    /** Melee weapon that opened the target's current damage-invul window, or {@code null} (fist / none). */
    public static @Nullable ItemStack openingHitItem(LivingEntity target) {
        return target.getTag(OPENING_ITEM);
    }

    private void applyDamage(LivingEntity living, DamageType type, DamageSnapshot snap, float amount, boolean silent) {
        // lethal hits fall through to living.damage() so Minestom handles death (its EntityDeathEvent/PlayerDeathEvent);
        // non-lethal silent hits skip the hurt effect.
        if (silent && living instanceof Player p) {
            // absorption absorbs first (Minestom's damage() does this; the silent path sets health directly, so replicate it)
            float absorb = p.getAdditionalHearts();
            float absorbed = Math.min(absorb, amount);
            float newHealth = p.getHealth() - (amount - absorbed);
            if (newHealth > 0) {
                if (absorbed > 0) p.setAdditionalHearts(absorb - absorbed);
                SilentDamage.setHealthWithoutHurtEffect(p, newHealth, mm.clientInfo());
                return;
            }
        }
        Entity source = snap.source();
        Damage damage = new Damage(type.minecraftType(), source, source, snap.point(), amount);
        living.damage(damage);
    }

    public DamageConfig config() { return config; }

    /**
     * Effective invul ticks for a type: per-type override, else the global value, else {@link #DEFAULT_INVUL_TICKS}.
     * A {@code null} type resolves the global value only.
     */
    public int defaultInvulTicks(@Nullable DamageType type) {
        // only constant invul values resolve without a snapshot
        if (type != null) {
            DamageTypeConfig tcfg = config != null ? config.typeConfig(type.key()) : null;
            if (tcfg == null) tcfg = type.defaultConfig();
            Integer v = tcfg.invulTicksConstant();
            if (v != null) return v;
        }
        Integer v = config != null && config.invulTicks != null ? config.invulTicks.constantOrNull() : null;
        return v != null ? v : DEFAULT_INVUL_TICKS;
    }

    /** The standard hit i-frame window (the {@code player_attack} type's invul), used to align other systems' windows. */
    public int defaultInvulTicks() {
        return defaultInvulTicks(registry.get(MeleeDamage.KEY));
    }

    /** Registry of damage types and their handlers. */
    public DamageTypeRegistry registry() { return registry; }

    public EventNode<@NotNull Event> node() { return node; }

    /**
     * Installs reading the GLOBAL profile's {@link DamageConfig}: its {@code typeConfigs} start the self-driven
     * producers (fall/fire/cactus/...). Set the profile before installing. With no global profile this is inert
     * (a hit with no scoped or snapshot config is a no-op). {@code extraTypes} registers + enables custom types.
     */
    public static DamageSystem install(MinestomMechanics mm, DamageType... extraTypes) {
        return install(mm, mm.profiles().resolve(null, MechanicsKeys.DAMAGE), extraTypes);
    }

    /** Installs from an explicit config (the modular path): enables its {@code typeConfigs} producers. */
    public static DamageSystem install(MinestomMechanics mm, DamageConfig cfg, DamageType... extraTypes) {
        var system = new DamageSystem(mm, cfg);
        mm.register(system);
        EnvironmentalDamageTicker.instance().bind(system);
        HurtSuppression.install(system.node);
        mm.install(system.node);
        for (DamageType type : extraTypes) {
            if (!system.registry.contains(type.key())) system.registry.register(type);
        }
        if (cfg != null) {
            for (Key key : cfg.typeConfigs.keySet()) {
                if (system.registry.contains(key)) system.registry.enable(key);
            }
        }
        for (DamageType type : extraTypes) system.registry.enable(type.key());
        return system;
    }

    /**
     * Pre-event early-out: while the target's window is active, an attempt that can't beat the stored highwater is
     * dropped before any {@link DamageEvent}. Repeating producers (fire, cactus) use this to avoid event spam.
     */
    public static boolean absorbedByWindow(LivingEntity target, float amount) {
        return isInvulnerableToDamage(target) && amount <= lastDamage(target);
    }

    /** The "last damage" highwater stored for the target's current invul window ({@code 0} if none). */
    public static float lastDamage(LivingEntity le) {
        Float v = le.getTag(LAST_DAMAGE);
        return v != null ? v : 0f;
    }

    /** Type of the target's most recent LANDED damage (fresh or overdamage - a replacement overwrites it), or {@code null}. */
    public static @Nullable DamageType lastDamageType(LivingEntity le) {
        return le.getTag(LAST_DAMAGE_TYPE);
    }

    /** Opens (or re-opens) the target's damage-invulnerability window (i-frames) for {@code duration} ticks. */
    public static void setDamageInvulnerable(Entity e, int duration) {
        if (!(e instanceof LivingEntity le) || duration <= 0) return;
        // stamp against the instance-local combat clock so the window is opened and checked on one phase (see TickSystem)
        le.setTag(INVUL_DAMAGE, new TickState(TickSystem.tick(le), duration));
    }

    /** Whether the target is inside its i-frame window. Not fundamental immunity (creative/spectator). */
    public static boolean isInvulnerableToDamage(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        TickState s = getDamageInvul(le);
        return s != null && s.isActive(TickSystem.tick(le));
    }

    /** Fundamental immunity - creative/spectator, which take no damage AND no knockback (vanilla {@code abilities.isInvulnerable}). Distinct from the i-frame window {@link #isInvulnerableToDamage}. */
    public static boolean isImmune(Entity e) {
        return e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    /** Ticks left in the target's damage-invulnerability window ({@code 0} if none). */
    public static int remainingDamageInvul(LivingEntity le) {
        TickState s = getDamageInvul(le);
        return s != null ? s.remainingTicks(TickSystem.tick(le)) : 0;
    }

    /** Clears the i-frame window state (the window, its overdamage highwater, its type, and its opening item) as one unit. */
    public static void clearDamageWindow(LivingEntity le) {
        le.removeTag(INVUL_DAMAGE);
        le.removeTag(LAST_DAMAGE);
        le.removeTag(LAST_DAMAGE_TYPE);
        le.removeTag(OPENING_ITEM);
    }

    /**
     * Resets the transient combat/visual state vanilla starts fresh after death (it carries to respawn - the Player
     * object persists): fire, drowning air, stuck arrows, and residual velocity. Gated by {@link DeathConfig#resetCombatState};
     * effects are gated separately ({@link DeathConfig#clearEffects}); the i-frame window + fall distance reset on (re)spawn.
     */
    private static void resetCombatState(LivingEntity entity) {
        entity.setFireTicks(0);
        entity.setVelocity(Vec.ZERO);
        DrowningDamage.resetAir(entity);
        StuckArrows.clear(entity);
    }

    private static @Nullable TickState getDamageInvul(LivingEntity le) {
        return le.getTag(INVUL_DAMAGE);
    }
}
