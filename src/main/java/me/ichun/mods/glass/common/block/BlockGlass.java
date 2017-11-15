package me.ichun.mods.glass.common.block;

import me.ichun.mods.glass.client.gui.GuiChannelSetterProjector;
import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassWireless;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Random;

public class BlockGlass extends net.minecraft.block.BlockGlass implements ITileEntityProvider
{
    public static final PropertyBool MASTER = PropertyBool.create("master");
    public static final PropertyBool WIRELESS = PropertyBool.create("wireless");


    public BlockGlass(Material materialIn, boolean ignoreSimilarity)
    {
        super(materialIn, ignoreSimilarity);
        this.setCreativeTab(CreativeTabs.REDSTONE);
        this.setDefaultState(this.blockState.getBaseState().withProperty(MASTER, false).withProperty(WIRELESS, false));
    }

    @Override
    public int quantityDropped(Random random)
    {
        return 1;
    }

    @Override
    public int damageDropped(IBlockState state)
    {
        return getMetaFromState(state);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
            TileEntity te = worldIn.getTileEntity(pos);
            if(te instanceof TileEntityGlassMaster && !((TileEntityGlassMaster)te).active)
            {
                if(worldIn.isRemote)
                {
                    openGui(((TileEntityGlassMaster)te));
                }
                return true;
            }
        return false;
    }

    @Override
    public void onBlockClicked(World worldIn, BlockPos pos, EntityPlayer playerIn)
    {
        if(worldIn.isRemote)
        {
            TileEntity te = worldIn.getTileEntity(pos);
            if(te instanceof TileEntityGlassBase)
            {
                TileEntityGlassBase base = (TileEntityGlassBase)te;
                if(base.active)
                {
                    GeneralLaymansAestheticSpyingScreen.eventHandlerClient.clickedPos = pos;

                    base.fadeoutTime = TileEntityGlassBase.FADEOUT_TIME;
                    base.fadePropagate = TileEntityGlassBase.PROPAGATE_TIME;
                    base.fadeDistance = 2;
                    base.fadePropagate();
                }
            }
        }
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta)
    {
        return meta == 1 ? new TileEntityGlassMaster() : meta == 2 ? new TileEntityGlassWireless() : new TileEntityGlassBase();
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
//        items.add(new ItemStack(this, 1, 2)); //wireless
        items.add(new ItemStack(this, 1, 0));
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos)
    {
        if(!worldIn.isRemote)
        {
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
                        HashSet<EnumFacing> propagationFaces = new HashSet<>();
                        for(EnumFacing facing : base.activeFaces)
                        {
                            propagationFaces.addAll(TileEntityGlassBase.PROPAGATION_FACES.get(facing));
                        }
                        for(EnumFacing facing : propagationFaces)
                        {
                            BlockPos pos1 = pos.offset(facing, -1);
                            TileEntity te = worldIn.getTileEntity(pos1);
                            if(te instanceof TileEntityGlassBase)
                            {
                                TileEntityGlassBase base2 = (TileEntityGlassBase)te;
                                if(base2.active && base2.channel.equals(base.channel) && base2.distance < base.distance) //this is the origin
                                {
                                    base.checkFacesToTurnOn(base2);
                                    if(!base1.active || base1.channel.equals(base.channel))
                                    {
                                        base1.bePropagatedTo(base, base.channel, base.active);
                                    }
                                }
                            }
                        }
                    }
                }

                //block was removed
                if(blockIn == this)
                {
                    if(base.active)
                    {
                        int distance = base.distance;
                        HashSet<EnumFacing> propagationFaces = new HashSet<>();
                        for(EnumFacing facing : base.activeFaces)
                        {
                            propagationFaces.addAll(TileEntityGlassBase.PROPAGATION_FACES.get(facing));
                        }
                        for(EnumFacing facing : propagationFaces)
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
                        else
                        {
                            for(EnumFacing facing : propagationFaces)
                            {
                                BlockPos pos1 = pos.offset(facing, -1);
                                TileEntity te = worldIn.getTileEntity(pos1);
                                if(te instanceof TileEntityGlassBase)
                                {
                                    TileEntityGlassBase base2 = (TileEntityGlassBase)te;
                                    if(base2.active && base2.channel.equals(base.channel) && base2.distance < base.distance) //this is the origin
                                    {
                                        base.checkFacesToTurnOn(base2);
                                    }
                                }
                            }
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
        return new BlockStateContainer(this, MASTER, WIRELESS);
    }

    @Override
    public IBlockState getStateFromMeta(int meta)
    {
        return this.getDefaultState().withProperty(MASTER, meta == 1).withProperty(WIRELESS, meta == 2);
    }

    @Override
    public int getMetaFromState(IBlockState state)
    {
        return state.getValue(WIRELESS) ? 2 : state.getValue(MASTER) ? 1 : 0;
    }

    @SideOnly(Side.CLIENT)
    public void openGui(TileEntityGlassMaster master)
    {
        FMLClientHandler.instance().displayGuiScreen(Minecraft.getMinecraft().player, new GuiChannelSetterProjector(master));
    }
}
