package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.platform.compatibility.CompatAnimatium;
import io.github.term4.minestommechanics.platform.compatibility.CompatCreativeGuard;
import io.github.term4.minestommechanics.platform.compatibility.CompatMovement;
import io.github.term4.minestommechanics.platform.compatibility.CompatOffhand;
import io.github.term4.minestommechanics.platform.compatibility.CompatPlacement;
import io.github.term4.minestommechanics.platform.compatibility.LegacyVelocityBridge;
import io.github.term4.minestommechanics.platform.compatibility.ViaBridgeRpc;
import io.github.term4.minestommechanics.platform.fixes.client.MetaFix;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.platform.player.PlayerConfigApplier;
import io.github.term4.minestommechanics.tracking.ClientInfoTracker;
import io.github.term4.minestommechanics.tracking.ClientProfile;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.Tracker;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main initialization class for the library: server-level options (trackers, metaFix), the node tree
 * ({@code mm:root} -&gt; trackers / systems / api-events), the system registry, and scoped config profiles.
 */
public final class MinestomMechanics {

    private static final Logger LOG = LoggerFactory.getLogger(MinestomMechanics.class);
    private static final MinestomMechanics INSTANCE = new MinestomMechanics();

    // Server level options (defaults)
    /** Listens for player details sent via the ViaVersion proxy message. Default: true */
    public boolean viaProxyDetails = true;

    /** Tracks each player's sprint status. Default: true */
    public boolean installSprintTracker = true;
    /** Tracks per-entity air-time, launch state, and position-delta motion (drives knockback velocity). Default: true */
    public boolean installMotionTracker = true;
    /** Removes the pose-change stutter (sneak/sprint/...) 1.9+ clients show under high ping. Requires {@link #installPlayerProvider}. */
    public boolean metaFix = true;
    /**
     * Installs the {@code OptimizedPlayer} provider and scoped {@code PlayerConfig} application (e.g.
     * {@code positionBroadcastInterval}). Independent of {@link #metaFix}. Disable only if you set your own player
     * provider; extend {@code OptimizedPlayer} there to keep PlayerConfig support. Default: true
     */
    public boolean installPlayerProvider = true;

    // Node tree: mm:root -> api-events / trackers / system nodes. trackersNode is built in init().
    private final EventNode<@NotNull Event> root = EventNode.all("mm:root");
    private final EventNode<@NotNull Event> apiEvents = EventNode.all("mm:api-events");
    private EventNode<@NotNull Event> trackersNode;

    // Server level services
    private ClientInfoTracker clientInfo;
    private final MechanicsProfiles profiles = new MechanicsProfiles();

    // Core trackers (optional, mounted under mm:trackers)
    private @Nullable SprintTracker sprintTracker;
    private @Nullable MotionTracker motionTracker;

    /** Installed systems, keyed by concrete type. Populated at install (boot); read during gameplay. */
    private final Map<Class<? extends MechanicsModule>, MechanicsModule> modules = new ConcurrentHashMap<>();

    private boolean initialized = false;

    private MinestomMechanics() {}

    /** The JVM-wide library instance - the blessed lookup for code that can't be handed {@link Services} (entity factories). */
    public static MinestomMechanics getInstance() { return INSTANCE; }

    /** Registers an installed system; later retrievable via {@link #module(Class)}. Called from each system's {@code install}. */
    public <M extends MechanicsModule> void register(M module) {
        modules.put(module.getClass(), module);
    }

    /** The installed system of the given type, or {@code null} if it was not installed. */
    public <M extends MechanicsModule> @Nullable M module(Class<M> type) {
        return type.cast(modules.get(type));
    }

    public @Nullable SprintTracker sprintTracker() { return sprintTracker; }
    public @Nullable MotionTracker motionTracker() { return motionTracker; }

