package io.github.term4.minestommechanics.mechanics.explosion;

import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla's ray is verified in {@code docs/HANDOFF-explosion-block-breaking.md}; these pin the behaviours a preset
 * depends on - resistance actually gating, the 1.8 piston override, and per-SOURCE rules (a BedWars fireball eating
 * wood but not end stone while TNT in the same world eats both).
 */
class BlockBreakingTest extends HeadlessServerTest {

    private static final BlockVec CENTER = new BlockVec(600, 64, 600);

    /** Built per case rather than installed: with no scoped profile, resolve() falls back to this install config. */
    private static ExplosionSystem system(BlockBreaking breaking) {
        return new ExplosionSystem(mm, ExplosionConfig.builder(Vanilla18.explosion())
                .blockBreaking(breaking).build());
    }

    private static Instance world() {
        return flatInstance(MechanicsProfile.builder().build());
    }

    private static void fill(Instance inst, Block block) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                inst.setBlock(CENTER.blockX() + dx, CENTER.blockY(), CENTER.blockZ() + dz, block);
    }

    private static long remaining(Instance inst, Block block) {
        long n = 0;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (inst.getBlock(CENTER.blockX() + dx, CENTER.blockY(), CENTER.blockZ() + dz).compare(block)) n++;
        return n;
    }

    private static void detonate(ExplosionSystem sys, Instance inst, float power) {
        sys.explode(inst, new Pos(CENTER.blockX() + 0.5, CENTER.blockY() + 1.5, CENTER.blockZ() + 0.5), power);
    }

    @Test
    void dirtBreaksAndObsidianSurvivesTheSameBlast() {
        Instance soft = world();
        fill(soft, Block.DIRT);
        detonate(system(BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS).build()), soft, 4.0f);
        assertEquals(0, remaining(soft, Block.DIRT), "a power-4 blast clears dirt (resistance 0.5)");

        Instance hard = world();
        fill(hard, Block.OBSIDIAN);
        detonate(system(BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS).build()), hard, 4.0f);
        assertEquals(25, remaining(hard, Block.OBSIDIAN), "obsidian (1200) outlasts it entirely");
    }

    /** No policy on the config = the blast is entity-only, which is the pre-existing behaviour. */
    @Test
    void withoutAPolicyNothingBreaks() {
        Instance inst = world();
        fill(inst, Block.DIRT);
        detonate(new ExplosionSystem(mm, ExplosionConfig.builder().build()), inst, 4.0f);
        assertEquals(25, remaining(inst, Block.DIRT), "no blockBreaking -> blocks untouched");
    }

    /**
     * The requirement: one world, two exploders, different blocks break. The rule reads {@code ctx.source()}, so a
     * fireball spares end stone while TNT levels it - no second config, no second explosion system.
     */
    @Test
    void breakRulesSeeTheExplosionSource() {
        BlockBreaking perSource = BlockBreaking.builder()
                .model(BlockBreaking.Model.RAY_1_8)
                .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS)
                .breakRule((block, pos, ctx) -> {
                    boolean fireball = ctx.source() != null && ctx.source().getEntityType() == EntityType.FIREBALL;
                    return !fireball || !block.compare(Block.END_STONE);
                })
                .build();

        Instance inst = world();
        ExplosionSystem sys = system(perSource);
        Entity fireball = new Entity(EntityType.FIREBALL);
        fireball.setInstance(inst, new Pos(CENTER.blockX() + 0.5, CENTER.blockY() + 1.5, CENTER.blockZ() + 0.5)).join();
        Entity tnt = new Entity(EntityType.TNT);
        tnt.setInstance(inst, new Pos(CENTER.blockX() + 0.5, CENTER.blockY() + 1.5, CENTER.blockZ() + 0.5)).join();
        try {
            fill(inst, Block.END_STONE);
            sys.explode(inst, fireball.getPosition(), 4.0f, fireball);
            assertEquals(25, remaining(inst, Block.END_STONE), "the fireball spares end stone");

            fill(inst, Block.OAK_PLANKS);
            sys.explode(inst, fireball.getPosition(), 4.0f, fireball);
            assertEquals(0, remaining(inst, Block.OAK_PLANKS), "but still eats wood");

            fill(inst, Block.END_STONE);
            sys.explode(inst, tnt.getPosition(), 4.0f, tnt);
            assertTrue(remaining(inst, Block.END_STONE) < 25, "TNT in the SAME world does break it");
        } finally {
            fireball.remove();
            tnt.remove();
        }
    }

    /** {@code onlyBreaks} is the minigame shape: a whitelist beats resistance in both directions. */
    @Test
    void onlyBreaksIgnoresResistanceEntirely() {
        Instance inst = world();
        fill(inst, Block.OBSIDIAN);
        detonate(system(BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS).onlyBreaks(Set.of(Block.OBSIDIAN)).build()), inst, 4.0f);
        assertEquals(0, remaining(inst, Block.OBSIDIAN), "whitelisting drops obsidian's resistance to zero");

        Instance spared = world();
        fill(spared, Block.DIRT);
        detonate(system(BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS).onlyBreaks(Set.of(Block.OBSIDIAN)).build()), spared, 4.0f);
        assertEquals(25, remaining(spared, Block.DIRT), "and dirt survives because it is not on the list");
    }

    private static long fires(Instance inst) {
        long n = 0;
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = 0; dy <= 3; dy++)
                for (int dz = -3; dz <= 3; dz++)
                    if (inst.getBlock(CENTER.blockX() + dx, CENTER.blockY() + dy, CENTER.blockZ() + dz).compare(Block.FIRE)) n++;
        return n;
    }

    /** Vanilla's incendiary pass: 1-in-3 over the SELECTED cells that are air over something solid. */
    @Test
    void fireLightsOverTheSelectedSet() {
        Instance inst = world();
        fill(inst, Block.STONE);
        ExplosionSystem sys = new ExplosionSystem(mm, ExplosionConfig.builder(Vanilla18.explosion())
                .fire(true)
                .blockBreaking(BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                        .interaction(BlockBreaking.Interaction.KEEP).build())
                .build());
        detonate(sys, inst, 4.0f);
        assertEquals(25, remaining(inst, Block.STONE), "KEEP selects without destroying");
        assertTrue(fires(inst) > 0, "and fire still lands on the selected air cells - vanilla lights fire under KEEP");
    }

    /** {@code fire} is a FieldValue, so incendiary-ness follows the source exactly like the ghast/TNT split. */
    @Test
    void incendiaryFollowsTheSource() {
        Instance inst = world();
        ExplosionSystem sys = new ExplosionSystem(mm, ExplosionConfig.builder(Vanilla18.explosion())
                .fire(FieldValue.of(ctx -> ctx.source() != null && ctx.source().getEntityType() == EntityType.FIREBALL))
                .blockBreaking(BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                        .interaction(BlockBreaking.Interaction.KEEP).build())
                .build());
        Entity fireball = new Entity(EntityType.FIREBALL);
        Entity tnt = new Entity(EntityType.TNT);
        Pos at = new Pos(CENTER.blockX() + 0.5, CENTER.blockY() + 1.5, CENTER.blockZ() + 0.5);
        fireball.setInstance(inst, at).join();
        tnt.setInstance(inst, at).join();
        try {
            fill(inst, Block.STONE);
            sys.explode(inst, at, 4.0f, tnt);
            assertEquals(0, fires(inst), "TNT is not incendiary");

            sys.explode(inst, at, 4.0f, fireball);
            assertTrue(fires(inst) > 0, "the fireball is - same config, same world");
        } finally {
            fireball.remove();
            tnt.remove();
        }
    }

    /**
     * Every 1.8-derived preset inherits vanilla18's block breaking + incendiary. Guards the builder COPY-CTOR:
     * a hand-written object member left out of it silently resets to default, which is how this shipped broken once.
     */
    @Test
    void legacyPresetsInheritVanilla18BlockBreaking() {
        var ctx = ExplosionConfigResolver.ExplosionContext.of(instance, CENTER, null, services);
        assertNotNull(Vanilla18.explosion().blockBreaking, "vanilla18 itself declares one");
        record Preset(String name, ExplosionConfig config) {}
        for (Preset preset : java.util.List.of(
                new Preset("hypixel", io.github.term4.minestommechanics.presets.hypixel.Explosion.config()),
                new Preset("mmc18", io.github.term4.minestommechanics.presets.mmc18.Explosion.config()),
                new Preset("mmc18 fireball-fight", io.github.term4.minestommechanics.presets.mmc18.Explosion.fireballFight()))) {
            BlockBreaking b = preset.config().blockBreaking;
            assertNotNull(b, preset.name() + " inherits vanilla18's block breaking");
            assertEquals(BlockBreaking.Model.RAY_1_8, b.model(), preset.name() + " keeps the 1.8 ray");
            assertEquals(BlockBreaking.Interaction.DESTROY_WITH_DECAY, b.interaction(), preset.name() + " keeps vanilla drops");
            assertEquals(0.5, b.resistance(Block.PISTON, ctx), 1e-9, preset.name() + " keeps the 1.8 piston table");
        }
        // fireScope must survive builder(base) WITHOUT being re-set - the copy-ctor omission guard (bit twice before)
        ExplosionConfig base = ExplosionConfig.builder().fireScope(ExplosionConfig.FireScope.BROKEN).build();
        ExplosionConfig derived = ExplosionConfig.builder(base).power(5.0).build();
        assertEquals(ExplosionConfig.FireScope.BROKEN, derived.fireScope, "builder(base) carries fireScope");
        assertEquals(ExplosionConfig.FireScope.BROKEN, base.toBuilder().build().fireScope, "toBuilder carries fireScope");
    }

    /**
     * The regression: NOTHING set {@code fire} until 2026-07-21, so no explosion was ever incendiary. Asserts the
     * preset makes it SOURCE-dependent rather than a constant - the live fireball path is covered by
     * {@link #incendiaryFollowsTheSource} (building a real FireballEntity needs a snapshot + type config).
     */
    @Test
    void presetsMakeIncendiarySourceDependent() {
        // mmc18 is deliberately NOT here - its fireballs never ignite (a CONSTANT false, not source-dependent)
        for (ExplosionConfig cfg : java.util.List.of(Vanilla18.explosion(),
                io.github.term4.minestommechanics.presets.vanilla.Explosion.config(),
                io.github.term4.minestommechanics.presets.hypixel.Explosion.config())) {
            assertNotNull(cfg.fire, "the preset sets fire at all");
            assertNull(cfg.fire.constantOrNull(), "and makes it depend on the source, not a hardcoded flag");
            var tntCtx = ExplosionConfigResolver.ExplosionContext.of(instance, CENTER, null, services);
            assertFalse(cfg.fire.resolve(tntCtx), "a non-fireball source is never incendiary");
        }
    }

    /**
     * Hypixel fireballs light fire ONLY in cells a block was broken in - never on intact ground. A blast over a flat
     * unbreakable plate breaks nothing, so it lights nothing (the vanilla SELECTED scope WOULD light the air above it).
     */
    @Test
    void brokenScopeLightsNothingOverAnUnbreakablePlate() {
        // one plate of obsidian (never breaks), fire ON, source above it
        var breaking = BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS).build();

        Instance vanilla = world();
        fill(vanilla, Block.OBSIDIAN);
        detonateFire(vanilla, breaking, ExplosionConfig.FireScope.SELECTED);
        assertTrue(fires(vanilla) > 0, "vanilla scope lights the air the blast reached above the plate");

        Instance hypixel = world();
        fill(hypixel, Block.OBSIDIAN);
        detonateFire(hypixel, breaking, ExplosionConfig.FireScope.BROKEN);
        assertEquals(0, fires(hypixel), "BROKEN scope: nothing broke, so nothing burns");
    }

    /** BROKEN scope lights only vacated cells: a breakable floor DOES catch, because those cells were broken. */
    @Test
    void brokenScopeLightsWhereBlocksBroke() {
        Instance inst = world();
        fill(inst, Block.DIRT); // breaks under a power-4 blast
        detonateFire(inst, BlockBreaking.builder().model(BlockBreaking.Model.RAY_1_8)
                .interaction(BlockBreaking.Interaction.DESTROY_NO_DROPS).build(), ExplosionConfig.FireScope.BROKEN);
        assertTrue(fires(inst) > 0, "the broken dirt cells are lit");
    }

    private void detonateFire(Instance inst, BlockBreaking breaking, ExplosionConfig.FireScope scope) {
        ExplosionSystem sys = new ExplosionSystem(mm, ExplosionConfig.builder(Vanilla18.explosion())
                .fire(true).fireScope(scope).blockBreaking(breaking).build());
        // above the plate so rays clear the surface and the incendiary pass has candidates
        sys.explode(inst, new Pos(CENTER.blockX() + 0.5, CENTER.blockY() + 1.5, CENTER.blockZ() + 0.5), 4.0f);
    }

    /** MineMen fireballs never ignite - both the normal and Fireball-Fight explosion configs must resolve fire=false. */
    @Test
    void mmc18FireballsAreNeverIncendiary() {
        // a CONSTANT false overriding vanilla18's fireball predicate - so no source (fireball or not) ever ignites
        assertEquals(Boolean.FALSE, io.github.term4.minestommechanics.presets.mmc18.Explosion.config().fire.constantOrNull(),
                "mmc18 normal explosion never ignites");
        assertEquals(Boolean.FALSE, io.github.term4.minestommechanics.presets.mmc18.Explosion.fireballFight().fire.constantOrNull(),
                "mmc18 Fireball-Fight never ignites either");
        // guard against a global regression: vanilla18 keeps its SOURCE-dependent predicate (not overridden to a constant)
        assertNull(Vanilla18.explosion().fire.constantOrNull(), "vanilla18 keeps the fireball incendiary predicate");
    }

    /** The 1.8 piston override - the one blast-resistance value that genuinely changed. */
    @Test
    void legacyResistanceOverridesPistons() {
        var ctx = ExplosionConfigResolver.ExplosionContext.of(instance, CENTER, null, services);
        assertEquals(0.5, BlockBreaking.LEGACY_RESISTANCE.of(Block.PISTON, ctx), 1e-9);
        assertEquals(1.5, BlockBreaking.VANILLA_RESISTANCE.of(Block.PISTON, ctx), 1e-9);
        assertEquals(0.0, BlockBreaking.LEGACY_RESISTANCE.of(Block.MOVING_PISTON, ctx), 1e-9,
                "1.8 c(-1.0F) never raised durability: unbreakable by tools, free to explosions");
        // everything else agrees, so the override table stays tiny
        assertEquals(BlockBreaking.VANILLA_RESISTANCE.of(Block.STONE, ctx),
                BlockBreaking.LEGACY_RESISTANCE.of(Block.STONE, ctx), 1e-9);
        assertFalse(Double.isNaN(BlockBreaking.VANILLA_RESISTANCE.of(Block.OBSIDIAN, ctx)));
    }
}
