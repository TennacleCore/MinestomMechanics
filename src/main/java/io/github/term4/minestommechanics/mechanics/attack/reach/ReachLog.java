package io.github.term4.minestommechanics.mechanics.attack.reach;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.api.event.PreAttackEvent;
import io.github.term4.minestommechanics.platform.compatibility.ClientEye;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.tracking.ClientInfoTracker;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

/**
 * Simple reach guard: on a detected hit ({@link PreAttackEvent}) it cancels the hit when the attacker's optimal
 * (nearest-point) reach to the target exceeds {@code maxReach}. That distance uses the attacker's client-perceived eye
 * height (1.8 vs modern, by protocol) and is rotation-independent and a LOWER BOUND on true reach, so a hit past it is
 * geometrically impossible regardless of aim - safe to drop synchronously. Keep {@code maxReach} generous (only kill
 * clearly-impossible hits). The precise lag-compensated reach analysis lives on the {@code anticheat} branch.
 */
public final class ReachLog {

    private ReachLog() {}

    /** Installs the guard: cancels a hit whose lower-bound reach exceeds {@code maxReach}. */
    public static void install(MinestomMechanics mm, double maxReach) {
        ClientInfoTracker clientInfo = mm.clientInfo();
        EventNode<@NotNull Event> node = EventNode.all("mm:reach-guard");
        node.addListener(PreAttackEvent.class, e -> onPreAttack(e, clientInfo, maxReach));
        mm.install(node);
    }

    private static void onPreAttack(PreAttackEvent e, ClientInfoTracker clientInfo, double maxReach) {
        if (e.target() == null || !(e.attacker() instanceof Player attacker)) return; // player-vs-entity hits only
        Entity target = e.target();

        int protocol = clientInfo.getProtocol(attacker);
        double[] eyes = ClientEye.candidates(protocol); // client-perceived eye candidates; min taken so swim/crawl needn't be detected
        // attack-box growth this attacker's client applies to targets: 1.8 grows 0.1 natively; a modern client grows by its
        // compat-stamped attack_range margin - read from the same CompatState as the attack-box feature, so the two don't diverge
        double nativePad = ClientInfoTracker.isLegacy(protocol) ? 0.1 : 0.0;
        Float margin = attacker instanceof OptimizedPlayer op ? op.compat().attackHitboxMargin() : null;
        double pad = Math.max(nativePad, margin != null ? margin : 0.0);

        BoundingBox bb = target.getBoundingBox();
        double[][] boxes = {aabb(bb, target.getPosition(), pad), aabb(bb, target.getPreviousPosition(), pad)};
        double optimal = minOptimal(attacker.getPosition(), boxes, eyes);
        boolean cancel = optimal > maxReach; // optimal is a rotation-independent lower bound; past maxReach the hit is geometrically impossible
        if (cancel) e.setCancelled(true);
        // log both eye-height tracks: clientEye = what the client believes (value a), serverEye = what the server treats it as (value b)
        System.out.printf("[mm][reach] %d -> %d: clientEye=%.2f serverEye=%.2f optimal=%.3f%s%n",
                attacker.getEntityId(), target.getEntityId(), ClientEye.perceived(attacker, protocol), attacker.getEyeHeight(), optimal, cancel ? " CANCEL" : "");
    }

    /** Minimum nearest-point distance from the eye to any candidate target box, over the eye-height candidates (a lower bound on reach). */
    private static double minOptimal(Pos eyeBase, double[][] boxes, double[] eyes) {
        double best = Double.POSITIVE_INFINITY;
        for (double eye : eyes) {
            double ex = eyeBase.x(), ey = eyeBase.y() + eye, ez = eyeBase.z();
            for (double[] b : boxes) {
                double nx = clamp(ex, b[0], b[3]), ny = clamp(ey, b[1], b[4]), nz = clamp(ez, b[2], b[5]);
                best = Math.min(best, Math.sqrt(sq(ex - nx) + sq(ey - ny) + sq(ez - nz)));
            }
        }
        return best;
    }

    /** World AABB {@code {minX,minY,minZ,maxX,maxY,maxZ}} of {@code bb} placed at {@code pos}, padded {@code pad} every side. */
    private static double[] aabb(BoundingBox bb, Pos pos, double pad) {
        return new double[]{
                pos.x() + bb.minX() - pad, pos.y() + bb.minY() - pad, pos.z() + bb.minZ() - pad,
                pos.x() + bb.maxX() + pad, pos.y() + bb.maxY() + pad, pos.z() + bb.maxZ() + pad};
    }

    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
    private static double sq(double v) { return v * v; }
}
