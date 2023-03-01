package com.tntmodders.transporter.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;

public class Guidepost implements Node {
    private ArrayList<Freight> freights = new ArrayList<>();
    private long lastUpdate = 0;

    public static Guidepost fromNBT(CompoundTag tag) {
        var result = new Guidepost();
        tag.getList("freights", Tag.TAG_COMPOUND).stream().map(element -> Freight.fromNBT((CompoundTag) element)).forEach(result.freights::add);
        result.lastUpdate = tag.getLong("last_update");
        return result;
    }

    @Override
    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.putString("type", "guidepost");
        var list = new ListTag();
        freights.stream().map(Freight::toNBT).forEach(list::add);
        tag.put("freights", list);
        tag.putLong("last_update", lastUpdate);
        return tag;
    }

    @Override
    public void update(TransportContext context, BlockCoord coord) {
        if (context.time - lastUpdate < 20 || freights.isEmpty()) return;
        var receivers = context.data.getReceivers(coord);
        if (receivers.isEmpty()) return;
        var iter = freights.iterator();
        while (iter.hasNext()) {
            var freight = iter.next();
            for (var entry : receivers.entrySet()) {
                var receiver_coord = entry.getKey();
                if (freight.hasPassed(receiver_coord)) continue;
                var next_freight = freight.getNext(context, receiver_coord);
                if (entry.getValue().canReceive(context, next_freight)) {
                    iter.remove();
                    context.data.addFreight(next_freight);
                    lastUpdate = context.time;
                    return;
                }
            }
        }
    }

    @Override
    public boolean canReceive(TransportContext context, Freight freight) {
        return freights.stream().allMatch(freight1 -> freight1.getSender() != freight.getSender()) && context.data.hasNextReceiver(context, freight);
    }

    @Override
    public void receive(TransportContext context, Freight freight) {
        freights.add(freight);
    }

    @Override
    public ArrayList<Freight> removeFreights() {
        var result = freights;
        freights = new ArrayList<>();
        return result;
    }
}
