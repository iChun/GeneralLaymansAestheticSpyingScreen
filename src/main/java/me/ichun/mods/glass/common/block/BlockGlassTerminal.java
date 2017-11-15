package me.ichun.mods.glass.common.block;

import me.ichun.mods.glass.client.gui.GuiChannelSetter;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

public class BlockGlassTerminal extends BlockObsidian implements ITileEntityProvider
{
    public BlockGlassTerminal()
    {
        super();
        this.setCreativeTab(CreativeTabs.REDSTONE);
        this.setSoundType(SoundType.STONE);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune)
    {
        return Item.getItemFromBlock(this);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return new TileEntityGlassTerminal();
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        if(worldIn.isRemote)
        {
            TileEntity te = worldIn.getTileEntity(pos);
            if(te instanceof TileEntityGlassTerminal)
            {
                openGui(((TileEntityGlassTerminal)te));
            }
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
        if(!worldIn.isRemote)
        {
            TileEntity te = worldIn.getTileEntity(pos);
            if(te instanceof TileEntityGlassTerminal)
            {
                TileEntityGlassTerminal terminal = (TileEntityGlassTerminal)te;
                terminal.facing = EnumFacing.getDirectionFromEntityLiving(pos, placer);
                terminal.markDirty();
            }
        }
    }

    @Override
    public boolean isFullCube(IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos)
    {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state)
    {
        return false;
    }

    @SideOnly(Side.CLIENT)
    public void openGui(TileEntityGlassTerminal terminal)
    {
        FMLClientHandler.instance().displayGuiScreen(Minecraft.getMinecraft().player, new GuiChannelSetter(terminal));
    }
}
