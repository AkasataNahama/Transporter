package com.tntmodders.transporter.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

public class Freight {
    public final ItemStack stack;
    private final ArrayList<BlockCoord> route;
    private final long startedTime;

    public Freight(TransportContext context, ItemStack stack, BlockCoord sender, BlockCoord receiver) {
        this(stack, new ArrayList<>(), context.time);
        route.add(sender);
        route.add(receiver);
    }

    private Freight(ItemStack stack, ArrayList<BlockCoord> route, long startedTime) {
        this.stack = stack;
        this.route = route;
        this.startedTime = startedTime;
    }

    public static Freight fromNBT(CompoundTag tag) {
        var route = new ArrayList<BlockCoord>();
        tag.getList("route", Tag.TAG_COMPOUND).stream().map(coord -> BlockCoord.fromNBT((CompoundTag) coord)).forEach(route::add);
        return new Freight(ItemStack.of(tag.getCompound("stack")), route, tag.getLong("started_time"));
    }

    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.put("stack", stack.serializeNBT());
        var list = new ListTag();
        route.stream().map(BlockCoord::toNBT).forEach(list::add);
        tag.put("route", list);
        tag.putLong("started_time", startedTime);
        return tag;
    }

    public BlockCoord getSender() {
        return route.get(route.size() - 2);
    }

    public BlockCoord getReceiver() {
        return route.get(route.size() - 1);
    }

    public boolean isArrived(long currentTime) {
        var elapsed = currentTime - startedTime;
        return getReceiver().distanceSq(getSender()) * 20 * 20 <= elapsed * elapsed;
    }

    public void drop(Level level) {
        var receiver = getReceiver();
        Containers.dropItemStack(level, receiver.x(), receiver.y(), receiver.z(), stack);
    }

    public boolean hasPassed(BlockCoord coord) {
        return route.contains(coord);
    }

    public Freight getNext(TransportContext context, BlockCoord receiver) {
        var new_route = new ArrayList<>(route);
        new_route.add(receiver);
        return new Freight(stack, new_route, context.time);
    }
}
