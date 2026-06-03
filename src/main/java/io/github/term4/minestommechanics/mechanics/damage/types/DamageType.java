package io.github.term4.minestommechanics.mechanics.damage.types;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.NotNull;

/**
 * Base for a damage type registered in the {@code DamageTypeRegistry}. Identifies a source of damage
 * (melee, fire tick, fall, custom, etc.) and maps to a Minestom
 * {@link net.minestom.server.entity.damage.DamageType} for application and death messages.
 *
 * <p>Tunable values are configured externally on the global {@code DamageConfig} (via
 * {@code typeConfigs(...)}), keyed by {@link #key()}. Each type carries an immutable
 * {@link #defaultConfig()} used when the active config has no override for it. The identity (key,
 * name, Minecraft type) is fixed. Each concrete type lives in its own sub-package and extends this class.
 *
 * <p>A one-off type is driven externally (e.g. {@code PlayerAttack} from the attack pipeline);
 * a self-driven/repeating type (e.g. fire, fall) overrides {@link #enable(DamageSystem, MinestomMechanics)}
 * and {@link #disable()} to wire its own triggers and emit snapshots, reading its options from the
 * active {@code DamageConfig}.
 */
public abstract class DamageType {

    private final Key key;
    private final String name;
    private final RegistryKey<net.minestom.server.entity.damage.DamageType> minecraftType;
    private final DamageTypeConfig defaultConfig;

    protected DamageType(Key key, String name,
                         RegistryKey<net.minestom.server.entity.damage.@NotNull DamageType> minecraftType,
                         DamageTypeConfig defaultConfig) {
        this.key = key;
        this.name = name;
        this.minecraftType = minecraftType;
        this.defaultConfig = defaultConfig;
    }

    /** Unique identity for this damage type (registry key). */
    public Key key() { return key; }

    /** Human-readable label for this damage type (debug, death messages). */
    public String name() { return name; }

    /** Minestom damage type used when applying the damage (cosmetic, death messages). */
    public RegistryKey<net.minestom.server.entity.damage.@NotNull DamageType> minecraftType() { return minecraftType; }

    /** Immutable per-type defaults, used when the active {@code DamageConfig} has no override for this type. */
    public @NotNull DamageTypeConfig defaultConfig() { return defaultConfig; }

    /**
     * Called when this type is enabled in the registry. Self-driven types wire triggers here
     * (event listeners, scheduled tasks) and emit snapshots through {@code system}. No-op by default.
     */
    public void enable(DamageSystem system, MinestomMechanics mm) {}

    /** Called when this type is disabled. Tear down anything registered in {@link #enable}. No-op by default. */
    public void disable() {}

    @Override public String toString() { return "DamageType(" + key.asString() + ")"; }
}
