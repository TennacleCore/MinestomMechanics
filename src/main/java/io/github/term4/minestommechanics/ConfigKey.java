package io.github.term4.minestommechanics;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Type-safe identifier for a {@link MechanicsProfile} member. Built-in keys live in {@link MechanicsKeys}; a module
 * declares its own as a static constant. Keys are equal by {@link #id()}, so each id must be unique (convention
 * {@code "namespace:name"}) - two modules claiming one id with different types fail fast at key creation.
 */
public final class ConfigKey<C> {

    private static final ConcurrentHashMap<String, Class<?>> TYPES = new ConcurrentHashMap<>();

    private final String id;
    private final Class<C> type;

    private ConfigKey(String id, Class<C> type) { this.id = id; this.type = type; }

    public static <C> ConfigKey<C> of(String id, Class<C> type) {
        Class<?> previous = TYPES.putIfAbsent(id, type);
        if (previous != null && previous != type) {
            throw new IllegalStateException("ConfigKey id collision: \"" + id + "\" already registered as "
                    + previous.getName() + ", now requested as " + type.getName());
        }
        return new ConfigKey<>(id, type);
    }

    public String id() { return id; }
    public Class<C> type() { return type; }

    @Override public boolean equals(Object o) { return o instanceof ConfigKey<?> k && id.equals(k.id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "ConfigKey[" + id + "]"; }
}
