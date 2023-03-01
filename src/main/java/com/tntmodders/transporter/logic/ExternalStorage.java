package com.tntmodders.transporter.logic;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.util.ArrayList;

public class ExternalStorage implements Node {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ArrayList<Freight> freights = new ArrayList<>();
    private long lastUpdate = 0;

    public static ExternalStorage fromNBT(CompoundTag tag) {
        var result = new ExternalStorage();
        tag.getList("freights", Tag.TAG_COMPOUND).stream().map(element -> Freight.fromNBT((CompoundTag) element)).forEach(result.freights::add);
        result.lastUpdate = tag.getLong("last_update");
        return result;
    }

    @Override
    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.putString("type", "external_storage");
        var list = new ListTag();
        freights.stream().map(Freight::toNBT).forEach(list::add);
        tag.put("freights", list);
        tag.putLong("last_update", lastUpdate);
        return tag;
    }

    @Override
    public void update(TransportContext context, BlockCoord coord) {
        if (context.time - lastUpdate < 20 || !context.level.isLoaded(coord.toBlockPos())) return;
        var blockEntity = context.level.getBlockEntity(coord.toBlockPos());
        if (blockEntity == null) return;
        boolean updated = extract(context, coord, blockEntity);
        var iter = freights.iterator();
        while (iter.hasNext()) {
            var freight = iter.next();
            if (insert(freight, blockEntity, false)) {
                iter.remove();
                updated = true;
                break;
            }
        }
        if (updated) {
            lastUpdate = context.time;
        }
    }

    private boolean extract(TransportContext context, BlockCoord coord, BlockEntity blockEntity) {
        var receivers = context.data.getReceivers(coord);
        boolean extracted = false;
        for (var entry : receivers.entrySet()) {
            var receiver_coord = entry.getKey();
            var receiver = entry.getValue();
            var side = coord.getDirection(receiver_coord);
            if (side == null) LOGGER.warn("Failed to get side. storage: {}, receiver: {}", coord, receiver_coord);
            extracted |= blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).map(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    var candidate = handler.extractItem(i, 1, true);
                    if (candidate.isEmpty()) continue;
                    var freight = new Freight(context, candidate, coord, receiver_coord);
                    if (!receiver.canReceive(context, freight)) continue;
                    var stack = handler.extractItem(i, 1, false);
                    if (stack.isEmpty()) continue;
                    context.data.addFreight(new Freight(context, stack, coord, receiver_coord));
                    return true;
                }
                return false;
            }).orElse(false);
        }
        return extracted;
    }

    @Override
    public boolean canReceive(TransportContext context, Freight freight) {
        var coord = freight.getReceiver();
        if (!context.level.isLoaded(coord.toBlockPos())) return false;
        var blockEntity = context.level.getBlockEntity(coord.toBlockPos());
        return blockEntity != null && insert(freight, blockEntity, true);
    }

    private boolean insert(Freight freight, BlockEntity blockEntity, boolean simulate) {
        var coord = freight.getReceiver();
        var side = coord.getDirection(freight.getSender());
        if (side == null) LOGGER.warn("Failed to get side. storage: {}, sender: {}", coord, freight.getSender());
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .map(handler -> ItemHandlerHelper.insertItem(handler, freight.stack, simulate).isEmpty())
                .orElse(false);
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
