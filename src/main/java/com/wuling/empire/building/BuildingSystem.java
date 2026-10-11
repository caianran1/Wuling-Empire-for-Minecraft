package com.wuling.empire.building;

import com.wuling.empire.Config;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 建筑系统的核心逻辑（2026-10-02 用户设定）。
 *
 * <h3>规则</h3>
 * 玩家放下的方块，如果<b>正下方没有支撑</b>，会在 {@code building.delayTicks}（默认 2 秒）后
 * 像沙子一样坠落。倒计时期间用<b>粘液球右键</b>那格可以固定住，之后永远不掉。
 * 自然生成的地形 / 建筑不参与 —— 靠 {@link BuildingSavedData} 那份「玩家放置」台账区分。
 *
 * <h3>为什么不能只检查「放置那一刻」</h3>
 * 用户选了「像真沙子一样持续判定」：方块放好、2 秒也过了，之后它下方的方块被挖掉，
 * 它仍然要掉。所以除了放置时检查，<b>方块消失时</b>也要把上方那串重新排进队列
 * （见 {@link #onBlockRemoved}）。
 *
 * <h3>坠落怎么实现</h3>
 * 直接交给原版的 {@link FallingBlockEntity}（沙子用的那套），不自己写下落物理。
 * 附带的限制：<b>带方块实体的方块（箱子 / 熔炉等）不参与</b> —— 转实体再落地会丢内容。
 *
 * <h3>状态放哪</h3>
 * <ul>
 *   <li>「玩家放置」台账 → {@link BuildingSavedData}，持久化</li>
 *   <li>「正在倒计时」队列 → {@link #PENDING}，纯内存。<b>不持久化</b>：
 *       重启后没落定的方块就地保住，玩家重放一块即可，没必要为服务器卡顿时的
 *       半截状态买单</li>
 * </ul>
 */
public final class BuildingSystem {

    /** 倒计时队列：位置打包值 -> 到期游戏刻。每个维度一张，不持久化 */
    private static final Map<ResourceKey<Level>, Long2LongMap> PENDING = new HashMap<>();

    /**
     * 一次连锁向上最多爬多少格。
     * 正常建筑远达不到，纯粹是防御性上限 —— 万一出现自引用之类的怪状态，
     * 不至于把服务器拖死。
     */
    private static final int CHAIN_LIMIT = 384;

    private BuildingSystem() {
    }

    private static Long2LongMap pending(ResourceKey<Level> dimension) {
        return PENDING.computeIfAbsent(dimension, key -> new Long2LongOpenHashMap());
    }

    // ===================== 判定 =====================

    /**
     * 下方是不是「空的」—— 与原生沙子同一套判据（{@code FallingBlock#isFree}）。
     *
     * <p>空气、火、流体、以及「可被替换」的东西（草、雪层…）都算没有支撑；
     * 台阶、栅栏这类有实体碰撞箱的算有支撑。
     */
    public static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(BlockTags.FIRE)
                || state.liquid() || state.canBeReplaced();
    }

    /**
     * 这个方块是否纳入坠落规则。
     *
     * <p>排除四类：空气；带方块实体的（转实体会丢内容）；「依附型」小方块（见
     * {@link #isAttached}）；完全没有碰撞箱的（火把、花、草、红石线）。
     * 最后再让玩家配置的排除名单兜一道底。
     */
    public static boolean qualifies(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        if (isAttached(state) || state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return !Config.BUILDING_NEVER_FALLS.contains(id)
                && !Config.BUILDING_EXCLUDED.get().contains(id);
    }

    /**
     * 「本来就依附别的东西」的方块 —— 维持原版行为，不参与坠落。
     *
     * <p>不豁免的话，贴在墙上的告示牌、放在地上的压力板、门、铁轨会在放下 2 秒后
     * 莫名其妙掉一地。
     */
    public static boolean isAttached(BlockState state) {
        Block block = state.getBlock();
        if (state.is(BlockTags.FIRE) || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BEDS)
                || state.is(BlockTags.BANNERS) || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.CLIMBABLE) || state.is(BlockTags.RAILS)
                || state.is(BlockTags.PRESSURE_PLATES) || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.CANDLES) || state.is(BlockTags.FLOWER_POTS)
                || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.WOOL_CARPETS)) {
            return true;
        }
        return block instanceof LanternBlock || block instanceof ChainBlock
                || block instanceof DiodeBlock || block instanceof SnowLayerBlock
                || block instanceof LadderBlock;
    }

    // ===================== 倒计时队列 =====================

    /** 把一个方块排进「延迟后坠落」队列 */
    public static void schedule(ServerLevel level, BlockPos pos) {
        // 固定过的方块永远不进队列 —— 这是它豁免坠落的唯一入口，挡在这里最省事
        if (BuildingSavedData.get(level).isPinned(pos)) {
            return;
        }
        int delay = Math.max(0, Config.BUILDING_DELAY_TICKS.get());
        pending(level.dimension()).put(pos.asLong(), level.getGameTime() + delay);
    }

    /** 这个方块是不是正在倒计时（也就是「粘液球还能救」的状态） */
    public static boolean isPending(ServerLevel level, BlockPos pos) {
        Long2LongMap map = PENDING.get(level.dimension());
        return map != null && map.containsKey(pos.asLong());
    }

    /**
     * 粘液球固定：取消倒计时，并把这一笔记进台账 —— <b>从此永久不再坠落</b>。
     *
     * <p>固定不能只是「这次不倒」。方块放好、倒计时也过了，之后下方支撑被挖掉时，
     * 持续判定（{@link #onBlockRemoved} → {@link #scheduleChainUp}）会重新把它排进队列，
     * 于是「固定过的方块还是掉」—— 用户 2026-10-11 报的就是这个。所以固定必须落盘成状态，
     * 而不是一次性的取消。
     *
     * <p>只有<b>正在倒计时</b>的方块才受理（返回 {@code true}），否则手持粘液球点任何方块
     * 都会白吃一颗。
     */
    public static boolean pin(ServerLevel level, BlockPos pos) {
        Long2LongMap map = PENDING.get(level.dimension());
        if (map == null || !map.containsKey(pos.asLong())) {
            return false;
        }
        map.remove(pos.asLong());
        BuildingSavedData.get(level).pin(pos);
        return true;
    }

    /**
     * 丢弃某个维度的倒计时队列。
     *
     * <p>{@link #PENDING} 是纯内存的，且用维度当键 —— 同一 JVM 里换个存档，
     * 维度名还是 {@code minecraft:overworld}。不主动清就会把上个世界的待落方块
     * 带到下个世界的同一坐标上去，所以世界卸载时调用这里。
     */
    public static void clearPending(ResourceKey<Level> dimension) {
        PENDING.remove(dimension);
    }

    /**
     * 方块消失（被挖 / 被炸）时调用：把它自己从台账划掉，
     * 并让它上方那一串「玩家放的」方块重新进入倒计时 —— 这就是持续判定。
     */
    public static void onBlockRemoved(ServerLevel level, BlockPos pos) {
        BuildingSavedData.get(level).unmanage(pos);
        Long2LongMap map = PENDING.get(level.dimension());
        if (map != null) {
            map.remove(pos.asLong());
        }
        scheduleChainUp(level, pos);
    }

    /**
     * 从 {@code from} 的正上方开始，把一整串「玩家放的、还没有支撑」的方块都排进队列。
     *
     * <p>遇到空气 / 非玩家放置 / 依附型方块就停 —— 三者都说明这一串到这里断了。
     * 碰到<b>被固定过的方块</b>也停：它照样站在那儿撑着上面，上面那串没有悬空，
     * 而且它自己以后也不会掉（{@link #pin}）。
     * 同一个到期时刻，所以整根柱子会一起开始落，而不是从上往下一格一格地掉。
     */
    private static void scheduleChainUp(ServerLevel level, BlockPos from) {
        BuildingSavedData data = BuildingSavedData.get(level);
        BlockPos pos = from.above();
        for (int i = 0; i < CHAIN_LIMIT && level.isLoaded(pos); i++) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !data.isManaged(pos) || !qualifies(level, pos, state)
                    || data.isPinned(pos)) {
                return;
            }
            schedule(level, pos);
            pos = pos.above();
        }
    }

    // ===================== 每 tick =====================

    public static void tick(ServerLevel level) {
        Long2LongMap map = PENDING.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return;
        }
        long now = level.getGameTime();

        // 先收集到期的再处理：resolve() 会往同一个 map 里塞新条目（上方连锁）
        LongList due = null;
        LongIterator iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            if (map.get(packed) <= now) {
                if (due == null) {
                    due = new LongArrayList();
                }
                due.add(packed);
            }
        }
        if (due != null) {
            for (int i = 0; i < due.size(); i++) {
                BlockPos pos = BlockPos.of(due.getLong(i));
                // 区块没加载就先留着（读方块状态会把区块强制拉起来）——
                // 等玩家回到附近、区块加载了再落
                if (!level.isLoaded(pos)) {
                    continue;
                }
                map.remove(pos.asLong());
                resolve(level, pos);
            }
        }

        // 倒计时提示：每 10 tick 在方块顶面冒一点粒子
        if (Config.BUILDING_SHOW_PARTICLES.get() && now % 10 == 0 && !map.isEmpty()) {
            LongIterator show = map.keySet().iterator();
            while (show.hasNext()) {
                BlockPos pos = BlockPos.of(show.nextLong());
                level.sendParticles(ParticleTypes.CRIT,
                        pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                        1, 0.25D, 0.02D, 0.25D, 0.0D);
            }
        }
    }

    /** 倒计时到点：还悬空就落，已经有支撑（玩家补了方块）就不再管它 */
    private static void resolve(ServerLevel level, BlockPos pos) {
        BuildingSavedData data = BuildingSavedData.get(level);
        // 期间被别的途径（活塞 / 指令 / 其它模组）换成了非玩家放置的方块 → 不再受理
        if (!data.isManaged(pos)) {
            return;
        }
        // 固定过的方块不倒（正常进不来，队列是内存态，防的是边界情况）
        if (data.isPinned(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!qualifies(level, pos, state) || !isFree(level, pos.below())) {
            return;
        }
        drop(level, pos, state);
    }

    /**
     * 真的让它落下去：转成下落方块实体，并把它上方那串也带进队列。
     *
     * <p>坠落等于「这格已经不是原来那块了」→ 顺手把这一笔从台账划掉。
     * 不划的话空气格会永远留在 `<存档>/data` 里，只增不减。
     *
     * <p>落定后的新位置<b>不再补登记</b>：落下来的方块按自然方块处理，不会再二次坠落。
     */
    private static void drop(ServerLevel level, BlockPos pos, BlockState state) {
        BuildingSavedData.get(level).unmanage(pos);
        FallingBlockEntity.fall(level, pos, state);
        scheduleChainUp(level, pos);
    }

    // ===================== 提示文本 =====================

    /** 延迟秒数的展示文本：整秒不带小数点，非整秒保留一位 */
    public static String delaySeconds() {
        int ticks = Math.max(0, Config.BUILDING_DELAY_TICKS.get());
        if (ticks % 20 == 0) {
            return String.valueOf(ticks / 20);
        }
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
