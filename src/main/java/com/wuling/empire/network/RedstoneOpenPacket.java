package com.wuling.empire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：让客户端打开「红石充能」界面。
 *
 * <p>入口是「手持任意红石装备右键」—— 判定在服务端做（
 * {@code events/RedstoneEvents} 的右键处理），命中后才发这个包，
 * 客户端收到就 {@code Minecraft.getInstance().setScreen(new RedstoneChargeScreen())}。
 *
 * <p>界面本体不携带任何数据（列什么灵珠是读客户端自己背包的），
 * 所以这个包没有字段。
 */
public class RedstoneOpenPacket {

    public RedstoneOpenPacket() {
    }

    public static void encode(RedstoneOpenPacket msg, FriendlyByteBuf buffer) {
    }

    public static RedstoneOpenPacket decode(FriendlyByteBuf buffer) {
        return new RedstoneOpenPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        // 只会 S→C；服务端收到就丢掉，别去开界面（专用服没有客户端类）
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(com.wuling.empire.client.WuLingClientBridge::openChargeScreen);
        ctx.setPacketHandled(true);
    }
}
