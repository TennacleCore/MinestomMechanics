package io.github.term4.minestommechanics.mechanics.damage.types;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.registry.RegistryKey;

/**
 * Cache of the vanilla Minecraft damage-type registry keys we reference. These are Minestom
 * {@link DamageType} keys (not our {@code io...mechanics.damage.types.DamageType}); each of our
 * damage types passes one as its {@code minecraftType} for application and death messages.
 */
public final class VanillaTypes {

    private VanillaTypes() {}

    private static RegistryKey<DamageType> key(String id) {
        return MinecraftServer.getDamageTypeRegistry().getKey(Key.key(id));
    }

    public static final RegistryKey<DamageType> PLAYER_ATTACK = key("minecraft:player_attack");
    public static final RegistryKey<DamageType> GENERIC = key("minecraft:generic");
    public static final RegistryKey<DamageType> FALL = key("minecraft:fall");
    public static final RegistryKey<DamageType> FIRE = key("minecraft:fire");

}
