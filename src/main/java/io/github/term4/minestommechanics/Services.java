package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.mechanics.attack.AttackSystem;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeSystem;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingSystem;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableSystem;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.durability.DurabilitySystem;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import io.github.term4.minestommechanics.mechanics.hunger.HungerSystem;
import io.github.term4.minestommechanics.platform.fixes.FixesSystem;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import org.jetbrains.annotations.Nullable;

/** Access to the systems registered on MinestomMechanics. */
public record Services(MinestomMechanics mm) {

    // Accessors read the live registry, so lookups are lazy per hit and install order doesn't matter; null = not installed.
    public @Nullable SprintTracker sprintTracker() { return mm.sprintTracker(); }
    public @Nullable MotionTracker motionTracker() { return mm.motionTracker(); }
    public @Nullable AttackSystem attack() { return mm.module(AttackSystem.class); }
    public @Nullable KnockbackSystem knockback() { return mm.module(KnockbackSystem.class); }
    public @Nullable DamageSystem damage() { return mm.module(DamageSystem.class); }
    public @Nullable ProjectileSystem projectiles() { return mm.module(ProjectileSystem.class); }
    public @Nullable ExplosionSystem explosion() { return mm.module(ExplosionSystem.class); }
    public @Nullable FixesSystem fixes() { return mm.module(FixesSystem.class); }
    public @Nullable AttributeSystem attributes() { return mm.module(AttributeSystem.class); }
    public @Nullable DurabilitySystem durability() { return mm.module(DurabilitySystem.class); }
    public @Nullable HungerSystem hunger() { return mm.module(HungerSystem.class); }
    public @Nullable ConsumableSystem consumables() { return mm.module(ConsumableSystem.class); }
    public @Nullable BlockingSystem blocking() { return mm.module(BlockingSystem.class); }
    /** Scoped config profiles (player / instance / global). */
    public MechanicsProfiles profiles() { return mm.profiles(); }

}
