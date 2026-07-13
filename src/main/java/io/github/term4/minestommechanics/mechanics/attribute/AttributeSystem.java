package io.github.term4.minestommechanics.mechanics.attribute;

import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.ItemSource;
import io.github.term4.minestommechanics.mechanics.attribute.source.ArmorSource;
import io.github.term4.minestommechanics.mechanics.attribute.source.HeldSource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Behavior;
import io.github.term4.minestommechanics.mechanics.attribute.source.SourceRegistry;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfigResolver.AttributeContext;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfigResolver.ResolvedAttributeConfig;
import io.github.term4.minestommechanics.mechanics.attribute.combat.HitContext;
import io.github.term4.minestommechanics.mechanics.attribute.combat.OnHit;
import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.attribute.defense.MitigationRequest;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityPotionAddEvent;
import net.minestom.server.event.entity.EntityPotionRemoveEvent;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.item.EntityEquipEvent;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.trait.EntityEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.tag.Tag;
import io.github.term4.minestommechanics.item.Enchants;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Attribute/enchant/potion system: a {@link SourceRegistry} plus a scan of active effects and the in-context item's
 * enchants into active sources, read via {@link #context}. {@code install}/{@code node} shape like the other systems.
 */
public final class AttributeSystem implements MechanicsModule {

    /** This system's identity for per-module TPS scaling (its {@code referenceTps} feel-baseline). */
    public static final Key KEY = Key.key("mm:attribute");

    static final Key RESISTANCE_KEY = Key.key("minecraft:resistance");
    /** The armor attribute's key - the armor stage's identity for targeted {@link Bypass#attribute} bypass. */
    static final Key ARMOR_ATTRIBUTE_KEY = Key.key("minecraft:armor");

    private final MinestomMechanics mm;
    private final SourceRegistry registry = new SourceRegistry();
    private final AttributeConfig config;
    private final EventNode<@NotNull EntityEvent> node;
    /** Re-entry guard for the TPS duration rescale (the re-added potion fires the add event again). */
    private final ThreadLocal<Boolean> rescaling = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** Per-entity {@code armorEnchantKey -> worn level}, driving {@link ArmorSource} behavior transitions + ticking. */
    private static final Tag<Map<Key, Integer>> WORN_ARMOR = Tag.Transient("mm:worn-armor-sources");
    /** Last-reconciled {@code [helmet, chest, legs, boots, mainhand]} per player, so the per-tick reconcile is a no-op unless the gear changed. */
    private static final Tag<ItemStack[]> EQUIP_SNAPSHOT = Tag.Transient("mm:equip-snapshot");

    public AttributeSystem(MinestomMechanics mm, @Nullable AttributeConfig config) {
        this.mm = mm;
        this.config = config != null ? config : AttributeConfig.builder().build();
        this.node = EventNode.type("mm:attributes", EventFilter.ENTITY);
        for (Source source : this.config.sources()) registry.register(source);
        // Potion: a source's vanilla modifiers ride the holder's Minestom AttributeInstance (client-facing) and its
        // Behavior fires on/off; Minestom only stores the effect.
        node.addListener(EntityPotionAddEvent.class, this::onPotionAdd);
        node.addListener(EntityPotionRemoveEvent.class, e -> onPotion(e.getEntity(), e.getPotion(), false));
        node.addListener(EntityTickEvent.class, this::onEntityTick);
        node.addListener(EntityEquipEvent.class, this::onEntityEquip);
        node.addListener(PlayerChangeHeldSlotEvent.class, this::onHeldSlotChange);
        // Death handling (clearing effects, etc.) lives in the DamageSystem; here we only react to the potion-remove events
        // that clearing fires (which drop the pushed modifiers).
    }

    /** Per-tick lifecycle: effects, armor behavior, and (players only) the equipment reconcile. */
    private void onEntityTick(EntityTickEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        tickEffects(le);
        tickArmor(le);
        if (le instanceof Player p) tickEquipment(p);
    }

    /**
     * Mob equipment: push worn/held modifiers immediately off the equip event (no client to race; mobs aren't tick-reconciled).
     * Players are excluded - a synchronous push races the use-item client prediction, so they ride {@link #tickEquipment} instead.
     */
    private void onEntityEquip(EntityEquipEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || le instanceof Player) return;
        if (isArmorSlot(e.getSlot())) syncArmor(le, e.getSlot(), e.getEquippedItem());
        else if (e.getSlot() == EquipmentSlot.MAIN_HAND) syncHeld(le, e.getEquippedItem());
    }

    /**
     * A hotbar-slot switch changes the held item without an equip event. With attribute swapping disabled (default) the held
     * push is forced immediately off the new slot, closing the swap window; when permitted it's left to {@link #tickEquipment}
     * so the held attributes lag a tick (see {@link AttributeConfig#attributeSwapping}).
     */
    private void onHeldSlotChange(PlayerChangeHeldSlotEvent e) {
        if (!configFor(e.getPlayer()).attributeSwapping()) syncHeld(e.getPlayer(), e.getItemInNewSlot());
    }

    /** Periodic Behavior dispatch: fires {@code onTick} on each active source whose interval divides the effect's remaining duration (vanilla's {@code duration % interval} cadence). */
    private void tickEffects(LivingEntity entity) {
        List<TimedPotion> active = entity.getActiveEffects();
        if (active.isEmpty()) return;
        long now = entity.getAliveTicks();
        for (TimedPotion tp : active) {
            EntitySource source = registry.entitySource(tp.potion().effect().key());
            if (source == null) continue;
            int level = tp.potion().amplifier() + 1;
            int interval = source.behavior().tickInterval(level);
            if (interval <= 0) continue;
            int scaledInterval = Math.max(1, TickScaler.duration(interval, KEY)); // cadence in server ticks
            int remaining = tp.potion().duration() - (int) (now - tp.startingTicks());
            if (remaining > 0 && remaining % scaledInterval == 0) source.behavior().onTick(entity, level);
        }
    }

    /**
     * Minestom decrements potion duration at the live TPS, so at high TPS an effect expires too early in real time. Re-add
     * it at a TPS-scaled duration (identity at 20); the re-add re-fires this event, guarded by {@link #rescaling}.
     */
    private void onPotionAdd(EntityPotionAddEvent e) {
        Potion p = e.getPotion();
        if (!rescaling.get() && p.duration() != Potion.INFINITE_DURATION) {
            int scaled = TickScaler.duration(p.duration(), KEY);
            if (scaled != p.duration()) {
                e.setCancelled(true);
                rescaling.set(Boolean.TRUE);
                try { e.getEntity().addEffect(new Potion(p.effect(), p.amplifier(), scaled, p.flags())); }
                finally { rescaling.set(Boolean.FALSE); }
                return;
            }
        }
        onPotion(e.getEntity(), p, true);
    }

    private void onPotion(Entity entity, Potion potion, boolean added) {
        if (!(entity instanceof LivingEntity living)) return;
        EntitySource source = registry.entitySource(potion.effect().key());
        if (source == null) return;
        int level = potion.amplifier() + 1;
        syncModifiers(living, source, level, added);
        if (added) source.behavior().onApply(living, level);
        else source.behavior().onRemove(living, level);
    }

    /** Adds or removes a source's vanilla-attribute modifiers (after the scope's tuning) on the holder's Minestom instances. */
    private void syncModifiers(LivingEntity entity, Source source, int level, boolean add) {
        AttributeContext ctx = context(entity, null);
        pushModifiers(entity, ctx.config().tuningFor(source.key()).apply(ctx, source.modifiers(level)), modifierId(source, level), add);
    }

    /** Re-applies {@code mods} under {@code id} on the entity's instances: removeModifier (idempotent re-apply/level change), then addModifier when {@code add}. A custom attribute (null handle) is pull-only via AttributeContext, never pushed. */
    private static void pushModifiers(LivingEntity entity, List<Source.Mod> mods, Key id, boolean add) {
        for (Source.Mod mod : mods) {
            var handle = mod.attribute().handle();
            if (handle == null) continue;
            AttributeInstance instance = entity.getAttribute(handle);
            if (instance == null) continue;
            instance.removeModifier(id);
            if (add) instance.addModifier(new AttributeModifier(id, mod.amount(), mod.operation()));
        }
    }

    /**
     * Stable per-source-per-level modifier id (removable when the effect ends). The level keeps a higher effect's push
     * from being stripped when a replaced lower one's removal fires (Minestom replaces via add-then-remove, same key), e.g. Speed II -> X.
     */
    private static Key modifierId(Source source, int level) {
        return Key.key("mm", source.key().asString().replace(':', '.') + "." + level);
    }

    /**
     * Reconciles every {@link ArmorSource} against {@code entity}'s worn armor: push/clear modifiers, fire behavior on
     * equip/unequip. A full recompute off current armor (not an add/remove diff), so order-independent - no potion-path race.
     */
    private void syncArmor(LivingEntity entity, EquipmentSlot changedSlot, ItemStack changedItem) {
        Map<Key, Integer> worn = wornArmorMap(entity);
        for (ArmorSource source : registry.armorSources()) {
            int level = wornLevel(entity, source.key(), changedSlot, changedItem);
            applyEquipModifiers(entity, source, level, armorModifierId(source));
            int was = worn.getOrDefault(source.key(), 0);
            if (was == 0 && level > 0) source.behavior().onApply(entity, level);
            else if (was > 0 && level == 0) source.behavior().onRemove(entity, was);
            if (level > 0) worn.put(source.key(), level); else worn.remove(source.key());
        }
    }

    /** Sets ({@code level > 0}) or clears a worn/held source's pushed modifiers under {@code id} (per-source id, no level: reconciled by full recompute, not add/remove events). */
    private void applyEquipModifiers(LivingEntity entity, Source source, int level, Key id) {
        AttributeContext ctx = context(entity, null);
        pushModifiers(entity, ctx.config().tuningFor(source.key()).apply(ctx, source.modifiers(Math.max(level, 1), ctx)), id, level > 0);
    }

    /** Reconciles every registered {@link HeldSource} against the main-hand {@code held} item: pushes/clears its attribute modifier (e.g. Efficiency -> {@code MINING_EFFICIENCY}), since mining is client-computed off the holder's attributes. */
    private void syncHeld(LivingEntity entity, ItemStack held) {
        for (HeldSource source : registry.heldSources()) {
            applyEquipModifiers(entity, source, Enchants.level(held, source.key()), heldModifierId(source));
        }
    }

    /** Stable per-held-source modifier id (level-independent; reconciled by recompute, distinct from the potion/armor ids). */
    private static Key heldModifierId(HeldSource source) {
        return Key.key("mm", "held." + source.key().asString().replace(':', '.'));
    }

    /** Periodic Behavior dispatch for worn armor sources (Frost Walker etc.), mirroring {@link #tickEffects} but off the worn-armor map. */
    private void tickArmor(LivingEntity entity) {
        Map<Key, Integer> worn = entity.getTag(WORN_ARMOR);
        if (worn == null || worn.isEmpty()) return;
        long now = entity.getAliveTicks();
        for (Map.Entry<Key, Integer> e : worn.entrySet()) {
            ArmorSource source = registry.armorSource(e.getKey());
            if (source == null) continue;
            int interval = source.behavior().tickInterval(e.getValue());
            if (interval <= 0) continue;
            int scaled = Math.max(1, TickScaler.duration(interval, KEY)); // cadence in server ticks
            if (now % scaled == 0) source.behavior().onTick(entity, e.getValue());
        }
    }

    /**
     * Player equipment-attribute reconcile (vanilla {@code detectEquipmentUpdates}): re-applies worn-armor + held pushes
     * off the SETTLED equipment, on the tick (post-settle) so it matches vanilla timing and isn't raced by the use-item
     * client prediction (a synchronous equip-event push is dropped by the predicting client). No-op unless a tracked stack changed.
     */
    private void tickEquipment(Player player) {
        ItemStack[] cur = {
                player.getEquipment(EquipmentSlot.HELMET), player.getEquipment(EquipmentSlot.CHESTPLATE),
                player.getEquipment(EquipmentSlot.LEGGINGS), player.getEquipment(EquipmentSlot.BOOTS),
                player.getItemInMainHand()
        };
        ItemStack[] prev = player.getTag(EQUIP_SNAPSHOT);
        if (prev != null && Arrays.equals(prev, cur)) return;
        player.setTag(EQUIP_SNAPSHOT, cur);
        syncArmor(player, null, null);            // null changedSlot -> wornLevel reads getEquipment for every slot
        syncHeld(player, player.getItemInMainHand());
    }

    /**
     * Highest level of {@code enchantKey} across the entity's four armor pieces (single-slot enchants). With a non-null
     * {@code changedSlot} (the equip event, which fires before Minestom commits the slot) the event's {@code changedItem}
     * is used for that slot; otherwise (the settled tick reconcile) every slot is read from {@code getEquipment}. 0 if none.
     */
    private static int wornLevel(LivingEntity entity, Key enchantKey, EquipmentSlot changedSlot, ItemStack changedItem) {
        int max = 0;
        for (EquipmentSlot slot : EquipmentSlot.armors()) {
            ItemStack item = slot == changedSlot ? changedItem : entity.getEquipment(slot);
            int lvl = Enchants.level(item, enchantKey);
            if (lvl > max) max = lvl;
        }
        return max;
    }

    private static boolean isArmorSlot(EquipmentSlot s) {
        return s == EquipmentSlot.HELMET || s == EquipmentSlot.CHESTPLATE || s == EquipmentSlot.LEGGINGS || s == EquipmentSlot.BOOTS;
    }

    private static Map<Key, Integer> wornArmorMap(LivingEntity e) {
        Map<Key, Integer> m = e.getTag(WORN_ARMOR);
        if (m == null) { m = new HashMap<>(); e.setTag(WORN_ARMOR, m); }
        return m;
    }

    /** Stable per-armor-source modifier id (level-independent; armor is reconciled by recompute, distinct from the potion ids). */
    private static Key armorModifierId(ArmorSource source) {
        return Key.key("mm", "armor." + source.key().asString().replace(':', '.'));
    }

    /**
     * Fires the {@link OnHit} side effects of {@code item}'s enchants - the attacker's weapon on-hit dispatch (Fire
     * Aspect, ...). Triggered by the damage system after a fresh landed hit; the enchant definitions live in the catalog,
     * the trigger lives with the domain. Knockback-domain enchants are not {@code OnHit} (they feed the KB computation).
     */
    public void dispatchWeaponOnHit(LivingEntity attacker, LivingEntity victim, ItemStack item) {
        if (item == null || item.isAir()) return;
        EnchantmentList enchants = item.get(DataComponents.ENCHANTMENTS);
        if (enchants == null) return;
        for (Map.Entry<RegistryKey<Enchantment>, Integer> e : enchants.enchantments().entrySet()) {
            if (e.getValue() <= 0) continue;
            ItemSource s = registry.itemSource(e.getKey().key());
            if (s instanceof OnHit oh) {
                oh.onHit(new HitContext(attacker, victim, e.getValue(), item, mm.services()));
            }
        }
    }

    /** Registers a source (custom enchant/potion); returns this for chaining. */
    public AttributeSystem register(Source source) {
        registry.register(source);
        return this;
    }

    /** Effective config for {@code entity}: the scoped profile, else the install config. */
    public AttributeConfig configFor(@Nullable Entity entity) {
        AttributeConfig scoped = mm.profiles().resolve(entity, MechanicsKeys.ATTRIBUTES);
        return scoped != null ? scoped : config;
    }

    /** Query context for {@code entity} + the in-context {@code item} (scope-resolved config). */
    public AttributeContext context(LivingEntity entity, @Nullable ItemStack item) {
        return AttributeContext.of(entity, item, configFor(entity), mm.services());
    }

    /** Resolves the config against a context (its {@code enabled} switch etc.). */
    public ResolvedAttributeConfig resolveConfig(AttributeContext ctx) {
        return AttributeConfigResolver.resolve(ctx.config(), ctx);
    }

    /**
     * Defense mitigation against {@code damage} for {@code victim}, vanilla order:
     * <pre>armor → resistance → EPF/protection</pre>
     * (absorption is Minestom's, applied later by {@code living.damage()}). Each stage self-gates on its
     * {@link MitigationRequest} bypass and on whether the victim's config configures it; the per-stage {@code Formula}
     * comes from the victim's scoped {@link AttributeConfig}.
     */
    public float mitigate(LivingEntity victim, float damage, MitigationRequest req) {
        if (damage <= 0) return damage;
        AttributeConfig cfg = configFor(victim);
        return MitigationPipeline.run(cfg.mitigationStages,
                new MitigationPipeline.State(this, victim, req, cfg, damage));
    }

    /** Effect level of {@code key} on {@code victim} (the conditional-fact context read); {@code 0} = absent. */
    int effectLevel(LivingEntity victim, Key key) {
        return context(victim, null).effectLevel(key);
    }

    /**
     * The victim's knockback resistance: source-contributed modifiers folded onto Minestom's attribute value. The caller
     * owns what to do with it (LEGACY rolls to negate, 1:1 with vanilla; MODERN scales).
     */
    public double knockbackResistance(LivingEntity living) {
        double base = living.getAttributeValue(net.minestom.server.entity.attribute.Attribute.KNOCKBACK_RESISTANCE);
        return context(living, null).value(Attribute.KNOCKBACK_RESISTANCE, base);
    }

    /**
     * Whether {@code living} has Fire Resistance ({@code minecraft:fire_resistance}) - total immunity to fire damage. Read
     * by {@code DamageSystem} at hit entry (blocked outright, like vanilla's {@code return false}), not a mitigation-stage reduction.
     */
    public boolean fireResistant(LivingEntity living) {
        return living.hasEffect(PotionEffect.FIRE_RESISTANCE);
    }

    /**
     * Active sources for {@code entity} now: registered matches of its active potion effects + the in-context
     * {@code item}'s enchants. Level = potion amplifier+1 / enchant level.
     */
    public List<Active> activeSources(LivingEntity entity, @Nullable ItemStack item) {
        List<Active> out = new ArrayList<>();
        for (TimedPotion tp : entity.getActiveEffects()) {
            EntitySource s = registry.entitySource(tp.potion().effect().key());
            if (s != null) out.add(new Active(s, tp.potion().amplifier() + 1));
        }
        if (item != null && !item.isAir()) {
            EnchantmentList enchants = item.get(DataComponents.ENCHANTMENTS);
            if (enchants != null) {
                for (Map.Entry<RegistryKey<Enchantment>, Integer> e : enchants.enchantments().entrySet()) {
                    ItemSource s = registry.itemSource(e.getKey().key());
                    if (s != null && e.getValue() > 0) out.add(new Active(s, e.getValue()));
                }
            }
        }
        return out;
    }

    public SourceRegistry registry() { return registry; }
    public AttributeConfig config() { return config; }
    public EventNode<@NotNull EntityEvent> node() { return node; }

    /** A source active on an entity at a resolved {@code level} (1-based). */
    public record Active(Source source, int level) {}

    /**
     * Installs from the GLOBAL profile's {@link AttributeConfig}: its {@code sources} register up front. Set the profile
     * before installing; no global profile = inert (no sources). Per-scope tuning still resolves per hit.
     */
    public static AttributeSystem install(MinestomMechanics mm) {
        return install(mm, mm.profiles().resolve(null, MechanicsKeys.ATTRIBUTES));
    }

    /** Installs from an explicit config (the modular path): registers its {@code sources}. */
    public static AttributeSystem install(MinestomMechanics mm, @Nullable AttributeConfig config) {
        AttributeSystem system = new AttributeSystem(mm, config);
        mm.register(system);
        mm.install(system.node);
        return system;
    }
}
