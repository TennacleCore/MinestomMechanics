package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.platform.compatibility.CompatAnimatium;
import io.github.term4.minestommechanics.platform.compatibility.CompatMovement;
import io.github.term4.minestommechanics.platform.compatibility.CompatOffhand;
import io.github.term4.minestommechanics.platform.compatibility.CompatPlacement;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MinestomMechanics {

    // Main initialization class for the library: server-level options (trackers, metaFix), the node tree
    // (mm:root -> trackers / systems / api-events), the system registry, and scoped config profiles.

    // Server level options (defaults)
    /** When enabled the server listens for player details sent from ViaVersion proxy message */
    public boolean viaProxyDetails = true;

    /** When enabled MinestomMechanics manually tracks a players sprinting status (useful for combat). Default: true */
    public boolean installSprintTracker = true;
    /** When enabled, tracks per-entity air-time, launch state, and position-delta motion (drives knockback velocity). Default: true */
    public boolean installMotionTracker = true;
    /**
     * When enabled, active potion effects (and so their pushed attribute modifiers) are cleared when an entity dies -
     * Minestom's {@code kill()} does not do this, so without it effects/modifiers leak across death/respawn. Vanilla
     * clears them on death; disable only if your server keeps effects through death. Default: true
     */
    public boolean clearEffectsOnDeath = true;
    /**
     * When enabled, transient combat/visual state is reset on death so a respawned entity starts fresh (vanilla parity):
     * fire ticks, drowning air, stuck arrows, and residual velocity. The i-frame window + fall distance reset on (re)spawn
     * regardless; potion effects are gated by {@link #clearEffectsOnDeath}. Disable to persist this state (keep-state servers). Default: true
     */
    public boolean resetCombatStateOnDeath = true;
    /** When enabled, the stutter seen on 1.9+ clients when changing poses (sneaking, sprinting, etc.) due to high ping will not be present. Requires {@link #installPlayerProvider}. */
    public boolean metaFix = true;
    /**
     * When enabled, installs the {@code OptimizedPlayer} provider and the scoped {@code PlayerConfig}
     * application (e.g. {@code positionBroadcastInterval}). Independent of {@link #metaFix} - broadcast
     * throttling works without the meta fix. Disable only if you set your own player provider; extend
     * {@code OptimizedPlayer} there to keep PlayerConfig support. Default: true
     */
    public boolean installPlayerProvider = true;

    // might add an option for packet validation when not using a proxy? probably better to use a separate library for that though
    private final EventNode<@NotNull Event> root = EventNode.all("mm:root");
    private final EventNode<@NotNull Event> apiEvents = EventNode.all("mm:api-events");

    // Server level services
    private ClientInfoTracker clientInfo;
    private final MechanicsProfiles profiles = new MechanicsProfiles();

    // Core trackers (optional, mounted under mm:trackers)
    private @Nullable SprintTracker sprintTracker;
    private @Nullable MotionTracker motionTracker;

    /** Installed systems, keyed by concrete type. Populated at install (boot); read during gameplay. */
    private final Map<Class<? extends MechanicsModule>, MechanicsModule> modules = new ConcurrentHashMap<>();

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

    private static final MinestomMechanics INSTANCE = new MinestomMechanics();
    private boolean initialized = false;

    private MinestomMechanics() {}

    public static MinestomMechanics getInstance() { return INSTANCE; }

    /** Initialize with current options (or defaults if no options specified) */
    public void init() {
        if (initialized) return;
        initialized = true;

        // Enable always-necessary functions
        TickSystem.start(); // clocks (server-wide + per-instance) and the per-tick update loop

        if (metaFix && !installPlayerProvider) {
            System.out.println("[mm] metaFix is enabled but installPlayerProvider is not - the meta fix needs the"
                    + " OptimizedPlayer provider and will be inert (set your own provider extending OptimizedPlayer).");
        }
        if (metaFix) MetaFix.installListeners();
        if (installPlayerProvider) {
            MinecraftServer.getConnectionManager().setPlayerProvider((conn, profile) ->
                    new OptimizedPlayer(conn, profile));
            // Scoped PlayerConfig (profiles) -> OptimizedPlayer, applied at spawn (join / instance
            // change) and pushed to online players whenever a profile assignment changes.
            PlayerConfigApplier.install(this);
            // Compat movement restrictions: hitbox-collision (restrictMovement) + 1.8 speed clamps (restrictSprint*/restrictSwimSpeed).
            CompatMovement.install(this);
            // Compat offhand disabling (inert unless a player's CompatConfig.disableOffhand is on).
            CompatOffhand.install(this);
            // Compat block-placement reach restriction (inert unless a player's CompatConfig.blockPlaceReach is set).
            CompatPlacement.install(this);
        }
        // The global scope's scaling baselines drive physics + static-context durations; refresh on any profile change.
        profiles.onChange(() -> {
            refreshGlobalScaling();
            if (installPlayerProvider) PlayerConfigApplier.applyAll(this);
        });
        refreshGlobalScaling();

        // Root node for all of MinestomMechanics
        MinecraftServer.getGlobalEventHandler().addChild(root);
        root.addChild(apiEvents);

        // Trackers: per-player state stamped off events; each enabled one mounts under a shared node.
        trackersNode = EventNode.all("mm:trackers");
        root.addChild(trackersNode);

        clientInfo = new ClientInfoTracker(viaProxyDetails);
        if (installSprintTracker) mountTracker(sprintTracker = new SprintTracker());
        if (installMotionTracker) mountTracker(motionTracker = new MotionTracker(profiles));
        // The client-info hub also routes client-mod handshakes (Animatium), so mount it whenever either is wanted.
        if (viaProxyDetails || installPlayerProvider) mountTracker(clientInfo);
        // Animatium integration: detect Animatium clients via animatium:info, send their native feature set, and gate the
        // matching server-side hacks off for them. Needs the player provider (feature set recorded on OptimizedPlayer.compat()).
        if (installPlayerProvider) CompatAnimatium.install(this);
    }

    private EventNode<@NotNull Event> trackersNode;

    /** Mounts a tracker under {@code mm:trackers} (init-time or lazily from installed systems). */
    public void mountTracker(Tracker tracker) {
        if (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
        mount(trackersNode, tracker);
    }

    /** Starts a tracker and mounts its listener node. */
    private static void mount(EventNode<@NotNull Event> trackers, Tracker tracker) {
        tracker.start();
        trackers.addChild(tracker.node());
    }

    /** Access registered MinestomMechanics services. */
    public Services services() {
        if (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
        return new Services(this);
    }

    /** Access client info (e.g. protocol version) stamped by the client info tracker */
    public ClientInfoTracker clientInfo() {
        if (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
        return clientInfo;
    }

    /**
     * A unified, extensible per-player view of client-side info: protocol version, Animatium status, and an end-user
     * typed store ({@link ClientProfile#get}/{@link ClientProfile#set} keyed by {@code ClientKey}) for custom data to
     * record / persist. A fresh lightweight view each call.
     */
    public ClientProfile client(@NotNull Player player) {
        if (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
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
        if (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
        return apiEvents;
    }

    /** Public method to install a node to the root MinestomMechanics node */
    public void install(EventNode<? extends @NotNull Event> node) {
        if  (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
        root.addChild(node);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
