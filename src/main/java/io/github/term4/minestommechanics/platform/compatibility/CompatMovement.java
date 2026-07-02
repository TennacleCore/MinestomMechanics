package io.github.term4.minestommechanics.platform.compatibility;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.platform.player.OptimizedPlayer;
import io.github.term4.minestommechanics.tracking.ClientInfoTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.util.BlockContact;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code PlayerMoveEvent} home for the compat movement restrictions (all <em>modern-clients-only</em> for the speed
 * ones - a 1.8 client already moves at 1.8 speed). The knockback i-frame window is always skipped so a hit still launches.
 *
 * <p><b>{@code restrictMovement}</b> - the server-authoritative half of pose disabling: rejects a move that newly puts the
 * player's pose-aware {@link Player#getBoundingBox()} hitbox into a solid block it wasn't already overlapping (a stuck
 * player can still slide out), set back without an absolute-view snap (camera kept; the resolved crawl-drag fix).
 *
 * <p><b>{@code restrictSprintSneak}/{@code restrictSprintUse}</b> - while the client believes it's sprinting (tracked, not
 * the forced server state) and sneaking / using an item, {@code setSprinting(false)} strips the server's {@code +0.3}
 * sprint speed modifier + clears the sprint flag (1.8 can't sprint-sneak/use; also removes the sprint knockback). Restored
 * once they stop. Needs the {@code SprintTracker}.
 *
 * <p><b>{@code restrictSwimSpeed}</b> - while swim-posed (sprinting + feet in water, the {@code ClientEye} proxy), a reduced
 * velocity ({@link EntityVelocityPacket}) is spammed to the client each move (Hypixel's approach), capping the swim in all
 * directions (vertical damped harder, since holding space re-adds swim-up input). Gated on sprint so plain bobbing/floating
 * in water stays natural - only the fast sprint-swim is capped.
 *
 * <p>Installed once when the player provider is on; inert unless the player's config enables a restriction. Minemen behaviour.
 */
public final class CompatMovement {

    /** Swim HORIZONTAL velocity divisor - the spammed velocity is the observed move scaled by {@code 1/SWIM_FACTOR}. Higher = slower. Tune in-game. */
    private static final double SWIM_FACTOR = 1.25;
    /** Swim VERTICAL divisor - stronger than horizontal, since holding space re-adds swim-up input each tick. Higher = slower up/down. Tune in-game. */
    private static final double SWIM_VERTICAL_FACTOR = 3.0;
    /** Below this squared move, the dampen is a no-op (look-only). */
    private static final double MIN_MOVE_SQ = 1.0e-8;

    private CompatMovement() {}

    /** Installs the move-restriction listener. Inert unless a player enables a {@code restrictMovement}/{@code restrictSprint*}/{@code restrictSwimSpeed} knob. */
    public static void install(MinestomMechanics mm) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:compat-movement", EventFilter.PLAYER);
        // resolve the trackers lazily - install runs before they're created in init()
        node.addListener(PlayerMoveEvent.class, e -> onMove(e, mm.clientInfo(), mm.sprintTracker()));
        mm.install(node);
    }

    private static void onMove(PlayerMoveEvent event, ClientInfoTracker clientInfo, @Nullable SprintTracker sprintTracker) {
        Player player = event.getPlayer();
        if (!(player instanceof OptimizedPlayer op)) return;
        CompatState c = op.compat();
        // The collision revert exists only to stop the squeeze-to-fit crawl; a DISABLE_CRAWL_POSE Animatium client already
        // prevents it natively (its crawl box = standing box), so skip the server revert for it - otherwise the revert
        // false-positives on its fast 1.8 fluid descent into a pool (box briefly overshoots the floor) and bounces it off water.
        boolean collision = c.restrictMovement() && !c.handlesNatively(AnimatiumFeature.DISABLE_CRAWL_POSE);
        boolean speed = c.anySpeedRestriction();
        if (!collision && !speed) return;
        // spectators noclip; a passenger's collision is the vehicle's - leave both alone
        if (player.getGameMode() == GameMode.SPECTATOR || player.getVehicle() != null) return;
        Pos from = player.getPosition();
        Pos to = event.getNewPosition();
        if (to.samePoint(from)) return; // look-only change: the hitbox didn't move
        Instance instance = player.getInstance();
        if (instance == null) return;

        // Collision restriction: reject a move that newly enters a block (revert fully to from, kill momentum).
        if (collision && entersNewCollision(instance, player.getBoundingBox(), from, to)) {
            boolean rotated = to.yaw() != from.yaw() || to.pitch() != from.pitch();
            // a non-rotating revert-to-from has no view delta to trip the early-out, so nudge the yaw one ULP (invisible)
            float yaw = rotated ? to.yaw() : Math.nextUp(from.yaw());
            Pos pos = from.withView(yaw, to.pitch());
            player.refreshPosition(pos, false, false);
            player.sendPacket(new PlayerPositionAndLookPacket(-1, pos, Vec.ZERO, 0f, 0f, RelativeFlags.VIEW));
            return;
        }

        // Speed restrictions - modern clients only; skip the knockback i-frame window (a hit's velocity would be clamped away).
        if (!speed || clientInfo.isLegacy(player) || DamageSystem.isInvulnerableToDamage(player)) return;
        // swim cap only when swim-POSED (sprint + in water, the ClientEye proxy) - plain in-water stays natural (bobbing/floating).
        // Skipped for Animatium clients running old_fluid_physics: they already move at 1.8 speed natively, and the dampen would
        // fight the 1.8 current (e.g. cap the falling-water down-push). Non-Animatium modern clients keep the dampen.
        if (c.restrictSwimSpeed() && player.isSprinting() && inWater(player, instance)
                && !c.handlesNatively(AnimatiumFeature.OLD_FLUID_PHYSICS)) {
            dampenSwim(player, from, to);
            return;
        }
        restrictSprint(player, c, sprintTracker);
    }

    /**
     * Sneak/use 1.8 parity via the sprint state itself: while the CLIENT believes it's sprinting (so we still act after the
     * forced-off state), {@code setSprinting(false)} drops the server's {@code +0.3} sprint speed modifier + the sprint flag
     * when sneaking/using, and restores it otherwise. Tracked sprint (not {@code isSprinting()}, which we force off) keeps it
     * accurate; {@code setSprinting} doesn't fire the sprint events, so the tracker stays clean.
     */
    private static void restrictSprint(Player player, CompatState c, @Nullable SprintTracker sprintTracker) {
        if (!SprintTracker.isClientSprinting(sprintTracker, player)) return; // client isn't sprinting -> nothing to strip/restore
        // skip the state Animatium fixes natively (it forces sprint off client-side, no rubber-band)
        boolean strip = (c.restrictSprintSneak() && player.isSneaking() && !c.handlesNatively(AnimatiumFeature.FIX_SPRINT_SNEAKING))
                || (c.restrictSprintUse() && player.isUsingItem() && !c.handlesNatively(AnimatiumFeature.FIX_SPRINT_ITEM_USE));
        if (strip) {
            if (player.isSprinting()) { player.setSprinting(false); c.setSprintStripped(true); }
        } else if (c.sprintStripped()) {
            // restore ONLY the sprint WE stripped for sneak/use - a combat sprint-reset didn't set the flag, so the 1.8 w-tap requirement is kept
            if (!player.isSprinting()) player.setSprinting(true);
            c.setSprintStripped(false);
        }
    }

    /** Hypixel-style swim cap: spam a reduced velocity (blocks/tick) to the client each move, damping horizontal AND (harder) vertical swim - smoother than a position setback. */
    private static void dampenSwim(Player player, Pos from, Pos to) {
        double dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        if (dx * dx + dy * dy + dz * dz <= MIN_MOVE_SQ) return;
        Vec velocity = new Vec(dx / SWIM_FACTOR, dy / SWIM_VERTICAL_FACTOR, dz / SWIM_FACTOR);
        player.sendPacket(new EntityVelocityPacket(player.getEntityId(), velocity));
    }

    /** Whether the player's feet are in water (position-based, like {@code ClientEye.inWater}); 1.8 slows all in-water movement, so this also covers surface swimming + wading. */
    private static boolean inWater(Player p, Instance instance) {
        Pos pos = p.getPosition();
        try {
            Block b = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.Getter.Condition.TYPE);
            return b != null && b.compare(Block.WATER);
        } catch (Exception ignored) {
            return false; // unloaded chunk
        }
    }

    /**
     * Whether the move newly puts {@code box} into a solid block it wasn't already overlapping at {@code from}. A block the
     * box already overlaps at {@code from} is ignored (so a player stuck in a block can slide out); only freshly entering a
     * block - crawling in from open ground or along a tunnel - is caught. Normal (non-colliding) movement is never affected.
     */
    private static boolean entersNewCollision(Instance instance, BoundingBox box, Pos from, Pos to) {
        var blocks = box.getBlocks(to);
        while (blocks.hasNext()) {
            var bp = blocks.next();
            Block block;
            try {
                block = instance.getBlock(bp.blockX(), bp.blockY(), bp.blockZ(), Block.Getter.Condition.TYPE);
            } catch (Exception ignored) {
                continue; // unloaded chunk -> no collision
            }
            // passable (non-blocksMotion) blocks - ladder/vine/plant/carpet/... - are meant to be occupied, never obstacles
            // (same test as the self-placement fix); vanilla ladders carry a wall-side slab, which was setting climb moves back.
            // scaffolding's dynamic shape Minestom doesn't model, so skip it too.
            if (block == null || BlockContact.isPassable(block) || block.id() == Block.SCAFFOLDING.id()) continue;
            var shape = block.registry().collisionShape();
            if (!shape.intersectBox(to.sub(bp.blockX(), bp.blockY(), bp.blockZ()), box)) continue;  // not colliding at the destination
            if (shape.intersectBox(from.sub(bp.blockX(), bp.blockY(), bp.blockZ()), box)) continue;  // already inside at the source -> not new (allow sliding out)
            return true;                                                                             // newly entered a solid block
        }
        return false;
    }
}
