package io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8;

import io.github.term4.minestommechanics.platform.fixes.FixesSystem;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps enabled players on one cosmetically neutral, friendly-fire-off scoreboard team so a 1.8 client stops hiding
 * deflected / passed-through arrows. The glitch is a native 1.8 client bug ({@code EntityArrow} bounces+hides a hit it
 * can't damage); the only client lever is the {@code canAttackPlayer} null-out - same team + FF off - which survives
 * the Via chain. Server-side damage ignores teams, and the team carries no color/prefix/collision changes.
 * Per PLAYER, not per arrow (teams are pairwise - both shooter and target must be enabled members); membership is
 * resolved from {@link FixesSystem#legacyArrowVisibilityEnabled} on (re)spawn. {@link #setEnabled} = global on/off.
 */
public final class LegacyArrowVisibility {

    /** Scoreboard team name ({@code <= 16} chars for 1.8). Flags default to {@code 0x00} = friendly fire off. */
    private static final String TEAM_NAME = "mm_arrow_vis";

    private final FixesSystem system;
    private volatile boolean enabled = true; // runtime master switch; the per-player config still decides who
    private @Nullable Team team;              // lazy: created only when the first player actually needs the fix

    public LegacyArrowVisibility(FixesSystem system) {
        this.system = system;
    }

    /** Wires the per-player membership sync onto {@code node}: re-evaluate on every spawn (first / respawn / instance
     *  change, so an instance-scoped config is picked up too), drop the name on disconnect. */
    public void install(EventNode<Event> node) {
        node.addListener(PlayerSpawnEvent.class, e -> apply(e.getPlayer()));
        node.addListener(PlayerDisconnectEvent.class, e -> {
            String name = e.getPlayer().getUsername();
            if (team != null && team.getMembers().contains(name)) team.removeMember(name); // keep the member set from growing unbounded
        });
    }

    /**
     * Runtime master switch (the on/off toggle): {@code false} removes everyone from the team (the glitch returns),
     * {@code true} re-applies the per-player config. The {@link LegacyArrowVisibilityConfig#enabled} knob still decides
     * who is included when on. Re-evaluates all online players immediately.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        refreshAll();
    }

    public boolean isEnabled() { return enabled; }

    /** Re-evaluates every online player's membership - call after changing profile configs at runtime. */
    public void refreshAll() {
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) apply(p);
    }

    /** Whether {@code player}'s resolved fixes config turns the fix on (and the master switch is on). */
    private boolean wants(Player player) {
        return enabled && system.legacyArrowVisibilityEnabled(player);
    }

    /** Adds or removes {@code player} to/from the visibility team per {@link #wants(Player)} - only on an actual change. */
    private void apply(Player player) {
        String name = player.getUsername();
        boolean want = wants(player);
        boolean member = team != null && team.getMembers().contains(name);
        if (want && !member) team().addMember(name);
        else if (!want && member) team.removeMember(name);
    }

    /** Lazily creates the cosmetically-neutral, friendly-fire-off team (default flags / color / visibility / collision). */
    private Team team() {
        Team t = team;
        if (t == null) team = t = MinecraftServer.getTeamManager().createBuilder(TEAM_NAME).build();
        return t;
    }
}
