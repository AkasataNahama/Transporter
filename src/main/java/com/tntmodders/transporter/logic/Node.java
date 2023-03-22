package com.tntmodders.transporter.logic;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;

/**
 * 輸送網の構成要素。
 */
public interface Node {
    /**
     * NBTに保存する。
     *
     * @return すべてのデータを保存したNBT
     */
    CompoundTag toNBT();

    /**
     * 毎tick実行される更新処理。
     *
     * @param context 現在の状態
     * @param coord   対象の座標
     */
    void update(TransportContext context, BlockCoord coord);

    /**
     * この荷物を受け取れるか。
     *
     * @param context 現在の状態
     * @param freight 判定する荷物
     * @return 荷物を受け取れるか
     */
    boolean canReceive(TransportContext context, Freight freight);

    /**
     * 荷物を受け取る。
     *
     * @param context 現在の状態
     * @param freight 受け取る荷物
     */
    void receive(TransportContext context, Freight freight);

    /**
     * 内部に保持されている荷物をすべて返す。
     *
     * @return 内部で保持されていた荷物
     */
    ArrayList<Freight> removeFreights();
}
