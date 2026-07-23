package io.github.term4.minestommechanics.presets;

import io.github.term4.minestommechanics.entity.PrimedTnt;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.presets.hypixel.Hypixel;
import io.github.term4.minestommechanics.presets.mmc18.Mmc18;

import java.util.function.Supplier;

/**
 * A selectable server preset: its mechanics {@link MechanicsProfile} paired with the matching primed-TNT config, so a
 * server switches both from ONE constant. Bundling them rules out a profile/TNT mismatch - a Hypixel profile with the
 * MineMen TNT entity (wrong wire + bounce) behaves like neither.
 */
public enum Preset {
    HYPIXEL(Hypixel::profile, io.github.term4.minestommechanics.presets.hypixel.Tnt.CONFIG),
    MMC18(Mmc18::profile, io.github.term4.minestommechanics.presets.mmc18.Tnt.CONFIG);

    private final Supplier<MechanicsProfile> profile;
    public final PrimedTnt.Config tnt;

    Preset(Supplier<MechanicsProfile> profile, PrimedTnt.Config tnt) {
        this.profile = profile;
        this.tnt = tnt;
    }

    /** The mechanics profile (fresh build); the server layers compat + fixes on top. */
    public MechanicsProfile profile() { return profile.get(); }
}
