package ryzlar.glass.client.tome;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;

import java.util.Map;

/**
 * The tome's 3D scenes: little looped animations of how GLASS is set up and what it does, built from real block and item
 * models with simplified glowing stand-ins for its effects. Each says in a caption what is happening.
 */
public final class TomeScenes
{
    /** One looped animation, {@code length} seconds long, drawn round {@code centre}, about {@code size} blocks across. */
    public interface Scene
    {
        float length();

        Vec3 centre();

        float size();

        void draw(SceneDraw d, float t);

        String caption(float t);
    }

    private static final int CYAN = 0xFF3CE6FF;
    private static final int CYAN_GLOW = 0xC03CE6FF;
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();

    private static final Map<String, Scene> SCENES = Map.of(
            "screen", new ScreenScene(false),
            "selector", new ScreenScene(true),
            "wireless", new WirelessScene(),
            "cloak", new CloakScene(),
            "field", new FieldScene(),
            "amplifier", new AmplifierScene(),
            "hologram", new HologramScene(),
            "cube_scan", new CubeScanScene(),
            "cube_bubble", new CubeBubbleScene());

    private TomeScenes() {}

    @Nullable
    public static Scene get(String name)
    {
        return SCENES.get(name);
    }

    // ---- Helpers ----

    /** 0 before {@code start}, 1 after {@code start + duration}, eased in between. */
    static float step(float t, float start, float duration)
    {
        float x = Mth.clamp((t - start) / duration, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static BlockState glass()
    {
        return GlassRegistry.GLASS_EXTENSION.defaultBlockState();
    }

    private static BlockState projector()
    {
        return GlassRegistry.GLASS_PROJECTOR.defaultBlockState();
    }

    private static BlockState wireless()
    {
        return GlassRegistry.GLASS_WIRELESS_EXTENSION.defaultBlockState();
    }

    /** What a screen shows, by where on it (u right, v up, both 0..1). */
    private interface Picture
    {
        int at(float u, float v);
    }

    /** Red, yellow and blue pillars against the sky: what the terminal in the scenes looks at. */
    private static final Picture PILLARS = (u, v) -> {
        if(v > 0.7F)
        {
            return 0xFF8EC5FF;
        }
        int column = Mth.clamp((int)(u * 5F), 0, 4);
        return switch(column)
        {
            case 1 -> 0xFFB02E26;
            case 2 -> 0xFFFED83D;
            case 3 -> 0xFF3C44AA;
            default -> v < 0.15F ? 0xFF5D9C3A : 0xFF8EC5FF;
        };
    };

    /** A gold wall over grass: the other scene of the scene selector. */
    private static final Picture GOLD = (u, v) -> v < 0.2F ? 0xFF5D9C3A : v > 0.85F ? 0xFF8EC5FF : ((int)(u * 8F) + (int)(v * 6F)) % 2 == 0 ? 0xFFF5CC27 : 0xFFE0B020;

    /**
     * A screen on a wall (the plane z = {@code z}, from x0..x1, y0..y1) built out of "nano" pixels: shown where the wave
     * from (ox, oy) has reached ({@code radius}), with a glowing front.
     */
    private static void screen(SceneDraw d, float x0, float y0, float x1, float y1, float z, float ox, float oy, float radius, Picture picture,
                               @Nullable Picture old, float wipe)
    {
        if(radius <= 0F)
        {
            return;
        }
        float cell = 0.25F;
        for(float x = x0; x < x1 - 0.001F; x += cell)
        {
            for(float y = y0; y < y1 - 0.001F; y += cell)
            {
                float cx = x + cell / 2F, cy = y + cell / 2F;
                float dist = Mth.sqrt((cx - ox) * (cx - ox) + (cy - oy) * (cy - oy));
                if(dist > radius)
                {
                    continue;
                }
                float u = (cx - x0) / (x1 - x0), v = (cy - y0) / (y1 - y0);
                Picture shown = old != null && u > wipe ? old : picture;
                d.faceZ(x, y, x + cell, y + cell, z, shown.at(u, v), false);
                if(radius - dist < 0.35F)
                {
                    d.faceZ(x, y, x + cell, y + cell, z + 0.003F, CYAN_GLOW, true);
                }
            }
        }
        if(old != null && wipe > 0F && wipe < 1F)
        {
            float wx = x0 + (x1 - x0) * wipe;
            d.faceZ(wx - 0.06F, y0, wx + 0.06F, y1, z + 0.004F, CYAN, true);
        }
    }

    /** A cyan pixel sparkle over a block that is being hidden or digitized. */
    private static void sparkle(SceneDraw d, float x, float y, float z, float t, float strength)
    {
        if(strength <= 0F)
        {
            return;
        }
        for(int i = 0; i < 4; i++)
        {
            float px = x + Mth.frac(Mth.sin((x * 7 + z * 3 + i) * 12.9898F + t * 3F) * 43758.5F);
            float py = y + Mth.frac(Mth.sin((y * 5 + i) * 78.233F + t * 2F) * 12345.6F);
            float s = 0.08F * strength;
            d.faceZ(px - s, py - s, px + s, py + s, z + 1.01F, CYAN_GLOW, true);
        }
    }

    // ---- Screens ----

    /** A terminal looking at pillars, a wall of glass with a projector, redstone: the screen builds itself (and switches scene). */
    private static final class ScreenScene implements Scene
    {
        private final boolean selector;

        ScreenScene(boolean selector)
        {
            this.selector = selector;
        }

        @Override
        public float length()
        {
            return selector ? 11F : 9F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(3.5D, 1D, 3D);
        }

        @Override
        public float size()
        {
            return 7F;
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 7, 6, GRASS);
            if(!selector)
            {
                d.block(Blocks.RED_WOOL.defaultBlockState(), 2, 0, 0);
                d.block(Blocks.RED_WOOL.defaultBlockState(), 2, 1, 0);
                d.block(Blocks.YELLOW_WOOL.defaultBlockState(), 3, 0, 0);
                d.block(Blocks.YELLOW_WOOL.defaultBlockState(), 3, 1, 0);
                d.block(Blocks.BLUE_WOOL.defaultBlockState(), 4, 0, 0);
                d.block(Blocks.BLUE_WOOL.defaultBlockState(), 4, 1, 0);
                d.block(GlassRegistry.GLASS_TERMINAL.defaultBlockState(), 3, 0, 2, step(t, 0F, 0.6F) * 1.2F);
            }
            else
            {
                d.block(GlassRegistry.GLASS_SCENE_SELECTOR.defaultBlockState(), 5, 0, 4, step(t, 0F, 0.6F) * 1.2F);
            }
            for(int x = 2; x <= 4; x++)
            {
                for(int y = 0; y <= 1; y++)
                {
                    float at = 0.8F + (x - 2) * 0.15F + y * 0.2F;
                    d.block(x == 3 && y == 0 ? projector() : glass(), x, y, 4, step(t, at, 0.4F) * 1.15F);
                }
            }
            float on = selector ? 2.5F : 3F;
            float off = selector ? 9.5F : 7.5F;
            //The signal: a redstone block in the ground under the projector.
            d.block(t >= on - 0.4F && t < off ? Blocks.REDSTONE_BLOCK.defaultBlockState() : GRASS, 3, -1, 4);
            float radius = t < off ? (t - on) * 1.8F : Math.max(0F, 3.5F - (t - off) * 3.5F);
            float wipe = selector ? Mth.clamp((t - 5.5F) / 1.2F, 0F, 1F) : 1F;
            screen(d, 2F, 0F, 5F, 2F, 5.002F, 3.5F, 0.5F, radius, selector && t >= 5.5F ? GOLD : PILLARS,
                    selector && t >= 5.5F ? PILLARS : null, wipe);
            if(selector && t > 5.3F && t < 5.8F)
            {
                d.faceZ(5F, 0F, 6F, 1F, 5.003F, CYAN_GLOW, true); //the selector's pulse
            }
        }

        @Override
        public String caption(float t)
        {
            if(selector)
            {
                return t < 2.5F ? "A scene selector near the projector, with two scenes added"
                        : t < 5.3F ? "Powered, the screen shows the first scene"
                        : t < 9.5F ? "A redstone pulse on the selector: the next scene wipes over it"
                        : "No signal: the screen switches off";
            }
            return t < 0.8F ? "A terminal is the camera: it looks at the pillars"
                    : t < 2.6F ? "A wall of extensions with a projector in it, set to the terminal's channel"
                    : t < 3F ? "Give the projector a redstone signal"
                    : t < 7.5F ? "The screen builds itself out of the glass and shows what the terminal sees"
                    : "No signal: it switches off";
        }
    }

    /** A projector and two wireless extensions: a floating screen between them. */
    private static final class WirelessScene implements Scene
    {
        @Override
        public float length()
        {
            return 9F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(4D, 1D, 3D);
        }

        @Override
        public float size()
        {
            return 8F;
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 8, 6, GRASS);
            d.block(projector(), 1, 0, 4, step(t, 0F, 0.4F) * 1.15F);
            d.block(wireless(), 3, 0, 2, step(t, 0.5F, 0.4F) * 1.15F);
            d.block(wireless(), 6, 0, 2, step(t, 0.8F, 0.4F) * 1.15F);
            float link = step(t, 1.5F, 1F);
            if(link > 0F && t < 8F)
            {
                d.line(1.5F, 1F, 4.5F, Mth.lerp(link, 1.5F, 3.5F), 1F, Mth.lerp(link, 4.5F, 2.5F), CYAN);
                d.line(1.5F, 1F, 4.5F, Mth.lerp(link, 1.5F, 6.5F), 1F, Mth.lerp(link, 4.5F, 2.5F), CYAN);
            }
            d.block(t >= 2.8F && t < 7.5F ? Blocks.REDSTONE_BLOCK.defaultBlockState() : GRASS, 1, -1, 4);
            float radius = t < 7.5F ? (t - 3F) * 1.8F : Math.max(0F, 3.5F - (t - 7.5F) * 3.5F);
            screen(d, 3.5F, 1F, 6.5F, 3F, 2.5F, 3.5F, 1F, radius, PILLARS, null, 1F);
        }

        @Override
        public String caption(float t)
        {
            return t < 1.5F ? "A projector and two wireless extensions"
                    : t < 2.8F ? "Link each extension to the projector (within 64 blocks)"
                    : t < 7.5F ? "Powered, a screen floats between them"
                    : "No signal: it folds away";
        }
    }

