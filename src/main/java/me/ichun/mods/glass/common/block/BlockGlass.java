package me.ichun.mods.glass.common.block;

import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class BlockGlass extends net.minecraft.block.BlockGlass implements ITileEntityProvider
{
    public static final PropertyBool MASTER = PropertyBool.create("master");

    public BlockGlass(Material materialIn, boolean ignoreSimilarity)
    {
        super(materialIn, ignoreSimilarity);
        this.setCreativeTab(CreativeTabs.REDSTONE);
        this.setDefaultState(this.blockState.getBaseState().withProperty(MASTER, false));
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
            //TODO propagate &&             checkFacesToTurnOn(base);
            TileEntity tileentity = worldIn.getTileEntity(pos);
            if(tileentity instanceof TileEntityGlassBase)
            {
                TileEntityGlassBase base = (TileEntityGlassBase)tileentity;

                //new base placed
                TileEntity tileentity1 = worldIn.getTileEntity(fromPos);
                if(tileentity1 instanceof TileEntityGlassBase)
                {
                    TileEntityGlassBase base1 = (TileEntityGlassBase)tileentity1;
                    if(base.active)
                    {
                        base.checkFacesToTurnOn(base);
                        if(!base1.active || base1.channel.equals(base.channel))
                        {
                            base1.bePropagatedTo(base, base.channel, base.active);
                        }
                    }
                }

                //block was removed
                if(blockIn == this)
                {
                    if(base.active)
                    {
                        int distance = base.distance;
                        for(EnumFacing facing : EnumFacing.VALUES)
                        {
                            BlockPos pos1 = pos.offset(facing);
                            TileEntity te = worldIn.getTileEntity(pos1);
                            if(te instanceof TileEntityGlassBase)
                            {
                                TileEntityGlassBase base1 = (TileEntityGlassBase)te;
                                if(base1.active && base1.channel.equalsIgnoreCase(base.channel) && base1.distance < distance)
                                {
                                    distance = base1.distance;
                                }
                            }
                        }
                        if(distance == base.distance)
                        {
                            base.bePropagatedTo(base, base.channel, false); //turn off if we can't find a close base.
                        }
                    }
                }

                if(base instanceof TileEntityGlassMaster)
                {
                    TileEntityGlassMaster player = (TileEntityGlassMaster)base;
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

    @Override
    public BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, MASTER);
    }

    @Override
    public IBlockState getStateFromMeta(int meta)
    {
        return this.getDefaultState().withProperty(MASTER, meta == 1);
    }

    @Override
    public int getMetaFromState(IBlockState state)
    {
        return state.getValue(MASTER) ? 1 : 0;
    }
}
