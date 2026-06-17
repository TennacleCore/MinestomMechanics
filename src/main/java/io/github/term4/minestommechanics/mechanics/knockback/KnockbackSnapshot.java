package io.github.term4.minestommechanics.mechanics.knockback;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A knockback instance to apply.
 *
 * @param target    the entity being knocked back
 * @param melee     whether this is a melee hit - gates melee-only logic (sprint "extra" KB, melee components)
 * @param source    the entity dealing the knockback, or {@code null} (direction from origin/direction)
 * @param origin    explicit knockback origin, or {@code null}
 * @param direction explicit knockback direction override, or {@code null}
 * @param config    the knockback config, or {@code null} to resolve the scope chain (profile -> install config)
 */
public record KnockbackSnapshot(Entity target, boolean melee, @Nullable Entity source,
                                @Nullable Point origin, @Nullable Vec direction, @Nullable KnockbackConfig config) {

    public KnockbackSnapshot withTarget(Entity e) { return new KnockbackSnapshot(e, melee, source, origin, direction, config); }
    public KnockbackSnapshot withMelee(boolean m) { return new KnockbackSnapshot(target, m, source, origin, direction, config); }
    public KnockbackSnapshot withSource(Entity e) { return new KnockbackSnapshot(target, melee, e, origin, direction, config); }
    public KnockbackSnapshot withOrigin(Point p) { return new KnockbackSnapshot(target, melee, source, p, direction, config); }
    public KnockbackSnapshot withDirection(Vec v) { return new KnockbackSnapshot(target, melee, source, origin, v, config); }
    public KnockbackSnapshot withConfig(KnockbackConfig c) { return new KnockbackSnapshot(target, melee, source, origin, direction, c); }

    public Builder toBuilder() { return new Builder(target, melee, source, origin, direction, config); }

    public static final class Builder {
        private Entity target;
        private boolean melee;
        private Entity source;
        private Point origin;
        private Vec direction;
        private KnockbackConfig config;

        Builder(Entity t, boolean m, Entity s, Point o, Vec d, KnockbackConfig cfg) {
            target = t; melee = m; source = s; origin = o; direction = d; config = cfg;
        }

        public Builder target(Entity e) { this.target = e; return this; }
        public Builder melee(boolean m) { this.melee = m; return this; }
        public Builder source(Entity e) { this.source = e; return this; }
        public Builder origin(Point p) { this.origin = p; return this; }
        public Builder direction(Vec v) { this.direction = v; return this; }
        public Builder config(KnockbackConfig c) { this.config = c; return this; }

        public KnockbackSnapshot build() {
            return new KnockbackSnapshot(target, melee, source, origin, direction, config);
        }
    }
}
