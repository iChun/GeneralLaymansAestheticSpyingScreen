package me.ichun.mods.glass.client.core;

import me.ichun.mods.glass.client.render.TileEntityGlassRenderer;
import me.ichun.mods.glass.common.core.ProxyCommon;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class ProxyClient extends ProxyCommon
{
    @Override
    public void preInit()
    {
        super.preInit();

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGlassBase.class, new TileEntityGlassRenderer());
    }
}
