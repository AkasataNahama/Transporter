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

/**
 * 各ディメンションに付与され、輸送網の情報を管理する。
 */
@AutoRegisterCapability
public class TransportNet implements ICapabilitySerializable<CompoundTag> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LazyOptional<TransportNet> holder = LazyOptional.of(() -> this);
    /**
     * 輸送網の構成要素の一覧。
     */
    private final HashMap<BlockCoord, Node> nodes = new HashMap<>();
    /**
     * 輸送網に存在する道の一覧。
     */
    private final HashSet<Road> roads = new HashSet<>();
    /**
     * 道を通っている荷物の一覧。
     */
    private final ArrayList<Freight> freights = new ArrayList<>();

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        // 輸送網の要求なら、このインスタンスを返す。
        return Transporter.TRANSPORT.orEmpty(capability, holder);
    }

    @Override
    public CompoundTag serializeNBT() {
        // セーブデータの保存に合わせて、輸送網の状態を保存する。
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
        // セーブデータの読み込みに合わせて、輸送網の状態を読み込む。
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
        tag.getList("roads", Tag.TAG_COMPOUND)
                .stream()
                .map(element -> Road.fromNBT((CompoundTag) element))
                .forEach(roads::add);
        freights.clear();
        tag.getList("freights", Tag.TAG_COMPOUND)
                .stream()
                .map(element -> Freight.fromNBT((CompoundTag) element))
                .forEach(freights::add);
    }

    /**
     * この座標が空いていたら、構成要素を追加する。
     *
     * @param coord 対応する座標
     * @param node  追加する構成要素
     */
    public void addNode(BlockCoord coord, Node node) {
        var existing = nodes.get(coord);
        if (existing == null) {
            nodes.put(coord, node);
        } else if (existing.getClass() != node.getClass()) {
            LOGGER.warn("There is already another kind of node.");
        }
    }

    /**
     * 道標や外部接続を削除する。
     *
     * @param coord 削除する構成要素の座標
     * @return ドロップアイテムの一覧
     */
    public ArrayList<ItemStack> removeNode(BlockCoord coord) {
        var result = new ArrayList<ItemStack>();
        // 接続を削除し、その数だけ道をドロップする。
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
            // 構成要素が削除されたら、内部に保持されていたアイテムをドロップする。
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

    /**
     * 接続を追加する。
     *
     * @param road     追加する接続
     * @param sender   接続元の構成要素
     * @param receiver 接続先の構成要素
     * @return 接続を追加したか。
     */
    public boolean addRoad(Road road, Node sender, Node receiver) {
        addNode(road.sender(), sender);
        addNode(road.receiver(), receiver);
        return roads.add(road);
    }

    /**
     * 荷物を追加する。
     *
     * @param freight 追加する荷物。
     */
    public void addFreight(Freight freight) {
        freights.add(freight);
    }

    /**
     * この座標の構成要素が保持しているアイテムをすべて取り出す。
     *
     * @param coord 対象の座標
     * @return 保持されていたアイテムの一覧
     */
    public ArrayList<Freight> removeFreight(BlockCoord coord) {
        var node = nodes.get(coord);
        if (node != null) {
            return node.removeFreights();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 荷物を受け取れる宛先があるか。
     *
     * @param context 現在の状態
     * @param freight 対象となる荷物
     * @return 宛先があるか
     */
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

    /**
     * この座標に接続されている宛先の一覧を返す。
     *
     * @param coord 接続元の座標
     * @return 宛先の一覧
     */
    public HashMap<BlockCoord, Node> getReceivers(BlockCoord coord) {
        var result = new HashMap<BlockCoord, Node>();
        for (var road : roads) {
            if (road.sender().equals(coord)) {
                result.put(road.receiver(), nodes.get(road.receiver()));
            }
        }
        return result;
    }

    /**
     * 毎tickの更新処理。
     *
     * @param level 輸送網の存在するディメンション
     */
    public void update(Level level) {
        // 今回の更新で使うためのデータを作る。
        var context = new TransportContext(level, this);
        // 運搬中の荷物を順に確認する。
        var iter = freights.iterator();
        while (iter.hasNext()) {
            var freight = iter.next();
            if (freight.isArrived(context.time)) {
                // 荷物が到着したら宛先に渡す。
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
        // 輸送網の構成要素を更新する。
        for (var entry : nodes.entrySet()) {
            entry.getValue().update(context, entry.getKey());
        }
    }
}
