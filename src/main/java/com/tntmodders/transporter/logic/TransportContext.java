package com.tntmodders.transporter.logic;

import net.minecraft.world.level.Level;

public class TransportContext {
    public final Level level;
    public final TransportData data;
    public final long time;

    public TransportContext(Level level, TransportData data) {
        this.level = level;
        this.data = data;
        time = level.getGameTime();
    }
}
