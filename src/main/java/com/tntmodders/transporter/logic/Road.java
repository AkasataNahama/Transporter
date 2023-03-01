package com.tntmodders.transporter.logic;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

public record Road(BlockCoord sender, BlockCoord receiver) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public Road {
        if (sender.equals(receiver)) LOGGER.warn("sender equals receiver. pos: {}", sender);
    }

    public static Road fromNBT(CompoundTag tag) {
        return new Road(BlockCoord.fromNBT(tag.getCompound("sender")), BlockCoord.fromNBT(tag.getCompound("receiver")));
    }

    public boolean contains(BlockCoord coord) {
        return coord.equals(sender) || coord.equals(receiver);
    }

    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.put("sender", sender.toNBT());
        tag.put("receiver", receiver.toNBT());
        return tag;
    }
}
