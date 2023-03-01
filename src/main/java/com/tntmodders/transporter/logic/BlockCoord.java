package com.tntmodders.transporter.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public record BlockCoord(int x, int y, int z) {
    public static BlockCoord fromPos(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockCoord fromNBT(CompoundTag tag) {
        return new BlockCoord(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    /// 自身から見たtoの方向を返す。
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

    @Nullable
    public Direction getDirection(BlockCoord to) {
        return Direction.fromNormal(to.x - x, to.y - y, to.z - z);
    }

    public long distanceSq(BlockCoord other) {
        long xDiff = x - other.x;
        long yDiff = y - other.y;
        long zDiff = z - other.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
    }
}
