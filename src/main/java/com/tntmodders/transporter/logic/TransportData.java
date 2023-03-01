package com.tntmodders.transporter.logic;

import com.mojang.logging.LogUtils;
import com.tntmodders.transporter.Transporter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

@AutoRegisterCapability
public class TransportData implements ICapabilitySerializable<CompoundTag> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LazyOptional<TransportData> holder = LazyOptional.of(() -> this);
    private final HashMap<BlockCoord, Node> nodes = new HashMap<>();
    private final HashSet<Road> roads = new HashSet<>();
    private final ArrayList<Freight> freights = new ArrayList<>();

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return Transporter.TRANSPORT.orEmpty(capability, holder);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        var list = new ListTag();
        nodes.entrySet().stream().map(entry -> {
            var node = entry.getValue().toNBT();
            node.put("coord", entry.getKey().toNBT());
            return node;
        }).forEach(list::add);
        tag.put("nodes", list);
        list = new ListTag();
        roads.stream().map(Road::toNBT).forEach(list::add);
        tag.put("roads", list);
        list = new ListTag();
        freights.stream().map(Freight::toNBT).forEach(list::add);
        tag.put("freights", list);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        nodes.clear();
        tag.getList("nodes", Tag.TAG_COMPOUND).forEach(element -> {
            var node = (CompoundTag) element;
            var type = node.getString("type");
            var coord = BlockCoord.fromNBT(node.getCompound("coord"));
            if (type.equals("guidepost")) {
                nodes.put(coord, Guidepost.fromNBT(node));
            } else if (type.equals("external_storage")) {
                nodes.put(coord, ExternalStorage.fromNBT(node));
            } else {
                LOGGER.warn("Unknown type of node: " + type);
            }
        });
        roads.clear();
        tag.getList("roads", Tag.TAG_COMPOUND).stream().map(element -> Road.fromNBT((CompoundTag) element)).forEach(roads::add);
        freights.clear();
        tag.getList("freights", Tag.TAG_COMPOUND).stream().map(element -> Freight.fromNBT((CompoundTag) element)).forEach(freights::add);
    }

    public void addNode(BlockCoord coord, Node node) {
        nodes.put(coord, node);
    }

    public ArrayList<ItemStack> removeNode(BlockCoord coord) {
        var result = new ArrayList<ItemStack>();
        var roadStack = new ItemStack(Transporter.ROAD.get());
        roadStack.getOrCreateTagElement("road");
        roads.removeIf(road -> {
            if (road.contains(coord)) {
                result.add(roadStack);
                return true;
            } else {
                return false;
            }
        });
        var node = nodes.remove(coord);
        if (node != null) {
            for (var freight : node.removeFreights()) {
                result.add(freight.stack);
            }
            if (node instanceof Guidepost) {
                // 削除された道標に隣接する外部接続のうち、孤立したものを削除する。
                nodes.entrySet().removeIf(entry -> {
                    if (entry.getValue() instanceof ExternalStorage
                            && entry.getKey().distanceSq(coord) == 1
                            && roads.stream().noneMatch(road -> road.contains(entry.getKey()))) {
                        for (var freight : entry.getValue().removeFreights()) {
                            result.add(freight.stack);
                        }
                        return true;
                    } else {
                        return false;
                    }
                });
            }
        }
        return result;
    }

    public boolean addRoad(Road road, Node sender, Node receiver) {
        addNode(road.sender(), sender);
        addNode(road.receiver(), receiver);
        return roads.add(road);
    }

    public void addFreight(Freight freight) {
        freights.add(freight);
    }

    public ArrayList<Freight> removeFreight(BlockCoord coord) {
        var node = nodes.get(coord);
        if (node != null) {
            return node.removeFreights();
        } else {
            return new ArrayList<>();
        }
    }

    public boolean hasNextReceiver(TransportContext context, Freight freight) {
        for (var road : roads) {
            if (!road.sender().equals(freight.getReceiver()) || freight.hasPassed(road.receiver())) {
                continue;
            }
            var node = nodes.get(road.receiver());
            if (node.canReceive(context, freight.getNext(context, road.receiver()))) {
                return true;
            }
        }
        return false;
    }

    public HashMap<BlockCoord, Node> getReceivers(BlockCoord coord) {
        var result = new HashMap<BlockCoord, Node>();
        for (var road : roads) {
            if (road.sender().equals(coord)) {
                result.put(road.receiver(), nodes.get(road.receiver()));
            }
        }
        return result;
    }

    public void update(Level level) {
        var context = new TransportContext(level, this);
        var iter = freights.iterator();
        while (iter.hasNext()) {
            var freight = iter.next();
            if (freight.isArrived(context.time)) {
                // 到着した荷物を宛先に渡す。
                BlockCoord coord = freight.getReceiver();
                var node = nodes.get(coord);
                if (node != null) {
                    node.receive(context, freight);
                } else {
                    // 宛先がなくなっていたらドロップさせる。
                    freight.drop(level);
                }
                iter.remove();
            }
        }
        for (var entry : nodes.entrySet()) {
            entry.getValue().update(context, entry.getKey());
        }
    }
}
