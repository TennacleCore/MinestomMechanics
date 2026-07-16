package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.world.MechanicsWorld;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;

/** Shared guards for self-driven environmental damage producers. */
public final class DamageProducers {

    private DamageProducers() {}

    /** The dead, creative, spectator, flying, and view-only observers take no environmental damage (vanilla + viewing
     *  is not being). Dead is key for fall: a player who dies mid-fall keeps sending move packets on the death screen,
     *  so without this they accumulate fall distance that a quick respawn then lands as phantom damage. */
    public static boolean exempt(LivingEntity living) {
        if (living.isDead()) return true;
        if (!(living instanceof Player p)) return false;
        GameMode gm = p.getGameMode();
        if (p.isFlying() || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        // producers read VIEWED blocks; an observer's damage would come from a world they only watch
        return MechanicsWorld.viewed(p) != MechanicsWorld.of(p);
    }
}
