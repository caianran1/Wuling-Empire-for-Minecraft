package com.wuling.empire.network;

import com.wuling.empire.capability.ModCapabilities;
import com.wuling.empire.wuling.BreakthroughRequirement;
import com.wuling.empire.wuling.WuLingRealm;
import com.wuling.empire.wuling.WuLingStage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C → S：玩家在武灵面板里点下「提交物资」。
 *
 * 后期突破动辄十几二十组物资，背包放不下，所以允许分批缴入缴纳池，
 * 每点一次就把背包里「还缺的那部分」尽量缴进去（缴到需求上限为止）。
 */
public class WuLingSubmitPacket {

    public WuLingSubmitPacket() {
    }

    public static void encode(WuLingSubmitPacket msg, FriendlyByteBuf buf) {
        // 无载荷
    }

    public static WuLingSubmitPacket decode(FriendlyByteBuf buf) {
        return new WuLingSubmitPacket();
    }

    public static void handle(WuLingSubmitPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender == null) {
                return;
            }
            submit(sender);
        });
        context.setPacketHandled(true);
    }

    private static void submit(ServerPlayer player) {
        player.getCapability(ModCapabilities.WU_LING).ifPresent(wuLing -> {
            var data = wuLing.data();

            if (!data.isBound()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.not_bound"));
                return;
            }
            if (data.isMaxed()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.already_max"));
                return;
            }
            // 只有到了当前大境界的后期才谈得上为下一档突破备料
            if (data.stage() != WuLingStage.LATE) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.not_late_stage"));
                return;
            }
            if (data.realmOrdinal() >= data.capRealm()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.hit_cap",
                        Component.translatable(WuLingRealm.byOrdinal(data.capRealm()).translationKey())));
                return;
            }
            // 召唤类（腐肉）的突破只看击杀数，不收物资 —— 界面上按钮本来就是隐藏的，
            // 这里再挡一道，免得改包客户端把材料缴进一个用不上的池子
            if (data.type() != null && data.type().isSummoner()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.submit_none"));
                return;
            }

            int target = data.realmOrdinal() + 1;
            BreakthroughRequirement.Result result =
                    BreakthroughRequirement.submitPartial(player, target, data);
            player.sendSystemMessage(result.message());

            // 池子里的数量变了，推给客户端刷新面板
            ModMessages.sendWuLingTo(player);
        });
    }
}
