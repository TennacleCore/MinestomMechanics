package io.github.term4.minestommechanics.effect;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.EffectEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.ListenerHandle;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The effect layer's entry points: the built-in effect {@link Key keys}, the vanilla {@link EffectRegistry} factory a
 * preset installs, and the generic {@link #play} the library's mechanics call. An effect is defined once (here or by a
 * user in a registry) and applied anywhere by key; a server customizes feedback by providing a different registry on the
 * {@code MechanicsKeys.EFFECTS} profile member - no event listeners required (the {@link EffectEvent} is the optional
 * dynamic hook). Sound ids are the modern ones (Via translates for 1.8 clients); {@link #modern()} adds the
 * 1.9+ melee attack sound {@link #vanilla18()} omits.
 */
public final class Effects {

    private Effects() {}

    private static final ListenerHandle<EffectEvent> EFFECT = EventDispatcher.getHandle(EffectEvent.class);

    /** Melee critical hit: the crit sparkle on the attacker (seen by its viewers) + the crit sound. */
    public static final Key CRIT = Key.key("mm:crit");
    /** Enchantment ("magic") critical: the magic-crit sparkle. */
    public static final Key MAGIC_CRIT = Key.key("mm:magic_crit");
    /** Food chewed this tick - on the vanilla eating cadence (every 4 ticks). */
    public static final Key EAT = Key.key("mm:eat");
    /** Drink sipped this tick. */
    public static final Key DRINK = Key.key("mm:drink");
    /** Food finished: the vanilla burp. */
    public static final Key BURP = Key.key("mm:burp");
    /** Item collected: the pickup pop. */
    public static final Key ITEM_PICKUP = Key.key("mm:item_pickup");
    /** A critical arrow's flight-trail particles. */
    public static final Key ARROW_CRIT = Key.key("mm:arrow_crit");
    /** Snowball thrown from the hand. */
    public static final Key THROW_SNOWBALL = Key.key("mm:throw_snowball");
    /** Egg thrown from the hand. */
    public static final Key THROW_EGG = Key.key("mm:throw_egg");
    /** Ender pearl thrown from the hand. */
    public static final Key THROW_PEARL = Key.key("mm:throw_pearl");
    /** Fireball (fire charge) thrown from the hand. */
    public static final Key THROW_FIREBALL = Key.key("mm:throw_fireball");
    /** Bow released (arrow shot). */
    public static final Key BOW_SHOOT = Key.key("mm:bow_shoot");
    /** Fishing rod cast. */
    public static final Key ROD_CAST = Key.key("mm:rod_cast");
    /** Fishing rod reeled in. */
    public static final Key ROD_RETRIEVE = Key.key("mm:rod_retrieve");
    /** Arrow struck a block or entity (the thunk). */
    public static final Key ARROW_HIT = Key.key("mm:arrow_hit");
    /** Arrow struck a target - the hit-marker "ding" to the SHOOTER only. Unregistered by default (off); a PvP preset registers it. */
    public static final Key ARROW_HIT_PLAYER = Key.key("mm:arrow_hit_player");

    /**
     * Plays the effect registered for {@code key} in {@code ctx.source()}'s scope: resolves the scope
     * {@link EffectRegistry} ({@code MechanicsKeys.EFFECTS}), fires the cancellable {@link EffectEvent}, then plays. A
     * no-op when no registry is set for the scope, the key is unregistered, the effect is {@link Effect#NONE}, or a
     * listener cancels. The generic apply the library calls throughout - shard-scoped + thread-safe via {@code ctx}.
     */
    public static void play(@NotNull Services services, @NotNull Key key, @NotNull EffectContext ctx) {
        EffectRegistry registry = services.profiles().resolve(ctx.source(), MechanicsKeys.EFFECTS);
        if (registry == null) return;
        Effect effect = registry.get(key);
        if (effect == null) return;
        if (EFFECT.hasListener()) {
            EffectEvent event = new EffectEvent(key, ctx, effect, services);
            EventDispatcher.call(event);
            if (event.isCancelled()) return;
            effect = event.effect(); // a listener may swap the effect (or re-enable a NONE)
        }
        if (effect == Effect.NONE) return;
        effect.play(ctx);
    }

    /**
     * The 1.8 vanilla effects - the {@code Vanilla18} preset sets this as its {@code MechanicsKeys.EFFECTS} member.
     * 1.8 has no melee attack sounds (those are 1.9+), so the crit is particle-only; {@code register}/{@code withAll}
     * to tweak, or start a new registry from {@link EffectRegistry#empty()}.
     */
    public static @NotNull EffectRegistry vanilla18() {
        return EffectRegistry.empty()
                .register(CRIT, Effect.hitAnimation(EntityAnimationPacket.Animation.CRITICAL_EFFECT))
                .register(MAGIC_CRIT, Effect.hitAnimation(EntityAnimationPacket.Animation.MAGICAL_CRITICAL_EFFECT))
                // viewers only: the client self-predicts its own chew from the eating metadata, so echoing it back would double
                .register(EAT, ctx -> ctx.viewerSound(SoundEvent.ENTITY_GENERIC_EAT, Sound.Source.PLAYER, eatVolume(), eatPitch()))
                .register(DRINK, ctx -> ctx.viewerSound(SoundEvent.ENTITY_GENERIC_DRINK, Sound.Source.PLAYER, 0.5f, drinkPitch()))
                // 1.8 random.burp: volume 0.5, pitch rand * 0.1 + 0.9
                .register(BURP, ctx -> ctx.sound(SoundEvent.ENTITY_PLAYER_BURP, Sound.Source.PLAYER,
                        0.5f, ThreadLocalRandom.current().nextFloat() * 0.1f + 0.9f))
                // 1.8 item pickup: volume 0.2, pitch ((rand - rand) * 0.7 + 1) * 2
                .register(ITEM_PICKUP, ctx -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    ctx.sound(SoundEvent.ENTITY_ITEM_PICKUP, Sound.Source.PLAYER, 0.2f,
                            ((r.nextFloat() - r.nextFloat()) * 0.7f + 1.0f) * 2.0f);
                })
                // launch sounds: server-driven, everyone incl the doer - the 1.8 client does NOT self-predict them (unlike the eating chew)
                .register(THROW_SNOWBALL, throwSound(SoundEvent.ENTITY_SNOWBALL_THROW, Sound.Source.NEUTRAL))
                .register(THROW_EGG, throwSound(SoundEvent.ENTITY_EGG_THROW, Sound.Source.PLAYER))
                .register(THROW_PEARL, throwSound(SoundEvent.ENTITY_ENDER_PEARL_THROW, Sound.Source.NEUTRAL))
                .register(THROW_FIREBALL, ctx -> ctx.sound(SoundEvent.ENTITY_GHAST_SHOOT, Sound.Source.NEUTRAL, 1.0f, firePitch()))
                .register(BOW_SHOOT, ctx -> ctx.sound(SoundEvent.ENTITY_ARROW_SHOOT, Sound.Source.PLAYER, 1.0f, bowPitch()))
                .register(ROD_CAST, throwSound(SoundEvent.ENTITY_FISHING_BOBBER_THROW, Sound.Source.NEUTRAL))
                .register(ROD_RETRIEVE, throwSound(SoundEvent.ENTITY_FISHING_BOBBER_RETRIEVE, Sound.Source.NEUTRAL))
                .register(ARROW_HIT, ctx -> ctx.sound(SoundEvent.ENTITY_ARROW_HIT, Sound.Source.NEUTRAL, 1.0f, arrowHitPitch()))
                .register(ARROW_CRIT, Effect.particle(Particle.CRIT, 2, 0.05, 0f));
    }

    /** The modern (26.1) effects - the {@code Vanilla} preset sets this. {@link #vanilla18()} plus the 1.9+ melee attack sound. */
    public static @NotNull EffectRegistry modern() {
        return vanilla18()
                .register(CRIT, Effect.hitAnimation(EntityAnimationPacket.Animation.CRITICAL_EFFECT)
                        .and(Effect.sound(SoundEvent.ENTITY_PLAYER_ATTACK_CRIT, Sound.Source.PLAYER, 1.0f, 1.0f)));
    }

    /**
     * The arrow hit-marker "ding" a PvP preset registers under {@link #ARROW_HIT_PLAYER} (off in {@link #vanilla18()}/{@link #modern()}):
     * a sound to the SHOOTER only when their arrow strikes a target. {@code random.orb} is a working placeholder - override with the preset's real ding.
     */
    public static @NotNull Effect arrowHitMarker() {
        return ctx -> ctx.sourceSound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, 1.0f, 1.0f);
    }

    private static float eatVolume() { return ThreadLocalRandom.current().nextBoolean() ? 0.5f : 1.0f; }

    private static float eatPitch() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return (r.nextFloat() - r.nextFloat()) * 0.2f + 1.0f;
    }

    private static float drinkPitch() { return ThreadLocalRandom.current().nextFloat() * 0.1f + 0.9f; }

    /** A throwable's launch sound: server-driven to everyone. The 1.8 client does NOT self-predict the throw (unlike the eating chew), so no viewers-only. */
    private static Effect throwSound(SoundEvent sound, Sound.Source src) {
        return ctx -> ctx.sound(sound, src, 0.5f, throwPitch());
    }

    private static float throwPitch() { return 0.4f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f); }

    // vanilla BowItem: 1/(rand*0.4+1.2) + power*0.5; the power term is approximated at full draw (the common PvP release)
    private static float bowPitch() { return 1.0f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 1.2f) + 0.5f; }

    // vanilla fire-charge / ghast-shoot pitch
    private static float firePitch() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return (r.nextFloat() - r.nextFloat()) * 0.2f + 1.0f;
    }

    // vanilla AbstractArrow hit pitch
    private static float arrowHitPitch() { return 1.2f / (ThreadLocalRandom.current().nextFloat() * 0.2f + 0.9f); }
}
