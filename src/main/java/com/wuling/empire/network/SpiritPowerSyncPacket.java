package com.wuling.empire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步灵力当前值与上限，用于 HUD 显示。
 */
public class SpiritPowerSyncPacket {

    private final float spirit;
    private final float max;

    public SpiritPowerSyncPacket(float spirit, float max) {
        this.spirit = spirit;
        this.max = max;
    }

    public static void encode(SpiritPowerSyncPacket msg, FriendlyByteBuf buffer) {
        buffer.writeFloat(msg.spirit);
        buffer.writeFloat(msg.max);
    }

    public static SpiritPowerSyncPacket decode(FriendlyByteBuf buffer) {
        return new SpiritPowerSyncPacket(buffer.readFloat(), buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> com.wuling.empire.client.ClientSpiritData.set(this.spirit, this.max));
        ctx.setPacketHandled(true);
    }
}
