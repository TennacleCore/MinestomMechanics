package io.github.term4.minestommechanics.mechanics;

import io.github.term4.minestommechanics.mechanics.attribute.AttributeConfig;
import io.github.term4.minestommechanics.mechanics.attribute.source.Source;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that both preset catalogs register the requested potions. LEGACY ({@link Vanilla18#attributes()}) carries every
 * effect whose 1.8 form is attribute/behavior-based; MODERN ({@link Vanilla#attributes()}) additionally carries Haste /
 * Mining Fatigue / Jump Boost (attribute-based only in 26).
 */
class PresetAttributesTest {

    private static Set<Key> keys(AttributeConfig cfg) {
        Set<Key> out = new HashSet<>();
        for (Source s : cfg.sources()) out.add(s.key());
        return out;
    }

    private static Key k(String id) { return Key.key("minecraft:" + id); }

    @Test
    void legacyCatalogHasTheAttributeAndBehaviorPotions() {
        Set<Key> keys = keys(Vanilla18.attributes());
        for (String id : new String[]{"strength", "weakness", "sharpness", "speed",
                "invisibility", "regeneration", "instant_health", "absorption"}) {
            assertTrue(keys.contains(k(id)), "legacy catalog missing " + id);
        }
    }

    @Test
    void modernCatalogHasTheFullRequestedSet() {
        Set<Key> keys = keys(Vanilla.attributes());
        for (String id : new String[]{"strength", "weakness", "sharpness", "speed", "invisibility",
                "regeneration", "instant_health", "absorption", "haste", "mining_fatigue", "jump_boost"}) {
            assertTrue(keys.contains(k(id)), "modern catalog missing " + id);
        }
    }
}