    // ---- Cloak ----

    /** A house behind a wall of glass: the cloak hides it, glass and all. */
    private static final class CloakScene implements Scene
    {
        @Override
        public float length()
        {
            return 10F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(3.5D, 1.2D, 3.5D);
        }

        @Override
        public float size()
        {
            return 7.5F;
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 7, 8, GRASS);
            float hidden = step(t, 3.3F, 1.2F) * (1F - step(t, 8F, 1F));
            BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
            for(int x = 2; x <= 4; x++)
            {
                for(int y = 0; y <= 2; y++)
                {
                    for(int z = 1; z <= 3; z++)
                    {
                        float stagger = y * 0.15F;
                        float grow = 1F - Mth.clamp(hidden * 1.4F - stagger, 0F, 1F);
                        d.block(y == 2 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : planks, x, y, z, grow);
                    }
                }
                sparkle(d, x, 1, 3, t, hidden > 0F && hidden < 1F ? 1F : 0F);
            }
            for(int x = 1; x <= 5; x++)
            {
                for(int y = 0; y <= 2; y++)
                {
                    float build = step(t, 0.4F + x * 0.1F + y * 0.1F, 0.4F) * 1.15F;
                    d.block(x == 3 && y == 0 ? projector() : glass(), x, y, 5, build * (1F - hidden));
                }
            }
            if(hidden > 0.5F)
            {
                //A faint shimmer where the glass is: all that gives the cloak away.
                d.faceZ(1F, 0F, 6F, 3F, 6.002F, cyan(0.08F + 0.05F * Mth.sin(t * 6F)), true);
            }
            d.block(t >= 2.8F && t < 8F ? Blocks.REDSTONE_BLOCK.defaultBlockState() : GRASS, 3, -1, 5);
        }

