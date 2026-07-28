package test.presets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Pearl;
import io.github.term4.polyp.presets.mmc18.Projectiles;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the mmcgeometryclip capture (96 point-blank throws at a wall/ceiling corner, with the real geometry)
 * through OUR mmc18 pearl and diffs every throw against minemen's recorded response - rule teleport (position)
 * or refusal (unmoved). The ground-truth harness for the pearl mechanic.
 */
class MmcPearlCorpusParityTest extends HeadlessServerTest {

    @Test
    void corpusReplayMatchesMinemen() throws Exception {
        JsonObject corpus;
        try (var in = new InputStreamReader(getClass().getResourceAsStream("/mmc-pearl-corpus.json"), StandardCharsets.UTF_8)) {
            corpus = new Gson().fromJson(in, JsonObject.class);
        }
        for (var el : corpus.getAsJsonArray("blocks")) {
            JsonArray b = el.getAsJsonArray();
            instance.setBlock(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt(), Block.STONE);
        }
        // arena floor (top y=70): pre-dates the capture's block model; the throws never reach it, the shooter needs footing
        for (int x = 946686; x <= 946696; x++)
            for (int z = 946668; z <= 946686; z++) instance.setBlock(x, 69, z, Block.STONE);

        ProjectileConfig config = Projectiles.config();
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(946693, 71, 946677), "CorpusPearl");
        StringBuilder report = new StringBuilder();
        int ok = 0, ruleMissed = 0, falseRefusal = 0, falseTeleport = 0, posOff = 0, n = 0;
        try {
            for (var el : corpus.getAsJsonArray("throws")) {
                JsonObject t = el.getAsJsonObject();
                JsonArray f = t.getAsJsonArray("feet");
                Pos from = new Pos(f.get(0).getAsDouble(), f.get(1).getAsDouble(), f.get(2).getAsDouble(),
                        (float) t.get("yaw").getAsDouble(), (float) t.get("pitch").getAsDouble());
                boolean expectEcho = t.get("echo").getAsBoolean();
                JsonArray tpArr = t.getAsJsonArray("tp");
                Pos expected = new Pos(tpArr.get(0).getAsDouble(), tpArr.get(1).getAsDouble(), tpArr.get(2).getAsDouble());

                shooter.player.teleport(from).join();
                var snap = ProjectileSnapshot.of(shooter.player, Pearl.INSTANCE).withConfig(config);
                ProjectileEntity pearl = new ProjectileSystem(Polyp.getInstance(), config).launch(snap);
                if (pearl == null) continue;
                awaitSpawn(pearl);
                for (int tick = 1; tick <= 100 && !pearl.isRemoved(); tick++) pearl.tick(tick * 50L);
                if (!pearl.isRemoved()) pearl.remove();
                Pos got = shooter.player.getPosition();
                boolean moved = got.distanceSquared(from) > 1e-8;
                n++;

                if (expectEcho && !moved) { ok++; continue; }
                if (expectEcho) {
                    falseTeleport++;
                    report.append(String.format("#%d EXPECT refusal, GOT tp (%.3f,%.3f,%.3f) from (%.3f,%.3f,%.3f) p%.1f%n",
                            n, got.x(), got.y(), got.z(), from.x(), from.y(), from.z(), from.pitch()));
                } else if (!moved) {
                    falseRefusal++;
                    report.append(String.format("#%d EXPECT tp (%.3f,%.3f,%.3f), GOT refusal from (%.3f,%.3f,%.3f) p%.1f%n",
                            n, expected.x(), expected.y(), expected.z(), from.x(), from.y(), from.z(), from.pitch()));
                } else {
                    double d = got.distance(expected);
                    if (d < 0.2) ok++;
                    else {
                        posOff++;
                        report.append(String.format("#%d tp OFF by %.3f: expect (%.3f,%.3f,%.3f) got (%.3f,%.3f,%.3f)%n",
                                n, d, expected.x(), expected.y(), expected.z(), got.x(), got.y(), got.z()));
                    }
                }
            }
        } finally {
            shooter.player.remove();
        }
        String summary = String.format("corpus %d: ok %d, falseRefusal %d, falseTeleport %d, posOff %d%n%s",
                n, ok, falseRefusal, falseTeleport, posOff, report);
        assertTrue(ok >= n * 0.95, summary);
    }
}
