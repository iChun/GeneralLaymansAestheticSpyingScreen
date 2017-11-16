package me.ichun.mods.glass.common.packet;

import io.netty.buffer.ByteBuf;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassWireless;
import me.ichun.mods.ichunutil.common.core.network.AbstractPacket;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;

public class PacketSetProjector extends AbstractPacket
{
    public BlockPos wirelessPos;
    public BlockPos masterPos;

    public PacketSetProjector(){}

    public PacketSetProjector(BlockPos wirelessPos, BlockPos masterPos)
    {
        this.wirelessPos = wirelessPos;
        this.masterPos = masterPos;
    }

    @Override
    public void writeTo(ByteBuf buf)
    {
        PacketBuffer buffer = new PacketBuffer(buf);
        buffer.writeBlockPos(wirelessPos);
        buffer.writeBlockPos(masterPos);
    }

    @Override
    public void readFrom(ByteBuf buf)
    {
        PacketBuffer buffer = new PacketBuffer(buf);
        wirelessPos = buffer.readBlockPos();
        masterPos = buffer.readBlockPos();
    }

    @Override
    public void execute(Side side, EntityPlayer player)
    {
        TileEntity te = player.getEntityWorld().getTileEntity(masterPos);
        if(player.getEntityWorld().getTileEntity(wirelessPos) instanceof TileEntityGlassWireless && te instanceof TileEntityGlassMaster)
        {
            if(!((TileEntityGlassMaster)te).wirelessPos.contains(wirelessPos))
            {
                ((TileEntityGlassMaster)te).wirelessPos.add(wirelessPos);
                te.markDirty();

                IBlockState state = player.getEntityWorld().getBlockState(masterPos);
                player.getEntityWorld().notifyBlockUpdate(masterPos, state, state, 3);
            }
        }
    }

    @Override
    public Side receivingSide()
    {
        return Side.SERVER;
    }
}
