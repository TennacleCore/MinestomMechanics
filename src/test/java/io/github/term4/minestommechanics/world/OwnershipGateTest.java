package io.github.term4.minestommechanics.world;

import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.vri.DroppedItemEntity;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tick ownership gate: a covered entity ticked by a thread that is not its owner's must no-op entirely
 * (dispatcher eviction is queued, so a stalled server can drive a just-adopted entity late).
 */
class OwnershipGateTest extends HeadlessServerTest {

    @Test
    void staleDriversNeverRunACoveredEntitysTick() {
        DroppedItemEntity drop = new DroppedItemEntity(ItemStack.of(Material.STONE), DroppedItemEntity.Model.LEGACY);
        drop.setInstance(instance, new Pos(0.5, 70, 0.5)).join();
        try {
            MechanicsWorld.resolver(new MechanicsWorld.Resolver() {
                @Override public MechanicsWorld resolve(Entity e) { return e.getTag(MechanicsWorld.ENTITY_TAG); }
                @Override public boolean ownsCurrentTick(Entity e) { return e != drop; }
            });
            long before = drop.getAliveTicks();
            drop.tick(System.currentTimeMillis());
            assertEquals(before, drop.getAliveTicks(), "a stale driver's tick must not run");

            MechanicsWorld.resolver(MechanicsWorld.Resolver.DEFAULT);
            drop.tick(System.currentTimeMillis());
            assertEquals(before + 1, drop.getAliveTicks(), "the owner's tick runs");
        } finally {
            MechanicsWorld.resolver(MechanicsWorld.Resolver.DEFAULT);
            drop.remove();
        }
    }
}