        @Override
        public String caption(float t)
        {
            return t < 2.8F ? "A house, and a wall of glass with a projector in cloak mode"
                    : t < 3.3F ? "Give it a redstone signal"
                    : t < 8F ? "The glass shows what is behind the house: glass and house are gone"
                    : "No signal: everything is back";
        }
    }

    private static int cyan(float alpha)
    {
        return ((int)(Mth.clamp(alpha, 0F, 1F) * 255F) << 24) | 0x3CE6FF;
    }

    /** Four corners rise into walls and a roof that hide the house inside. */
    private static final class FieldScene implements Scene
    {
        @Override
        public float length()
        {
            return 11F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(5D, 1.5D, 5D);
        }

        @Override
        public float size()
        {
            return 10F;
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 10, 11, GRASS);
            int[][] corners = { { 1, 1 }, { 8, 1 }, { 1, 8 }, { 8, 8 } };
            for(int i = 0; i < 4; i++)
            {
                d.block(wireless(), corners[i][0], 0, corners[i][1], step(t, i * 0.25F, 0.4F) * 1.15F);
            }
            d.block(projector(), 4, 0, 10, step(t, 1F, 0.4F) * 1.15F);
            d.block(t >= 1.6F && t < 9.5F ? Blocks.REDSTONE_BLOCK.defaultBlockState() : GRASS, 4, -1, 10);
            float height = 5F * (t < 9.5F ? step(t, 2F, 2F) : 1F - step(t, 9.5F, 1.2F));
            float hidden = step(t, 4.6F, 1F) * (1F - step(t, 9.3F, 0.6F));
            for(int x = 3; x <= 6; x++)
            {
                for(int y = 0; y <= 2; y++)
                {
                    for(int z = 3; z <= 6; z++)
                    {
                        if(x == 3 || x == 6 || z == 3 || z == 6 || y == 2)
                        {
                            d.block(y == 2 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), x, y, z,
                                    1F - Mth.clamp(hidden * 1.3F - y * 0.15F, 0F, 1F));
                        }
                    }
                }
            }
            if(height > 0.02F)
            {
                float x0 = 1F, x1 = 9F, z0 = 1F, z1 = 9F;
                int wall = cyan(0.2F - hidden * 0.15F);
                d.faceZ(x0, 0F, x1, height, z0, wall, true);
                d.faceZ(x0, 0F, x1, height, z1, wall, true);
                d.faceX(z0, 0F, z1, height, x0, wall, true);
                d.faceX(z0, 0F, z1, height, x1, wall, true);
                if(height >= 4.99F)
                {
                    d.faceY(x0, z0, x1, z1, height, wall, true);
                }
                int grid = cyan(0.9F - hidden * 0.6F);
                d.box(new AABB(x0, 0D, z0, x1, height, z1), grid);
                for(float y = 1F; y < height; y += 1F)
                {
                    d.box(new AABB(x0, y, z0, x1, y, z1), cyan(0.25F));
                }
            }
        }

        @Override
        public String caption(float t)
        {
            return t < 1F ? "Four wireless extensions in the corners of a rectangle"
                    : t < 2F ? "Linked to a projector in cloak mode, field on, and powered"
                    : t < 4.6F ? "Walls and a roof rise out of the ground"
                    : t < 9.3F ? "Everything inside is hidden, from every side and from above"
                    : "No signal: the field sinks away";
        }
    }

    /** Amplifiers next to the projector let a cloak grow bigger. */
    private static final class AmplifierScene implements Scene
    {
        @Override
        public float length()
        {
            return 9F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(4.5D, 0.5D, 4.5D);
        }

        @Override
        public float size()
        {
            return 9F;
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 9, 9, GRASS);
            d.block(projector(), 4, 0, 4);
            d.block(GlassRegistry.CLOAK_AMPLIFIER.defaultBlockState(), 5, 0, 4, step(t, 3F, 0.4F) * 1.15F);
            d.block(GlassRegistry.CLOAK_AMPLIFIER.defaultBlockState(), 3, 0, 4, step(t, 6F, 0.4F) * 1.15F);
            float half = 2F + step(t, 3.4F, 1F) + step(t, 6.4F, 1F);
            d.box(new AABB(4.5F - half, 0.02D, 4.5F - half, 4.5F + half, 0.02D + half * 0.75F, 4.5F + half), CYAN);
        }

        @Override
        public String caption(float t)
        {
            return t < 3F ? "No amplifier: a field up to 32x32, 16 high"
                    : t < 6F ? "One amplifier touching the projector: up to 48x48, 24 high"
                    : "Two amplifiers: up to 64x64, 32 high";
        }
    }

    // ---- Holograms ----

    /** The scanner captures a tree into a crystal; the projector, with emitters round it, projects it. */
    private static final class HologramScene implements Scene
    {
        @Override
        public float length()
        {
            return 12F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(5D, 1.5D, 3.5D);
        }

        @Override
        public float size()
        {
            return 10F;
        }

        private static void tree(SceneDraw d, float ox, float oy, float oz, float scale, boolean holo, float grow)
        {
            BlockState log = Blocks.OAK_LOG.defaultBlockState();
            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
            int[][] blocks = {
                    { 1, 0, 1, 0 }, { 1, 1, 1, 0 }, { 1, 2, 1, 0 },
                    { 0, 2, 1, 1 }, { 2, 2, 1, 1 }, { 1, 2, 0, 1 }, { 1, 2, 2, 1 }, { 1, 3, 1, 1 },
            };
            for(int[] b : blocks)
            {
                BlockState state = b[3] == 0 ? log : leaves;
                if(holo)
                {
                    if(grow > b[1] / 4F)
                    {
                        d.holo(state, ox + b[0] * scale, oy + b[1] * scale, oz + b[2] * scale, scale, 0xA040A0FF);
                    }
                }
                else
                {
                    d.block(state, ox + b[0], oy + b[1], oz + b[2]);
                }
            }
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 10, 8, GRASS);
            d.block(GlassRegistry.HOLO_SCANNER.defaultBlockState(), 1, 0, 4);
            tree(d, 1, 0, 0, 1F, false, 1F);
            if(t > 1F && t < 3.2F)
            {
                d.box(new AABB(1D, 0D, 0D, 4D, 4D, 3D), CYAN);
                float sweep = (t - 1F) / 2.2F * 4F;
                d.faceY(1F, 0F, 4F, 3F, sweep, cyan(0.35F), true);
            }
            //The crystal: out of the scanner, over to the projector.
            float made = step(t, 3.2F, 0.6F);
            float fly = step(t, 5.6F, 1F);
            if(made > 0F && t < 6.7F)
            {
                d.item(new ItemStack(GlassRegistry.PROJECTION_CRYSTAL), Mth.lerp(fly, 1.5F, 7.5F), 1.2F + made * 0.5F, Mth.lerp(fly, 4.5F, 4.5F),
                        0.7F, t * 90F);
            }
            d.block(GlassRegistry.HOLO_PROJECTOR.defaultBlockState(), 7, 0, 4, step(t, 4.3F, 0.4F) * 1.15F);
            int[][] emitters = { { 5, 2 }, { 9, 2 }, { 5, 6 }, { 9, 6 } };
            for(int i = 0; i < emitters.length; i++)
            {
                d.block(GlassRegistry.HOLO_EMITTER.defaultBlockState(), emitters[i][0], 0, emitters[i][1], step(t, 4.6F + i * 0.15F, 0.4F) * 1.15F);
            }
            float projected = step(t, 6.8F, 1.6F) * (1F - step(t, 11.2F, 0.6F));
            if(projected > 0F)
            {
                d.block(Blocks.REDSTONE_BLOCK.defaultBlockState(), 7, -1, 4);
                tree(d, 6.25F + 0.25F, 1.2F, 3.25F + 0.25F, 0.5F, true, projected);
                d.line(7.5F, 1F, 4.5F, 7.5F, 1.2F + 2F * projected, 4.5F, cyan(0.6F));
            }
        }

        @Override
        public String caption(float t)
        {
            return t < 1F ? "A holo scanner, set to the area with the tree"
                    : t < 3.2F ? "Scan: it costs one amethyst shard"
                    : t < 4.3F ? "Out comes a projection crystal"
                    : t < 5.6F ? "A holo projector, with emitters round it"
                    : t < 6.8F ? "Put the crystal in the projector"
                    : "Powered, it projects the tree: emitters light up its sides";
        }
    }

    // ---- Holo cube ----

    /** The little place the cube scans (and later shows in its bubble). */
    private static void place(SceneDraw d, float cx, float cz, float scale, boolean holo)
    {
        Object[][] blocks = {
                { Blocks.CHEST.defaultBlockState(), 1, 0, -1 }, { Blocks.POPPY.defaultBlockState(), -2, 0, -2 },
                { Blocks.DANDELION.defaultBlockState(), 2, 0, 2 }, { Blocks.PUMPKIN.defaultBlockState(), -2, 0, 1 },
                { Blocks.OAK_LOG.defaultBlockState(), 2, 0, -2 }, { Blocks.OAK_LOG.defaultBlockState(), 2, 1, -2 },
                { Blocks.OAK_LEAVES.defaultBlockState(), 2, 2, -2 }, { Blocks.HAY_BLOCK.defaultBlockState(), -1, 0, 2 },
        };
        for(Object[] b : blocks)
        {
            float x = cx + ((int)b[1] - 0.5F) * scale, y = (int)b[2] * scale, z = cz + ((int)b[3] - 0.5F) * scale;
            if(holo)
            {
                d.holo((BlockState)b[0], x, y, z, scale, 0xB070D8FF);
            }
            else
            {
                d.block((BlockState)b[0], x, y, z);
            }
        }
    }

    /** The cube rises and digitizes the ball of the world round it. */
    private static final class CubeScanScene implements Scene
    {
        @Override
        public float length()
        {
            return 9F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(4.5D, 1D, 4.5D);
        }

        @Override
        public float size()
        {
            return 9F;
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 9, 9, GRASS);
            place(d, 4.5F, 4.5F, 1F, false);
            float rise = step(t, 1F, 0.8F) * (1F - step(t, 7F, 0.8F));
            d.item(new ItemStack(GlassRegistry.HOLO_CUBE), 4.5F, 0.3F + rise * 0.9F, 4.5F, 0.6F, t * 40F);
            float scan = step(t, 2F, 3F);
            float fade = 1F - step(t, 5.5F, 1F);
            if(scan > 0F && fade > 0F)
            {
                d.sphere(4.5F, 0.5F, 4.5F, scan * 4.4F, cyan(0.9F * fade));
                for(int x = 1; x < 8; x++)
                {
                    for(int z = 1; z < 8; z++)
                    {
                        float dist = Mth.sqrt((x + 0.5F - 4.5F) * (x + 0.5F - 4.5F) + (z + 0.5F - 4.5F) * (z + 0.5F - 4.5F));
                        if(dist < scan * 4.4F && Mth.frac(Mth.sin(x * 12.9898F + z * 78.233F + Mth.floor(t * 8F)) * 43758.5F) > 0.6F)
                        {
                            d.faceY(x + 0.1F, z + 0.1F, x + 0.9F, z + 0.9F, 0.02F, cyan(0.5F * fade), true);
                        }
                    }
                }
            }
        }

        @Override
        public String caption(float t)
        {
            return t < 1F ? "Set the holo cube down and right-click it"
                    : t < 2F ? "It rises"
                    : t < 5.5F ? "The ball round it, fifteen blocks across, is digitized"
                    : "Blocks, chests, mobs, time and weather: all stored in the cube";
        }
    }

    /** A scanned cube set down elsewhere: its bubble grows, the scan inside as a miniature. */
    private static final class CubeBubbleScene implements Scene
    {
        @Override
        public float length()
        {
            return 9F;
        }

        @Override
        public Vec3 centre()
        {
            return new Vec3(4.5D, 1.2D, 4.5D);
        }

        @Override
        public float size()
        {
            return 9F;
        }

        @Override
        public void draw(SceneDraw d, float t)
        {
            d.ground(0, 0, 9, 9, Blocks.SAND.defaultBlockState());
            d.item(new ItemStack(GlassRegistry.HOLO_CUBE), 4.5F, 0.3F, 4.5F, 0.6F, 0F);
            float grow = step(t, 1F, 2.2F) * (1F - step(t, 7.3F, 1.2F));
            if(grow > 0F)
            {
                d.sphere(4.5F, 0.5F, 4.5F, grow * 3.4F, cyan(0.8F));
                if(grow > 0.6F)
                {
                    place(d, 4.5F, 4.5F, 2F / 3F, true);
                    for(int x = -2; x <= 2; x++)
                    {
                        for(int z = -2; z <= 2; z++)
                        {
                            d.faceY(4.5F + (x - 0.5F) * 2F / 3F, 4.5F + (z - 0.5F) * 2F / 3F, 4.5F + (x + 0.5F) * 2F / 3F,
                                    4.5F + (z + 0.5F) * 2F / 3F, 0.01F, cyan(0.25F), true);
                        }
                    }
                }
            }
        }

        @Override
        public String caption(float t)
        {
            return t < 1F ? "A scanned cube, set down somewhere else"
                    : t < 3.2F ? "Right-click it: a bubble grows out of it"
                    : t < 7.3F ? "Inside, the scan as a miniature: stand on its ground, walk through the rest"
                    : "Right-click again: it shrinks away. The real world was never touched";
        }
    }
}
