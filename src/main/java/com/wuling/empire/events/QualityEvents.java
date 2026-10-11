package com.wuling.empire.events;

import com.wuling.empire.Config;
import com.wuling.empire.WulingEmpire;
import com.wuling.empire.item.SpiritQuality;
import com.wuling.empire.wuling.QualitySmithing;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 锻造品质的事件接线（2026-10-11）。
 *
 * <table>
 *   <tr><td>合成出装备</td><td>{@link PlayerEvent.ItemCraftedEvent}</td>
 *       <td>按合成者的武灵境界掷一次品质</td></tr>
 *   <tr><td>鼠标悬停</td><td>{@link ItemTooltipEvent}</td>
 *       <td>良品以上在名字下面加一行「锻造品质」</td></tr>
 * </table>
 *
 * <p>入口就是原版的<b>工作台合成</b> —— 用户 2026-10-11 选的口径「工作台合成即可」，
 * 不额外加界面、不要求别的动作。凡品不写 NBT、不加属性、也不发提示，
 * 所以「凡品 = 原版武器」是字面成立的。
 */
@Mod.EventBusSubscriber(modid = WulingEmpire.MODID)
public final class QualityEvents {

    private QualityEvents() {
    }

    /**
     * 合成完成：给武器 / 工具 / 护甲掷一次品质。
     *
     * <p>改的是 {@code event.getCrafting()} 本身 —— 它就是玩家拿走的那份 ItemStack，
     * 所以在事件里挂 NBT 会跟着进背包（社区标准做法）。
     */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!Config.SMITH_ENABLED.get()) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        if (!QualitySmithing.canHaveQuality(crafted)) {
            return;
        }
        // 已经带品质的不重掷（防御：合成表里混进了带品质的成品）
        if (QualitySmithing.qualityOf(crafted) != SpiritQuality.FAN) {
            return;
        }
        SpiritQuality quality = QualitySmithing.roll(player);
        if (!QualitySmithing.apply(crafted, quality)) {
            return; // 凡品：保持原版，不写 NBT、不提示
        }
        player.displayClientMessage(Component.translatable(
                "message.wulingdiguo.quality_forged",
                Component.translatable(quality.translationKey())
                        .withStyle(style -> style.withColor(quality.color())),
                crafted.getHoverName()), false);
    }

    /** 品质行插在物品名下面（原版 tooltip 的第 0 行是名字） */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        SpiritQuality quality = QualitySmithing.qualityOf(event.getItemStack());
        if (quality == SpiritQuality.FAN) {
            return;
        }
        List<Component> lines = event.getToolTip();
        lines.add(Math.min(1, lines.size()), QualitySmithing.qualityLine(quality));
    }
}
