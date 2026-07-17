package io.github.term4.minestommechanics.mechanics.consumable.catalog;

import io.github.term4.minestommechanics.mechanics.attribute.catalog.VanillaPotions;
import io.github.term4.minestommechanics.mechanics.consumable.Consumable;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableBehavior;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import io.github.term4.minestommechanics.mechanics.damage.types.magic.HealOrHarm;
import io.github.term4.minestommechanics.mechanics.hunger.HungerSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import net.minestom.server.potion.CustomPotionEffect;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla consumable <em>type identities</em> (golden apples, drinks, the effect foods) + the building blocks presets
 * use for their version-specific behavior. Version-split effects (golden apples, pufferfish) live in the preset's
 * per-type behavior; version-invariant ones (milk, the other effect foods) on the type. Effects apply via
 * {@code addEffect} (the attribute potion lifecycle); food/saturation routes through {@link HungerSystem}. Plain foods
 * need no entry at all - the {@code ComponentFood} floor eats them off the item registry.
 */
public final class VanillaConsumables {

    private VanillaConsumables() {}

    /** Golden apple type ({@code minecraft:golden_apple}); the preset supplies its effects/food. */
    public static final Consumable GOLDEN_APPLE = Consumable
            .builder(Key.key("minecraft:golden_apple"), Material.GOLDEN_APPLE).build();

    /** Enchanted ("notch") golden apple type ({@code minecraft:enchanted_golden_apple}); the preset supplies its effects/food. */
    public static final Consumable ENCHANTED_GOLDEN_APPLE = Consumable
            .builder(Key.key("minecraft:enchanted_golden_apple"), Material.ENCHANTED_GOLDEN_APPLE).build();

    /** Milk bucket ({@code minecraft:milk_bucket}): drinking clears all effects (version-invariant, so the behavior is on the type). */
    public static final Consumable MILK_BUCKET = Consumable
            .builder(Key.key("minecraft:milk_bucket"), Material.MILK_BUCKET)
            .behavior(clearEffects())
            .build();

    /** Drinkable potion ({@code minecraft:potion}): applies the item's {@code potion_contents} effects (the same payload tipped arrows resolve), leaving a glass bottle. */
    public static final Consumable POTION = Consumable
            .builder(Key.key("minecraft:potion"), Material.POTION)
            .behavior(drinkPotion())
            .remainder(Material.GLASS_BOTTLE)
            .build();

    // The effect foods (the component floor covers plain foods). Values from the pristine 1.8 ItemFood table; all but
    // the pufferfish are version-identical, so their behavior lives on the type - the presets gate canConsume.
    /** Raw chicken: 30% Hunger I (30s). */
    public static final Consumable RAW_CHICKEN = Consumable
            .builder(Key.key("minecraft:chicken"), Material.CHICKEN)
            .behavior(effectFood(2, 1.2f, eff(PotionEffect.HUNGER, 1, 600, 0.3f)))
            .build();

    /** Rotten flesh: 80% Hunger I (30s). */
    public static final Consumable ROTTEN_FLESH = Consumable
            .builder(Key.key("minecraft:rotten_flesh"), Material.ROTTEN_FLESH)
            .behavior(effectFood(4, 0.8f, eff(PotionEffect.HUNGER, 1, 600, 0.8f)))
            .build();

    /** Spider eye: Poison I (5s), always. */
    public static final Consumable SPIDER_EYE = Consumable
            .builder(Key.key("minecraft:spider_eye"), Material.SPIDER_EYE)
            .behavior(effectFood(2, 3.2f, eff(PotionEffect.POISON, 1, 100)))
            .build();

    /** Poisonous potato: 60% Poison I (5s). */
    public static final Consumable POISONOUS_POTATO = Consumable
            .builder(Key.key("minecraft:poisonous_potato"), Material.POISONOUS_POTATO)
            .behavior(effectFood(2, 1.2f, eff(PotionEffect.POISON, 1, 100, 0.6f)))
            .build();

    /** Pufferfish type; the effects are VERSION-SPLIT (1.8: Poison IV + Nausea II; modern: Poison II + Nausea I), so the preset supplies the behavior. */
    public static final Consumable PUFFERFISH = Consumable
            .builder(Key.key("minecraft:pufferfish"), Material.PUFFERFISH)
            .build();