    /** Initialize with current options (or defaults if no options specified) */
    public void init() {
        if (initialized) return;
        initialized = true;

        TickSystem.start();

        if (metaFix && !installPlayerProvider) {
            LOG.warn("metaFix is enabled but installPlayerProvider is not - the meta fix needs the OptimizedPlayer"
                    + " provider and will be inert (set your own provider extending OptimizedPlayer).");
        }
        if (metaFix) MetaFix.installListeners();
        if (installPlayerProvider) {
            MinecraftServer.getConnectionManager().setPlayerProvider((conn, profile) ->
                    new OptimizedPlayer(conn, profile));
            // scoped PlayerConfig -> OptimizedPlayer, at spawn + on every profile change
            PlayerConfigApplier.install(this);
            // compat movement: hitbox-collision + 1.8 speed clamps
            CompatMovement.install(this);
            // compat offhand (inert unless CompatConfig.disableOffhand)
            CompatOffhand.install(this);
            // compat block-placement reach (inert unless CompatConfig.blockPlaceReach)
            CompatPlacement.install(this);
            // seal the attack_range stamp: strip it from a stamped client's creative-echoed items (never becomes server state)
            CompatCreativeGuard.install(this);
        }
        // global scaling drives physics + static-context durations; refresh on any profile change
        profiles.onChange(() -> {
            refreshGlobalScaling();
            if (installPlayerProvider) PlayerConfigApplier.applyAll(this);
        });
        refreshGlobalScaling();

        MinecraftServer.getGlobalEventHandler().addChild(root);
        root.addChild(apiEvents);

        // trackers: per-player state off events, each mounts under mm:trackers
        trackersNode = EventNode.all("mm:trackers");
        root.addChild(trackersNode);

        clientInfo = new ClientInfoTracker(viaProxyDetails);
        if (installSprintTracker) mountTracker(sprintTracker = new SprintTracker());
        if (installMotionTracker) mountTracker(motionTracker = new MotionTracker(profiles));
        // client-info hub also routes Animatium handshakes, so mount it when either is wanted
        if (viaProxyDetails || installPlayerProvider) mountTracker(clientInfo);
        // Animatium: detect via animatium:info, send the native feature set, gate the matching hacks off. Needs the player provider.
        if (installPlayerProvider) {
            CompatAnimatium.install(this);
            ViaBridgeRpc.install(this);
            LegacyVelocityBridge.install(this);
        }
    }

    /** Mounts a tracker under {@code mm:trackers} (init-time or lazily from installed systems). */
    public void mountTracker(Tracker tracker) {
        ensureInitialized();
        mount(trackersNode, tracker);
    }

    /** Starts a tracker and mounts its listener node. */
    private static void mount(EventNode<@NotNull Event> trackers, Tracker tracker) {
        tracker.start();
        trackers.addChild(tracker.node());
    }

    /** Access registered MinestomMechanics services. */
    public Services services() {
        ensureInitialized();
        return new Services(this);
    }

    /** Access client info (e.g. protocol version) stamped by the client info tracker */
    public ClientInfoTracker clientInfo() {
        ensureInitialized();
        return clientInfo;
    }

    /**
     * A per-player view of client-side info: protocol version, Animatium status, and an end-user typed store
     * ({@link ClientProfile#get}/{@link ClientProfile#set} keyed by {@code ClientKey}) for custom data. A fresh
     * lightweight view each call.
     */
    public ClientProfile client(@NotNull Player player) {
        ensureInitialized();
        return clientInfo.of(player);
    }

    /**
     * Scoped config profiles (player / instance / global). Assignable any time - including before
     * {@link #init()} - and swappable at runtime; systems consult them per hit.
     */
    public MechanicsProfiles profiles() { return profiles; }

    /** Pushes the global scope's scaling config into {@link TickScaler} (physics + static-context durations read it). */
    private void refreshGlobalScaling() {
        TickScaler.setGlobal(profiles.resolve(null, MechanicsKeys.TICK_SCALING));
    }

    /**
     * Convenience node for MinestomMechanics API event listeners. API events are dispatched through the
     * global handler, so listening here or on {@code MinecraftServer.getGlobalEventHandler()} both work.
     */
    public EventNode<@NotNull Event> events() {
        ensureInitialized();
        return apiEvents;
    }

    /** Installs a node under the root MinestomMechanics node. */
    public void install(EventNode<? extends @NotNull Event> node) {
        ensureInitialized();
        root.addChild(node);
    }

    /** Throws until {@link #init()} has run; guards the public entry points. */
    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
