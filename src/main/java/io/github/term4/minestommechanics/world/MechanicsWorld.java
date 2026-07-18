package io.github.term4.minestommechanics.world;

import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.EntityCollisionResult;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Weather;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.tag.Tag;
import net.minestom.server.tag.Taggable;
import net.minestom.server.world.DimensionType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The gameplay world an entity acts in. {@link InstanceWorld} wraps a plain Minestom {@link Instance} 1:1;
 * other implementations may virtualize it (isolated block/entity/viewer state over a shared instance).
 * {@link #instance()} is the escape hatch for Minestom APIs with no equivalent here.
 *
 * <p>Also a {@link Block.Getter}, an adventure {@link Audience} over {@link #players()}, and {@link Taggable}
 * (distinct from the instance's tags).
 */
public interface MechanicsWorld extends Block.Getter, ForwardingAudience, Taggable {

    Tag<MechanicsWorld> TAG = Tag.Transient("mm:world");
    /** An entity's world binding, stamped when a virtual world spawns/admits it; absent = the instance's wrapper. */
    Tag<MechanicsWorld> ENTITY_TAG = Tag.Transient("mm:entity-world");
    /**
     * A fresh, unspawned copy of the entity's current state, stamped by entities that support duplication
     * (drops, primed TNT). A world system's fork/respawn cloner reads it; absent = the entity doesn't copy.
     */
    Tag<Supplier<Entity>> ENTITY_COPY = Tag.Transient("mm:entity-copy");
    /** {@link #ENTITY_COPY}'s disk twin: an NBT descriptor for world saves; {@code "id"} names its reviver. */
    Tag<Supplier<CompoundBinaryTag>> ENTITY_SAVE = Tag.Transient("mm:entity-save");

    /** The world wrapping {@code instance} (cached on the instance itself). */
    static @NotNull MechanicsWorld of(@NotNull Instance instance) {
        MechanicsWorld world = instance.getTag(TAG);
        if (world == null) {
            // locked: TickContext.owns() compares wrappers by IDENTITY, so a racy duplicate is not benign
            synchronized (Resolver.class) {
                world = instance.getTag(TAG);
                if (world == null) {
                    world = new InstanceWorld(instance);
                    instance.setTag(TAG, world);
                }
            }
        }
        return world;
    }

    /** The world {@code entity} belongs to: its binding, else its instance's wrapper. The entity must be in a world. */
    static @NotNull MechanicsWorld of(@NotNull Entity entity) {
        MechanicsWorld bound = binding(entity);
        return bound != null ? bound : of(entity.getInstance());
    }

    /** {@link #of(Entity)} tolerating a removed entity: falls back to {@code instance} when it has no binding. */
    static @NotNull MechanicsWorld of(@NotNull Entity entity, @NotNull Instance instance) {
        MechanicsWorld bound = binding(entity);
        return bound != null ? bound : of(instance);
    }

    /** The entity's virtual-world binding, or {@code null} for the plain instance (never touches the instance). */
    static @Nullable MechanicsWorld binding(@NotNull Entity entity) {
        return Holder.RESOLVER.resolve(entity);
    }

    /**
     * The world whose BLOCKS {@code player}'s client renders - what client-parity checks (movement, eye, dig)
     * collide against. Only differs from {@link #of(Entity)} for observers viewing a virtual world.
     */
    static @NotNull MechanicsWorld viewed(@NotNull Player player) {
        MechanicsWorld viewed = Holder.RESOLVER.viewedBlocks(player);
        return viewed != null ? viewed : of(player.getInstance());
    }

    /** {@link #viewed(Player)} generalized: the blocks physically around {@code entity} (non-players: their binding). */
    static @NotNull MechanicsWorld viewed(@NotNull Entity entity) {
        return entity instanceof Player p ? viewed(p) : of(entity);
    }

    /** Whether an external world ticker owns {@code entity}'s tick (see {@link Resolver#externallyTicked}). */
    static boolean externallyTicked(@NotNull Entity entity) {
        return Holder.RESOLVER.externallyTicked(entity);
    }

    /** The external owner's clock for {@code entity}, or {@code -1} when the server owns its tick. */
    static long externalTick(@NotNull Entity entity) {
        return Holder.RESOLVER.externalTick(entity);
    }

    /** Whether the current thread may run {@code entity}'s tick (see {@link Resolver#ownsCurrentTick}). */
    static boolean ownsCurrentTick(@NotNull Entity entity) {
        return Holder.RESOLVER.ownsCurrentTick(entity);
    }

    /**
     * Entity -> virtual world. Default reads {@link #ENTITY_TAG}; an external world system (e.g. ShardKit)
     * re-points it at its own binding. {@code null} = unbound - never fall back to the instance here (actors
     * may be world-less mid-action).
     */
    @FunctionalInterface
    interface Resolver {
        @Nullable MechanicsWorld resolve(@NotNull Entity entity);

        /** {@link #viewed(Player)}'s source; a view layer re-points this at its per-observer block view. */
        default @Nullable MechanicsWorld viewedBlocks(@NotNull Player player) {
            return resolve(player);
        }

        /** True while an external ticker owns the entity's tick - it must stay out of the global dispatcher. */
        default boolean externallyTicked(@NotNull Entity entity) {
            return false;
        }

        /** The owning external clock's tick for {@code entity}, or {@code -1} when the server owns its tick. */
        default long externalTick(@NotNull Entity entity) {
            return -1;
        }

        /**
         * Whether the CURRENT thread may run {@code entity}'s tick ({@code true} when server-owned). Covered
         * classes gate their tick on it: dispatcher eviction is a queued signal, so a stalled server can drive
         * a just-adopted entity late - ownership, not timing, closes the double-tick.
         */
        default boolean ownsCurrentTick(@NotNull Entity entity) {
            return true;
        }

        Resolver DEFAULT = entity -> entity.getTag(ENTITY_TAG);
    }

    /** Installs the binding resolver server-wide (see {@link Resolver}); returns the previous one so a wrapper can delegate to it. */
    static @NotNull Resolver resolver(@NotNull Resolver resolver) {
        Resolver previous = Holder.RESOLVER;
        Holder.RESOLVER = resolver;
        return previous;
    }

    final class Holder {
        private static volatile Resolver RESOLVER = Resolver.DEFAULT;
        private Holder() {}
    }

    /** The backing instance; prefer the world operations. */
    @NotNull Instance instance();

    @NotNull Block getBlock(@NotNull Point pos);

    @NotNull Block getBlock(int x, int y, int z);

    @NotNull Block getBlock(@NotNull Point pos, Block.Getter.@NotNull Condition condition);

    @NotNull Block getBlock(int x, int y, int z, Block.Getter.@NotNull Condition condition);

    void setBlock(@NotNull Point pos, @NotNull Block block);

    /** One batch; a virtual world groups the client updates per section. */
    default void setBlocks(@NotNull Map<Point, Block> blocks) {
        blocks.forEach(this::setBlock);
    }

    boolean isChunkLoaded(@NotNull Point pos);

    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** The world's dimension (Y bounds, ambient light). */
    @NotNull DimensionType dimension();

    /** Swept box move against the world; unloaded chunks read SOLID (the safe default - exposure rays, movement). */
    PhysicsResult sweep(@NotNull BoundingBox box, @NotNull Pos position, @NotNull Vec velocity,
                        @Nullable PhysicsResult last, boolean singleCollision);

    /** Swept box move where unloaded chunks read EMPTY - projectile flight collides with loaded terrain only. */
    PhysicsResult sweepLoaded(@NotNull BoundingBox box, @NotNull Pos position, @NotNull Vec velocity,
                              @Nullable PhysicsResult last, boolean singleCollision);

    /** Swept entity collision along {@code velocity} (the projectile hit test). */
    Collection<EntityCollisionResult> sweepEntities(@NotNull BoundingBox box, @NotNull Pos position, @NotNull Vec velocity,
                                                    double extendRadius, @NotNull Function<Entity, Boolean> filter,
                                                    @Nullable PhysicsResult physics);

    /** One Minestom physics step against THIS world's blocks - the movementTick replacement on a virtual world. */
    PhysicsResult simulateMovement(@NotNull Pos position, @NotNull Vec velocityPerTick, @NotNull BoundingBox box,
                                   @NotNull Aerodynamics aerodynamics, boolean noGravity, boolean hasPhysics,
                                   boolean onGround, @Nullable PhysicsResult last);

    @NotNull WorldBorder worldBorder();

    boolean isInVoid(@NotNull Point pos);

    @NotNull Collection<@NotNull Entity> entities();

    @NotNull Collection<@NotNull Entity> nearbyEntities(@NotNull Point point, double range);

    void nearbyPlayers(@NotNull Point point, double range, @NotNull Consumer<Player> consumer);

    /** Puts {@code entity} into this world at {@code position} (a virtual world also binds it). */
    @NotNull CompletableFuture<Void> spawn(@NotNull Entity entity, @NotNull Pos position);

    /** This world's own players - the audience gameplay targets. */
    @NotNull Collection<@NotNull Player> players();

    /** Everyone rendering this world: its players plus registered observers (spectators/staff). */
    @NotNull Collection<@NotNull Player> watchers();

    /** One packet to every {@link #watchers() watcher} (presentation, not mechanics targeting). */
    void broadcast(@NotNull ServerPacket packet);

    /** World FX to every {@link #watchers() watcher}. */
    void playSound(@NotNull Sound sound, @NotNull Point pos);

    @Override default @NotNull Iterable<? extends Audience> audiences() { return players(); }

    /** This world's weather. */
    @NotNull Weather weather();

    /** Per-world weather on a virtual world (its viewers get it); the instance's on a plain one. */
    void setWeather(@NotNull Weather weather);

    /** Time of day of the default clock ({@code -1} when the dimension has none). */
    long time();

    /** A FIXED per-world time of day on a virtual world; the instance clock on a plain one. */
    void setTime(long time);

    long worldAge();

    /** Runs {@code task} on this world's next tick. */
    void scheduleNextTick(@NotNull Consumer<MechanicsWorld> task);
}