    /** The vanilla consumable types to register on the system ({@code ConsumableSystem.install(mm, cfg, VanillaConsumables.types())}). */
    public static Consumable[] types() {
        return new Consumable[]{GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE, MILK_BUCKET, POTION,
                RAW_CHICKEN, ROTTEN_FLESH, SPIDER_EYE, POISONOUS_POTATO, PUFFERFISH};
    }

    /**
     * 1.8 {@code EntityPlayer.canEat} for a food item: {@code (alwaysEdible || hungry) && !invulnerable}. Creative /
     * spectator ({@code disableDamage}) can NEVER start eating in 1.8 - the {@code canConsume} gate for the vanilla18
     * food consumables. Drinks (potion / milk) don't call this ({@code setItemInUse} is unconditional there).
     */
    public static boolean legacyCanEat(ConsumableContext ctx, boolean alwaysEdible) {
        Player u = ctx.user();
        return (alwaysEdible || u.getFood() < 20) && !invulnerable(u);
    }

    /**
     * 26.1 {@code Player.canEat}: {@code invulnerable || alwaysEdible || hungry}. Creative / spectator ALWAYS eat in
     * 26 (they just don't lose the item) - the difference from {@link #legacyCanEat} that the preset split encodes.
     */
    public static boolean modernCanEat(ConsumableContext ctx, boolean alwaysEdible) {
        Player u = ctx.user();
        return invulnerable(u) || alwaysEdible || u.getFood() < 20;
    }

    /** Vanilla {@code abilities.invulnerable} / {@code disableDamage}: creative and spectator. */
    private static boolean invulnerable(Player u) {
        return u.getGameMode() == GameMode.CREATIVE || u.getGameMode() == GameMode.SPECTATOR;
    }

    /** One potion effect for {@link #effectFood}: {@code level} is 1-based (level I = amplifier 0), {@code ticks} the duration. */
    public static Effect eff(PotionEffect id, int level, int ticks) { return new Effect(id, (byte) (level - 1), ticks, 1f); }

    /** {@link #eff} with a vanilla apply {@code chance} (raw chicken's 0.3 etc.). */
    public static Effect eff(PotionEffect id, int level, int ticks, float chance) { return new Effect(id, (byte) (level - 1), ticks, chance); }

    /** A resolved potion effect a consumable applies on finish, at {@code chance} (1 = always). */
    public record Effect(PotionEffect id, byte amplifier, int ticks, float chance) {}

    /**
     * A {@link ConsumableBehavior} that, on finish, restores {@code nutrition} food + {@code saturation} (through the
     * {@link HungerSystem}) and applies each {@link Effect} (particles + icon). The building block for food/golden-apple behaviors.
     */
    public static ConsumableBehavior effectFood(int nutrition, float saturation, Effect... effects) {
        return new ConsumableBehavior() {
            @Override public void onFinish(ConsumableContext ctx) {
                Player u = ctx.user();
                byte flags = ctx.particles().potionFlags();
                for (Effect e : effects) {
                    if (e.chance() < 1f && ThreadLocalRandom.current().nextFloat() >= e.chance()) continue;
                    VanillaPotions.addEffect(u, new Potion(e.id(), e.amplifier(), e.ticks(), flags));
                }
                HungerSystem hunger = ctx.services() != null ? ctx.services().hunger() : null;
                if (hunger != null) hunger.restore(u, nutrition, saturation);
            }
        };
    }

    /** A {@link ConsumableBehavior} that clears all of the user's active effects on finish (milk). */
    public static ConsumableBehavior clearEffects() {
        return new ConsumableBehavior() {
            @Override public void onFinish(ConsumableContext ctx) { ctx.user().clearEffects(); }
        };
    }

    /**
     * A {@link ConsumableBehavior} that, on finish, applies the item's {@code potion_contents} - the base potion (via
     * {@link VanillaPotions}) + any custom effects - at full duration. Instant effects (healing/harming) apply through
     * {@link HealOrHarm} at intensity {@code 1.0} (vanilla drink); a water bottle is a no-op.
     */
    public static ConsumableBehavior drinkPotion() {
        return new ConsumableBehavior() {
            @Override public void onFinish(ConsumableContext ctx) {
                Player u = ctx.user();
                byte flags = ctx.particles().potionFlags();
                for (CustomPotionEffect e : VanillaPotions.payload(ctx.item())) {
                    if (HealOrHarm.apply(ctx.services(), u, u, null, e, 1.0)) continue;
                    VanillaPotions.addEffect(u, new Potion(e.id(), e.amplifier(), e.duration(), flags));
                }
            }
        };
    }
}
