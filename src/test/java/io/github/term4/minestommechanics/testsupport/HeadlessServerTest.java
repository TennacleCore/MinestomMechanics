package io.github.term4.minestommechanics.testsupport;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.vanilla18.Attributes;
import io.github.term4.minestommechanics.mechanics.vanilla18.Damage;
import io.github.term4.minestommechanics.mechanics.vanilla18.Items;
import io.github.term4.minestommechanics.mechanics.vanilla18.Knockback;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeSystem;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.NetworkBuffer;
import org.junit.jupiter.api.BeforeAll;

/**
 * Minimal headless harness for entity-backed golden tests: boots a single {@link MinecraftServer} process (registries +
 * dispatcher, no socket) and the vanilla-1.8 {@link MinestomMechanics} systems once per JVM, then exposes a loaded flat
 * instance to place entities in. The calculators under test are pure functions of entity/config state, so no ticking is
 * needed - tests read {@code compute}/{@code snapshot} directly. See docs/attributes-design.md (step 0 harness).
 */
public abstract class HeadlessServerTest {

    protected static MinestomMechanics mm;
    protected static Services services;
    protected static InstanceContainer instance;

    @BeforeAll
    static void boot() {
        if (mm != null) return; // one server per JVM, shared by every subclass

        MinecraftServer.init();
        // entity registration/ticking partition threads (EnvImpl does the same); lets setInstance(...).join() complete
        MinecraftServer.process().dispatcher().start();

        mm = MinestomMechanics.getInstance();
        mm.init();
        DamageSystem.install(mm, Damage.config());
        KnockbackSystem.install(mm, Knockback.melee());
        AttributeSystem.install(mm, Attributes.config());
        services = mm.services();

        instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 64, Block.STONE));
        instance.loadChunk(0, 0).join();
        // Item-stat lookups resolve from the profile; scope them to this instance so tests that swap the GLOBAL profile
        // (e.g. AttributeTuningTest's setGlobal(null)) don't wipe the registry for entities placed here.
        mm.profiles().setInstance(instance, MechanicsProfile.builder().set(MechanicsKeys.ITEMS, Items.registry()).build());
    }

    /** A fresh loaded stone-floor instance, with {@code profile} scoped onto it when non-null. */
    protected static InstanceContainer flatInstance(MechanicsProfile profile) {
        InstanceContainer inst = MinecraftServer.getInstanceManager().createInstanceContainer();
        inst.setGenerator(unit -> unit.modifier().fillHeight(0, 64, Block.STONE));
        inst.loadChunk(0, 0).join();
        if (profile != null) mm.profiles().setInstance(inst, profile);
        return inst;
    }

    /** A stationary zombie placed at {@code pos} (yaw/pitch from the {@link Pos}); non-player, so its tracked velocity is zero. */
    protected static LivingEntity zombie(Pos pos) {
        LivingEntity e = new LivingEntity(EntityType.ZOMBIE);
        e.setInstance(instance, pos).join();
        return e;
    }

    /** A zombie not bound to any instance: {@link Entity#getPosition()} is the origin, tracked velocity zero. */
    protected static LivingEntity looseZombie() {
        return new LivingEntity(EntityType.ZOMBIE);
    }

    /** Waits (max 2s) for an entity's async {@code setInstance} to land - the launch join for projectile tests. */
    protected static void awaitSpawn(Entity e) {
        long deadline = System.currentTimeMillis() + 2000;
        while (e.getInstance() == null && System.currentTimeMillis() < deadline) Thread.onSpinWait();
        if (e.getInstance() == null) throw new AssertionError("launch setInstance did not complete");
    }

    /** The client's decode of an LP-encoded velocity (the modern wire grid). */
    protected static Vec lpRoundTrip(Vec v) {
        byte[] wire = NetworkBuffer.makeArray(NetworkBuffer.LP_VECTOR3, v);
        return NetworkBuffer.wrap(wire, 0, wire.length).read(NetworkBuffer.LP_VECTOR3);
    }

    /** Vanilla/Via truncate to shorts; the 1.8 client reads {@code short / 8000.0}. */
    protected static double legacyShortAxis(double bt) {
        return (short) (int) (bt * 8000.0) / 8000.0;
    }
}
