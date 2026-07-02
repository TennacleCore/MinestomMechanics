package io.github.term4.minestommechanics.config;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Shared base for per-type configs (damage types, projectile types): a keyed {@link Config}, the key identifying which
 * registry type the entry configures.
 */
public abstract class TypeConfig<CTX, SELF extends TypeConfig<CTX, SELF>> extends Config<CTX, SELF> {

    private final Key key;

    protected TypeConfig(Key key, @Nullable Function<CTX, SELF> subConfig) {
        super(subConfig);
        this.key = key;
    }

    /** Identity of the type this config applies to (the registry key). */
    public final Key key() { return key; }
}
