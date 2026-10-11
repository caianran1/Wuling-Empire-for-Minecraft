package com.wuling.empire.building;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 「哪些方块是玩家亲手放的」台账，<b>每个维度一份</b>，落盘在
 * {@code <存档>/data/wuling_building.dat}。
 *
 * <h3>为什么非要存这份台账</h3>
 * 建筑系统要区分「玩家搭的」和「自然生成的」—— 用户口径是
 * 「自然产生的地形或建筑除外」。但原版方块没有通用 NBT，没法在方块自己身上做记号，
 * 所以只能在世界存档里另记一份位置清单。
 *
 * <p>台账要持久化（而不是只在内存里），是因为「持续判定」：方块放好、过了 2 秒判定之后，
 * 它下方被挖掉时仍然要掉。服务器重启后台账还在，这条规则才不会断。
 *
 * <h3>内存占用</h3>
 * 用 {@link LongSet} 存 {@link BlockPos#asLong()} 打包值，一个位置 8 字节 + 哈希表开销。
 * 十万块建筑约 2~3 MB，可接受。台账只在「玩家放置」和「方块消失」两个时机写入，
 * 不参与逐 tick 扫描。
 *
 * <h3>两份清单</h3>
 * <ul>
 *   <li>{@link #managed}：玩家放过的方块 —— 用来区分「玩家搭的」和「自然地形」</li>
 *   <li>{@link #pinned}：被粘液球固定过的方块 —— 永久豁免坠落（{@link #pin}）</li>
 * </ul>
 */
public class BuildingSavedData extends SavedData {

    private static final String DATA_NAME = "wuling_building";
    private static final String TAG_MANAGED = "Managed";
    private static final String TAG_PINNED = "Pinned";

    /** 玩家放置过的方块位置（{@link BlockPos#asLong()} 打包） */
    private final LongSet managed = new LongOpenHashSet();

    /**
     * 被粘液球固定过的方块位置 —— <b>永久豁免坠落</b>。
     *
     * <p>必须持久化、必须独立于 {@link #managed}：固定是「这块以后都不落」的承诺
     * （见语言文件里那句「已固定，这块不会再坠落」），只取消内存里那次倒计时的话，
     * 之后下方方块被挖掉、重新走一遍持续判定时它会又掉下去。
     */
    private final LongSet pinned = new LongOpenHashSet();

    public static BuildingSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BuildingSavedData::load, BuildingSavedData::new, DATA_NAME);
    }

    public static BuildingSavedData load(CompoundTag tag) {
        BuildingSavedData data = new BuildingSavedData();
        for (long packed : tag.getLongArray(TAG_MANAGED)) {
            data.managed.add(packed);
        }
        for (long packed : tag.getLongArray(TAG_PINNED)) {
            data.pinned.add(packed);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(TAG_MANAGED, managed.toLongArray());
        tag.putLongArray(TAG_PINNED, pinned.toLongArray());
        return tag;
    }

    /** 这个位置是不是玩家放的方块 */
    public boolean isManaged(BlockPos pos) {
        return managed.contains(pos.asLong());
    }

    /**
     * 登记一个玩家放置的方块。
     *
     * <p>顺手清掉同位置残留的「已固定」标记：这个位置上的旧方块已经被换掉了
     * （固定过的方块被活塞推走时不会触发挖掘事件，标记会留在原地），
     * 记在新方块头上就等于白送一次豁免。
     */
    public void manage(BlockPos pos) {
        boolean dirty = managed.add(pos.asLong());
        if (pinned.remove(pos.asLong())) {
            dirty = true;
        }
        if (dirty) {
            setDirty();
        }
    }

    /** 划掉一个已经不在的位置（被挖 / 被炸 / 坠落移除），固定标记一并作废 */
    public void unmanage(BlockPos pos) {
        boolean dirty = managed.remove(pos.asLong());
        if (pinned.remove(pos.asLong())) {
            dirty = true;
        }
        if (dirty) {
            setDirty();
        }
    }

    /** 这个位置是不是被粘液球固定过（永久不坠落） */
    public boolean isPinned(BlockPos pos) {
        return pinned.contains(pos.asLong());
    }

    /** 用一个粘液球把这块钉住：从此不再参与坠落 */
    public void pin(BlockPos pos) {
        if (pinned.add(pos.asLong())) {
            setDirty();
        }
    }

    /** 已登记的方块数（调试 / 排查用） */
    public int size() {
        return managed.size();
    }
}
