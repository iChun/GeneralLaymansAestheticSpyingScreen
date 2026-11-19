package ryzlar.glass.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.client.render.CloakFill;
import ryzlar.glass.client.sound.GlassAmbienceSound;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Links a channel's projector to its terminal and maps positions and directions from one to the other.
 * Port of TerminalPlacement, which was a WorldPortal from iChunUtil.
 * <p>
 * The projector's screen face is mapped onto the terminal's front face: standing in front of the screen is like
 * standing behind the terminal, looking out in the direction the terminal faces.
 * <p>
 * A cloaking projector has no terminal: the view is the player's own, and only the clip plane is moved, to the back of
 * the part of the world being hidden. Each side of the screen hides what is behind that side, so a cloak has one
 * placement (and one view) per side, see {@link #forFace}.
 */
public class TerminalPlacement
{
    /** The key of the view this placement is drawn into: the screen key, plus "/side" for a side of a cloak. */
    public final String channel;
    /** The screen key. */
    public final String screen;
    public final GlassProjectorBlockEntity master;
    /** The camera (terminal) being shown, as the projector reported it. */
    private final String cameraSignature;
    public final boolean cloak;

    public boolean playedAmbience;
    /** True when the screen is one flat face, so everything between the camera and the terminal's face can be clipped. */
    private boolean cullRender = true;

    private final Vec3 sourceOrigin;
    private final Vec3 destOrigin;
    private final Matrix3f sourceToDest;
    private final Quaternionf rotation;
    private final Direction placingFace;
    private Vec3 clipPoint;
    private Vector3f clipNormal;

    /** Cloak side: its ground, rebuilt from the surroundings (null for the top and bottom sides). */
    @org.jetbrains.annotations.Nullable
    private CloakFill fill;

    /** Cloak side: the part of the world this side hides (the screen's glass included). */
    private AABB hidden = new AABB(0, 0, 0, 0, 0, 0);
    /** Cloak: the placement of each side of the screen, made when first needed. */
    private final Map<Direction, TerminalPlacement> faces = new EnumMap<>(Direction.class);

    public TerminalPlacement(String channel, GlassProjectorBlockEntity master)
    {
        this.channel = channel;
        this.screen = channel;
        this.master = master;
        this.cameraSignature = master.cameraSignature();
        this.placingFace = master.placingFace;
        this.cloak = master.cloak;

        if(cloak)
        {
            sourceOrigin = Vec3.ZERO;
            destOrigin = Vec3.ZERO;
            sourceToDest = new Matrix3f();
            rotation = new Quaternionf();
            clipPoint = Vec3.ZERO;
            clipNormal = new Vector3f(0F, 0F, 1F);
        }
        else
        {
            Direction terminalFacing = master.camFacing;
            sourceOrigin = Vec3.atCenterOf(master.getBlockPos());
            destOrigin = Vec3.atCenterOf(master.camPos).add(Vec3.atLowerCornerOf(terminalFacing.getUnitVec3i()));

            Matrix3f source = frame(placingFace.getOpposite());
            Matrix3f dest = frame(terminalFacing);
            sourceToDest = new Matrix3f(dest).mul(new Matrix3f(source).transpose());
            rotation = sourceToDest.getNormalizedRotation(new Quaternionf());

            //The terminal's front face, pulled back a hair towards the camera. A block right in front of the terminal has
            //a face lying exactly on that front face (the terminal does not hide it); with the plane there too, rounding
            //clipped random bits of it every frame and it flickered like z-fighting.
            clipPoint = destOrigin.subtract(Vec3.atLowerCornerOf(terminalFacing.getUnitVec3i()).scale(0.5D + CLIP_MARGIN));
            clipNormal = new Vector3f(terminalFacing.getStepX(), terminalFacing.getStepY(), terminalFacing.getStepZ());
        }

        onGlassChanged();
    }

    /**
     * A fixed camera with no screen (a GLASS tablet's view): looking out of a terminal's front face, the way it faces,
     * whatever the player does. See {@link TabletPlacement}.
     */
    protected TerminalPlacement(String key, BlockPos terminal, Direction facing)
    {
        this.channel = key;
        this.screen = key;
        this.master = null;
        this.cameraSignature = "";
        this.placingFace = facing;
        this.cloak = false;
        this.playedAmbience = true;
        this.sourceOrigin = Vec3.ZERO;
        this.destOrigin = Vec3.atCenterOf(terminal).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.52D));
        this.sourceToDest = new Matrix3f();
        float yaw = switch(facing)
        {
            case WEST -> 90F;
            case NORTH -> 180F;
            case EAST -> -90F;
            default -> 0F;
        };
        float pitch = facing == Direction.UP ? -90F : facing == Direction.DOWN ? 90F : 0F;
        //As the game's camera turns for a yaw and pitch.
        this.rotation = new Quaternionf().rotationYXZ((float)Math.PI - yaw * net.minecraft.util.Mth.DEG_TO_RAD, -pitch * net.minecraft.util.Mth.DEG_TO_RAD, 0F);
        this.clipPoint = Vec3.atCenterOf(terminal).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.5D - CLIP_MARGIN));
        this.clipNormal = new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        this.cullRender = true;
    }

    /** The fixed camera's eye (see the fixed camera constructor). */
    protected Vec3 fixedEye()
    {
        return destOrigin;
    }

    /** The fixed camera's turn. */
    protected Quaternionf fixedRotation()
    {
        return new Quaternionf(rotation);
    }

    /** One side of a cloak. */
    private TerminalPlacement(TerminalPlacement cloak, Direction side)
    {
        this.channel = cloak.screen + "/" + side.getSerializedName();
        this.screen = cloak.screen;
        this.master = cloak.master;
        this.cameraSignature = cloak.cameraSignature;
        this.placingFace = cloak.placingFace;
        this.cloak = true;
        this.sourceOrigin = Vec3.ZERO;
        this.destOrigin = Vec3.ZERO;
        this.sourceToDest = new Matrix3f();
        this.rotation = new Quaternionf();
        this.playedAmbience = true; //a cloak is silent
        placeCloakPlane(side);
    }

    /** Columns: right, up, forward. */
    private static Matrix3f frame(Direction forward)
    {
        Vector3f f = new Vector3f(forward.getStepX(), forward.getStepY(), forward.getStepZ());
        Vector3f u = forward.getAxis() == Direction.Axis.Y ? new Vector3f(0F, 0F, -1F) : new Vector3f(0F, 1F, 0F);
        Vector3f r = f.cross(u, new Vector3f());
        return new Matrix3f(r, u, f);
    }

    public boolean isValid()
    {
        return isSameScreen() && cameraSignature.equals(master.cameraSignature());
    }

    /** Still the same projector showing the same screen (the camera may have changed). */
    public boolean isSameScreen()
    {
        return !master.isRemoved() && master.active && master.placingFace == placingFace && screen.equals(master.channel);
    }

    public Set<GlassBaseBlockEntity> activeBlocks()
    {
        return GlassClientState.INSTANCE.getActiveGlass(screen);
    }

    /** Port of TerminalPlacement.generateActiveFaces. */
    public void onGlassChanged()
    {
        if(cloak)
        {
            //The screen changed shape, so the hidden part of the world did too.
            for(Map.Entry<Direction, TerminalPlacement> e : faces.entrySet())
            {
                e.getValue().placeCloakPlane(e.getKey());
            }
            cullRender = true;
            return;
        }
        boolean cull = true;
        Set<Direction> faces = new HashSet<>();
        for(GlassBaseBlockEntity base : activeBlocks())
        {
            if(base instanceof GlassProjectorBlockEntity projector && !projector.wirelessPos.isEmpty())
            {
                cull = false;
                break;
            }
            if(base.active)
            {
                faces.addAll(base.activeFaces);
                if(faces.size() > 1)
                {
                    cull = false;
                    break;
                }
            }
        }
        cullRender = cull;
    }

    /** Cloak: the sides made so far (one per side seen). */
    public Iterable<TerminalPlacement> cloakSides()
    {
        return faces.values();
    }

    /** Cloak: the placement of the view for one side of the screen. */
    public TerminalPlacement forFace(Direction side)
    {
        return cloak ? faces.computeIfAbsent(side, s -> new TerminalPlacement(this, s)) : this;
    }

    /**
     * Cloak: what one side of the screen hides is everything from that side's outer face back to the far side of the
     * screen (a glass box hides what is inside it), or at least the projector's cloak depth (a glass wall in front of a
     * base). Whatever lies further back shows through.
     */
    private void placeCloakPlane(Direction side)
    {
        AABB field = master.cloakFieldBox();
        if(field != null)
        {
            placeFieldPlane(side, field);
            return;
        }
        double outer = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for(GlassBaseBlockEntity base : activeBlocks())
        {
            double along = along(base.getBlockPos(), side);
            min = Math.min(min, along);
            max = Math.max(max, along);
            if(base.activeFaces.contains(side))
            {
                outer = Math.max(outer, along);
            }
        }
        if(outer == Double.NEGATIVE_INFINITY)
        {
            outer = along(master.getBlockPos(), side);
            min = Math.min(min, outer);
            max = Math.max(max, outer);
        }
        double depth = Math.max(master.cloakDepth, max - min + 1D);
        //Distance of the plane along the side's normal: the outer face (block centre + 0.5) minus the depth, plus a hair
        //towards the viewer so the faces of blocks right behind the hidden part are kept (see the camera clip plane).
        double plane = outer + 0.5D - depth + CLIP_MARGIN;
        Vec3 n = Vec3.atLowerCornerOf(side.getUnitVec3i());
        Vec3 centre = Vec3.atCenterOf(master.getBlockPos());
        clipPoint = centre.add(n.scale(plane - centre.dot(n)));
        clipNormal = new Vector3f(-side.getStepX(), -side.getStepY(), -side.getStepZ());
        cullRender = true;
        hidden = hiddenBox(side, plane - CLIP_MARGIN, outer + 0.5D);
        CloakFill.Area area = side.getAxis().isHorizontal() ? areaOf(side, outer, (int)Math.round(depth)) : null;
        fill = area == null ? null : new CloakFill(area);
    }

    /**
     * Cloak field: each side of the box hides the whole box. Its plane is the far side of the box, its ground the box's
     * floor (for the roof, the whole footprint).
     */
    private void placeFieldPlane(Direction side, AABB box)
    {
        Vec3 n = Vec3.atLowerCornerOf(side.getUnitVec3i());
        //The box's far side along the side's normal, pulled a hair towards the viewer (see the camera clip plane).
        double inner = Math.min(n.dot(new Vec3(box.minX, box.minY, box.minZ)), n.dot(new Vec3(box.maxX, box.maxY, box.maxZ)));
        double plane = inner + CLIP_MARGIN;
        Vec3 centre = box.getCenter();
        clipPoint = centre.add(n.scale(plane - centre.dot(n)));
        clipNormal = new Vector3f(-side.getStepX(), -side.getStepY(), -side.getStepZ());
        cullRender = true;
        hidden = box;
        if(side.getAxis().isHorizontal())
        {
            fill = new CloakFill(fieldArea(side, box));
        }
        else if(side == Direction.UP)
        {
            //The roof is only looked through from right above the field (from beside it, it shows a wall's view). Its
            //plane is the floor, which cuts away everything around the field too, as far as a ray through the roof can
            //run sideways before it comes down: draw the world back in all around.
            fill = new CloakFill(fieldArea(Direction.SOUTH, box).allAround(Math.min(CloakFill.SPREAD, (int)Math.ceil(box.maxY - box.minY) + 4)));
        }
        else
        {
            fill = null;
        }
    }

    /** The ground of a cloak field, seen through one side: the row under the box, back through all of it. */
    private static CloakFill.Area fieldArea(Direction side, AABB box)
    {
        Direction across = side.getClockWise();
        int minX = (int)Math.floor(box.minX), maxX = (int)Math.floor(box.maxX) - 1;
        int minZ = (int)Math.floor(box.minZ), maxZ = (int)Math.floor(box.maxZ) - 1;
        int y = (int)Math.floor(box.minY);
        int a1 = minX * across.getStepX() + minZ * across.getStepZ();
        int a2 = maxX * across.getStepX() + maxZ * across.getStepZ();
        int minAcross = Math.min(a1, a2), maxAcross = Math.max(a1, a2);
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int outerBlock = side.getAxis() == Direction.Axis.X ? (positive ? maxX : minX) : (positive ? maxZ : minZ);
        int depth = side.getAxis() == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int middle = (minAcross + maxAcross) / 2 * (across.getStepX() + across.getStepZ());
        int front = outerBlock + side.getStepX() + side.getStepZ();
        BlockPos lightPos = new BlockPos(side.getAxis() == Direction.Axis.X ? front : middle, y, side.getAxis() == Direction.Axis.Z ? front : middle);
        return CloakFill.Area.side(side, y - 1, minAcross, maxAcross, outerBlock, depth, box, lightPos);
    }

    /** The screen's bounding box, stretched along the side's axis from the clip plane to the outer face. */
    private AABB hiddenBox(Direction side, double from, double to)
    {
        AABB box = null;
        for(GlassBaseBlockEntity base : activeBlocks())
        {
            AABB block = new AABB(base.getBlockPos());
            box = box == null ? block : box.minmax(block);
        }
        if(box == null)
        {
            box = new AABB(master.getBlockPos());
        }
        //Along the side's normal n, the hidden part runs from "from" to "to" (n . p); turn that into world coordinates.
        double a = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? from : -to;
        double b = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? to : -from;
        return switch(side.getAxis())
        {
            case X -> new AABB(a, box.minY, box.minZ, b, box.maxY, box.maxZ);
            case Y -> new AABB(box.minX, a, box.minZ, box.maxX, b, box.maxZ);
            case Z -> new AABB(box.minX, box.minY, a, box.maxX, box.maxY, b);
        };
    }

    /** Cloak side: true if this block lies in the part of the world this side hides. */
    public boolean hides(BlockPos pos)
    {
        return hidden.contains(Vec3.atCenterOf(pos));
    }

    /** Cloak side: true if the eye is inside the hidden part (then it sees the real thing). */
    public boolean contains(Vec3 eye)
    {
        return hidden.contains(eye);
    }

    /** Where this side's ground is: the block row under the screen, from its outer glass back through the hidden part. */
    @org.jetbrains.annotations.Nullable
    private CloakFill.Area areaOf(Direction side, double outer, int depth)
    {
        Direction across = side.getClockWise();
        int minY = Integer.MAX_VALUE;
        int minAcross = Integer.MAX_VALUE;
        int maxAcross = Integer.MIN_VALUE;
        for(GlassBaseBlockEntity base : activeBlocks())
        {
            BlockPos pos = base.getBlockPos();
            minY = Math.min(minY, pos.getY());
            int a = pos.getX() * across.getStepX() + pos.getZ() * across.getStepZ();
            minAcross = Math.min(minAcross, a);
            maxAcross = Math.max(maxAcross, a);
        }
        if(minY == Integer.MAX_VALUE)
        {
            return null;
        }
        //The outermost glass block, as a block coordinate along the side's axis.
        int outerBlock = (int)Math.floor(outer * (side.getStepX() + side.getStepZ()));
        int middle = (minAcross + maxAcross) / 2 * (across.getStepX() + across.getStepZ());
        int front = outerBlock + side.getStepX() + side.getStepZ();
        //Where the light is read: just outside the screen's bottom row (inside, the hidden blocks are dark).
        BlockPos lightPos = new BlockPos(side.getAxis() == Direction.Axis.X ? front : middle, minY, side.getAxis() == Direction.Axis.Z ? front : middle);
        return CloakFill.Area.side(side, minY - 1, minAcross, maxAcross, outerBlock, depth, hidden, lightPos);
    }

    /** Cloak side: its ground, rebuilt from the surroundings. Null for the top and bottom, and for camera screens. */
    @org.jetbrains.annotations.Nullable
    public CloakFill cloakFill()
    {
        return fill;
    }

    /** The block centre's distance along a side's outward normal (larger is further out on that side). */
    private static double along(BlockPos pos, Direction side)
    {
        return Vec3.atCenterOf(pos).dot(Vec3.atLowerCornerOf(side.getUnitVec3i()));
    }

    public boolean cullRender()
    {
        return cullRender;
    }

    public Vec3 mapPosition(Vec3 pos)
    {
        if(cloak)
        {
            return pos;
        }
        Vector3f local = new Vector3f((float)(pos.x - sourceOrigin.x), (float)(pos.y - sourceOrigin.y), (float)(pos.z - sourceOrigin.z));
        sourceToDest.transform(local);
        return destOrigin.add(local.x, local.y, local.z);
    }

    public Quaternionf mapRotation(Quaternionf cameraRotation)
    {
        return new Quaternionf(rotation).mul(cameraRotation);
    }

    /** A point on the clip plane: everything on the camera's side of it is not drawn. */
    public Vec3 clipPlanePoint()
    {
        return clipPoint;
    }

    /** How far the clip plane sits in front of what must stay visible, in blocks. Too small to see as a gap. */
    private static final double CLIP_MARGIN = 1.0D / 256.0D;

    /** Points away from the camera, into the part of the world the screen shows. */
    public Vector3f clipPlaneNormal()
    {
        return new Vector3f(clipNormal);
    }

    public void playAmbienceOnce()
    {
        if(!playedAmbience && !cloak)
        {
            playedAmbience = true;
            Minecraft.getInstance().getSoundManager().play(new GlassAmbienceSound(GlassRegistry.SOUND_AMBIENCE, SoundSource.BLOCKS, 0.015F, 0.6F, this));
        }
    }
}
