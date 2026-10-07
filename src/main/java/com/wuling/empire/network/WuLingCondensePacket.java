package com.wuling.empire.network;

import com.wuling.empire.wuling.WuLingBinding;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C → S：武灵相关操作。
 *
 * 两种动作：
 *   - "bind"     ：选择武灵种类并绑定（武灵绑定器 Shift+右键 / 创造绑定器 打开选择界面后选中）
 *   - "condense" ：凝聚出实体武灵（Shift + M，按种类+境界生成对应实物，不开界面，不需要绑定器）
 *
 * 服务端才是数据权威，实际判定与消耗都在服务端完成。
 */
public class WuLingCondensePacket {

    public static final String ACTION_BIND = "bind";
    public static final String ACTION_CONDENSE = "condense";

    private final String action;
    /** 玩家选定的武灵种类 key（仅 bind 动作使用） */
    private final String typeKey;
    /** true = 创造武灵绑定器触发，强制覆盖（无视已拥有） */
    private final boolean creative;

    /** bind 构造 */
    public WuLingCondensePacket(String typeKey, boolean creative) {
        this(ACTION_BIND, typeKey, creative);
    }

    /** condense 构造（不需要种类，按当前已绑定武灵生成实物） */
    public WuLingCondensePacket() {
        this(ACTION_CONDENSE, "", false);
    }

    private WuLingCondensePacket(String action, String typeKey, boolean creative) {
        this.action = action;
        this.typeKey = typeKey == null ? "" : typeKey;
        this.creative = creative;
    }

    public static void encode(WuLingCondensePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeUtf(msg.typeKey);
        buf.writeBoolean(msg.creative);
    }

    public static WuLingCondensePacket decode(FriendlyByteBuf buf) {
        return new WuLingCondensePacket(buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(WuLingCondensePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                if (ACTION_CONDENSE.equals(msg.action)) {
                    WuLingBinding.condense(sender);
                } else {
                    // ⚠️ creative 是客户端传上来的，不能直接信 ——
                    // 否则改个包就能跳过绑定器与灵珠，白嫖传说档武灵
                    WuLingBinding.bind(sender, msg.typeKey, msg.creative && sender.isCreative());
                }
            }
        });
        context.setPacketHandled(true);
    }
}
