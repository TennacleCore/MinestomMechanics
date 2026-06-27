package io.github.term4.minestommechanics.mechanics.attribute.source;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registered {@link Source} catalog, split by kind ({@link EntitySource} by effect key, {@link ItemSource}/{@link HeldSource}
 * by enchant key, {@link ArmorSource} by armor-enchant key) so each scan matches only the relevant kind. {@link #register}
 * dispatches by subtype.
 */
public final class SourceRegistry {

    private final Map<Key, EntitySource> entitySources = new ConcurrentHashMap<>();
    private final Map<Key, ItemSource> itemSources = new ConcurrentHashMap<>();
    private final Map<Key, ArmorSource> armorSources = new ConcurrentHashMap<>();
    private final Map<Key, HeldSource> heldSources = new ConcurrentHashMap<>();

    public SourceRegistry register(Source source) {
        if (source instanceof EntitySource e) entitySources.put(e.key(), e);
        else if (source instanceof ArmorSource a) armorSources.put(a.key(), a);
        else if (source instanceof HeldSource h) heldSources.put(h.key(), h);
        else if (source instanceof ItemSource i) itemSources.put(i.key(), i);
        return this;
    }

    /** The entity-borne (potion-effect) source for {@code effectKey}, or {@code null}. */
    public @Nullable EntitySource entitySource(Key effectKey) { return entitySources.get(effectKey); }

    /** The item-borne (held-enchant) source for {@code enchantKey}, or {@code null}. */
    public @Nullable ItemSource itemSource(Key enchantKey) { return itemSources.get(enchantKey); }

    /** The worn-armor source for {@code enchantKey}, or {@code null}. */
    public @Nullable ArmorSource armorSource(Key enchantKey) { return armorSources.get(enchantKey); }

    /** Every registered worn-armor source (for the equipment-lifecycle reconcile). */
    public Collection<ArmorSource> armorSources() { return armorSources.values(); }

    /** The held-item source for {@code enchantKey}, or {@code null}. */
    public @Nullable HeldSource heldSource(Key enchantKey) { return heldSources.get(enchantKey); }

    /** Every registered held-item source (for the held-item lifecycle reconcile). */
    public Collection<HeldSource> heldSources() { return heldSources.values(); }
}
