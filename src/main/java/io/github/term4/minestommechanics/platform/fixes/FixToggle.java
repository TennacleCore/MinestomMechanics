package io.github.term4.minestommechanics.platform.fixes;

import org.jetbrains.annotations.Nullable;

/** A per-fix config with an on/off switch; {@code null} = unset (off unless a scope enables it). */
public interface FixToggle {

    @Nullable Boolean enabled();
}
