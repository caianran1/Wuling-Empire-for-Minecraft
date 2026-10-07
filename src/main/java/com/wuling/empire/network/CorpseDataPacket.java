package com.wuling.empire.network;

import com.wuling.empire.entity.CorpseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步尸体的外观数据（死亡怪物的 NBT + 朝向 + 是否末影龙）。
 *
 * <p>只在玩家开始追踪该尸体时发送一次，客户端据此还原出用于渲染的实体。
 *
 * <p>{@code dragon} 必须一起发：客户端实体是网络生成的，不读服务端的 NBT，
 * 少了这个标志客户端就不知道要画紫色光柱、也不知道该不该套原版的偏航。
 */
public class CorpseDataPacket {

    private final int entityId;
    private final CompoundTag mobData;
    private final float yaw;
    private final boolean dragon;

    public CorpseDataPacket(int entityId, CompoundTag mobData, float yaw, boolean dragon) {
        this.entityId = entityId;
        this.mobData = mobData;
        this.yaw = yaw;
        this.dragon = dragon;
    }

    public static void encode(CorpseDataPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.entityId);
        buffer.writeNbt(msg.mobData);
        buffer.writeFloat(msg.yaw);
        buffer.writeBoolean(msg.dragon);
    }

    public static CorpseDataPacket decode(FriendlyByteBuf buffer) {
        return new CorpseDataPacket(buffer.readInt(), buffer.readNbt(), buffer.readFloat(),
                buffer.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        // 这个包只会 S→C；万一有人在服务端收到它（改包客户端可以硬发），
        // 这里引用的 net.minecraft.client.Minecraft 在专用服上不存在 → 崩服
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) {
                return;
            }
            Entity target = Minecraft.getInstance().level.getEntity(this.entityId);
            if (target instanceof CorpseEntity corpse) {
                corpse.setMobData(this.mobData, this.yaw);
                corpse.setDragon(this.dragon);
            }
        });
        ctx.setPacketHandled(true);
    }
}
