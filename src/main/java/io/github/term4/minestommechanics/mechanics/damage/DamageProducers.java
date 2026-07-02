package io.github.term4.minestommechanics.mechanics.damage;

import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;

/** Shared guards for self-driven environmental damage producers. */
public final class DamageProducers {

    private DamageProducers() {}

    /** The dead, creative, spectator, and flying take no environmental damage (vanilla). Dead is key for fall: a player who
     *  dies mid-fall keeps sending move packets on the death screen, so without this they accumulate fall distance that a
     *  quick respawn then lands as phantom damage. */
    public static boolean exempt(LivingEntity living) {
        if (living.isDead()) return true;
        if (!(living instanceof Player p)) return false;
        GameMode gm = p.getGameMode();
        return p.isFlying() || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }
}
