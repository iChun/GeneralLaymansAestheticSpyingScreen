package me.ichun.mods.glass.client.core;

import me.ichun.mods.glass.client.render.TileEntityGlassRenderer;
import me.ichun.mods.glass.common.core.ProxyCommon;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class ProxyClient extends ProxyCommon
{
    @Override
    public void preInit()
    {
        super.preInit();

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGlassMaster.class, new TileEntityGlassRenderer());
    }
}
