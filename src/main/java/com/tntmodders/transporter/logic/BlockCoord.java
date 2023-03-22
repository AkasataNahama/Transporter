package com.tntmodders.transporter.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * 不変なブロック座標。
 *
 * @param x
 * @param y
 * @param z
 */
public record BlockCoord(int x, int y, int z) {
    public static BlockCoord fromPos(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockCoord fromNBT(CompoundTag tag) {
        return new BlockCoord(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.putInt("z", z);
        tag.putInt("x", x);
        tag.putInt("y", y);
        return tag;
    }

    /**
     * この座標から見た引数の座標の方向を返す。
     *
     * @param to この座標に隣接する座標
     * @return 引数の座標が隣接していないならnull
     */
    @Nullable
    public Direction getDirection(BlockCoord to) {
        return Direction.fromNormal(to.x - x, to.y - y, to.z - z);
    }

    /**
     * この座標との距離の2乗を返す。
     *
     * @param other 相手の座標
     * @return この座標との距離の2乗
     */
    public long distanceSq(BlockCoord other) {
        long xDiff = x - other.x;
        long yDiff = y - other.y;
        long zDiff = z - other.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }
}
