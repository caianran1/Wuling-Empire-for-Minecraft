package com.wuling.empire.item;

import com.wuling.empire.WulingEmpire;
import com.wuling.empire.wuling.WuLingRealm;
import com.wuling.empire.wuling.WuLingType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组专属的创造模式物品栏（2026-10-17 用户口径：
 * 「重新注册物品，将其单独列入可以从创造模式获得的创造模式物品栏」）。
 *
 * <p>0.4.3 及更早是把物品零散塞进原版的「原材料 / 战斗 / 工具」三个标签页，
 * 导致两个问题：</p>
 * <ul>
 *   <li>91 件凝聚物只有绿宝石档的 8 件被登记，其余 83 件在创造模式里根本拿不到；</li>
 *   <li>红石装备散落在两个标签页，玩家找不到。</li>
 * </ul>
 *
 * <p>现在统一收进 {@code wulingdiguo:main} 一个标签页，顺序固定：
 * 灵珠 → 绑定器 → 红石锭 → 七个境界各 13 件（剑斧镐铲弓水火红石书 + 护甲四件）→ 红石八件。
 * 原版标签页里不再重复登记，避免同一个物品出现两次。</p>
 */
public final class ModTabs {

    private ModTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WulingEmpire.MODID);

    /** 非护甲的九种凝聚物，按玩家最常用的顺序摆 */
    private static final WuLingType[] MANIFEST_ORDER = {
            WuLingType.SWORD, WuLingType.AXE, WuLingType.PICKAXE, WuLingType.SHOVEL,
            WuLingType.BOW, WuLingType.WATER, WuLingType.FIRE, WuLingType.REDSTONE,
            WuLingType.BOOK
    };

    /** 护甲部位，头 → 胸 → 腿 → 靴 */
    private static final ArmorItem.Type[] ARMOR_ORDER = {
            ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS
    };

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wulingdiguo.main"))
                    .icon(() -> new ItemStack(ModItems.BEADS.get(SpiritQuality.JI).get()))
                    .displayItems((params, output) -> fill(output))
                    .build());

    private static void fill(CreativeModeTab.Output output) {
        // ---- 灵珠：凡 → 极 ----
        for (SpiritQuality quality : SpiritQuality.values()) {
            output.accept(ModItems.BEADS.get(quality).get());
        }

        // ---- 绑定器（仅创造可得）与红石锭 ----
        output.accept(ModItems.WU_LING_BINDER.get());
        output.accept(ModItems.CREATIVE_WU_LING_BINDER.get());
        output.accept(RedstoneItems.REDSTONE_INGOT.get());

        // ---- 七个境界的凝聚物，每境界 13 件 ----
        for (WuLingRealm realm : WuLingRealm.values()) {
            for (WuLingType type : MANIFEST_ORDER) {
                output.accept(ManifestItems.single(realm, type));
            }
            for (ArmorItem.Type armorType : ARMOR_ORDER) {
                output.accept(ManifestItems.armor(realm, armorType));
            }
        }

        // ---- 红石战铠八件 ----
        for (var piece : RedstoneItems.GEAR) {
            output.accept(piece.get());
        }
    }
}
