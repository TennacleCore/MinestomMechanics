package test.presets.hypixel;

import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import test.presets.customItems.PrimedTnt;

/** Hypixel TNT: fuse 50 (~2.5s, vanilla is 80), vanilla {@code +height/16} detonation, the HYPIXEL wire shape. */
public final class Tnt {

    private Tnt() {}

    public static final PrimedTnt.Config CONFIG = new PrimedTnt.Config(50, 4.0f, false, PrimedTnt.Wire.HYPIXEL);

    public static PrimedTnt spawn(ExplosionSystem explosion, Instance instance, Point tntBlock) {
        return PrimedTnt.spawn(explosion, instance, tntBlock, CONFIG);
    }
}
