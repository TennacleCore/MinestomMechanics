package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.tracking.ClientInfoTracker;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/**
 * The eye height a client perceives itself at, by protocol - value (a) of the compat eye model (value (b), the server eye,
 * is {@code OptimizedPlayer.getEyeHeight()} / {@link CompatState#eyeHeight}). 1.8: {@link #STANDING} / {@link #LEGACY_SNEAKING};
 * modern adds {@link #MODERN_CROUCH} crouch + {@link #POSE_EYE} crawl/swim/elytra. The eye constants are shared here so both
 * value tracks read one source.
 * {@link #candidates} = every eye the client could have (reach takes the MIN, so the pose needn't be pinned down);
 * {@link #perceived} = one value from the client's believed pose ({@code OptimizedPlayer.compat().clientPose}) + a sprint+water swim check.
 */
public final class ClientEye {

    private ClientEye() {}

    /** Standing eye - shared by 1.8 and modern (also Minestom's default). */
    public static final double STANDING = 1.62;
    /** 1.8 sneaking eye (modern crouch is lower); also the legacy server-eye preset, value (b). */
    public static final double LEGACY_SNEAKING = 1.54;
    /** Modern crouch eye. */
    public static final double MODERN_CROUCH = 1.27;
    /** Modern crawl / swim / elytra / riptide eye. */
    public static final double POSE_EYE = 0.4;

    /** Every eye height {@code protocol}'s client could believe it has (unknown protocol = modern). */
    public static double[] candidates(int protocol) {
        return ClientInfoTracker.isLegacy(protocol)
                ? new double[]{STANDING, LEGACY_SNEAKING}
                : new double[]{STANDING, MODERN_CROUCH, POSE_EYE};
    }

    /** Single best-guess client-perceived eye for {@code player} from its protocol + the pose the client believes it's in. */
    public static double perceived(Player player, int protocol) {
        if (ClientInfoTracker.isLegacy(protocol)) return player.isSneaking() ? LEGACY_SNEAKING : STANDING; // 1.8 has no lower pose
        // the client's BELIEVED pose: the disabled pose we intercepted (crawl=SWIMMING) even though the server pose is forced to STANDING
        EntityPose pose = player instanceof OptimizedPlayer op ? op.compat().clientPose(op.getPose()) : player.getPose();
        return switch (pose) {
            case FALL_FLYING, SWIMMING, SPIN_ATTACK -> POSE_EYE; // crawl / elytra / riptide
            case SNEAKING -> MODERN_CROUCH;
            // STANDING: Minestom never computes the swim pose (no client signal), so detect sprint-swimming directly
            default -> player.isSprinting() && inWater(player) ? POSE_EYE : STANDING;
        };
    }

    private static boolean inWater(Player player) {
        Instance instance = player.getInstance();
        if (instance == null) return false;
        Pos p = player.getPosition();
        try {
            Block block = instance.getBlock(p.blockX(), p.blockY(), p.blockZ(), Block.Getter.Condition.TYPE);
            return block != null && block.compare(Block.WATER);
        } catch (Exception ignored) {
            return false; // unloaded chunk
        }
    }
}
