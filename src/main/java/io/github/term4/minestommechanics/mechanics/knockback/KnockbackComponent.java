package io.github.term4.minestommechanics.mechanics.knockback;

import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.Nullable;

/**
 * A pluggable, additive knockback term. After the base/extra/friction/range pipeline has produced the final
 * knockback vector, each configured component contributes a velocity (blocks/tick, i.e. the client-decoded packet
 * units) that is summed on top - independently on x, y and z. Returning {@code null} contributes nothing for that
 * hit, so a component fully self-gates (decides whether <em>and</em> how it applies) from the
 * {@link KnockbackConfigResolver.KnockbackContext} (attacker/target, cause, services, ...).
 *
 * <p>Unlike the linear per-axis friction term, a component can apply non-linear logic (e.g. snapping to the
 * dominant cardinal axis), which is what the base pipeline cannot express.
 */
@FunctionalInterface
public interface KnockbackComponent {

    /** Velocity (blocks/tick) to add on top of the computed knockback, or {@code null} to contribute nothing. */
    @Nullable Vec contribute(KnockbackConfigResolver.KnockbackContext ctx);
}
