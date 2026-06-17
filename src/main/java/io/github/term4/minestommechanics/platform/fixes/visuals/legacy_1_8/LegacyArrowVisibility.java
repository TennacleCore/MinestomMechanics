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
 * Drives the {@link LegacyArrowVisibilityConfig#enabled} fix: keeps the enabled players on a single, cosmetically
 * neutral scoreboard team so a 1.8 client (via Via) stops hiding deflected / passed-through arrows.
 *
 * <p><b>Why a team:</b> the "arrow goes invisible after touching an entity" glitch is a native 1.8 client bug - the
 * client runs its own {@code EntityArrow} collision and bounces+hides the arrow on a hit it cannot damage. The only
 * client lever that suppresses it is {@code EntityArrow.t_()}'s {@code !shooter.canAttackPlayer(target)} null-out
 * (same team + friendly fire off). Team membership is replicated to the 1.8 client (the shooter id rides the spawn
 * packet, the friendly-fire byte survives the Via chain), so shooter+target on one FF-off team -&gt; the client nulls
 * the hit -&gt; the arrow flies straight through the teammate and stays visible. Server-side damage ignores teams, so
 * combat is unaffected; the team carries no color / prefix / suffix and default nametag-visibility + collision, so the
 * only client-visible change is arrow rendering.
 *
 * <p><b>Per player, not per arrow:</b> a scoreboard team is a persistent, pairwise object - you cannot put a single
 * in-flight arrow on a team, and both the shooter and the target must be members. So membership is resolved per player
 * from {@link FixesSystem#legacyArrowVisibilityEnabled} (the profile chain, else the install config) and applied
 * on (re)spawn. For the fix to work between two players both must be enabled.
 *
 * <p>The {@link #setEnabled(boolean) runtime master switch} is the global on/off (the config decides who when on).
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
