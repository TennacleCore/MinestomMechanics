package io.github.term4.minestommechanics.tracking;

import net.minestom.server.ServerFlag;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/**
 * Block-raytrace landing lookahead: predicts whether a descending entity will reach the ground within
 * the next couple of ticks, so callers do not have to rely on the client {@code onGround} flag (which
 * can be 1 tick late). Kept separate from {@link GroundTracker} (the ground/air timeline) and
 * {@link MovementTracker} (player motion) as its own geometry concern.
 */
public final class LandingPredictor {

    private static final double TPS = ServerFlag.SERVER_TICKS_PER_SECOND;
    // TODO: Make this landing epsilon value configurable in the presets, make sure raytracing is correct
    private static final double LANDING_EPSILON = 0.0001;

    private LandingPredictor() {}

    /**
     * Whether the entity should be treated as "landing soon". Uses a small feet-distance epsilon while
     * falling and a short lookahead so we do not rely on the client onGround flag, which can be 1 tick late.
     */
    public static boolean willLandSoon(Entity entity) {
        if (entity == null) return false;
        Instance instance = entity.getInstance();
        if (instance == null) return entity.isOnGround();
        if (entity.isOnGround()) return true;

        // Player-aware velocity (blocks/tick) so the predictor agrees with the rest of the system;
        // Minestom's entity.getVelocity() does not reflect a client's vertical motion. Non-players
        // keep the blocks/second -> blocks/tick conversion.
        double vyTick = entity instanceof Player
                ? MovementTracker.tracked(entity).y()
                : entity.getVelocity().y() / TPS;
        if (vyTick >= 0) return false;

        double x = entity.getPosition().x();
        double y = entity.getPosition().y();
        double z = entity.getPosition().z();

        if (isSolidWithinEpsilon(instance, x, y, z, LANDING_EPSILON)) return true;

        double nextY = y + vyTick;
        if (isSolidWithinEpsilon(instance, x, nextY, z, LANDING_EPSILON)) return true;

        // 2nd-tick lookahead (vanilla-ish): v = v * scale - gravity
        double vyNext = (vyTick * VelocityContext.GRAVITY_SCALE) - VelocityContext.GRAVITY_PER_TICK;
        double next2Y = nextY + vyNext;
        return isSolidWithinEpsilon(instance, x, next2Y, z, LANDING_EPSILON);
    }

    private static boolean isSolidWithinEpsilon(Instance instance, double x, double y, double z, double epsilon) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(y - epsilon);
        Block block = instance.getBlock(bx, by, bz);
        return block != null && !block.isAir() && block.isSolid();
    }
}
