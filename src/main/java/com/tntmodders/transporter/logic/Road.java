package com.tntmodders.transporter.logic;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

/**
 * 発送元と宛先との接続を表す。
 *
 * @param sender   発送元の座標
 * @param receiver 宛先の座標
 */
public record Road(BlockCoord sender, BlockCoord receiver) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public Road {
        if (sender.equals(receiver)) LOGGER.warn("sender equals receiver. pos: {}", sender);
    }

    public static Road fromNBT(CompoundTag tag) {
        return new Road(BlockCoord.fromNBT(tag.getCompound("sender")), BlockCoord.fromNBT(tag.getCompound("receiver")));
    }

    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.put("sender", sender.toNBT());
        tag.put("receiver", receiver.toNBT());
        return tag;
    }

    /**
     * この座標が発送元や宛先と一致するか。
     *
     * @param coord 確認する座標
     * @return この座標が接続に含まれるか
     */
    public boolean contains(BlockCoord coord) {
        return coord.equals(sender) || coord.equals(receiver);
    }
}
