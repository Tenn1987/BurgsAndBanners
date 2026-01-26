package com.brandon.burgsbanners.burg.plot;

import org.bukkit.Location;

public class PlotSelection {

    private Location pos1;
    private Location pos2;

    public Location getPos1() { return pos1; }
    public Location getPos2() { return pos2; }

    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }

    public boolean isComplete() {
        return pos1 != null && pos2 != null
                && pos1.getWorld() != null
                && pos2.getWorld() != null
                && pos1.getWorld().getUID().equals(pos2.getWorld().getUID());
    }
}