package io.github.term4.minestommechanics.world;

import io.github.term4.minestommechanics.MinestomMechanics;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockSoundType;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Block place + footstep sounds Minestom doesn't emit. Audience is version-dependent: a modern doer predicts them
 * client-side (vanilla excludes it from the broadcast), but a 1.8 client cannot - its {@code RenderGlobal.playSound}
 * sinks are empty stubs and 1.8 servers included the doer - so legacy doers get the packet, modern doers don't.
 */
public final class WorldSounds {

    private WorldSounds() {}

    // vanilla step cadence (Entity.moveDist/nextStep): accumulate horizontalDistance*0.6, step past nextStep = floor(moveDist)+1
    private static final Tag<Float> MOVE_DIST = Tag.Transient("mm:step-move-dist");
    private static final Tag<Float> NEXT_STEP = Tag.Transient("mm:step-next");

    public static void install(MinestomMechanics mm) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:world-sounds", EventFilter.PLAYER);
        node.addListener(PlayerBlockPlaceEvent.class, e -> onPlace(mm, e));
        node.addListener(PlayerMoveEvent.class, e -> onMove(mm, e));
        mm.install(node);
    }

    private static void onPlace(MinestomMechanics mm, PlayerBlockPlaceEvent e) {
        if (e.isCancelled()) return; // a compat rule (reach / air) may cancel the placement
        BlockSoundType st = e.getBlock().registry().getBlockSoundType();
        if (st == null || st.placeSound() == null) return;
        // vanilla BlockItem.place: volume (v+1)/2, pitch p*0.8, BLOCKS category
        emit(mm, e.getPlayer(), st.placeSound(), Sound.Source.BLOCK, e.getBlockPosition(), (st.volume() + 1.0f) / 2.0f, st.pitch() * 0.8f);
    }

    private static void onMove(MinestomMechanics mm, PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!p.isOnGround()) return; // steps only while grounded
        Pos from = p.getPosition(); // pre-move; getNewPosition is the target
        Point to = e.getNewPosition();
        double dx = to.x() - from.x(), dz = to.z() - from.z();
        float moved = (float) Math.sqrt(dx * dx + dz * dz) * 0.6f;
        if (moved <= 0) return;
        Float prev = p.getTag(MOVE_DIST);
        float moveDist = (prev != null ? prev : 0f) + moved;
        Float next = p.getTag(NEXT_STEP);
        float nextStep = next != null ? next : 1.0f;
        if (moveDist > nextStep) {
            step(mm, p, to);
            nextStep = (int) moveDist + 1;
        }
        p.setTag(MOVE_DIST, moveDist);
        p.setTag(NEXT_STEP, nextStep);
    }

    private static void step(MinestomMechanics mm, Player p, Point at) {
        if (p.getInstance() == null) return;
        // viewed world: on a virtual world the feet rest on the OVERLAY block, not the base map's
        Block below = MechanicsWorld.viewed(p).getBlock(at.withY(at.y() - 0.2));
        if (below.isAir()) return;
        BlockSoundType st = below.registry().getBlockSoundType();
        if (st == null || st.stepSound() == null) return;
        // vanilla Entity.playStepSound: volume soundType.volume * 0.15, pitch soundType.pitch
        emit(mm, p, st.stepSound(), Sound.Source.PLAYER, at, st.volume() * 0.15f, st.pitch());
    }

    /** Viewers always; the doer too only on legacy clients (1.8 cannot self-play - modern predicts, would double). */
    private static void emit(MinestomMechanics mm, Player doer, SoundEvent sound, Sound.Source src, Point at, float vol, float pitch) {
        SoundEffectPacket packet = new SoundEffectPacket(sound, src, at, vol, pitch, ThreadLocalRandom.current().nextLong());
        doer.sendPacketToViewers(packet);
        if (mm.clientInfo() != null && mm.clientInfo().isLegacy(doer)) {
            doer.sendPacket(packet);
        }
    }
}
