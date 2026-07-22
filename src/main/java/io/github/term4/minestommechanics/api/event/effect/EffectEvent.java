package io.github.term4.minestommechanics.api.event.effect;

import io.github.term4.minestommechanics.api.event.CancellableMechanicsEvent;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.effect.Effect;
import io.github.term4.minestommechanics.effect.EffectContext;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before an {@link Effect} plays, per {@link #key()}. Cancel to suppress it, or {@link #effect(Effect) swap} it.
 * The resolved effect is the scope's override, else the built-in default.
 */
public final class EffectEvent extends CancellableMechanicsEvent<EffectContext> {

    private final Key key;
    private @NotNull Effect effect;

    public EffectEvent(@NotNull Key key, @NotNull EffectContext context, @NotNull Effect effect, Services services) {
        super(context, services);
        this.key = key;
        this.effect = effect;
    }

    /** A built-in {@code Effects} key or a custom one. */
    public @NotNull Key key() { return key; }

    /** Position / source / target. */
    public @NotNull EffectContext context() { return snapshot(); }

    /** The effect that will play - the resolved override or built-in. */
    public @NotNull Effect effect() { return effect; }
    public void effect(@NotNull Effect effect) { this.effect = effect; }
}
