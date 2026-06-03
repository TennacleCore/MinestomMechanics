package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.api.event.DamageEvent;
import io.github.term4.minestommechanics.mechanics.damage.DamageCalculator.DamageResult;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.ResolvedDamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.silent.HurtSuppression;
import io.github.term4.minestommechanics.mechanics.damage.silent.SilentDamage;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.minestommechanics.mechanics.damage.types.playerattack.PlayerAttack;
import io.github.term4.minestommechanics.platform.Constants;
import io.github.term4.minestommechanics.util.TickClock;
import io.github.term4.minestommechanics.util.TickState;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main damage system. Resolves config, computes the final amount, fires the {@link DamageEvent}
 * API, applies the 1.8 overdamage replacement rule, and applies damage. Mirrors KnockbackSystem.
 */
public final class DamageSystem {

    private static final Tag<TickState> INVUL_DAMAGE = Tag.Transient("mm:invul-damage");
    /** Amount of the hit that opened the current invulnerability window (for overdamage replacement). */
    private static final Tag<Float> LAST_DAMAGE = Tag.Transient("mm:last-damage");

    private final MinestomMechanics mm;
    private final EventNode<@NotNull Event> apiEvents;
    private final DamageConfig config;
    private final DamageCalculator calc;
    private final DamageTypeRegistry registry;
    private final Services services;

    public DamageSystem(MinestomMechanics mm, DamageConfig config) {
        this.mm = mm;
        this.apiEvents = mm.events();
        this.config = config;
        this.services = mm.services();
        this.calc = new DamageCalculator(this.services, Vanilla18.dmg());
        this.registry = new DamageTypeRegistry(this, mm).registerVanillaDefaults();
    }

    /**
     * Applies damage from a snapshot. The base amount comes from the snapshot/type via the
     * {@link DamageCalculator}; type-specific modifiers (e.g. the melee crit multiplier) are baked
     * into the snapshot by the producing {@link DamageType} before it is applied.
     */
    public void apply(DamageSnapshot snap) {
        if (!(snap.target() instanceof LivingEntity)) return;

        DamageSnapshot working = snap.config() != null ? snap : snap.withConfig(config);
        DamageResult result = calc.compute(working);

        float amount = result.amount();

        DamageEvent event = new DamageEvent(working, amount);
        apiEvents.call(event);
        if (event.cancelled()) return;

        DamageSnapshot finalSnap = event.finalSnap();
        if (!(finalSnap.target() instanceof LivingEntity living)) return;

        DamageType type = finalSnap.type();
        DamageContext typeCtx = DamageContext.of(finalSnap, services);
        // Per-type config from the active DamageConfig (override), else the type's defaults.
        DamageTypeConfig typeCfg = typeCtx.typeConfig();
        amount = event.amount();
        boolean bypass = event.bypassInvul() || typeCfg.bypassInvul(typeCtx);

        ResolvedDamageConfig resolved = calc.resolveConfig(
                finalSnap.config() != null ? finalSnap : finalSnap.withConfig(config));

        // Each knob: per-type override when set, else the global config value.
        boolean overdamage = Boolean.TRUE.equals(pick(typeCfg.overdamage(typeCtx), resolved.enableOverdamage()));
        // Silent (no hurt animation): default false when unset anywhere.
        boolean generalSilent = Boolean.TRUE.equals(pick(typeCfg.silent(typeCtx), resolved.silent()));

        if (event.invulnerable() && !bypass) {
            // overdamage replacement: in vanilla, when a player experiences an event that deals greater damage than what started their
            // invulnerability period, the greater source of damage "replaces" the weaker one. We do this by "making up" the difference.
            // NOTE: This CAN happen multiple times during one invulnerability window.
            if (!overdamage) return;
            DamageEvent.OverdamageRule rule = typeCfg.overdamageRule(typeCtx);
            if (rule == null) rule = resolved.overdamageRule();
            if (rule == null) rule = DamageEvent.OverdamageRule.vanilla();
            float applied = rule.overdamage(event);
            if (applied > 0) {
                // Overdamage-specific silent override; falls back to the general silent flag when unset.
                Boolean odSilent = pick(typeCfg.overdamageSilent(typeCtx), resolved.overdamageSilent());
                boolean replacementSilent = odSilent != null ? odSilent : generalSilent;
                living.setTag(LAST_DAMAGE, Math.max(event.stored(), amount)); // vanilla-equivalent highwater
                applyDamage(living, type, finalSnap, applied, replacementSilent);
            }
            return;
        }

        if (amount <= 0) return;

        living.setTag(LAST_DAMAGE, amount);
        applyDamage(living, type, finalSnap, amount, generalSilent);
        Integer invulTicks = pick(typeCfg.invulTicks(typeCtx), resolved.invulTicks());
        if (typeCfg.triggersInvul(typeCtx) && invulTicks != null && invulTicks > 0) {
            setDamageInvulnerable(living, invulTicks);
        }
    }

