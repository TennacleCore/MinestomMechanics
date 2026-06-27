package io.github.term4.minestommechanics;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A bundle of mechanics configs assignable to a scope (player / instance / global) via {@link MechanicsProfiles}.
 * Backed by a typed-key map: members are keyed by {@link ConfigKey} (built-ins in {@link MechanicsKeys}) and read with
 * {@link #get}. A partial profile (e.g. knockback only) overrides just that member and lets the rest fall through to the
 * next scope. Compose with the builder: {@code MechanicsProfile.builder().set(MechanicsKeys.ATTACK, cfg).build()}.
 */
public final class MechanicsProfile {

    private final Map<ConfigKey<?>, Object> values;

    private MechanicsProfile(Map<ConfigKey<?>, Object> values) { this.values = values; }

    /** The config bound to {@code key} for this profile, or {@code null} if this profile does not set it. */
    @SuppressWarnings("unchecked")
    public <C> @Nullable C get(ConfigKey<C> key) { return (C) values.get(key); }

    public Builder toBuilder() { return new Builder(values); }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<ConfigKey<?>, Object> values;

        Builder() { this.values = new HashMap<>(); }
        Builder(Map<ConfigKey<?>, Object> base) { this.values = new HashMap<>(base); }

        /** Sets (or with {@code null} clears) the member bound to {@code key}. */
        public <C> Builder set(ConfigKey<C> key, @Nullable C value) {
            if (value == null) values.remove(key); else values.put(key, value);
            return this;
        }

        public MechanicsProfile build() { return new MechanicsProfile(Map.copyOf(values)); }
    }
}
