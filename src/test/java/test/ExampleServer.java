package test;

import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.api.event.DamageEvent;
import io.github.term4.minestommechanics.mechanics.Vanilla18;
import io.github.term4.minestommechanics.mechanics.attack.AttackSystem;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import io.github.term4.minestommechanics.platform.fixes.FixesSystem;
import io.github.term4.minestommechanics.platform.fixes.FixesConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.VisualsConfig;
import io.github.term4.minestommechanics.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig;
import io.github.term4.minestommechanics.platform.fixes.client.SelfPlacementFixConfig;
import io.github.term4.minestommechanics.platform.fixes.world.BlockPlacementFixConfig;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.Bow;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;

import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.attribute.Attribute;
import io.github.term4.minestommechanics.tracking.ClientInfoTracker;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;
import test.presets.Hypixel;
import test.presets.mmc18;

public class ExampleServer {
    static void main() {
        // Could wrap these in compatibility methods (mm.legacyProperties(mode: 1.7, 1.8, etc)

        // Enable faster socket writes
        System.setProperty("minestom.new-socket-write-lock", "true");

        // Disable interaction range enforcement (mechanics lib handles reach)
        System.setProperty( "minestom.enforce-entity-interaction-range", "false");

        // Set up required flags for legacy players (prevents visual bugs on older versions)
        System.setProperty("minestom.chunk-view-distance", "12"); // less than 12 causes players to disappear at ~150 block from spawn

        // Set server TPS (default is 20, library should work with any TPS tested up to 1000)
        System.setProperty("minestom.tps", "20");

        // Keep-alive cadence = how often Player#getLatency() refreshes (the round-trip itself is measured
        // read-thread/sub-tick accurate). Vanilla 1.8's 2s here; pass e.g. 500 for Hypixel's probe rate.
        MinestomMechanics.keepAliveInterval(MinestomMechanics.LEGACY_KEEP_ALIVE_MS);

        // Initialize the server
        MinecraftServer server = MinecraftServer.init(new Auth.Bungee()); // bungee auth allows 1.7 clients to join (velocity works for all later versions, and a proxy is not required)

        // Debug: test-only inbound-lag simulator (delays a player's movement packets so the server perceives
        // their position/onGround late, like a high-ping client). Purely in the test server - mechanics is untouched.
        final LagSimulator lag = new LagSimulator();
        lag.install();

        // Get mm instance
        MinestomMechanics mm = MinestomMechanics.getInstance();

        // Enable ViaVersion proxy details
        mm.viaProxyDetails = true;
        mm.init();

        // 1. Initialize knockback system
        KnockbackSystem.install(mm, mmc18.kb());

        // 2. Initialize damage system.
        DamageSystem.install(mm, mmc18.dmg());

        // 3. Initialize combat system
        AttackSystem.install(mm, mmc18.atk());

        // 4. Initialize projectile system
        ProjectileSystem.install(mm, ProjectileConfig.builder(mmc18.projectiles())
                .build(), new Bow());

        // 5. Initialize the client/protocol fixes system.
        FixesSystem fixes = FixesSystem.install(mm, FixesConfig.builder()
                .visuals(VisualsConfig.builder()
                        .legacyArrowVisibility(LegacyArrowVisibilityConfig.builder().enabled(true).deflectParticles(true).build())
                        .build())
                .blockPlacement(BlockPlacementFixConfig.builder().enabled(true).build())
                .selfPlacement(SelfPlacementFixConfig.builder().enabled(true).build())
                .build());

        // Debug (temporary)
        MinecraftServer.getGlobalEventHandler().addListener(DamageEvent.class, event -> {
            if (!MeleeDamage.KEY.equals(event.type().key())) return;
            if (!(event.target() instanceof Player victim)) return;
            MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                if (victim.isOnline() && !victim.isDead())
                    victim.setHealth((float) victim.getAttributeValue(Attribute.MAX_HEALTH));
            });
        });

        // Scoped mechanics (player -> instance -> global)
        mm.profiles().setGlobal(MechanicsProfile.builder()
                .player(mmc18.player())
                .velocity(mmc18.velocity())
                .build());

        // Create the instance (world)
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();

        // Generate the world & add lighting
        instanceContainer.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));
        instanceContainer.setChunkSupplier(LightingChunk::new);

        // Test dammage types
        instanceContainer.loadChunk(1, 0).thenRun(() -> {
            for (int x = 18; x <= 20; x++)
                for (int z = 0; z <= 2; z++)
                    for (int y = 40; y <= 46; y++)
                        instanceContainer.setBlock(x, y, z, Block.WATER);
            instanceContainer.setBlock(25, 40, 0, Block.FIRE);
            instanceContainer.setBlock(25, 40, 1, Block.FIRE);
            instanceContainer.setBlock(29, 39, 0, Block.SAND);
            instanceContainer.setBlock(29, 40, 0, Block.CACTUS);
            instanceContainer.setBlock(29, 41, 0, Block.CACTUS);
        });

        // Water FLOW-push test pads: 4 enclosed lanes, each with a hand-set `level` gradient so the current flows
        // a known cardinal direction (Minestom does not spread fluids, so we set the levels manually). Stand in a
        // lane feet-in-water on the floor and take a hit: the folded knockback should carry the flow term (the
        // VelocityConfig.flowPush residual) in the AWAY-from-the-wool direction. Compare to vanilla 1.8.
        instanceContainer.loadChunk(0, 0).thenRun(() -> {
            buildFlowLane(instanceContainer,  2, 40,  4,  1,  0, 7, Block.RED_WOOL);    // EAST  (+X)
            buildFlowLane(instanceContainer,  8, 40,  7, -1,  0, 7, Block.BLUE_WOOL);   // WEST  (-X)
            buildFlowLane(instanceContainer, 12, 40,  4,  0,  1, 7, Block.YELLOW_WOOL); // SOUTH (+Z)
            buildFlowLane(instanceContainer, 14, 40, 10,  0, -1, 7, Block.LIME_WOOL);   // NORTH (-Z)
            System.out.println("[flow-test] lanes @ y=40 (flow points AWAY from the wool marker): "
                    + "EAST/+X red src(2,4) | WEST/-X blue src(8,7) | SOUTH/+Z yellow src(12,4) | NORTH/-Z green src(14,10)");
        });

        // Add an event handler to handle player spawning
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 42, 0));

            // Example of how to get a players protocol on login (with multiple attempts, stops once protocol is known)
            if (mm.viaProxyDetails) {
                var scheduler = MinecraftServer.getSchedulerManager();
                final int maxRuns = 3;
                final int[] runs = {0};

                scheduler.scheduleTask(() -> {
                    if (!player.isOnline()) return TaskSchedule.stop();

                    // Returns -1 until ViaVersion/proxy handshake completes
                    int protocol = mm.clientInfo().getProtocol(player);

                    if (protocol == ClientInfoTracker.UNKNOWN_PROTOCOL) {
                        return (++runs[0] >= maxRuns)
                                ? TaskSchedule.stop() : TaskSchedule.tick(20);
                    }
                    System.out.println(player.getUsername() + " protocol " + protocol);
                    return TaskSchedule.stop();
                }, TaskSchedule.tick(20));
            }

            //player.setGameMode(GameMode.CREATIVE);

            player.getInventory().addItemStack(ItemStack.of(Material.RED_WOOL, 1000));
            player.getInventory().addItemStack(ItemStack.of(Material.LADDER, 64));
            player.getInventory().addItemStack(ItemStack.of(Material.DIAMOND_SWORD, 1));
            player.getInventory().addItemStack(ItemStack.of(Material.SNOWBALL, 64));
            player.getInventory().addItemStack(ItemStack.of(Material.EGG, 16));
            player.getInventory().addItemStack(ItemStack.of(Material.ENDER_PEARL, 16));
            player.getInventory().addItemStack(ItemStack.of(Material.BOW, 1));
            player.getInventory().addItemStack(ItemStack.of(Material.ARROW, 64));



            /*
            player.getAttribute(net.minestom.server.entity.attribute.Attribute.MOVEMENT_SPEED)
                    .setBaseValue(0.1 * (1 + (0.2 * 2))); // Speed II

             */

        });
        
        // Debug: /lag <ticks> sets the sender's own simulated inbound packet latency (0 = off). Lets you
        // toggle/tune the laggy-landing scenario at runtime on whichever account you're testing the victim with.
        Command lagCmd = new Command("lag");
        Argument<Integer> ticksArg = ArgumentType.Integer("ticks");
        lagCmd.setDefaultExecutor((sender, ctx) -> sender.sendMessage("usage: /lag <ticks>  (0 = off)"));
        lagCmd.addSyntax((sender, ctx) -> {
            if (!(sender instanceof Player p)) return;
            int ticks = ctx.get(ticksArg);
            lag.setDelay(p, ticks);
            p.sendMessage("[sim-lag] inbound packet latency = " + ticks + " ticks");
        }, ticksArg);
        MinecraftServer.getCommandManager().register(lagCmd);

        // Debug: /fix <on|off> flips the legacy arrow-visibility compat fix at runtime via the manager's master switch
        // (setEnabled re-evaluates every online player's team membership). on = deflected/passed arrows stay visible on
        // 1.8; off = the 1.8 client hides them again (the underlying glitch). The per-player WHO is the config knob above.
        Command fixCmd = new Command("fix");
        Argument<String> stateArg = ArgumentType.Word("state").from("on", "off");
        fixCmd.setDefaultExecutor((sender, ctx) -> sender.sendMessage("usage: /fix <on|off>  (legacy arrow-visibility fix)"));
        fixCmd.addSyntax((sender, ctx) -> {
            boolean on = "on".equals(ctx.get(stateArg));
            fixes.legacyArrowVisibility().setEnabled(on);
            sender.sendMessage("[arrow-fix] " + (on ? "ON  - 1.8 deflect/pass arrows stay visible" : "OFF - 1.8 deflect/pass arrows go invisible (bug)"));
        }, stateArg);
        MinecraftServer.getCommandManager().register(fixCmd);

        // Debug: /resendchunk resends the chunk the sender stands in, to themselves - exactly what
        // BlockPlacementListener#refresh does when it cancels a placement that collides with an entity. Tests the
        // suspected root cause of the "after placing a block onto player B, A can't hit B until someone re-enters
        // the chunk" bug: on a 1.8 client (via Via) a chunk resend drops that chunk's entities client-side, and the
        // server never re-spawns them. Stand next to another player (or use /testmob), run this on the 1.8 client,
        // and watch the entity vanish (and stay gone until it re-enters tracking). If it does, the mechanism is
        // confirmed and the fix = stop resending the chunk (send a targeted BlockChangePacket instead).
        Command resendChunkCmd = new Command("resendchunk");
        resendChunkCmd.setDefaultExecutor((sender, ctx) -> {
            if (!(sender instanceof Player p)) return;
            var chunk = p.getInstance().getChunkAt(p.getPosition());
            if (chunk == null) {
                p.sendMessage("[resendchunk] no chunk at your position");
                return;
            }
            chunk.sendChunk(p);
            p.sendMessage("[resendchunk] resent chunk " + chunk.getChunkX() + "," + chunk.getChunkZ() + " to you");
        });
        MinecraftServer.getCommandManager().register(resendChunkCmd);

        // Debug: /testmob spawns a static zombie next to the sender as a watch target for /resendchunk.
        Command testMobCmd = new Command("testmob");
        testMobCmd.setDefaultExecutor((sender, ctx) -> {
            if (!(sender instanceof Player p)) return;
            Entity mob = new Entity(EntityType.ZOMBIE);
            mob.setInstance(p.getInstance(), p.getPosition().add(1, 0, 0));
            p.sendMessage("[testmob] spawned a zombie next to you");
        });
        MinecraftServer.getCommandManager().register(testMobCmd);

        // Start the server
        server.start("0.0.0.0", 25566);
    }

    /**
     * Builds a 1-wide enclosed water channel with a manual {@code level} gradient (source/marker end = level 0,
     * rising along {@code (dirX,dirZ)}) so the water flows toward the far end - a deterministic flow-push test pad.
     * Stone floor + side/end walls isolate the lane; the player stands feet-in-water on the floor (the verified
     * standing {@code -0.02} motY case), so only the horizontal flow term folds into a hit.
     */
    private static void buildFlowLane(InstanceContainer inst, int sx, int baseY, int sz,
                                      int dirX, int dirZ, int length, Block marker) {
        int pdx = dirZ, pdz = -dirX; // perpendicular = side-wall offset
        for (int i = -1; i <= length; i++) { // -1 and length = solid end caps
            int cx = sx + dirX * i, cz = sz + dirZ * i;
            boolean cap = i < 0 || i >= length;
            Block fill = cap ? Block.STONE : Block.WATER.withProperty("level", Integer.toString(Math.min(i, 7)));
            inst.setBlock(cx, baseY - 1, cz, Block.STONE); // floor
            inst.setBlock(cx, baseY, cz, fill);            // feet
            inst.setBlock(cx, baseY + 1, cz, fill);        // body
            inst.setBlock(cx, baseY + 2, cz, Block.AIR);   // headroom
            for (int s = -1; s <= 1; s += 2) {             // side walls (solid -> no spurious side flow)
                int wx = cx + pdx * s, wz = cz + pdz * s;
                inst.setBlock(wx, baseY - 1, wz, Block.STONE);
                inst.setBlock(wx, baseY, wz, Block.STONE);
                inst.setBlock(wx, baseY + 1, wz, Block.STONE);
            }
        }
        inst.setBlock(sx, baseY + 2, sz, marker); // flow points AWAY from this marker
    }
}
