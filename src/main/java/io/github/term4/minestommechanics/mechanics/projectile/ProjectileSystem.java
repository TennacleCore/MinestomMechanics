package io.github.term4.minestommechanics.mechanics.projectile;

import io.github.term4.minestommechanics.world.MechanicsWorld;
import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.ProjectileLaunchEvent;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ProjectileContext;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ResolvedFlight;
import io.github.term4.minestommechanics.mechanics.projectile.entities.arrow.ArrowEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FishingBobberEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.Shootable;
import io.github.term4.minestommechanics.mechanics.projectile.types.Arrow;
import io.github.term4.minestommechanics.mechanics.projectile.types.Egg;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.FishingBobber;
import io.github.term4.minestommechanics.mechanics.projectile.types.Pearl;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileType;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;
import io.github.term4.minestommechanics.mechanics.projectile.types.SplashPotion;
import io.github.term4.minestommechanics.tracking.motion.LegacyVelocity;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Flame;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Power;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.enchant.Punch;
import io.github.term4.minestommechanics.util.Directions;
import io.github.term4.minestommechanics.item.Enchants;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Projectile system: resolves a {@link ProjectileSnapshot} into a spawn + velocity, fires {@link ProjectileLaunchEvent},
 * and spawns the entity. Mirrors {@code DamageSystem} - types with a {@code typeConfigs} entry enable at install (the
 * per-type {@code enabled} knob gates per launch). Self-driven types wire their item triggers in {@link ProjectileType#enable}.
 */
public final class ProjectileSystem implements MechanicsModule {

    /** This system's identity for per-module TPS scaling (its {@code referenceTps} feel-baseline). */
    public static final Key KEY = Key.key("mm:projectile");

    private final ProjectileConfig config;
    private final Services services;
    private final MinestomMechanics mm;
    private final EventNode<@NotNull Event> node;
    private final Map<Key, ProjectileType> types = new ConcurrentHashMap<>();
    private final Set<Key> enabled = ConcurrentHashMap.newKeySet();

    public ProjectileSystem(MinestomMechanics mm, ProjectileConfig config) {
        this.mm = mm;
        this.config = config;
        this.services = mm.services();
        this.node = EventNode.all("mm:projectile");
        // melee/left-click deflection: attacking a deflectable projectile (a fireball) redirects it along the attacker's look
        node.addListener(EntityAttackEvent.class, e -> {
            if (e.getTarget() instanceof ProjectileEntity target) target.deflectBy(e.getEntity());
        });
    }

    public ProjectileConfig config() { return config; }
    public EventNode<@NotNull Event> node() { return node; }

    /** Effective config for a snapshot carrying none: the shooter's scoped profile, else the install config. */
    private ProjectileConfig configFor(@Nullable Entity shooter) {
        ProjectileConfig scoped = mm.profiles().resolve(shooter, MechanicsKeys.PROJECTILES);
        return scoped != null ? scoped : config;
    }

    /**
     * Launches a projectile from a snapshot: resolves config, computes spawn + velocity (snapshot overrides win, else
     * aim x speed x power + spread + momentum), fires the cancellable {@link ProjectileLaunchEvent}, then spawns the
     * entity. {@code null} if the type is disabled, cancelled, or the shooter has no instance.
     */
    public @Nullable ProjectileEntity launch(ProjectileSnapshot snap) {
        ProjectileSnapshot working = snap.config() != null ? snap : snap.withConfig(configFor(snap.shooter()));
        ProjectileContext ctx = ProjectileContext.of(working, services);
        ProjectileTypeConfig effectiveConfig = ctx.typeConfig();
        ResolvedFlight flight = ProjectileConfigResolver.resolveFlight(effectiveConfig, ctx);
        if (!flight.enabled()) return null;
        return launch(working, effectiveConfig, flight);
    }

    /** The launch-time flight values for {@code snap} under its effective config chain (what {@link #launch} will use); lets a launcher read a knob (e.g. the bow's {@code critChance}) without re-resolving by hand. */
    public ResolvedFlight resolveFlight(ProjectileSnapshot snap) {
        ProjectileSnapshot working = snap.config() != null ? snap : snap.withConfig(configFor(snap.shooter()));
        ProjectileContext ctx = ProjectileContext.of(working, services);
        return ProjectileConfigResolver.resolveFlight(ctx.typeConfig(), ctx);
    }

    private @Nullable ProjectileEntity launch(ProjectileSnapshot working, ProjectileTypeConfig effectiveConfig, ResolvedFlight flight) {
        Entity shooter = working.shooter();
        Instance instance = shooter.getInstance();
        if (instance == null) return null;

        Pos spawnPos = working.spawnPos() != null ? working.spawnPos() : spawnPos(shooter, flight);
        Vec velocity = working.velocity() != null ? working.velocity() : launchVelocity(shooter, flight, working.power());

        ProjectileEntity entity = working.type().createEntity(shooter, working, effectiveConfig);
        stampFlight(entity, flight, working);

        ProjectileLaunchEvent event = new ProjectileLaunchEvent(working, entity, flight, spawnPos, velocity);
        EventDispatcher.call(event);
        if (event.isCancelled()) return null;
        ProjectileBehavior behavior = event.behavior();
        if (behavior != null && entity instanceof ManagedProjectile mp) mp.setBehavior(behavior);
        stampWorldSeams(entity, working, effectiveConfig, flight, behavior);

        // wire lockstep: snap the spawn state onto what the predicting client decodes (per WireGrid); server-
        // authoritative projectiles (fireball) keep full precision - the 1/32 truncation leaked horizontal KB
        Vec spawnVel = event.velocity();
        Pos spawnAt = event.spawnPos().withView(Directions.yaw(spawnVel), Directions.pitch(spawnVel));
        boolean lockstep = flight.wireLockstep() != null ? flight.wireLockstep() : flight.velocitySyncInterval() <= 0;
        if (lockstep) {
            boolean legacy = flight.wireGrid() == ProjectileTypeConfig.WireGrid.LEGACY_1_8;
            if (legacy) spawnAt = quantizeToWireGrid(spawnAt);
            spawnVel = quantizeToWireVelocity(spawnVel, legacy);
        }
        entity.setVelocityBt(spawnVel);
        entity.setSpawnPosition(spawnAt);
        MechanicsWorld.of(shooter).spawn(entity, spawnAt);
        return entity;
    }

    /**
     * World-system seams: the fork copy + save descriptor stamps, with the CURRENT velocity read at copy/save time.
     * Bobbers get no DEFAULT stamps (a forked/saved copy is an orphan - the rod tracks ITS instance); move a live
     * bobber cross-world instead, or a game with re-attach logic stamps its own.
     */
    private void stampWorldSeams(ProjectileEntity entity, ProjectileSnapshot working, ProjectileTypeConfig effectiveConfig,
                                 ResolvedFlight flight, @Nullable ProjectileBehavior behavior) {
        if (entity instanceof FishingBobberEntity) return;
        Entity shooter = working.shooter();
        entity.setTag(MechanicsWorld.ENTITY_COPY, () -> {
            ProjectileEntity copy = working.type().createEntity(shooter, working, effectiveConfig);
            stampFlight(copy, flight, working);
            if (behavior != null && copy instanceof ManagedProjectile mp) mp.setBehavior(behavior);
            stampWorldSeams(copy, working, effectiveConfig, flight, behavior);
            copy.setVelocityBt(entity.velocityBt());
            copy.setSpawnPosition(entity.getPosition());
            return copy;
        });
        entity.setTag(MechanicsWorld.ENTITY_SAVE, () -> {
            Vec vel = entity.velocityBt();
            CompoundBinaryTag.Builder out = CompoundBinaryTag.builder().putString("id", "mm:projectile")
                    .putString("type", working.type().key().asString())
                    .putDouble("power", working.power())
                    .put("vel", ListBinaryTag.builder(BinaryTagTypes.DOUBLE)
                            .add(DoubleBinaryTag.doubleBinaryTag(vel.x()))
                            .add(DoubleBinaryTag.doubleBinaryTag(vel.y()))
                            .add(DoubleBinaryTag.doubleBinaryTag(vel.z())).build());
            if (working.item() != null) out.put("item", ItemStack.CODEC.encode(Transcoder.NBT, working.item()).orElseThrow());
            if (shooter != null) out.putString("shooter", shooter.getUuid().toString());
            return out.build();
        });
    }

    /**
     * Rebuilds a saved projectile - the load-side reviver for {@code "mm:projectile"} descriptors. The launch
     * build path minus the event and spawn, resolved against the shooter's (or global) profile; the shooter
     * re-links when still online. {@code null} if the type key is unknown to this server.
     */
    public @Nullable ProjectileEntity fromSave(@NotNull CompoundBinaryTag data) {
        ProjectileType type = ProjectileType.byKey(Key.key(data.getString("type")));
        if (type == null) return null;
        String uuid = data.getString("shooter");
        Entity shooter = uuid.isEmpty() ? null
                : MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(UUID.fromString(uuid));
        ItemStack item = data.get("item") != null
                ? ItemStack.CODEC.decode(Transcoder.NBT, data.get("item")).orElseThrow() : null;
        ProjectileSnapshot snap = new ProjectileSnapshot(shooter, type, item, data.getDouble("power"),
                null, null, configFor(shooter), null);
        ProjectileContext ctx = ProjectileContext.of(snap, services);
        ProjectileTypeConfig effectiveConfig = ctx.typeConfig();
        ResolvedFlight flight = ProjectileConfigResolver.resolveFlight(effectiveConfig, ctx);
        ProjectileEntity entity = type.createEntity(shooter, snap, effectiveConfig);
        stampFlight(entity, flight, snap);
        stampWorldSeams(entity, snap, effectiveConfig, flight, null);
        ListBinaryTag vel = data.getList("vel", BinaryTagTypes.DOUBLE);
        if (vel.size() == 3) entity.setVelocityBt(new Vec(vel.getDouble(0), vel.getDouble(1), vel.getDouble(2)));
        return entity;
    }

    /** Truncates a spawn position to the 1.8 wire grid (1/32) so the server simulates from where a 1.8 client predicts. See {@link #launch}. */
    private static Pos quantizeToWireGrid(Pos p) {
        return new Pos((int) (p.x() * 32.0) / 32.0, (int) (p.y() * 32.0) / 32.0, (int) (p.z() * 32.0) / 32.0, p.yaw(), p.pitch());
    }

    /**
     * Snaps a spawn velocity (client b/t) onto what the predicting client decodes: the spawn packet's LP encoding, plus
     * the Via re-encode to 1.8 shorts for a legacy client. Neither encode is invertible on its grid, so iterate to a
     * fixed point - re-encoding the sim value must reproduce it, or the sim runs a wire unit off the client's.
     */
    private static Vec quantizeToWireVelocity(Vec bt, boolean legacyShorts) {
        Vec v = bt;
        // the LP grid step is ~0.49 short steps, so the short truncation can cascade down ~11 consecutive wire units
        for (int i = 0; i < 32; i++) {
            byte[] wire = NetworkBuffer.makeArray(NetworkBuffer.LP_VECTOR3, v);
            Vec decoded = NetworkBuffer.wrap(wire, 0, wire.length).read(NetworkBuffer.LP_VECTOR3);
            if (legacyShorts) decoded = new Vec(
                    LegacyVelocity.snapAxisBt(decoded.x(), LegacyVelocity.DEFAULT_CAP),
                    LegacyVelocity.snapAxisBt(decoded.y(), LegacyVelocity.DEFAULT_CAP),
                    LegacyVelocity.snapAxisBt(decoded.z(), LegacyVelocity.DEFAULT_CAP));
            if (decoded.equals(v)) return v;
            v = decoded;
        }
        return v;
    }

    /** Stamps the resolved flight config onto a freshly created entity (physics, sync, immunity, behavior, pickup). */
    private static void stampFlight(ProjectileEntity entity, ResolvedFlight flight, ProjectileSnapshot snap) {
        entity.setBoundingBox(flight.boundingBox().width(), flight.boundingBox().height(), flight.boundingBox().depth());
        entity.setAerodynamics(new Aerodynamics(flight.gravity(), flight.horizontalDrag(), flight.verticalDrag()));
        entity.setBroadcastMovement(flight.broadcastMovement());
        entity.setSynchronizationTicks(TickScaler.duration(flight.syncInterval(), KEY));
        entity.setVelocitySyncInterval(TickScaler.duration(flight.velocitySyncInterval(), KEY));
        // Minestom seeds nextSynchronizationTick at 20 and setSynchronizationTicks doesn't reset it - a denser
        // cadence would stay silent until tick 20 (the fireball froze ~1s, then snapped downrange)
        if (flight.velocitySyncInterval() > 0) entity.synchronizeNextTick();
        entity.setShooterImmunityTicks(TickScaler.duration(flight.shooterImmunityTicks(), KEY));
        entity.setEntityHitGrow(flight.entityHitGrow());
        entity.setPhysicsOrder(flight.physicsOrder());
        entity.setLeftOwnerImmunity(flight.leftOwnerImmunity());
        entity.setStickPullback(flight.stickPullback());
        entity.setWireMotYFloor(flight.wireMotYFloor());
        if (entity instanceof ManagedProjectile mp) {
            mp.setBehavior(snap.behavior() != null ? snap.behavior() : flight.behavior());
        }
        if (entity instanceof ArrowEntity arrow) {
            arrow.setShakeTicks(flight.shakeTicks());
            if (flight.pickupBox() != null) arrow.setPickupBox(flight.pickupBox());
        }
        if (entity instanceof FireballEntity fireball) fireball.setExplosionPower((float) flight.explosionPower());
        // read off the launch item for ANY projectile - a preset may put Power/Punch/Flame on non-bows
        entity.setProjectileEnchants(Enchants.level(snap.item(), Power.KEY), Enchants.level(snap.item(), Punch.KEY), Enchants.level(snap.item(), Flame.KEY));
    }

    // Projectile spawn

    private static Pos spawnPos(Entity shooter, ResolvedFlight cfg) {
        Pos eye = shooter.getPosition().add(0, shooter.getEyeHeight(), 0);
        Vec aim = eye.direction();
        Vec right = Directions.rightOf(eye.yaw());
        double side = cfg.spawnOffsetSideways();
        return eye.add(aim.x() * cfg.spawnOffsetForward() + right.x() * side, cfg.spawnOffsetVertical(),
                aim.z() * cfg.spawnOffsetForward() + right.z() * side);
    }

    private Vec launchVelocity(Entity shooter, ResolvedFlight cfg, double power) {
        Pos view = shooter.getPosition();
        Vec aim = cfg.launchPitchOffset() != 0
                ? Directions.headingWithPitchOffset(view.yaw(), view.pitch(), cfg.launchPitchOffset())
                : view.direction();
        Vec vel = aim.mul(cfg.speed() * power);
        if (cfg.spread() > 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double len = vel.length();
            if (len > 0) {
                vel = vel.div(len)
                        .add(r.nextGaussian() * cfg.spread(), r.nextGaussian() * cfg.spread(), r.nextGaussian() * cfg.spread())
                        .mul(len);
            }
        }
        double mh = cfg.momentumHorizontal(), mv = cfg.momentumVertical();
        if (mh != 0 || mv != 0) {
            // fold a fraction of the shooter's velocity (26.1 shootFromRotation)
            Vec sv = MotionTracker.positionDelta(shooter);
            vel = vel.add(sv.x() * mh, sv.y() * mv, sv.z() * mh);
        }
        return vel;
    }

    // Registry

    /** Registers a type (data only; not enabled). No-op if its key is already registered. */
    public ProjectileSystem register(ProjectileType type) { types.putIfAbsent(type.key(), type); return this; }

    public @Nullable ProjectileType get(Key key) { return types.get(key); }
    public boolean contains(Key key) { return types.containsKey(key); }
    public boolean isEnabled(Key key) { return enabled.contains(key); }

    /** Enables a registered type (wires its launch trigger). Idempotent; throws for an unregistered key (catches preset typos). */
    public void enable(Key key) {
        ProjectileType type = types.get(key);
        if (type == null) throw new IllegalArgumentException("No projectile type registered for " + key.asString());
        if (enabled.add(key)) type.enable(this, mm);
    }

    /** Disables an enabled type (tears down its trigger). Idempotent. */
    public void disable(Key key) {
        ProjectileType type = types.get(key);
        if (type != null && enabled.remove(key)) type.disable();
    }

    /** Registers the built-in vanilla projectile types (data only; not enabled). */
    public ProjectileSystem registerVanillaDefaults() {
        register(Snowball.INSTANCE);
        register(Egg.INSTANCE);
        register(Pearl.INSTANCE);
        register(Arrow.INSTANCE);
        register(Fireball.INSTANCE);
        register(SplashPotion.INSTANCE);
        register(FishingBobber.INSTANCE);
        return this;
    }

    /**
     * Installs reading the GLOBAL profile's {@link ProjectileConfig}: its {@code typeConfigs} enable the self-launching
     * types and its {@code shootables} (item launchers, e.g. {@code new Bow()}) are mounted. Set the profile before
     * installing. Per-hit physics still resolves per-scope; only these one-time registrations are read here.
     */
    public static ProjectileSystem install(MinestomMechanics mm) {
        ProjectileConfig global = mm.profiles().resolve(null, MechanicsKeys.PROJECTILES);
        return install(mm, global != null ? global : ProjectileConfig.builder().build());
    }

    /** Installs from an explicit config (the modular path): enables its {@code typeConfigs} and mounts its {@code shootables}. */
    public static ProjectileSystem install(MinestomMechanics mm, ProjectileConfig cfg) {
        ProjectileSystem system = new ProjectileSystem(mm, cfg);
        mm.register(system);
        system.registerVanillaDefaults();
        mm.install(system.node);
        for (Key key : cfg.typeConfigs.keySet()) system.enable(key);
        for (Shootable shootable : cfg.shootables()) shootable.install(system.node, system);
        return system;
    }
}
