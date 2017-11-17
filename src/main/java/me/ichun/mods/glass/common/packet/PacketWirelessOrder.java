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

import java.util.ArrayList;

public class PacketWirelessOrder extends AbstractPacket
{
    public BlockPos pos;
    public ArrayList<BlockPos> channel;

    public PacketWirelessOrder(){}

    public PacketWirelessOrder(BlockPos pos, ArrayList<BlockPos> channel)
    {
        this.pos = pos;
        this.channel = channel;
    }

    @Override
    public void writeTo(ByteBuf buf)
    {
        PacketBuffer buffer = new PacketBuffer(buf);
        buffer.writeBlockPos(pos);
        buffer.writeInt(channel.size());
        for(BlockPos pos : channel)
        {
            buffer.writeBlockPos(pos);
        }
    }

    @Override
    public void readFrom(ByteBuf buf)
    {
        PacketBuffer buffer = new PacketBuffer(buf);
        pos = buffer.readBlockPos();
        channel = new ArrayList<>();
        int count = buffer.readInt();
        for(int i = 0; i < count; i++)
        {
            channel.add(buffer.readBlockPos());
        }
    }

    @Override
    public void execute(Side side, EntityPlayer player)
    {
        TileEntity te = player.getEntityWorld().getTileEntity(pos);
        if(te instanceof TileEntityGlassMaster && !((TileEntityGlassMaster)te).active)
        {
            ((TileEntityGlassMaster)te).wirelessPos = channel;
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
