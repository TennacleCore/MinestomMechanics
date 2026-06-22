package io.github.term4.minestommechanics.mechanics.attribute.catalog.effect;

import io.github.term4.minestommechanics.mechanics.attribute.source.Behavior;
import io.github.term4.minestommechanics.mechanics.attribute.source.EntitySource;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;

/**
 * Absorption (potion) - extra "yellow heart" buffer (Player-only; Minestom's {@code damage()} consumes it before health).
 * {@link #LEGACY} is 1.8's additive {@code +4 × level} on apply, removed on expiry. {@link #MODERN} is 26's
 * {@code max(cur, 4×level)} on start, which persists past the effect ({@code onRemove} is a no-op).
 */
public final class Absorption {

    public static final Key KEY = Key.key("minecraft:absorption");

    private Absorption() {}

    public static final Source LEGACY = new EntitySource(KEY) {
        @Override public Behavior behavior() {
            return new Behavior() {
                @Override public void onApply(Entity entity, int level) {
                    if (entity instanceof Player p) p.setAdditionalHearts(p.getAdditionalHearts() + 4f * level);
                }
                @Override public void onRemove(Entity entity, int level) {
                    if (entity instanceof Player p) p.setAdditionalHearts(Math.max(0f, p.getAdditionalHearts() - 4f * level));
                }
            };
        }
    };

    public static final Source MODERN = new EntitySource(KEY) {
        @Override public Behavior behavior() {
            return new Behavior() {
                @Override public void onApply(Entity entity, int level) {
                    if (entity instanceof Player p) p.setAdditionalHearts(Math.max(p.getAdditionalHearts(), 4f * level));
                }
            };
        }
    };
}