    /** Per-type override when non-null, else the global config value (which may itself be null). */
    private static <T> @Nullable T pick(@Nullable T typeValue, @Nullable T globalValue) {
        return typeValue != null ? typeValue : globalValue;
    }

    private void applyDamage(LivingEntity living, DamageType type, DamageSnapshot snap, float amount, boolean silent) {
        // Non-lethal silent hits update health via the no-hurt path; lethal hits fall through to
        // living.damage() so Minestom handles death (message, drops, respawn).
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
     * Effective invulnerability ticks for a given type: the per-type override when set, else the
     * configured global value, else {@link Constants#DEFAULT_INVUL_TICKS} (when unset or
     * context-dependent). A {@code null} type resolves the global value only.
     */
    public int defaultInvulTicks(@Nullable DamageType type) {
        // Hit-independent default: only constant invul values are meaningful here. A context-dependent
        // (per-hit) value can't be evaluated without a snapshot, so it falls through to the global/default.
        if (type != null) {
            DamageTypeConfig tcfg = config.typeConfig(type.key());
            if (tcfg == null) tcfg = type.defaultConfig();
            Integer v = tcfg.invulTicksConstant();
            if (v != null) return v;
        }
        Integer v = config.invulTicks != null ? config.invulTicks.constantOrNull() : null;
        return v != null ? v : Constants.DEFAULT_INVUL_TICKS;
    }

    /**
     * Standard hit i-frame window used by other systems (attack/knockback) to align their invul
     * windows. Reflects the {@code player_attack} type's effective invul, so a custom melee
     * {@link DamageTypeConfig#invulTicks(DamageContext)} propagates to those windows without touching their resolvers.
     */
    public int defaultInvulTicks() {
        return defaultInvulTicks(registry.get(PlayerAttack.KEY));
    }

    /** Registry of damage types and their handlers. */
    public DamageTypeRegistry registry() { return registry; }

    public static DamageSystem install(MinestomMechanics mm, DamageConfig cfg) {
        var system = new DamageSystem(mm, cfg);
        mm.registerDamage(system);
        HurtSuppression.install(mm);
        return system;
    }

    /** The "last damage" highwater stored for the target's current invul window ({@code 0} if none). */
    public static float lastDamage(LivingEntity le) {
        Float v = le.getTag(LAST_DAMAGE);
        return v != null ? v : 0f;
    }

    public static void setDamageInvulnerable(Entity e, int duration) {
        if (!(e instanceof LivingEntity le) || duration <= 0) return;
        le.setTag(INVUL_DAMAGE, new TickState(TickClock.now(), duration));
    }

    public static boolean isInvulnerableToDamage(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        TickState s = getDamageInvul(le);
        return s != null && s.isActive();
    }

    public static int remainingDamageInvulTicks(LivingEntity le) {
        TickState s = getDamageInvul(le);
        return s != null ? s.remainingTicks() : 0;
    }

    private static @Nullable TickState getDamageInvul(LivingEntity le) {
        return le.getTag(INVUL_DAMAGE);
    }
}
