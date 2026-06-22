package io.github.term4.minestommechanics.mechanics.attribute;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

/**
 * A gameplay attribute a {@link Source} can modify - either a {@link Vanilla} one backed by a Minestom attribute
 * (client-facing; Minestom does its own base+modifier math) or a {@link Custom} one we resolve ourselves (a key, e.g. a
 * pipeline term like melee flat-add). One uniform handle so a modifier can target anything.
 *
 * <p>Inside this package the Minestom type is referenced fully-qualified to avoid the clash with this {@code Attribute}.
 */
public sealed interface Attribute permits Attribute.Vanilla, Attribute.Custom {

    Key key();

    /** A Minestom-backed vanilla attribute. */
    record Vanilla(net.minestom.server.entity.attribute.Attribute handle) implements Attribute {
        @Override public Key key() { return handle.key(); }
    }

    /** A custom attribute we own; not known to Minestom. */
    record Custom(Key key) implements Attribute {}

    /** The wrapped Minestom attribute, or {@code null} for a {@link Custom} one. */
    default @Nullable net.minestom.server.entity.attribute.Attribute handle() {
        return this instanceof Vanilla v ? v.handle() : null;
    }

    static Attribute of(net.minestom.server.entity.attribute.Attribute handle) { return new Vanilla(handle); }

    static Attribute custom(String id) { return new Custom(Key.key(id)); }

    // Vanilla (Minestom-backed)
    Attribute ATTACK_DAMAGE = of(net.minestom.server.entity.attribute.Attribute.ATTACK_DAMAGE);
    Attribute ATTACK_SPEED = of(net.minestom.server.entity.attribute.Attribute.ATTACK_SPEED);
    Attribute KNOCKBACK_RESISTANCE = of(net.minestom.server.entity.attribute.Attribute.KNOCKBACK_RESISTANCE);
    Attribute MAX_HEALTH = of(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH);
    Attribute MOVEMENT_SPEED = of(net.minestom.server.entity.attribute.Attribute.MOVEMENT_SPEED);
    Attribute SAFE_FALL_DISTANCE = of(net.minestom.server.entity.attribute.Attribute.SAFE_FALL_DISTANCE);
    Attribute MINING_EFFICIENCY = of(net.minestom.server.entity.attribute.Attribute.MINING_EFFICIENCY);
    Attribute SUBMERGED_MINING_SPEED = of(net.minestom.server.entity.attribute.Attribute.SUBMERGED_MINING_SPEED);
    Attribute WATER_MOVEMENT_EFFICIENCY = of(net.minestom.server.entity.attribute.Attribute.WATER_MOVEMENT_EFFICIENCY);

    // Custom (ours): server-authored pipeline terms
    Attribute MELEE_FLAT_ADD = custom("mm:melee_flat_add"); // flat melee damage added after crit (Sharpness etc.)
}
