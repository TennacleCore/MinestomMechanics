package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.world.MechanicsWorld;
import java.util.function.Consumer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Scoped {@link MechanicsProfile} registry: assign profiles per player, per world (a virtual game world), per instance, or globally,
 * and swap them at runtime (configs are immutable, so a swap takes effect on the next hit; the {@code player} platform
 * member is pushed by {@code PlayerConfigApplier} on change). Resolution is per <em>member</em>, highest scope first:
 * <pre>player profile -> world profile -> instance profile -> global profile -> the system's install config</pre>
 * so a partial profile (e.g. knockback only) overrides just that system. Resolve a single member with {@link #resolve};
 * a hit reading several members should use {@link #resolved} to walk the scopes once.
 *
 * <p><b>Scope subject.</b> Attack resolves against the <em>attacker</em> (hit detection/ruleset is the attacker's
 * action); damage and knockback resolve against the <em>victim</em> (whose mechanics they are). In the usual minigame
 * case both share the instance, so the distinction only matters for player overrides.
 *
 * <p>Player and instance assignments live in transient tags, so they clean up with their holder (a player's profile
 * drops on disconnect).
 */
public final class MechanicsProfiles {

    private static final Tag<MechanicsProfile> PROFILE = Tag.Transient("mm:profile");

    private volatile @Nullable MechanicsProfile global;
    /** Notified after an assignment changes with the affected player, or {@code null} for a wider scope (global/world/instance). */
    private volatile @Nullable Consumer<@Nullable Player> changeHook;

    MechanicsProfiles() {}

    void onChange(Consumer<@Nullable Player> hook) { this.changeHook = hook; }

    private void changed(@Nullable Player player) {
        var hook = changeHook;
        if (hook != null) hook.accept(player);
    }

    /** Sets (or with {@code null} clears) the server-wide fallback profile. */
    public void setGlobal(@Nullable MechanicsProfile profile) {
        this.global = profile;
        changed(null);
    }
    public @Nullable MechanicsProfile global() { return global; }

    /** Re-fires the change hook (the player platform push) - call after moving a player between worlds. */
    public void refresh() { changed(null); }

    /** Sets (or with {@code null} clears) the profile for a world - per-game mechanics (above instance scope). */
    public void setWorld(MechanicsWorld world, @Nullable MechanicsProfile profile) {
        if (profile == null) world.removeTag(PROFILE);
        else world.setTag(PROFILE, profile);
        changed(null);
    }
    public @Nullable MechanicsProfile world(MechanicsWorld world) { return world.getTag(PROFILE); }

    /** Sets (or with {@code null} clears) the profile for an instance (world/dimension). */
    public void setInstance(Instance instance, @Nullable MechanicsProfile profile) {
        if (profile == null) instance.removeTag(PROFILE);
        else instance.setTag(PROFILE, profile);
        changed(null);
    }
    public @Nullable MechanicsProfile instance(Instance instance) { return instance.getTag(PROFILE); }

    /** Sets (or with {@code null} clears) the profile for a single player (highest precedence). */
    public void setPlayer(Player player, @Nullable MechanicsProfile profile) {
        if (profile == null) player.removeTag(PROFILE);
        else player.setTag(PROFILE, profile);
        changed(player);
    }
    public @Nullable MechanicsProfile player(Player player) { return player.getTag(PROFILE); }

    /**
     * The effective value of {@code key} for {@code subject}: player scope, else world, else instance, else global,
     * else {@code null}. For a hit that reads several members, prefer {@link #resolved} (one scope walk for all keys).
     */
    public <C> @Nullable C resolve(@Nullable Entity subject, ConfigKey<C> key) {
        if (subject != null) {
            if (subject instanceof Player p) {
                C v = memberOf(p.getTag(PROFILE), key);
                if (v != null) return v;
            }
            Instance in = subject.getInstance();
            if (in != null) {
                C v = memberOf(MechanicsWorld.of(subject).getTag(PROFILE), key);
                if (v != null) return v;
                v = memberOf(in.getTag(PROFILE), key);
                if (v != null) return v;
            }
        }
        return memberOf(global, key);
    }

    private static <C> @Nullable C memberOf(@Nullable MechanicsProfile profile, ConfigKey<C> key) {
        return profile != null ? profile.get(key) : null;
    }

    /**
     * Captures {@code subject}'s resolution scopes (player / world / instance / global) once, then answers any key off
     * that snapshot. Use it when a single hit reads several members, to avoid re-walking the scopes per member.
     */
    public Resolved resolved(@Nullable Entity subject) {
        MechanicsProfile player = subject instanceof Player p ? p.getTag(PROFILE) : null;
        MechanicsProfile world = null;
        MechanicsProfile instance = null;
        if (subject != null) {
            Instance in = subject.getInstance();
            if (in != null) {
                world = MechanicsWorld.of(subject).getTag(PROFILE);
                instance = in.getTag(PROFILE);
            }
        }
        return new Resolved(player, world, instance, global);
    }

    /** A one-shot resolution view over fixed player / world / instance / global scopes; resolve any key with {@link #get}. */
    public static final class Resolved {
        private final @Nullable MechanicsProfile player;
        private final @Nullable MechanicsProfile world;
        private final @Nullable MechanicsProfile instance;
        private final @Nullable MechanicsProfile global;

        private Resolved(@Nullable MechanicsProfile player, @Nullable MechanicsProfile world,
                         @Nullable MechanicsProfile instance, @Nullable MechanicsProfile global) {
            this.player = player;
            this.world = world;
            this.instance = instance;
            this.global = global;
        }

        /** The effective value of {@code key}: player, else world, else instance, else global; {@code null} if no scope sets it. */
        public <C> @Nullable C get(ConfigKey<C> key) {
            C v;
            if (player != null && (v = player.get(key)) != null) return v;
            if (world != null && (v = world.get(key)) != null) return v;
            if (instance != null && (v = instance.get(key)) != null) return v;
            return global != null ? global.get(key) : null;
        }
    }
}
