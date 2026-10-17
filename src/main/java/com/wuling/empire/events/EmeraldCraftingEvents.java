package com.wuling.empire.events;

import com.wuling.empire.WulingEmpire;
import com.wuling.empire.capability.ModCapabilities;
import com.wuling.empire.item.ManifestItems;
import com.wuling.empire.wuling.WuLingRealm;
import com.wuling.empire.wuling.WuLingType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 绿宝石武灵装备的合成接线（2026-10-17）。
 *
 * <p>用户要求「绿宝石装备的合成配方改为用绿宝石块合成」。配方本身只是
 * {@code crafting_shaped}（见 {@code data/wulingdiguo/recipes/emerald_*.json}），
 * 直接合成出来的会是一件<b>没打标记、没附魔、没强化</b>的裸物品 ——
 * 跟凝聚出来的绿宝石装备差一大截，玩家一定会当成 bug 报上来。</p>
 *
 * <p>所以在合成完成的瞬间，用 {@link WuLingType#manifestPiece} 把产物
 * 重建成「与凝聚完全一致」的那一件：附魔、属性强化、{@code WuLingManifest}
 * 标记、小境界标记一次性写全，再把新 NBT 盖到产出物品上（物品本体不变，
 * 只换数据，因此堆叠数与事件语义都不受影响）。</p>
 *
 * <p>小境界按<b>合成者当时的小境界</b>取：已开启武灵且确实到了绿宝石档就用真实小境界，
 * 否则按前段（系数 0）算 —— 和「没开武灵的人是凡人」这条口径一致。</p>
 */
@Mod.EventBusSubscriber(modid = WulingEmpire.MODID)
public final class EmeraldCraftingEvents {

    private EmeraldCraftingEvents() {
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) {
            return;
        }
        WuLingRealm realm = ManifestItems.realmOf(crafted.getItem());
        if (realm != WuLingRealm.EMERALD) {
            return;
        }
        WuLingType type = ManifestItems.typeOf(crafted.getItem());
        if (type == null) {
            return;
        }

        int stage = 0;
        var holder = player.getCapability(ModCapabilities.WU_LING).orElse(null);
        if (holder != null && holder.data().isBound()
                && holder.data().realmOrdinal() >= WuLingRealm.EMERALD.ordinal()) {
            stage = holder.data().stageOrdinal();
        }

        ItemStack built = type.manifestPiece(WuLingRealm.EMERALD.ordinal(), stage,
                ManifestItems.armorPartOf(crafted.getItem()));
        if (built.isEmpty()) {
            return;
        }
        CompoundTag tag = built.getTag();
        crafted.setTag(tag == null ? null : tag.copy());
    }
}
