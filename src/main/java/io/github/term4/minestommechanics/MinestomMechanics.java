package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.mechanics.attack.FakeHits;
import io.github.term4.minestommechanics.platform.SharedTeam;
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
import io.github.term4.minestommechanics.world.WorldSounds;
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

    /** Listens for player details sent via the ViaVersion proxy message. */
    public boolean viaProxyDetails = true;

    public boolean installSprintTracker = true;
    /** Tracks per-entity air-time, launch state, and position-delta motion (drives knockback velocity). */
    public boolean installMotionTracker = true;
    /** Removes the pose-change stutter (sneak/sprint/...) 1.9+ clients show under high ping. Requires {@link #installPlayerProvider}. */
    public boolean metaFix = true;
    /**
     * Installs the {@code OptimizedPlayer} provider and scoped {@code PlayerConfig} application. Independent of
     * {@link #metaFix}. Disable only if you set your own player provider; extend {@code OptimizedPlayer} there to keep
     * PlayerConfig support.
     */
    public boolean installPlayerProvider = true;

    private final EventNode<@NotNull Event> root = EventNode.all("mm:root");
    private final EventNode<@NotNull Event> apiEvents = EventNode.all("mm:api-events");
    private EventNode<@NotNull Event> trackersNode;

    private ClientInfoTracker clientInfo;
    private final MechanicsProfiles profiles = new MechanicsProfiles();

    private @Nullable SprintTracker sprintTracker;
    private @Nullable MotionTracker motionTracker;

    /** Installed systems, keyed by concrete type. */
    private final Map<Class<? extends MechanicsModule>, MechanicsModule> modules = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    private MinestomMechanics() {}

    /** The JVM-wide library instance; the lookup for code that can't be handed {@link Services} (entity factories). */
    public static MinestomMechanics getInstance() { return INSTANCE; }

    /** Registers an installed system; later retrievable via {@link #module(Class)}. Called from each system's {@code install}. */
    public <M extends MechanicsModule> void register(M module) {
        MechanicsModule previous = modules.put(module.getClass(), module);
        // a re-install replaces the listeners, not stacks them
        if (previous != null && previous.node() != null) uninstall(previous.node());
    }

    public <M extends MechanicsModule> @Nullable M module(Class<M> type) {
        return type.cast(modules.get(type));
    }

    public @Nullable SprintTracker sprintTracker() { return sprintTracker; }
    public @Nullable MotionTracker motionTracker() { return motionTracker; }

    /** Installs with the current options; a failed init resets the flag instead of latching a partial installation. */
    public synchronized void init() {
        if (initialized) return;
        initialized = true; // inside the lock; the internal mounts assert it
        try {
            doInit();
        } catch (RuntimeException | Error e) {
            initialized = false;
            throw e;
        }
    }

    private void doInit() {
        TickSystem.start();

        if (metaFix && !installPlayerProvider) {
            LOG.warn("metaFix is enabled but installPlayerProvider is not - the meta fix needs the OptimizedPlayer"
                    + " provider and will be inert (set your own provider extending OptimizedPlayer).");
        }
        if (metaFix) MetaFix.installListeners();
        if (installPlayerProvider) {
            MinecraftServer.getConnectionManager().setPlayerProvider((conn, profile) ->
                    new OptimizedPlayer(conn, profile));
            PlayerConfigApplier.install(this);
            CompatMovement.install(this);
            // inert unless CompatConfig.disableOffhand
            CompatOffhand.install(this);
            // inert unless CompatConfig.blockPlaceReach
            CompatPlacement.install(this);
            // strips attack_range off creative-echoed items, so the client-view stamp never becomes server state
            CompatCreativeGuard.install(this);
        }
        // the one lib scoreboard team; features enroll, this cleans up on disconnect
        SharedTeam.install(this);
        // inert unless AttackConfig.fakeHits or CompatConfig.fistRayHits
        FakeHits.install(this);
        // block-place + footstep sounds Minestom doesn't emit
        WorldSounds.install(this);
        profiles.onChange(changed -> {
            if (changed != null) {
                if (installPlayerProvider) PlayerConfigApplier.apply(this, changed);
                return;
            }
            refreshGlobalScaling();
            if (installPlayerProvider) PlayerConfigApplier.applyAll(this);
        });
        refreshGlobalScaling();
        // per-subject scope: one world can run dilated (simulated TPS) while the rest doesn't
        TickScaler.resolver(subject -> profiles.resolve(subject, MechanicsKeys.TICK_SCALING));

        MinecraftServer.getGlobalEventHandler().addChild(root);
        root.addChild(apiEvents);

        trackersNode = EventNode.all("mm:trackers");
        root.addChild(trackersNode);

        clientInfo = new ClientInfoTracker(viaProxyDetails);
        if (installSprintTracker) mountTracker(sprintTracker = new SprintTracker());
        if (installMotionTracker) mountTracker(motionTracker = new MotionTracker(profiles));
        // the client-info hub also routes Animatium handshakes
        if (viaProxyDetails || installPlayerProvider) mountTracker(clientInfo);
        if (installPlayerProvider) {
            CompatAnimatium.install(this);
            ViaBridgeRpc.install(this);
            LegacyVelocityBridge.install(this);
        }
    }

    /** Starts a tracker and mounts it under {@code mm:trackers}. */
    public void mountTracker(Tracker tracker) {
        ensureInitialized();
        tracker.start();
        trackersNode.addChild(tracker.node());
    }

    public Services services() {
        ensureInitialized();
        return new Services(this);
    }

    public ClientInfoTracker clientInfo() {
        ensureInitialized();
        return clientInfo;
    }

    /**
     * A per-player view of client-side info: protocol version, Animatium status, and a typed store keyed by
     * {@code ClientKey} for custom data. A fresh lightweight view each call.
     */
    public ClientProfile client(@NotNull Player player) {
        ensureInitialized();
        return clientInfo.of(player);
    }

    /** Scoped config profiles; assignable before {@link #init()} and swappable at runtime. */
    public MechanicsProfiles profiles() { return profiles; }

    private void refreshGlobalScaling() {
        TickScaler.setGlobal(profiles.resolve(null, MechanicsKeys.TICK_SCALING));
    }

    /**
     * Convenience node for MinestomMechanics API event listeners. API events dispatch through the global handler, so
     * listening here or on {@code MinecraftServer.getGlobalEventHandler()} both work.
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

    public void uninstall(EventNode<? extends @NotNull Event> node) {
        root.removeChild(node);
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("MinestomMechanics has not been initialized");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
