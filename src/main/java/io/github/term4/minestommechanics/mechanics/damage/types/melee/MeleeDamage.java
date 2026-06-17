package io.github.term4.minestommechanics.mechanics.damage.types.melee;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.damage.types.VanillaTypes;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The melee ({@code minecraft:player_attack}) damage type. Crit is determined in the attack layer; this type owns the
 * crit multiplier and builds the {@link DamageSnapshot} the caller applies. Tunables come from {@link MeleeDamageConfig}.
 */
public final class MeleeDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:player_attack");
    /** Builder defaults: 1.8 weapon-table base amount, 1.5x crit. */
    private static final MeleeDamageConfig DEFAULT = MeleeDamageConfig.builder().build();
    public static final MeleeDamage INSTANCE = new MeleeDamage();

    private MeleeDamage() {
        super(KEY, "Melee", VanillaTypes.PLAYER_ATTACK, DEFAULT);
    }

    /**
     * Builds a melee snapshot for a hit with {@code item} (vanilla 1.8 computes the amount from it, see
     * {@link LegacyWeaponDamage}), applying the crit multiplier when {@code critical}. Resolves the active
     * {@link MeleeDamageConfig} and bakes the final amount.
     */
    public DamageSnapshot snapshot(Entity attacker, Entity target, boolean critical, @Nullable ItemStack item,
                                   Services services) {
        DamageSnapshot prelim = DamageSnapshot.of(target, this).withSource(attacker).withItem(item);
        // victim-scoped chain so per-profile melee tables resolve
        DamageSystem dmg = services != null ? services.damage() : null;
        DamageContext ctx = dmg != null ? dmg.contextFor(prelim) : DamageContext.of(prelim, services);
        MeleeDamageConfig cfg = ctx.typeConfig() instanceof MeleeDamageConfig p ? p : DEFAULT;
        Double base = cfg.baseAmount(ctx);
        float amount = base != null ? base.floatValue() : 0f;
        if (critical) {
            Double crit = cfg.critMultiplier(ctx);
            if (crit != null) amount *= crit.floatValue();
        }
        return prelim.withAmount(amount);
    }
}
