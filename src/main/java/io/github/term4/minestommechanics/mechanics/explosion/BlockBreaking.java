package io.github.term4.minestommechanics.mechanics.explosion;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfigResolver.ExplosionContext;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an explosion does to blocks. Absent from an {@link ExplosionConfig} = it breaks none.
 *
 * <p>{@link Resistance} and {@link BreakRule} both receive the {@link ExplosionContext}, whose
 * {@code source()} is the exploding entity - so one config can break different blocks per source (a BedWars
 * fireball eating wood but not end stone, while TNT in the same world eats both).
 */
public final class BlockBreaking {

    /** How the destroyed set is chosen. Both rays are vanilla's 16³ shell; they differ only in what resists. */
    public enum Model {
        /** 1.8: resistance is the block's alone, and a ray never leaves the world. */
        RAY_1_8,
        /** 26.1: {@code max(block, fluid)} resistance, and a ray stops at the world border. */
        RAY_MODERN,
        /** Every breakable block within {@code power}; no ray, no shadowing. Cheap enough to spam. */
        SPHERE
    }

    /**
     * What happens to a selected block - vanilla's {@code Explosion.BlockInteraction} plus the no-drops case it
     * expresses per-block. Selection runs either way, so {@link #KEEP} is how you get FIRE without destruction.
     */
    public enum Interaction {
        /** Selected but left standing. */
        KEEP,
        /** Destroyed, drops nothing. */
        DESTROY_NO_DROPS,
        /** Destroyed; each item survives with probability {@code 1/power} (vanilla TNT). */
        DESTROY_WITH_DECAY,
        /** Destroyed with full drops. */
        DESTROY_WITH_DROPS;

        boolean destroys() { return this != KEEP; }
    }

    /** Blast resistance of {@code block}, in vanilla units (stone 6, obsidian 1200). {@code Double.POSITIVE_INFINITY} = never breaks. */
    @FunctionalInterface
    public interface Resistance {
        double of(@NotNull Block block, @NotNull ExplosionContext ctx);
    }

    /**
     * Final say once the ray has already reached {@code pos} with power to spare. It does NOT affect propagation -
     * a block spared here still shadows whatever is behind it. Use {@link Builder#onlyBreaks}/{@link Builder#neverBreaks}
     * to change what a blast can punch THROUGH.
     */
    @FunctionalInterface
    public interface BreakRule {
        boolean canBreak(@NotNull Block block, @NotNull Point pos, @NotNull ExplosionContext ctx);
    }

    /** Registry blast resistance - the modern value, correct for every block that still exists. */
    public static final Resistance VANILLA_RESISTANCE = (block, ctx) -> block.registry().explosionResistance();

    // the only blocks whose blast resistance actually changed since 1.8 (148 of 155 match - see
    // docs/HANDOFF-explosion-block-breaking.md). moving_piston is not a typo: 1.8's c(-1.0F) never raises
    // durability, leaving it unbreakable by tools yet free to explosions
    private static final Map<String, Double> LEGACY_OVERRIDES = Map.of(
            "minecraft:piston", 0.5, "minecraft:sticky_piston", 0.5,
            "minecraft:piston_head", 0.5, "minecraft:moving_piston", 0.0);

    /** 1.8 blast resistance: {@link #VANILLA_RESISTANCE} plus the piston overrides. */
    public static final Resistance LEGACY_RESISTANCE = (block, ctx) -> {
        Double override = LEGACY_OVERRIDES.get(block.key().asString());
        return override != null ? override : block.registry().explosionResistance();
    };

    private static final BreakRule ANY = (block, pos, ctx) -> true;

    private final Model model;
    private final Interaction interaction;
    private final Resistance resistance;
    private final BreakRule breakRule;

    private BlockBreaking(Builder b) {
        this.model = b.model;
        this.interaction = b.interaction;
        this.resistance = b.resistance;
        this.breakRule = b.breakRule;
    }

    public @NotNull Model model() { return model; }
    public @NotNull Interaction interaction() { return interaction; }

    double resistance(@NotNull Block block, @NotNull ExplosionContext ctx) { return resistance.of(block, ctx); }

    boolean canBreak(@NotNull Block block, @NotNull Point pos, @NotNull ExplosionContext ctx) {
        return breakRule.canBreak(block, pos, ctx);
    }

    /** The block's own item, 1x; blocks with no item form drop nothing. Loot beyond this is the app's {@link BreakRule} job. */
    static @NotNull List<ItemStack> dropsOf(@NotNull Block block) {
        Material material = Material.fromKey(block.key());
        return material == null ? List.of() : List.of(ItemStack.of(material));
    }

    public static @NotNull Builder builder() { return new Builder(); }

    public static final class Builder {
        private Model model = Model.RAY_MODERN;
        private Interaction interaction = Interaction.DESTROY_WITH_DECAY;
        private Resistance resistance = VANILLA_RESISTANCE;
        private BreakRule breakRule = ANY;

        private Builder() {}

        public Builder model(@NotNull Model v) { this.model = v; return this; }
        public Builder interaction(@NotNull Interaction v) { this.interaction = v; return this; }
        public Builder resistance(@NotNull Resistance v) { this.resistance = v; return this; }

        /** Replaces the rule; compose with {@code &&} yourself when you want several. */
        public Builder breakRule(@NotNull BreakRule v) { this.breakRule = v; return this; }

        /**
         * Only these break, whatever their own resistance - the minigame shape (BedWars wool/wood). Matched by type,
         * any state. Implemented as RESISTANCE, not a veto: listed blocks offer none (so a blast tunnels through a
         * whitelisted wall) and everything else is infinite (so it shields, exactly like obsidian in BedWars).
         */
        public Builder onlyBreaks(@NotNull Set<Block> blocks) {
            Set<String> keys = keys(blocks);
            return resistance((block, ctx) ->
                    keys.contains(block.key().asString()) ? 0.0 : Double.POSITIVE_INFINITY);
        }

        /** These never break AND shield what is behind them (beds, end stone, generators); everything else keeps its own resistance. */
        public Builder neverBreaks(@NotNull Set<Block> blocks) {
            Set<String> keys = keys(blocks);
            Resistance base = this.resistance;
            return resistance((block, ctx) -> keys.contains(block.key().asString())
                    ? Double.POSITIVE_INFINITY : base.of(block, ctx));
        }

        private static Set<String> keys(Set<Block> blocks) {
            return blocks.stream().map(b -> b.key().asString()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        public @NotNull BlockBreaking build() { return new BlockBreaking(this); }
    }
}
