package com.wuling.empire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步红石装备的充能（当前值 + 上限），用于 HUD 与充能界面显示。
 *
 * <p>红石装备的充能池存在玩家数据（{@code WuLingData#redstoneCharge}）里，
 * 客户端只做展示，权威值永远在服务端。
 */
public class RedstoneSyncPacket {

    private final int charge;
    private final int max;

    public RedstoneSyncPacket(int charge, int max) {
        this.charge = charge;
        this.max = max;
    }

    public static void encode(RedstoneSyncPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.charge);
        buffer.writeVarInt(msg.max);
    }

    public static RedstoneSyncPacket decode(FriendlyByteBuf buffer) {
        return new RedstoneSyncPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> com.wuling.empire.client.ClientRedstoneData.set(this.charge, this.max));
        ctx.setPacketHandled(true);
    }
}
