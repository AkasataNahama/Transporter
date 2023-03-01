package com.tntmodders.transporter.logic;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;

public interface Node {
    CompoundTag toNBT();

    void update(TransportContext context, BlockCoord coord);

    boolean canReceive(TransportContext context, Freight freight);

    void receive(TransportContext context, Freight freight);

    ArrayList<Freight> removeFreights();
}
