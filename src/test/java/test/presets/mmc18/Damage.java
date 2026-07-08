package test.presets.mmc18;

import io.github.term4.minestommechanics.api.event.DamageEvent;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * mmc18 damage: the vanilla 1.8 damage ({@link io.github.term4.minestommechanics.mechanics.vanilla18.Damage}) with
 * silent overdamage, no hurt-tick knockback, and the same-weapon overdamage block below.
 */
public final class Damage {

    private Damage() {}

    /** mmc18 DamageConfig: vanilla 1.8 with overdamage applied silently and no generic-hit knockback broadcast. */
    public static DamageConfig config() {
        return DamageConfig.builder(Vanilla18.damage())
                .overdamageSilent(true)
                // mmc18 deals no knockback on generic damage ticks: the hurt broadcast is off entirely
                // (the only way to switch off an inherited hurtKnockback - merge semantics can't null it).
                .syncHurtVelocity(false)
                .addCustomComponent(Damage::blockSameItemOverdamage)
                .build();
    }

    /**
     * mmc18 overdamage rule: skip replacement when the incoming melee hit uses the same weapon (material) as the hit
     * that opened the invulnerability window.
     */
    @Nullable
    private static Float blockSameItemOverdamage(DamageContext ctx, DamageEvent event, float amount, boolean overdamage) {
        if (!overdamage || amount <= 0) return null;
        if (!MeleeDamage.KEY.equals(event.type().key())) return null;
        if (!(event.target() instanceof LivingEntity le)) return null;
        if (sameItem(event.item(), DamageSystem.openingHitItem(le))) return 0f;
        return null;
    }

    private static boolean sameItem(@Nullable ItemStack a, @Nullable ItemStack b) {
        boolean aFist = a == null || a.isAir();
        boolean bFist = b == null || b.isAir();
        if (aFist && bFist) return true;
        if (aFist != bFist) return false;
        return a.material().equals(b.material());
    }
}
