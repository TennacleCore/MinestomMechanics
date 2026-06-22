package io.github.term4.minestommechanics.tracking.motion;

import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden/characterization pins for {@link LegacyVelocity#snap} - the 1.8 wire-grid quantization applied to outgoing
 * knockback velocity. Pure math over {@code ServerFlag.SERVER_TICKS_PER_SECOND}; pinned at the default 20 TPS (the lib
 * default). The attribute refactor must not change this. See docs/attributes-design.md.
 */
class LegacyVelocityCharacterizationTest {

    private static final double EPS = 1e-9;

    private static void assertVec(Vec expected, Vec actual) {
        assertEquals(expected.x(), actual.x(), EPS, "x");
        assertEquals(expected.y(), actual.y(), EPS, "y");
        assertEquals(expected.z(), actual.z(), EPS, "z");
    }

    /** The pins below assume 20 TPS (b/s = 20 * b/t). Fail loudly if the test JVM runs another TPS. */
    @Test
    void tpsPrecondition() {
        assertEquals(20, (int) ServerFlag.SERVER_TICKS_PER_SECOND);
    }

    @Test
    void zeroStaysZero() {
        assertVec(Vec.ZERO, LegacyVelocity.snap(Vec.ZERO));
    }

    @Test
    void snapsToVanillaShortBucket() {
        // 8 b/s = 0.4 b/t -> (int)(0.4*8000)=3200 shorts -> (3200+0.25)/8000 b/t -> *20 b/s
        assertVec(new Vec(8.000625, 0, 0), LegacyVelocity.snap(new Vec(8, 0, 0)));
        // negative truncates toward zero then re-centres negative
        assertVec(new Vec(-8.000625, 0, 0), LegacyVelocity.snap(new Vec(-8, 0, 0)));
    }

    @Test
    void allAxesSnapIndependently() {
        // x=8->0.4, y=4->0.2 (1600 shorts), z=-8->-0.4
        assertVec(new Vec(8.000625, 4.000625, -8.000625), LegacyVelocity.snap(new Vec(8, 4, -8)));
    }

    @Test
    void clampsToVanillaWireLimit() {
        // 100 b/s = 5 b/t -> clamped to 3.9 b/t -> (int)(3.9*8000)=31200 -> (31200+0.25)/8000 -> *20
        assertVec(new Vec(78.000625, 0, 0), LegacyVelocity.snap(new Vec(100, 0, 0)));
        assertVec(new Vec(0, -78.000625, 0), LegacyVelocity.snap(new Vec(0, -100, 0)));
    }

    @Test
    void subShortVelocityTruncatesToZero() {
        // 0.001 b/s = 0.00005 b/t -> 0.00005*8000=0.4 -> (int)=0 -> 0
        assertVec(Vec.ZERO, LegacyVelocity.snap(new Vec(0.001, 0.001, -0.001)));
    }
}
