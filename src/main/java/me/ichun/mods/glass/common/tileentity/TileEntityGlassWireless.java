package me.ichun.mods.glass.common.tileentity;

public class TileEntityGlassWireless extends TileEntityGlassBase
{
    public int ticks;
    public int users;

    @Override
    public void update()
    {
        super.update();
        ticks++;
    }
}
