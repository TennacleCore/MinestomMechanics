package io.github.term4.minestommechanics.effect;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A pluggable audiovisual effect: given an {@link EffectContext} (where + who), it emits sounds / particles / entity
 * animations to the shard-scoped audience. The unit of the effect layer - registered per {@link Effects effect key} (or
 * a custom key) in an {@link EffectRegistry}, or composed from the built-in factories. Runs on the caller's thread (the
 * shard's clock); its only side effect is packet sends, which are thread-safe.
 */
@FunctionalInterface
public interface Effect {

    void play(@NotNull EffectContext ctx);

    /** Plays nothing - register a key to this ({@code registry.register(key, Effect.NONE)}) to silence that effect. */
    Effect NONE = ctx -> {};

    /** A positional sound at the context position, to the shard audience. */
    static @NotNull Effect sound(@NotNull SoundEvent sound, @NotNull Sound.Source source, float volume, float pitch) {
        return ctx -> ctx.sound(sound, source, volume, pitch);
    }

    /** A symmetric particle burst at the context position. */
    static @NotNull Effect particle(@NotNull Particle particle, int count, double spread, float speed) {
        return ctx -> ctx.particle(particle, count, spread, spread, spread, speed);
    }

    /** An entity animation on the context source (its own sparkle), to its viewers + itself. */
    static @NotNull Effect entityAnimation(EntityAnimationPacket.@NotNull Animation animation) {
        return ctx -> ctx.entityAnimation(animation);
    }

    /** A hit animation on the context target (the vanilla crit / magic-crit sparkle), to the source's viewers - not the source (it predicts its own). */
    static @NotNull Effect hitAnimation(EntityAnimationPacket.@NotNull Animation animation) {
        return ctx -> ctx.hitAnimation(animation);
    }

    /** {@link #hitAnimation} including the source - for a server-filled hit its client predicted nothing. */
    static @NotNull Effect hitAnimationAll(EntityAnimationPacket.@NotNull Animation animation) {
        return ctx -> ctx.hitAnimationAll(animation);
    }

    /** This effect, then {@code next} (compose several plays into one). */
    default @NotNull Effect and(@NotNull Effect next) {
        return ctx -> { play(ctx); next.play(ctx); };
    }
}
