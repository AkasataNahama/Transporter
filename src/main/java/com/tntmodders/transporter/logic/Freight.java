package com.tntmodders.transporter.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

/**
 * 輸送経路と発送時刻を保持する荷物。
 */
public class Freight {
    public final ItemStack stack;
    private final ArrayList<BlockCoord> route;
    private final long startedTime;

    public Freight(TransportContext context, ItemStack stack, BlockCoord sender, BlockCoord receiver) {
        // 輸送経路は必ず2つ以上とする。
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
        tag.getList("route", Tag.TAG_COMPOUND)
                .stream()
                .map(coord -> BlockCoord.fromNBT((CompoundTag) coord))
                .forEach(route::add);
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

    /**
     * この荷物の発送元を返す。
     *
     * @return 発送元の座標
     */
    public BlockCoord getSender() {
        return route.get(route.size() - 2);
    }

    /**
     * この荷物の宛先を返す。
     *
     * @return 宛先の座標
     */
    public BlockCoord getReceiver() {
        return route.get(route.size() - 1);
    }

    /**
     * 宛先に到着したか。
     *
     * @param currentTime 現在時刻
     * @return 到着したか
     */
    public boolean isArrived(long currentTime) {
        var elapsed = currentTime - startedTime;
        return getReceiver().distanceSq(getSender()) * 20 * 20 <= elapsed * elapsed;
    }

    /**
     * 宛先の座標に、アイテムとしてドロップする。
     *
     * @param level ドロップするディメンション
     */
    public void drop(Level level) {
        var receiver = getReceiver();
        Containers.dropItemStack(level, receiver.x(), receiver.y(), receiver.z(), stack);
    }

    /**
     * この座標を通過したことがあるか。
     *
     * @param coord 確認する座標
     * @return 輸送経路に座標があるか。
     */
    public boolean hasPassed(BlockCoord coord) {
        return route.contains(coord);
    }

    /**
     * 次の宛先に発送する際の荷物を返す。この荷物は変更されない。
     *
     * @param context  現在の状態
     * @param receiver 次の宛先
     * @return 発送するべき荷物
     */
    public Freight getNext(TransportContext context, BlockCoord receiver) {
        var new_route = new ArrayList<>(route);
        new_route.add(receiver);
        return new Freight(stack, new_route, context.time);
    }
}
