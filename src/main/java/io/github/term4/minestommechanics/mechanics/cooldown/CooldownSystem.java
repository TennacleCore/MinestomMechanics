package io.github.term4.minestommechanics.mechanics.cooldown;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-authoritative item-use cooldowns. Vanilla's are client-predicted only (the {@code use_cooldown} component) -
 * the server happily honors a use packet from a client that ignores its own overlay. This system is the authority:
 * consumers ({@code ThrowableItemType} etc.) gate on {@link #tryUse}, which also sends the client overlay
 * ({@code set_cooldown}). Legacy 1.8 clients have no cooldown wire - enforcement still applies, the overlay just
 * doesn't render (a legacy visual analog may come later if there's demand).
 */
public final class CooldownSystem implements MechanicsModule {

    public static final Key KEY = Key.key("mm:cooldowns");

    /** Per-player active cooldowns: cooldown group -> expiry on the player's combat clock. */
    private static final Tag<Map<String, Long>> ACTIVE = Tag.Transient("mm:cooldowns");

    private final MinestomMechanics mm;
    private final @Nullable CooldownConfig config;

    private CooldownSystem(MinestomMechanics mm, @Nullable CooldownConfig config) {
        this.mm = mm;
        this.config = config;
    }

    /** Installs with no install-level config: cooldowns come from the profile scope ({@code MechanicsKeys.COOLDOWNS}). */
    public static CooldownSystem install(MinestomMechanics mm) {
        return install(mm, null);
    }

    public static CooldownSystem install(MinestomMechanics mm, @Nullable CooldownConfig config) {
        CooldownSystem system = new CooldownSystem(mm, config);
        mm.register(system);
        return system;
    }

    /** Whether {@code material} is currently on cooldown for {@code player}. */
    public boolean isOnCooldown(Player player, Material material) {
        Map<String, Long> active = player.getTag(ACTIVE);
        if (active == null) return false;
        Long until = active.get(material.key().asString());
        return until != null && TickSystem.tick(player) < until;
    }

    /**
     * Gates a use: {@code false} while on cooldown (the caller skips the action); otherwise arms the configured
     * cooldown (server clock, TPS-scaled) + sends the client overlay, and returns {@code true}. No config for the
     * material = always allowed.
     */
    public boolean tryUse(Player player, Material material) {
        CooldownConfig cfg = mm.profiles().resolve(player, MechanicsKeys.COOLDOWNS);
        if (cfg == null) cfg = config;
        Integer ticks = cfg != null ? cfg.ticks(material) : null;
        if (ticks == null || ticks <= 0) return true;

        String group = material.key().asString();
        long now = TickSystem.tick(player);
        Map<String, Long> active = player.getTag(ACTIVE);
        if (active == null) player.setTag(ACTIVE, active = new HashMap<>());
        Long until = active.get(group);
        if (until != null && now < until) return false;

        active.put(group, now + TickScaler.duration(ticks, mm.profiles().resolve(player, MechanicsKeys.TICK_SCALING), KEY));
        // the overlay counts on the CLIENT's clock, so it gets the unscaled vanilla ticks (same real time)
        player.sendPacket(new SetCooldownPacket(group, ticks));
        return true;
    }
}
