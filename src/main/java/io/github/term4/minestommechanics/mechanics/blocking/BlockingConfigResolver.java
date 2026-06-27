package io.github.term4.minestommechanics.mechanics.blocking;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Resolves a {@link BlockingConfig} into plain values against a {@link BlockingContext}. Mirrors the other resolvers
 * (per-material entry over {@link BlockingConfig#defaults()}, then resolve each knob). The {@link BlockingBehavior} reads
 * the resolved knobs + the hit (carried as a {@link DamageContext}) to decide the block.
 */
public final class BlockingConfigResolver {

    private BlockingConfigResolver() {}

    /**
     * The context the per-item {@code FieldValue}s resolve against, and that the {@link BlockingBehavior} receives: the
     * blocking {@code defender} + the {@code item}/{@code hand} they hold, and the incoming hit as a {@link DamageContext}
     * (so a behavior reads the damage type, attacker, {@code bypassArmor}, etc. through the damage domain's own resolution).
     */
    public record BlockingContext(Player defender, ItemStack item, PlayerHand hand, DamageContext damage,
                                  @Nullable Services services) {

        /** The incoming damage type. */
        public DamageType type() { return damage.snap().type(); }

        /** The attacking entity, or {@code null}. */
        public @Nullable Entity attacker() { return damage.snap().source(); }

        /** Whether the incoming damage bypasses armor (vanilla {@code ignoresArmor}) - the 1.8 sword's blockable test. */
        public boolean bypassesArmor() { return damage.typeConfig().bypassArmor(damage); }

        /** Ticks the defender has been using (holding) the blocking item - the shield block-delay reads this. */
        public int useTicks() { return (int) defender.getCurrentItemUseTime(); }

        /**
         * Horizontal angle (degrees) between the defender's facing and the direction to the attacker; {@code 0} = dead
         * ahead, {@code 180} = directly behind. {@code 0} when there's no positioned attacker (treated as blockable).
         */
        public double attackerAngleDegrees() {
            Entity atk = attacker();
            if (atk == null) return 0.0;
            Pos pos = defender.getPosition();
            Vec look = pos.direction();
            double dx = atk.getPosition().x() - pos.x();
            double dz = atk.getPosition().z() - pos.z();
            double toLen = Math.sqrt(dx * dx + dz * dz);
            double lookLen = Math.sqrt(look.x() * look.x() + look.z() * look.z());
            if (toLen < 1.0e-6 || lookLen < 1.0e-6) return 0.0;
            double dot = (look.x() * dx + look.z() * dz) / (toLen * lookLen);
            return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        }

        /**
         * Effective per-item config: the active config's entry for this item's material layered over its generic
         * {@link BlockingConfig#defaults()}, plus any {@code subConfig} overlay. Only called for blockable materials.
         */
        public BlockingTypeConfig typeConfig(@Nullable BlockingConfig cfg) {
            BlockingTypeConfig entry = cfg != null ? cfg.typeConfig(item.material()) : null;
            BlockingTypeConfig generic = cfg != null ? cfg.defaults() : null;
            BlockingTypeConfig base = generic != null ? generic : BlockingTypeConfig.builder().build();
            if (entry != null) base = entry.fromBase(base);
            if (base.subConfig != null) {
                BlockingTypeConfig overlay = base.subConfig.apply(this);
                if (overlay != null) base = overlay.fromBase(base);
            }
            return base;
        }
    }

    /** Resolves the effective blocking values for {@code ctx} under {@code cfg}. */
    public static ResolvedBlocking resolve(@Nullable BlockingConfig cfg, BlockingContext ctx) {
        BlockingTypeConfig tc = ctx.typeConfig(cfg);
        return new ResolvedBlocking(
                or(resolve(tc.enabled, ctx), Boolean.TRUE),
                or(resolve(tc.behavior, ctx), BlockingBehavior.SWORD),
                or(resolve(tc.reductionBase, ctx), 0.0),
                or(resolve(tc.reductionFactor, ctx), 1.0),
                or(resolve(tc.blockDelayTicks, ctx), 0),
                resolve(tc.blockingAngle, ctx),
                or(resolve(tc.bypassedTypes, ctx), Set.of()));
    }

    private static <T> T or(@Nullable T v, T def) { return v != null ? v : def; }

    private static <T> @Nullable T resolve(@Nullable FieldValue<BlockingContext, T> fv, BlockingContext ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }

    /** Resolved per-item values: whether it blocks, the model, the damage reduction ({@code base}+{@code factor}), and the shield gates. */
    public record ResolvedBlocking(boolean enabled, BlockingBehavior behavior, double reductionBase, double reductionFactor,
                                   int blockDelayTicks, @Nullable Double blockingAngle, Set<Key> bypassedTypes) {}
}
