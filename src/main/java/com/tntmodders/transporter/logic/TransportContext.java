package com.tntmodders.transporter.logic;

import net.minecraft.world.level.Level;

/**
 * 更新時の状態をまとめる。
 */
public class TransportContext {
    /**
     * 輸送網が存在するディメンション。
     */
    public final Level level;
    /**
     * 輸送網。
     */
    public final TransportNet net;
    /**
     * 現在時刻。
     */
    public final long time;

    public TransportContext(Level level, TransportNet net) {
        this.level = level;
        this.net = net;
        time = level.getGameTime();
    }
}
