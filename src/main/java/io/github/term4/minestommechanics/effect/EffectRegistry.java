package io.github.term4.minestommechanics.effect;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * An immutable registry of {@link Effect}s by {@link Key} - the {@code MechanicsKeys.EFFECTS} profile value, resolved
 * per scope. A preset provides one ({@link Effects#vanilla18()}); {@link #register} returns a NEW registry so overrides
 * compose without mutation, and {@link Effect#NONE} silences one effect.
 */
public final class EffectRegistry {

    private static final EffectRegistry EMPTY = new EffectRegistry(Map.of());

    private final Map<Key, Effect> effects;

    private EffectRegistry(Map<Key, Effect> effects) { this.effects = Map.copyOf(effects); }

    /** A registry with no effects - nothing plays until keys are {@link #register registered}. */
    public static @NotNull EffectRegistry empty() { return EMPTY; }

    /** The effect registered for {@code key}, or {@code null}. */
    public @Nullable Effect get(@NotNull Key key) { return effects.get(key); }

    /** A copy of this registry with {@code key} mapped to {@code effect} (added or replaced). */
    public @NotNull EffectRegistry register(@NotNull Key key, @NotNull Effect effect) {
        Map<Key, Effect> map = new HashMap<>(effects);
        map.put(key, effect);
        return new EffectRegistry(map);
    }

    /** A copy of this registry with every entry of {@code overrides} laid on top ({@code overrides} wins). */
    public @NotNull EffectRegistry withAll(@NotNull EffectRegistry overrides) {
        Map<Key, Effect> map = new HashMap<>(effects);
        map.putAll(overrides.effects);
        return new EffectRegistry(map);
    }

    public @NotNull Map<Key, Effect> effects() { return effects; }
}
