package com.tntmodders.transporter.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;

/**
 * 輸送網上の道標。
 */
public class Guidepost implements Node {
    /**
     * 発送を待機している荷物の一覧。
     */
    private ArrayList<Freight> freights = new ArrayList<>();
    /**
     * 最後に発送処理が実行された時刻。
     */
    private long lastUpdate = 0;

    public static Guidepost fromNBT(CompoundTag tag) {
        var result = new Guidepost();
        tag.getList("freights", Tag.TAG_COMPOUND)
                .stream()
                .map(element -> Freight.fromNBT((CompoundTag) element))
                .forEach(result.freights::add);
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
        // 待機している荷物を順に確認し、接続されている宛先に発送する。
        if (context.time - lastUpdate < 20 || freights.isEmpty()) return;
        var receivers = context.net.getReceivers(coord);
        if (receivers.isEmpty()) return;
        var iter = freights.iterator();
        while (iter.hasNext()) {
            var freight = iter.next();
            for (var entry : receivers.entrySet()) {
                var receiver_coord = entry.getKey();
                if (freight.hasPassed(receiver_coord)) continue;
                var next_freight = freight.getNext(context, receiver_coord);
                // 宛先が受け取り可能なら発送する。
                if (entry.getValue().canReceive(context, next_freight)) {
                    iter.remove();
                    context.net.addFreight(next_freight);
                    lastUpdate = context.time;
                    return;
                }
            }
        }
    }

    @Override
    public boolean canReceive(TransportContext context, Freight freight) {
        // 発送元の同じ荷物が待機しておらず、次の宛先があるなら受け取れる。
        return freights.stream().allMatch(freight1 -> freight1.getSender() != freight.getSender())
                && context.net.hasNextReceiver(context, freight);
    }

    @Override
    public void receive(TransportContext context, Freight freight) {
        // 今後の更新時に次の宛先に発送するため、荷物を保持する。
        freights.add(freight);
    }

    @Override
    public ArrayList<Freight> removeFreights() {
        var result = freights;
        freights = new ArrayList<>();
        return result;
    }
}
