package io.github.term4.minestommechanics.tracking.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link MotionTracker#jumpSeed} - the takeoff motY = the jump_strength base plus Jump Boost's float-exact
 * {@code +0.1/level} (vanilla {@code bF()} / {@code getJumpPower} add a separate float term, not an attribute).
 * No effect ({@code -1}) is the bare base; a tuned base passes straight through.
 */
class JumpVelocityTest {

    private static final double BASE = VelocityConfig.JUMP_VELOCITY; // 0.41999998688697815, the float-exact 0.42 takeoff

    @Test
    void noJumpBoostIsBareBase() {
        assertEquals(BASE, MotionTracker.jumpSeed(BASE, -1), 0.0);
    }

    @Test
    void jumpBoostAddsFloatExactTenthPerLevel() {
        // vanilla: motY += (double)((float)(amplifier + 1) * 0.1f)
        assertEquals(BASE + (double) (1f * 0.1f), MotionTracker.jumpSeed(BASE, 0), 0.0); // Jump Boost I
        assertEquals(BASE + (double) (2f * 0.1f), MotionTracker.jumpSeed(BASE, 1), 0.0); // Jump Boost II
        assertEquals(BASE + (double) (5f * 0.1f), MotionTracker.jumpSeed(BASE, 4), 0.0); // Jump Boost V
    }

    @Test
    void passesAnArbitraryBaseThrough() {
        // the helper is pure in its base; the +0.1/level term stays float-exact (0.1f widens to 0.10000000149...)
        assertEquals(0.5 + (double) (1f * 0.1f), MotionTracker.jumpSeed(0.5, 0), 0.0);
    }
}
