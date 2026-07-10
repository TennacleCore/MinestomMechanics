package test.presets;

import io.github.term4.minestommechanics.MechanicsProfile;
import test.presets.customItems.PrimedTnt;
import test.presets.hypixel.Hypixel;
import test.presets.mmc18.Mmc18;

import java.util.function.Supplier;

/**
 * A selectable server preset: its mechanics {@link MechanicsProfile} paired with the matching primed-TNT config, so
 * {@code ExampleServer} switches both from ONE constant. Bundling them rules out a profile/TNT mismatch - a Hypixel
 * profile with the MineMen TNT entity (wrong wire + bounce) behaves like neither.
 */
public enum Preset {
    HYPIXEL(Hypixel::profile, test.presets.hypixel.Tnt.CONFIG),
    MMC18(Mmc18::profile, test.presets.mmc18.Tnt.CONFIG);

    private final Supplier<MechanicsProfile> profile;
    public final PrimedTnt.Config tnt;

    Preset(Supplier<MechanicsProfile> profile, PrimedTnt.Config tnt) {
        this.profile = profile;
        this.tnt = tnt;
    }

    /** The mechanics profile (fresh build); {@code ExampleServer} layers compat + fixes on top. */
    public MechanicsProfile profile() { return profile.get(); }
}
