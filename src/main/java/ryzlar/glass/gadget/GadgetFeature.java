package ryzlar.glass.gadget;

/**
 * The gadgets (GLASS tablet, hard-light emitter, motion sensor, holo map table, holo decoy, optical camo) are not
 * finished yet, so a release leaves them out: with this off they are not registered at all (no blocks, items, entity
 * or effect), their recipes and loot tables are skipped (fabric:load_conditions), and their tests pass without running.
 * Turn it on to work on them.
 * <p>
 * Kept in its own class: reading it must not load {@link Gadgets}, whose fields register everything.
 */
public final class GadgetFeature
{
    public static final boolean ENABLED = false;

    private GadgetFeature() {}
}
