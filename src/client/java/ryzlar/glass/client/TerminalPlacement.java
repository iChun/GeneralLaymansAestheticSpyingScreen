package ryzlar.glass.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.client.sound.GlassAmbienceSound;

import java.util.HashSet;
import java.util.Set;

/**
 * Links a channel's projector to its terminal and maps positions and directions from one to the other.
 * Port of TerminalPlacement, which was a WorldPortal from iChunUtil.
 * <p>
 * The projector's screen face is mapped onto the terminal's front face: standing in front of the screen is like
 * standing behind the terminal, looking out in the direction the terminal faces.
 */
public class TerminalPlacement
{
    public final String channel;
    public final GlassProjectorBlockEntity master;
    /** The camera (terminal) being shown, as the projector reported it. */
    private final String cameraSignature;

    public boolean playedAmbience;
    /** True when the screen is one flat face, so everything between the camera and the terminal's face can be clipped. */
    private boolean cullRender = true;

    private final Vec3 sourceOrigin;
    private final Vec3 destOrigin;
    private final Matrix3f sourceToDest;
    private final Quaternionf rotation;
    private final Direction placingFace;
    private final Direction terminalFacing;

    public TerminalPlacement(String channel, GlassProjectorBlockEntity master)
    {
        this.channel = channel;
        this.master = master;
        this.cameraSignature = master.cameraSignature();
        this.placingFace = master.placingFace;
        this.terminalFacing = master.camFacing;

        sourceOrigin = Vec3.atCenterOf(master.getBlockPos());
        destOrigin = Vec3.atCenterOf(master.camPos).add(Vec3.atLowerCornerOf(terminalFacing.getUnitVec3i()));

        Matrix3f source = frame(placingFace.getOpposite());
        Matrix3f dest = frame(terminalFacing);
        sourceToDest = new Matrix3f(dest).mul(new Matrix3f(source).transpose());
        rotation = sourceToDest.getNormalizedRotation(new Quaternionf());

        onGlassChanged();
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
        return !master.isRemoved() && master.active && master.placingFace == placingFace && channel.equals(master.channel);
    }

    public Set<GlassBaseBlockEntity> activeBlocks()
    {
        return GlassClientState.INSTANCE.getActiveGlass(channel);
    }

    /** Port of TerminalPlacement.generateActiveFaces. */
    public void onGlassChanged()
    {
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

    public boolean cullRender()
    {
        return cullRender;
    }

    public Vec3 mapPosition(Vec3 pos)
    {
        Vector3f local = new Vector3f((float)(pos.x - sourceOrigin.x), (float)(pos.y - sourceOrigin.y), (float)(pos.z - sourceOrigin.z));
        sourceToDest.transform(local);
        return destOrigin.add(local.x, local.y, local.z);
    }

    public Quaternionf mapRotation(Quaternionf cameraRotation)
    {
        return new Quaternionf(rotation).mul(cameraRotation);
    }

    /** A point on the terminal's front face: the screen plane as seen from the terminal's side. */
    public Vec3 clipPlanePoint()
    {
        return destOrigin.subtract(Vec3.atLowerCornerOf(terminalFacing.getUnitVec3i()).scale(0.5D));
    }

    /** Points away from the camera, into the part of the world the screen shows. */
    public Vector3f clipPlaneNormal()
    {
        return new Vector3f(terminalFacing.getStepX(), terminalFacing.getStepY(), terminalFacing.getStepZ());
    }

    public void playAmbienceOnce()
    {
        if(!playedAmbience)
        {
            playedAmbience = true;
            Minecraft.getInstance().getSoundManager().play(new GlassAmbienceSound(GlassRegistry.SOUND_AMBIENCE, SoundSource.BLOCKS, 0.015F, 0.6F, this));
        }
    }
}
