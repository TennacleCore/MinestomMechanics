package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.Vanilla18;
import io.github.term4.minestommechanics.api.event.DamageEvent;
import io.github.term4.minestommechanics.mechanics.damage.DamageCalculator.DamageResult;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.ResolvedDamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.silent.HurtSuppression;
import io.github.term4.minestommechanics.mechanics.damage.silent.SilentDamage;
import io.github.term4.minestommechanics.tracking.EnvironmentalDamageTicker;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.projectile.ProjectileDamage;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.util.TickClock;
import io.github.term4.minestommechanics.util.TickState;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.item.ItemStack;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Main damage system: resolves config, computes the amount, fires {@link DamageEvent}, applies the 1.8 overdamage
 * rule, and applies the damage. Mirrors KnockbackSystem.
 *
 * <p>Every fresh hit except drowning broadcasts the victim's server-tracked velocity. Non-melee hits route it through
 * the {@link KnockbackSystem} with {@link DamageConfig#hurtKnockback}; melee's broadcast is its own knockback.
 */
public final class DamageSystem {

    private static final Tag<TickState> INVUL_DAMAGE = Tag.Transient("mm:invul-damage");
    /** Amount of the hit that opened the current invulnerability window (for overdamage replacement). */
    private static final Tag<Float> LAST_DAMAGE = Tag.Transient("mm:last-damage");
    /** Melee weapon that opened the current invulnerability window ({@code null} = fist / non-melee). */
    private static final Tag<ItemStack> OPENING_ITEM = Tag.Transient("mm:opening-hit-item");

    /** Default invul ticks when nothing resolves. TODO: scale by TPS once that system exists. */
    public static final int DEFAULT_INVUL_TICKS = 10;

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
        this.calc = new DamageCalculator(this.services, Vanilla18.dmg());
        this.registry = new DamageTypeRegistry(this, mm).registerVanillaDefaults();
    }

    /** Effective config for a snapshot carrying none: the victim's scoped profile, else the install config. */
    private DamageConfig configFor(@Nullable Entity target) {
        DamageConfig scoped = mm.profiles().damageFor(target);
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

        // config chain: snapshot -> victim scope -> install. none = inert (no-op); an empty config processes at the vanilla floor
        DamageConfig effective = snap.config() != null ? snap.config() : configFor(snap.target());
        if (effective == null) return DamageOutcome.BLOCKED;

        DamageSnapshot working = snap.config() != null ? snap : snap.withConfig(effective);
        DamageResult result = calc.compute(working);

        float amount = result.amount();

        // TODO(events): split into phased events (pre/modify/final) and carry the resolved config to avoid double-resolution
        DamageEvent event = new DamageEvent(working, amount);
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
        // two independent bypasses: bypassImmune = creative/spectator immunity, bypassInvul = the i-frame window
        boolean bypassImmune = event.bypassImmune() || typeCfg.bypassImmune(typeCtx);
        boolean bypassInvul = event.bypassInvul() || typeCfg.bypassInvul(typeCtx);

        // creative/spectator take no damage. IMMUNE (not BLOCKED) lets a projectile distinguish this from the i-frame window
        if (!bypassImmune && living instanceof Player gmPlayer
                && (gmPlayer.getGameMode() == GameMode.CREATIVE || gmPlayer.getGameMode() == GameMode.SPECTATOR)) {
            return DamageOutcome.IMMUNE;
        }

        ResolvedDamageConfig resolved = calc.resolveConfig(
                finalSnap.config() != null ? finalSnap : finalSnap.withConfig(configFor(finalSnap.target())));

        // per-type override, else the global value
        boolean overdamage = Boolean.TRUE.equals(pick(typeCfg.overdamage(typeCtx), resolved.enableOverdamage()));
        boolean generalSilent = Boolean.TRUE.equals(pick(typeCfg.silent(typeCtx), resolved.silent()));

        if (event.invulnerable() && !bypassInvul) {
            // overdamage replacement
            if (!overdamage) return DamageOutcome.BLOCKED;
            float applied = amount > event.stored() ? amount - event.stored() : 0f;
            applied = applyComponents(typeCtx, event, applied, true);
            if (applied > 0) {
                // overdamage silent override, else the general flag
                Boolean odSilent = pick(typeCfg.overdamageSilent(typeCtx), resolved.overdamageSilent());
                boolean replacementSilent = odSilent != null ? odSilent : generalSilent;
                living.setTag(LAST_DAMAGE, Math.max(event.stored(), amount));
                applyDamage(living, type, finalSnap, applied, replacementSilent);
                return DamageOutcome.OVERDAMAGE;
            }
            return DamageOutcome.BLOCKED;
        }

        amount = applyComponents(typeCtx, event, amount, false);
        // a 0-damage hit still lands when its type triggers invul (snowball/egg); only negative or non-invul 0 is dropped
        boolean triggersInvul = typeCfg.triggersInvul(typeCtx);
        if (amount < 0 || (amount == 0f && !triggersInvul)) return DamageOutcome.BLOCKED;

        storeOpeningItem(living, finalSnap.item());
        living.setTag(LAST_DAMAGE, amount);
        applyDamage(living, type, finalSnap, amount, generalSilent);
        // every fresh hit except drowning broadcasts velocity; melee/projectiles own theirs via the KnockbackSystem.
        // the per-type ownsVelocityBroadcast knob wins when set, else the built-in default
        Boolean ownsFlag = typeCfg.ownsVelocityBroadcast(typeCtx);
        boolean ownsVelocity = ownsFlag != null ? ownsFlag : knockbackOwnsVelocity(type);
        if (Boolean.TRUE.equals(resolved.syncHurtVelocity())
                && !ownsVelocity
                && !DROWN_KEY.equals(type.key())) {
            applyHurtKnockback(living, resolved.hurtKnockback());
        }
        Integer invulTicks = pick(typeCfg.invulTicks(typeCtx), resolved.invulTicks());
        if (triggersInvul && invulTicks != null && invulTicks > 0) {
            setDamageInvulnerable(living, invulTicks);
        }
        return DamageOutcome.FRESH_DAMAGE;
    }

    /** Built-in default for the per-type {@code ownsVelocityBroadcast} knob: melee + thrown own the velocity broadcast, so the generic hurt velocity isn't also sent. */
    private static boolean knockbackOwnsVelocity(DamageType type) {
        return MeleeDamage.KEY.equals(type.key()) || ProjectileDamage.KEY.equals(type.key());
    }

    /** Per-type override when non-null, else the global config value (which may itself be null). */
    private static <T> @Nullable T pick(@Nullable T typeValue, @Nullable T globalValue) {
        return typeValue != null ? typeValue : globalValue;
    }

    /** Vanilla {@code damageEntity}: drowning is the one source that never triggers {@code ac()}. */
    private static final Key DROWN_KEY = Key.key("minecraft:drown");
    /** Fallback hurt knockback when no config sets one (built once - it is immutable). */
    private static final KnockbackConfig DEFAULT_HURT_KB = Vanilla18.hurtKb();

    /**
     * The hurt velocity broadcast: a fresh hit broadcasts the victim's server velocity (gated by a knockback-resistance
     * roll), routed through the {@link KnockbackSystem} with a zero-impulse {@link DamageConfig#hurtKnockback} so all
     * velocity sends share one path.
     */
    private void applyHurtKnockback(LivingEntity living, @Nullable KnockbackConfig cfg) {
        KnockbackSystem kb = services.knockback();
        if (kb == null) return;
        if (ThreadLocalRandom.current().nextDouble() < living.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE)) return;
        kb.apply(new KnockbackSnapshot(living, false, null,
                living.getPosition(), living.getPosition().direction(),
                cfg != null ? cfg : DEFAULT_HURT_KB));
    }

    private float applyComponents(DamageContext ctx, DamageEvent event, float amount, boolean overdamage) {
        DamageConfig cfg = event.config();
        if (cfg == null) cfg = configFor(event.target());
        if (cfg == null) return amount; // inert: no config -> no custom components
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
        // lethal hits fall through to living.damage() so Minestom handles death; non-lethal silent hits skip the hurt effect
        float newHealth = (float) Math.max(0, living.getHealth() - amount);
        if (silent && living instanceof Player p && newHealth > 0) {
            SilentDamage.setHealthWithoutHurtEffect(p, newHealth, mm.clientInfo());
            return;
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

    /** This system's listener node ({@code mm:damage}); everything the system hooks lives under it. */
    public EventNode<@NotNull Event> node() { return node; }

    /**
     * Installs the system inert (no install-level config): a hit with no scoped or snapshot config is a no-op, and no
     * self-driven types (fall/fire/cactus) start - those only enable from a config's {@link DamageConfig#typeConfigs}.
     * Pass an empty config to process hits at the vanilla floor. {@code extraTypes} registers + enables custom types.
     */
    public static DamageSystem install(MinestomMechanics mm, DamageType... extraTypes) {
        return install(mm, (DamageConfig) null, extraTypes);
    }

    public static DamageSystem install(MinestomMechanics mm, DamageConfig cfg, DamageType... extraTypes) {
        var system = new DamageSystem(mm, cfg);
        mm.registerDamage(system);
        EnvironmentalDamageTicker.instance().bind(system, mm);
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

    /** Opens (or re-opens) the target's damage-invulnerability window (i-frames) for {@code duration} ticks. */
    public static void setDamageInvulnerable(Entity e, int duration) {
        if (!(e instanceof LivingEntity le) || duration <= 0) return;
        le.setTag(INVUL_DAMAGE, new TickState(TickClock.now(), duration));
    }

    /** Whether the target is inside its i-frame window. Not fundamental immunity (creative/spectator). */
    public static boolean isInvulnerableToDamage(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        TickState s = getDamageInvul(le);
        return s != null && s.isActive();
    }

    /** Ticks left in the target's damage-invulnerability window ({@code 0} if none). */
    public static int remainingDamageInvul(LivingEntity le) {
        TickState s = getDamageInvul(le);
        return s != null ? s.remainingTicks() : 0;
    }

    private static @Nullable TickState getDamageInvul(LivingEntity le) {
        return le.getTag(INVUL_DAMAGE);
    }
}
