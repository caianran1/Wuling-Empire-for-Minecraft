package com.wuling.empire.events;

import com.wuling.empire.Config;
import com.wuling.empire.WulingEmpire;
import com.wuling.empire.building.BuildingSavedData;
import com.wuling.empire.building.BuildingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 建筑系统的事件接线（2026-10-02）。
 *
 * <table>
 *   <tr><td>放置方块</td><td>{@link BlockEvent.EntityPlaceEvent}</td><td>登记台账；底下没支撑就开始倒计时</td></tr>
 *   <tr><td>挖掉方块</td><td>{@link BlockEvent.BreakEvent}</td><td>台账划掉自己，让上方那串重新倒计时（持续判定）</td></tr>
 *   <tr><td>爆炸</td><td>{@link ExplosionEvent.Detonate}</td><td>同上，逐个处理被炸掉的方块</td></tr>
 *   <tr><td>粘液球右键</td><td>{@link PlayerInteractEvent.RightClickBlock}</td><td>把正在倒计时的那格钉死（永久豁免，落盘）</td></tr>
 *   <tr><td>逐 tick</td><td>{@link TickEvent.LevelTickEvent}</td><td>推进倒计时、坠落、冒提示粒子</td></tr>
 * </table>
 *
 * <p>判断「自然地形」靠的不是事件来源，而是 {@link BuildingSavedData} 那份台账 ——
 * 自然生成的方块从来没进过台账，所以压根不会被受理。
 */
@Mod.EventBusSubscriber(modid = WulingEmpire.MODID)
public final class BuildingEvents {

    /** 悬空提示的节流表：连续搭桥时别把动作栏刷爆 */
    private static final Map<UUID, Long> LAST_WARN = new HashMap<>();

    private BuildingEvents() {
    }

    // ===================== 放置 =====================

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!Config.BUILDING_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // 创造 / 旁观默认豁免：盖东西时不该被自己的规则烦到（想测试就关掉这项配置）
        if (Config.BUILDING_CREATIVE_IMMUNE.get()
                && (player.isCreative() || player.isSpectator())) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!BuildingSystem.qualifies(level, pos, state)) {
            return;
        }
        // 登记：以后支撑被抽掉时靠它认出「这是玩家搭的」
        BuildingSavedData.get(level).manage(pos);
        if (BuildingSystem.isFree(level, pos.below())) {
            BuildingSystem.schedule(level, pos);
            warn(player, level);
        }
    }

    // ===================== 方块消失：持续判定 =====================

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!Config.BUILDING_ENABLED.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BuildingSystem.onBlockRemoved(level, event.getPos());
    }

    @SubscribeEvent
    public static void onExplode(ExplosionEvent.Detonate event) {
        if (!Config.BUILDING_ENABLED.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BuildingSavedData data = BuildingSavedData.get(level);
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (data.isManaged(pos)) {
                BuildingSystem.onBlockRemoved(level, pos);
            }
        }
    }

    // ===================== 粘液球：固定 =====================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.SLIME_BALL)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        // 只有正在倒计时的那格才受理 —— 否则手持粘液球点任何方块都会「吃」掉一颗。
        // 固定是永久状态（写进台账），不是「这次不倒」：之后底下的方块被挖掉也不会再落。
        if (!BuildingSystem.pin(level, pos)) {
            return;
        }
        if (Config.BUILDING_SLIME_CONSUMED.get() && !player.isCreative()) {
            stack.shrink(1);
        }
        player.displayClientMessage(
                Component.translatable("building.wulingdiguo.fixed"), true);
        event.setCanceled(true);
    }

    // ===================== 逐 tick =====================

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !Config.BUILDING_ENABLED.get()
                || !(event.level instanceof ServerLevel level)) {
            return;
        }
        BuildingSystem.tick(level);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            LAST_WARN.remove(player.getUUID());
        }
    }

    /**
     * 世界卸载（退出存档 / 停服）：把这个世界还没落定的倒计时丢掉。
     *
     * <p>倒计时本来就是内存态（设计上不持久化）；不丢的话，同一个 JVM 里
     * 换个存档再进，上个世界的待落方块会按旧坐标在新世界落地。
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BuildingSystem.clearPending(level.dimension());
        }
    }

    // ===================== 辅助 =====================

    /** 悬空提醒，同一玩家 2 秒内只发一次 */
    private static void warn(ServerPlayer player, ServerLevel level) {
        long now = level.getGameTime();
        Long last = LAST_WARN.get(player.getUUID());
        if (last != null && now - last < 20) {
            return;
        }
        LAST_WARN.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(
                "building.wulingdiguo.warn", BuildingSystem.delaySeconds()), true);
    }
}
