package io.github.term4.minestommechanics.tracking;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Type-safe identifier for a custom value on a player's {@link ClientProfile}. Keys are equal by {@link #id()}, so a
 * re-created key matches; ids must be unique ({@code "namespace:name"}) and a same-id different-type collision throws.
 */
public final class ClientKey<T> {

    private static final ConcurrentHashMap<String, Class<?>> TYPES = new ConcurrentHashMap<>();

    private final String id;
    private final Class<T> type;

    private ClientKey(String id, Class<T> type) { this.id = id; this.type = type; }

    public static <T> ClientKey<T> of(String id, Class<T> type) {
        Class<?> previous = TYPES.putIfAbsent(id, type);
        if (previous != null && previous != type) {
            throw new IllegalStateException("ClientKey id collision: \"" + id + "\" already registered as "
                    + previous.getName() + ", now requested as " + type.getName());
        }
        return new ClientKey<>(id, type);
    }

    public String id() { return id; }
    public Class<T> type() { return type; }

    @Override public boolean equals(Object o) { return o instanceof ClientKey<?> k && id.equals(k.id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "ClientKey[" + id + "]"; }
}
