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

/**
 * 輸送網上で、隣接する道標と接続された外部インベントリを表す。
 */
public class ExternalStorage implements Node {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * 外部インベントリへの引き渡しを待機している荷物の一覧。
     */
    private ArrayList<Freight> freights = new ArrayList<>();
    /**
     * 最後に更新処理が実行された時刻。
     */
    private long lastUpdate = 0;

    public static ExternalStorage fromNBT(CompoundTag tag) {
        var result = new ExternalStorage();
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
        tag.putString("type", "external_storage");
        var list = new ListTag();
        freights.stream().map(Freight::toNBT).forEach(list::add);
        tag.put("freights", list);
        tag.putLong("last_update", lastUpdate);
        return tag;
    }

    // 毎tick実行される更新処理。
    @Override
    public void update(TransportContext context, BlockCoord coord) {
        // 前回処理を実行してから1秒以上経過していて、対象のチャンクが読み込まれているなら続ける。
        if (context.time - lastUpdate < 20 || !context.level.isLoaded(coord.toBlockPos())) return;
        var blockEntity = context.level.getBlockEntity(coord.toBlockPos());
        if (blockEntity == null) return;
        // 対象からアイテムを取り出して輸送網に流す。
        boolean updated = extract(context, coord, blockEntity);
        // 待機している荷物を順に確認し、対象に引き渡す。
        var iter = freights.iterator();
        while (iter.hasNext()) {
            var freight = iter.next();
            if (insert(freight, blockEntity, false)) {
                iter.remove();
                updated = true;
                break;
            }
        }
        // 実際に取り出しか引き渡しを実行したら、1秒間休む。
        if (updated) {
            lastUpdate = context.time;
        }
    }

    /**
     * 対象からアイテムを取り出し、輸送網に流す。
     *
     * @param context     現在の状態
     * @param coord       対象の座標
     * @param blockEntity 対象
     * @return 成功し処理を実行したか
     */
    private boolean extract(TransportContext context, BlockCoord coord, BlockEntity blockEntity) {
        // 接続先の一覧を取得する。
        var receivers = context.net.getReceivers(coord);
        boolean extracted = false;
        // それぞれの接続先に対し発送を試みる。
        for (var entry : receivers.entrySet()) {
            var receiver_coord = entry.getKey();
            var receiver = entry.getValue();
            var side = coord.getDirection(receiver_coord);
            if (side == null) LOGGER.warn("Failed to get side. storage: {}, receiver: {}", coord, receiver_coord);
            // 対象のIItemHandlerを取得し、アイテムを取り出す。
            extracted |= blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).map(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    // 候補を取得する。
                    var candidate = handler.extractItem(i, 1, true);
                    if (candidate.isEmpty()) continue;
                    // 荷物として接続先が受け取れるか確認する。
                    var freight = new Freight(context, candidate, coord, receiver_coord);
                    if (!receiver.canReceive(context, freight)) continue;
                    // 実際に取り出せたら荷物として流す。
                    var stack = handler.extractItem(i, 1, false);
                    if (stack.isEmpty()) continue;
                    context.net.addFreight(new Freight(context, stack, coord, receiver_coord));
                    return true;
                }
                return false;
            }).orElse(false);
        }
        return extracted;
    }

    // この荷物を受け取れるか。
    @Override
    public boolean canReceive(TransportContext context, Freight freight) {
        // 輸送網の末端なので、対象のチャンクが読み込まれていて、対象に引き渡せるならtrue。
        var coord = freight.getReceiver();
        if (!context.level.isLoaded(coord.toBlockPos())) return false;
        var blockEntity = context.level.getBlockEntity(coord.toBlockPos());
        return blockEntity != null && insert(freight, blockEntity, true);
    }

    /**
     * 荷物を対象に引き渡す。
     *
     * @param freight     荷物
     * @param blockEntity 対象
     * @param simulate    試行ならtrue、実行ならfalse
     * @return 成功し処理を実行したか
     */
    private boolean insert(Freight freight, BlockEntity blockEntity, boolean simulate) {
        // 対象のIItemHandlerを取得し、アイテムを引き渡す。
        var coord = freight.getReceiver();
        var side = coord.getDirection(freight.getSender());
        if (side == null) LOGGER.warn("Failed to get side. storage: {}, sender: {}", coord, freight.getSender());
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .map(handler -> ItemHandlerHelper.insertItem(handler, freight.stack, simulate).isEmpty())
                .orElse(false);
    }

    // 荷物を受け取る。
    @Override
    public void receive(TransportContext context, Freight freight) {
        // 今後の更新時に引き渡すため、荷物を保持する。
        freights.add(freight);
    }

    // 内部に保持されている荷物をすべて返す。
    @Override
    public ArrayList<Freight> removeFreights() {
        var result = freights;
        freights = new ArrayList<>();
        return result;
    }
}
