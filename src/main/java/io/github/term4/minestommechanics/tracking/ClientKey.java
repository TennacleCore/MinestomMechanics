package io.github.term4.minestommechanics.tracking;

/**
 * Type-safe identifier for a custom value on a player's {@link ClientProfile} (the same heterogeneous-key pattern as
 * {@code ConfigKey}). An end user declares one as a static constant and reads/writes it via {@link ClientProfile#get} /
 * {@link ClientProfile#set}. Keys are equal by {@link #id()}, so each must use a unique id (convention {@code "namespace:name"}).
 */
public final class ClientKey<T> {

    private final String id;

    private ClientKey(String id) { this.id = id; }

    public static <T> ClientKey<T> of(String id) { return new ClientKey<>(id); }

    public String id() { return id; }

    @Override public boolean equals(Object o) { return o instanceof ClientKey<?> k && id.equals(k.id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "ClientKey[" + id + "]"; }
}
