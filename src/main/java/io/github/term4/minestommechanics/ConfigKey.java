package io.github.term4.minestommechanics;

/**
 * Type-safe identifier for a {@link MechanicsProfile} member (a heterogeneous-map key, like Minestom's {@code Tag}).
 * Built-in keys live in {@link MechanicsKeys}; a module declares its own as a static constant. Keys are equal by
 * {@link #id()}, so each must use a unique id (convention {@code "namespace:name"}).
 */
public final class ConfigKey<C> {

    private final String id;

    private ConfigKey(String id) { this.id = id; }

    public static <C> ConfigKey<C> of(String id) { return new ConfigKey<>(id); }

    public String id() { return id; }

    @Override public boolean equals(Object o) { return o instanceof ConfigKey<?> k && id.equals(k.id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "ConfigKey[" + id + "]"; }
}
