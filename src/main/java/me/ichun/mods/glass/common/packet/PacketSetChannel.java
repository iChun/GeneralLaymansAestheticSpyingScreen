package me.ichun.mods.glass.common.packet;

import io.netty.buffer.ByteBuf;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.ichunutil.common.core.network.AbstractPacket;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;

public class PacketSetChannel extends AbstractPacket
{
    public BlockPos pos;
    public String channel;

    public PacketSetChannel(){}

    public PacketSetChannel(BlockPos pos, String channel)
    {
        this.pos = pos;
        this.channel = channel;
    }

    @Override
    public void writeTo(ByteBuf buf)
    {
        PacketBuffer buffer = new PacketBuffer(buf);
        buffer.writeBlockPos(pos);
        ByteBufUtils.writeUTF8String(buffer, channel);
    }

    @Override
    public void readFrom(ByteBuf buf)
    {
        PacketBuffer buffer = new PacketBuffer(buf);
        pos = buffer.readBlockPos();
        channel = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void execute(Side side, EntityPlayer player)
    {
        TileEntity te = player.getEntityWorld().getTileEntity(pos);
        if(te instanceof TileEntityGlassTerminal)
        {
            ((TileEntityGlassTerminal)te).channelName = channel;
            te.markDirty();

            IBlockState state = player.getEntityWorld().getBlockState(pos);
            player.getEntityWorld().notifyBlockUpdate(pos, state, state, 3);
        }
        else if(te instanceof TileEntityGlassMaster && !((TileEntityGlassMaster)te).active)
        {
            ((TileEntityGlassMaster)te).setChannel = channel;
            te.markDirty();

            IBlockState state = player.getEntityWorld().getBlockState(pos);
            player.getEntityWorld().notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public Side receivingSide()
    {
        return Side.SERVER;
    }
}
