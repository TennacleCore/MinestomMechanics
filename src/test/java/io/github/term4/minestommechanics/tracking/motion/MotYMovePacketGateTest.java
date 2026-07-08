package io.github.term4.minestommechanics.tracking.motion;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link VelocityConfig#motYOnMovePacket}: with the knob on (MineMen), the motY sim holds on ticks without a client
 * move packet and resumes on the next one; default advances every tick. Own instance per test - the gate reads the
 * instance profile and clock.
 */
class MotYMovePacketGateTest extends HeadlessServerTest {

    private static final double G = 0.08, D = 0.98;
    private static final double SEED = VelocityConfig.JUMP_VELOCITY;

    private static Instance airInstance(MechanicsProfile profile) {
        var inst = MinecraftServer.getInstanceManager().createInstanceContainer();
        inst.setGenerator(unit -> unit.modifier().fillHeight(0, 64, Block.STONE));
        inst.loadChunk(0, 0).join();
        if (profile != null) mm.profiles().setInstance(inst, profile);
        return inst;
    }

    private static void tick(Instance inst) { EventDispatcher.call(new InstanceTickEvent(inst, 0, 0)); }

    private static void move(Player p, double y, boolean onGround) {
        EventDispatcher.call(new PlayerMoveEvent(p, new Pos(8.5, y, 8.5), onGround));
    }

    /** Baseline packet, rising launch packet (seeds 0.42), then one advanced tick. Returns the post-launch motY. */
    private static double launch(Instance inst, Player p) {
        tick(inst);                    // sim created
        move(p, 80.0, true);           // baseline
        tick(inst);
        move(p, 80.42, false);         // rising: latches the launch, seeds motY
        tick(inst);                    // move arrived last tick -> advances
        Double motY = MotionTracker.serverMotY(p, 0, true);
        assertNotNull(motY);
        assertEquals((SEED - G) * D, motY, 1e-12);
        return motY;
    }

    @Test
    void movePacketGateHoldsWithoutMovesAndResumesOnTheNext() {
        var inst = airInstance(MechanicsProfile.builder()
                .set(MechanicsKeys.VELOCITY, VelocityRule.simulated(
                        VelocityConfig.builder().motYOnMovePacket(true).build()))
                .build());
        Player p = FakePlayer.connect(inst, new Pos(8.5, 80, 8.5), "MotYHold").player;

        double v = launch(inst, p);
        tick(inst);                    // no move packet -> held
        tick(inst);
        assertEquals(v, MotionTracker.serverMotY(p, 0, true), 0.0, "frozen client: motY holds");

        move(p, 80.3, false);          // client resumes
        tick(inst);
        assertEquals((v - G) * D, MotionTracker.serverMotY(p, 0, true), 1e-12, "one step per move packet");
        p.remove();
    }

    @Test
    void defaultAdvancesEveryTick() {
        var inst = airInstance(null);  // no VELOCITY profile -> default gate (every tick)
        Player p = FakePlayer.connect(inst, new Pos(8.5, 80, 8.5), "MotYTick").player;

        double v = launch(inst, p);
        tick(inst);                    // no move packets, still advances
        tick(inst);
        double expected = ((v - G) * D - G) * D;
        assertEquals(expected, MotionTracker.serverMotY(p, 0, true), 1e-12);
        p.remove();
    }
}
