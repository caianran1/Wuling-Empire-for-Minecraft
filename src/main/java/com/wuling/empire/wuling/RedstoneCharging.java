package com.wuling.empire.wuling;

import com.wuling.empire.Config;
import com.wuling.empire.capability.ModCapabilities;
import com.wuling.empire.item.BeadPower;
import com.wuling.empire.item.SpiritBeadItem;
import com.wuling.empire.item.SpiritQuality;
import com.wuling.empire.network.ModMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * 红石装备的「灵珠充能」服务端逻辑（2026-10-02 用户口径：
 * 「用灵珠充能，充能数按灵珠种类以及品质决定」）。
 *
 * <p>充能量直接沿用灵珠的回复量口径 —— 也就是复用 {@link BeadPower}：
 *
 * <pre>
 *   充能点 = 该灵珠的回复量（% = 怪物基准 × 品质倍率）× Config#REDSTONE_CHARGE_PER_PERCENT
 * </pre>
 *
 * <p>默认换算系数 10，所以「这颗珠子能回多少百分比灵力，就充 10 倍点数的装备充能」：
 * 僵尸凡品 = 5% → 50 点，末影龙极品 = 100% → 1000 点（刚好把默认上限充满）。
 * 这样两种资源（玩家灵力 / 装备充能）共用一个强度标尺，不用另造一张表，
 * 也不会出现「某种怪物的珠子对装备特别划算」的失衡。
 */
public final class RedstoneCharging {

    private RedstoneCharging() {
    }

    /** 一颗灵珠能充多少点（不判断是否超过上限） */
    public static int chargeValue(String source, SpiritQuality quality) {
        float restore = BeadPower.restore(source, quality);
        return Math.max(0, Math.round(restore * Config.REDSTONE_CHARGE_PER_PERCENT.get()));
    }

    /**
     * 消耗背包里一颗「该来源 + 该品质」的灵珠，给红石充能池加充能。
     *
     * <p>客户端只传「哪一组珠子」，具体扣哪一颗由服务端按背包顺序找 ——
     * 这样即使客户端与背包状态不同步也不会扣错东西。
     */
    public static void chargeFromBead(ServerPlayer player, String source, String qualityKey) {
        SpiritQuality quality = SpiritQuality.byKey(qualityKey);
        player.getCapability(ModCapabilities.WU_LING).ifPresent(holder -> {
            WuLingData data = holder.data();
            int max = Config.REDSTONE_MAX_CHARGE.get();
            if (data.redstoneCharge() >= max) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.redstone_full"));
                return;
            }

            Container inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof SpiritBeadItem)) {
                    continue;
                }
                if (!source.equals(SpiritBeadItem.getSource(stack))
                        || SpiritBeadItem.getQuality(stack) != quality) {
                    continue;
                }

                int gain = chargeValue(source, quality);
                int applied = data.addRedstoneCharge(gain);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.7F, 1.4F);
                // 报实入值：池子快满时 gain 会被截断，照 gain 报「充入 100 / 当前 1000」
                // 跟玩家看到的数字对不上
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.redstone_charged",
                        trim(applied), trim(data.redstoneCharge()), trim(max)));
                if (applied <= 0) {
                    player.sendSystemMessage(Component.translatable("message.wulingdiguo.redstone_full"));
                }
                ModMessages.sendRedstoneTo(player);
                return;
            }

            // 没找到：多半是刚被别的手续消耗掉了
            player.sendSystemMessage(Component.translatable("message.wulingdiguo.redstone_no_bead"));
        });
    }

    /** 30.0 → "30"，12.5 → "12.5" */
    private static String trim(double value) {
        return Math.abs(value - Math.round(value)) < 0.05D
                ? String.valueOf(Math.round(value))
                : String.format("%.1f", value);
    }
}
