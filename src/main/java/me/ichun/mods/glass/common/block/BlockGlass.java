package me.ichun.mods.glass.common.block;

import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class BlockGlass extends net.minecraft.block.BlockGlass implements ITileEntityProvider
{
    public BlockGlass(Material materialIn, boolean ignoreSimilarity)
    {
        super(materialIn, ignoreSimilarity);
        this.setCreativeTab(CreativeTabs.REDSTONE);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return meta == 1 ? new TileEntityGlassMaster() : new TileEntityGlassBase();
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state)
    {
        super.breakBlock(worldIn, pos, state);
    }

    @Override
    public void getSubBlocks(CreativeTabs itemIn, NonNullList<ItemStack> items)
    {
        items.add(new ItemStack(this, 1, 1));
        items.add(new ItemStack(this, 1, 0));
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos)
    {
        if(!worldIn.isRemote)
        {
            //TODO propagate
            TileEntity tileentity = worldIn.getTileEntity(pos);
            if(tileentity instanceof TileEntityGlassMaster)
            {
                TileEntityGlassMaster player = (TileEntityGlassMaster)tileentity;
                boolean flag = worldIn.isBlockPowered(pos);

                if(player.powered != flag)
                {
                    player.changeRedstoneState(flag);
                    player.powered = flag;
                }
            }
        }
    }
}
