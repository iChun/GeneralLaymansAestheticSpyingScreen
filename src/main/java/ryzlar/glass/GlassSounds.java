package ryzlar.glass;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * GLASS's own sounds, all made for it (tools/synth_sounds.py): the nano screens building and falling apart, the cloak,
 * the cloak field rising, the holo cube scanning and opening its simulation, and the holo screens' buttons.
 */
public final class GlassSounds
{
    public static final SoundEvent NANO_ASSEMBLE = register("nano_assemble");
    public static final SoundEvent NANO_DISSOLVE = register("nano_dissolve");
    public static final SoundEvent CLOAK_ENGAGE = register("cloak_engage");
    public static final SoundEvent FIELD_RISE = register("field_rise");
    public static final SoundEvent SCAN_START = register("scan_start");
    public static final SoundEvent SCAN_SWEEP = register("scan_sweep");
    public static final SoundEvent SCAN_COMPLETE = register("scan_complete");
    public static final SoundEvent SIM_OPEN = register("sim_open");
    public static final SoundEvent SIM_CLOSE = register("sim_close");
    public static final SoundEvent UI_CLICK = register("ui_click");
    public static final SoundEvent UI_OPEN = register("ui_open");
    public static final SoundEvent RIPPLE = register("ripple");
    public static final SoundEvent ALERT = register("alert");

    private GlassSounds() {}

    private static SoundEvent register(String name)
    {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, Glass.id(name), SoundEvent.createVariableRangeEvent(Glass.id(name)));
    }

    /** Loads the class, so the sounds are registered with everything else. */
    public static void init() {}
}
