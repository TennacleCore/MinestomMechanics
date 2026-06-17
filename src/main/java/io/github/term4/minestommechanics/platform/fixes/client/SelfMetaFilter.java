package io.github.term4.minestommechanics.platform.fixes.client;

import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configures which metadata entries are suppressed to the player they belong to (the self-echo "meta fix").
 *
 * <p>Modern clients locally predict state changes (sneaking, sprinting, crawling, item use) before the server
 * confirms them. When the server echoes that data back to the initiating player, the client re-applies it,
 * causing a one-tick stutter. This filter strips the problem entries from self-bound metadata packets; other
 * viewers always get the full, unmodified packet.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use the default filter
 * SelfMetaFilter filter = SelfMetaFilter.defaultPlayerFilter();
 *
 * // Or build a custom filter (useful for cross version servers)
 * SelfMetaFilter filter = new SelfMetaFilter()
 *         .suppressBit((MetadataDef.Entry.BitMask) MetadataDef.IS_CROUCHING)
 *         .suppressIndex(MetadataDef.POSE);
 * }</pre>
 *
 * @see io.github.term4.minestommechanics.platform.player.OptimizedPlayer
 */
public final class SelfMetaFilter {

    /** Creates an empty filter; configure with {@link #suppressBit}, {@link #suppressIndex},
     *  {@link #suppressAttributes(boolean)}, or use {@link #defaultPlayerFilter()}. */
    public SelfMetaFilter() {  }

    private final Set<Integer> suppressedIndices = new HashSet<>();
    private final Map<Integer, Byte> suppressedBits = new HashMap<>();
    private boolean suppressAttributes = false;

    /**
     * Suppresses an entire metadata index from the self-echo, for indices the client always knows in full
     * (e.g. {@link MetadataDef#POSE}, {@link MetadataDef.LivingEntity#LIVING_ENTITY_FLAGS}).
     *
     * @param entry the metadata entry whose index should be suppressed
     * @return this filter for chaining
     */
    public SelfMetaFilter suppressIndex(MetadataDef.Entry<?> entry) {
        suppressedIndices.add(entry.index());
        return this;
    }

    /**
     * Suppresses specific bits within a flag byte from the self-echo; other bits at the same index pass
     * through. Used for index 0 (entity flags), where crouching/sprinting are client-predicted but on-fire,
     * invisible, glowing, and elytra are server-determined.
     *
     * @param entry the bit mask entry to suppress
     * @return this filter for chaining
     */
    public SelfMetaFilter suppressBit(MetadataDef.Entry.BitMask entry) {
        int idx = entry.index();
        byte existing = suppressedBits.getOrDefault(idx, (byte) 0);
        suppressedBits.put(idx, (byte) (existing | entry.bitMask()));
        return this;
    }

    /**
     * Suppresses {@code EntityAttributesPacket} echoes (e.g. the movement-speed update on sprint start/stop,
     * which the client already predicted - same one-tick stutter as metadata).
     *
     * @param suppress true to suppress attribute echoes
     * @return this filter for chaining
     */
    public SelfMetaFilter suppressAttributes(boolean suppress) {
        this.suppressAttributes = suppress;
        return this;
    }

    /** Whether attribute packet echoes are suppressed. */
    public boolean suppressAttributes() {
        return suppressAttributes;
    }

    /**
     * Filters a metadata entry map, stripping preconfigured data: bit-suppressed indices lose only the
     * configured bits, fully-suppressed indices are removed entirely.
     *
     * @param entries the original metadata entries from the packet
     * @return a filtered copy if anything changed, or {@code null} if no filtering was needed
     */
    public Map<Integer, Metadata.Entry<?>> filter(Map<Integer, Metadata.Entry<?>> entries) {
        Map<Integer, Metadata.Entry<?>> result = null;
        boolean modified = false;

        for (var e : entries.entrySet()) {
            int idx = e.getKey();

            // Check for bit suppression
            Byte suppressMask = suppressedBits.get(idx);
            if (suppressMask != null && e.getValue().value() instanceof Byte flags) {
                byte serverOnly = (byte) (flags & ~suppressMask);
                if (!modified) {
                    result = new HashMap<>(entries);
                    modified = true;
                }
                if (serverOnly != 0) {
                    result.put(idx, Metadata.Byte(serverOnly));
                } else {
                    result.remove(idx);
                }
            }
            // Check for full index suppression
            else if (suppressedIndices.contains(idx)) {
                if (!modified) {
                    result = new HashMap<>(entries);
                    modified = true;
                }
                result.remove(idx);
            }
        }

        return result;
    }

    /**
     * The default player filter: suppresses the crouching (0x02) and sprinting (0x08) bits, the pose index,
     * the living-entity flags index (eating, blocking, bow draw), and attribute echoes. Pose suppression also
     * covers swimming/crawling stutter even though the swimming bit (0x10) isn't filtered; non-predicted bits
     * (on fire, glowing) pass through.
     *
     * @return a new default filter
     */
    public static SelfMetaFilter defaultPlayerFilter() {
        return new SelfMetaFilter()
                .suppressBit((MetadataDef.Entry.BitMask) MetadataDef.IS_CROUCHING)
                .suppressBit((MetadataDef.Entry.BitMask) MetadataDef.IS_SPRINTING)
                .suppressIndex(MetadataDef.POSE)
                .suppressIndex(MetadataDef.LivingEntity.LIVING_ENTITY_FLAGS)
                .suppressAttributes(true);
    }
}
