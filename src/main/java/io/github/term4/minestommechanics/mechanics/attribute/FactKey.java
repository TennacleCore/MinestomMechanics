package io.github.term4.minestommechanics.mechanics.attribute;

/**
 * A typed key for an optional fact carried on an {@link AttributeConfigResolver.AttributeContext}. The calling domain
 * drops in whatever facts it has (combat -> the attack target, mining -> the block, ...); a conditional source reads the
 * ones it needs and contributes only when its own predicate holds. This is what keeps the attribute layer domain-agnostic
 * - the context type never grows a field per domain; only the runtime entries differ, and they're absent for ambient reads.
 *
 * @param <T> the fact's value type (use a neutral type - {@code Entity}, {@code Block} - to avoid cross-package coupling)
 */
public record FactKey<T>(String name) {
    @Override public String toString() { return "FactKey[" + name + "]"; }
}
