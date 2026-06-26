package io.github.term4.minestommechanics.mechanics.projectile;

import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ProjectileContext;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.Shootable;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable projectile config: a generic {@link #defaults} base plus per-type {@link ProjectileTypeConfig} overrides
 * keyed by type key. Mirrors {@code DamageConfig} - presence in {@link #typeConfigs} enables a type at install.
 * Resolution layers per-type override -&gt; {@link #defaults} -&gt; the type's {@code defaultConfig()} -&gt; hard fallbacks.
 */
public final class ProjectileConfig extends Config<ProjectileContext, ProjectileConfig> {

    /** Generic base config applied to every type unless the type's own entry overrides a knob ({@code null} = none). */
    public final @Nullable ProjectileTypeConfig defaults;
    /** Per-type config overrides, keyed by {@link ProjectileTypeConfig#key()}. */
    public final Map<Key, ProjectileTypeConfig> typeConfigs;
    /** Item launchers (e.g. {@code Bow}) this config installs - the item-&gt;projectile bindings. Read once at install, from the global profile. */
    public final List<Shootable> shootables;

    private ProjectileConfig(Builder b) {
        super(b.subConfig);
        this.defaults = b.defaults;
        this.typeConfigs = Map.copyOf(b.typeConfigs);
        this.shootables = List.copyOf(b.shootables);
    }

    /** The generic base config every type inherits, or {@code null} if none set. */
    public @Nullable ProjectileTypeConfig defaults() { return defaults; }

    /** Per-type config for {@code key}, or {@code null} if none registered. */
    public @Nullable ProjectileTypeConfig typeConfig(Key key) { return typeConfigs.get(key); }

    /** The item launchers this config installs. */
    public List<Shootable> shootables() { return shootables; }

    /** Merges this config over base (this config's generic defaults + per-type entries + shootables layered over base's). */
    public ProjectileConfig fromBase(ProjectileConfig base) {
        Map<Key, ProjectileTypeConfig> merged = new LinkedHashMap<>(base.typeConfigs);
        merged.putAll(typeConfigs);
        List<Shootable> mergedShootables = new ArrayList<>(base.shootables);
        mergedShootables.addAll(shootables);
        ProjectileTypeConfig mergedDefaults = defaults == null ? base.defaults
                : base.defaults == null ? defaults : defaults.fromBase(base.defaults);
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .defaults(mergedDefaults)
                .typeConfigs(merged)
                .shootables(mergedShootables)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable ProjectileConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private Function<ProjectileContext, ProjectileConfig> subConfig;
        private @Nullable ProjectileTypeConfig defaults;
        private final Map<Key, ProjectileTypeConfig> typeConfigs = new LinkedHashMap<>();
        private final List<Shootable> shootables = new ArrayList<>();

        Builder() {}
        Builder(ProjectileConfig c) { subConfig = c.subConfig; defaults = c.defaults; typeConfigs.putAll(c.typeConfigs); shootables.addAll(c.shootables); }

        public Builder subConfig(Function<ProjectileContext, ProjectileConfig> fn) { subConfig = fn; return this; }
        /** Sets the generic base config every type inherits (its knobs apply unless a per-type entry overrides them). */
        public Builder defaults(@Nullable ProjectileTypeConfig generic) { this.defaults = generic; return this; }

        /** Adds per-type config overrides, each keyed by its {@link ProjectileTypeConfig#key()}. */
        public Builder typeConfigs(ProjectileTypeConfig... cfgs) {
            for (ProjectileTypeConfig c : cfgs) typeConfigs.put(c.key(), c);
            return this;
        }

        Builder typeConfigs(Map<Key, ProjectileTypeConfig> cfgs) { typeConfigs.putAll(cfgs); return this; }

        /** Adds item launchers (the item-&gt;projectile bindings, e.g. {@code new Bow()}) this config installs. */
        public Builder shootables(Shootable... s) { for (Shootable sh : s) shootables.add(sh); return this; }

        Builder shootables(List<Shootable> s) { shootables.addAll(s); return this; }

        public ProjectileConfig build() { return new ProjectileConfig(this); }
    }
}
