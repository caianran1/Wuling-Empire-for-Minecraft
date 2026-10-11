package com.wuling.empire.wuling;

import com.google.common.collect.Multimap;
import com.wuling.empire.Config;
import com.wuling.empire.WulingEmpire;
import com.wuling.empire.capability.ModCapabilities;
import com.wuling.empire.item.SpiritQuality;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;

import java.util.UUID;

/**
 * 武器 / 工具 / 护甲的<b>锻造品质</b>系统（2026-10-11 用户设定）。
 *
 * <h3>口径</h3>
 * <ul>
 *   <li>品质沿用灵珠那五档：凡品 / 良品 / 优品 / 上品 / 极品。</li>
 *   <li><b>凡品 = 原版数值</b>，且<b>不打任何标记</b> —— 玩家拿着一柄普通铁剑，
 *       它跟原版铁剑完全一样，NBT 干净。</li>
 *   <li>良品以上才写 NBT + 挂属性修饰符，数值<b>逐级逼近「同材质的武灵凝聚物」</b>：
 *       倍率 = {@code 1 + (同材质武灵倍数 - 1) × 品质系数}。极品默认 0.85，
 *       也就是永远比武灵同款差一截（下界合金剑：极品 96.4 vs 武灵 112）。</li>
 *   <li>只加<b>属性</b>，不给附魔（用户 2026-10-11 明确「算了不给附魔加成」）。</li>
 * </ul>
 *
 * <h3>谁是铁匠</h3>
 * <b>持火武灵的玩家本人</b>（用户 2026-10-11 明确「铁匠是指玩家」）。
 * 合成时按自己的武灵境界掷品质；非火武灵的境界先除以 {@code smith.foreignDivisor}
 * 再查表，所以「一般人制造的一般都是凡品，少数有高阶的武器」。
 * 没开启武灵的玩家一律凡品。
 *
 * <h3>为什么用「1 + (M-1)×k」而不是「M×k」</h3>
 * 木剑的武灵倍数只有 3，乘 0.25 只有 0.75 —— 良品会比原版还弱。
 * 现在这个写法保证凡品严格等于原版，且各档单调递增。
 *
 * <h3>弓 / 弩为什么不参与</h3>
 * 1.20.1 没有任何「远程伤害」属性，弓的伤害由箭和附魔决定；
 * 而最大耐久不是属性、又没法在别人的物品类上覆盖 {@code getMaxDamage}。
 * 所以弓弩目前无属性可加，直接排除（{@link #manifestMultiplierOf} 返回 0）。
 * 挖矿速度同理不是属性（只有 {@code Tier#getSpeed}），工具的品质加成落在攻击力上。
 */
public final class QualitySmithing {

    /** 物品 NBT 上的品质键；没有这个键 = 凡品 */
    private static final String KEY_QUALITY = "WuLingQuality";

    /**
     * 属性修饰符 UUID —— 与武灵凝聚物那套<b>故意不同</b>。
     *
     * <p>UUID 必须稳定（原版换装备时按 UUID 摘旧的），且 MSB / LSB 都非 0，
     * 否则 {@code ItemStack#getAttributeModifiers} 会整条丢掉。
     * 每个装备槽一个，否则同时穿两件时只留先挂上的那条。
     */
    private static final UUID UUID_ATTACK_MAINHAND =
            UUID.fromString("a17e3c58-9b24-4d61-8f03-2e5b7c9d1046");
    private static final UUID UUID_ATTACK_OFFHAND =
            UUID.fromString("b28f4d69-0c35-4e72-9014-3f6c8d0e2157");
    /** 下标 = EquipmentSlot#getIndex()：0 脚 / 1 腿 / 2 胸 / 3 头 */
    private static final UUID[] UUID_ARMOR = {
            UUID.fromString("c3905e7a-1d46-4f83-a125-407d9e1f3268"),
            UUID.fromString("d4a16f8b-2e57-4094-b236-518e0f204379"),
            UUID.fromString("e5b2709c-3f68-41a5-8347-629f1031458a"),
            UUID.fromString("f6c3810d-4079-42b6-9458-73a02142569b"),
    };
    private static final UUID[] UUID_ARMOR_TOUGHNESS = {
            UUID.fromString("07d4921e-518a-43c7-a569-84b1325367ac"),
            UUID.fromString("18e5a32f-629b-44d8-b67a-95c2436478bd"),
            UUID.fromString("29f6b430-73ac-45e9-c78b-a6d3547589ce"),
            UUID.fromString("3a07c541-84bd-46fa-d89c-b7e465869adf"),
    };

    private QualitySmithing() {
    }

    // ===================== 读取 =====================

    /** 这件装备的锻造品质；没有标记 = 凡品 */
    public static SpiritQuality qualityOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SpiritQuality.FAN;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY_QUALITY)) {
            return SpiritQuality.FAN;
        }
        return SpiritQuality.byKey(tag.getString(KEY_QUALITY));
    }

    /**
     * 这件装备能不能带品质。
     *
     * <p>能带 = 有「材质」可对标的武器 / 工具 / 护甲。
     * <b>模组自己的物品一律排除</b>（红石装备、武灵凝聚物）——
     * 它们已经有自己的强度体系，再叠一层锻造品质就乱了。
     */
    public static boolean canHaveQuality(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (WulingEmpire.MODID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        return manifestMultiplierOf(stack) > 0.0D;
    }

    /**
     * 这件装备「同材质的武灵倍数」—— 品质加成的参照系。
     *
     * <p>木 3 / 石 4 / 黄金 8 / 玄铁 9 / 钻石 11 / 下界合金 14，与
     * {@link WuLingRealm} 的倍数一致，这样「极品 ≈ 武灵同款的 85%」才成立。
     * 返回 0 表示不参与（弓、弩、以及没有材质概念的东西）。
     */
    public static double manifestMultiplierOf(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TieredItem tiered) {
            return tierMultiplier(tiered.getTier());
        }
        if (item instanceof ArmorItem armor) {
            return materialMultiplier(armor.getMaterial());
        }
        if (item instanceof TridentItem) {
            // 三叉戟没有 Tier，但基础攻击 8 与下界合金剑同级
            return WuLingRealm.NETHERITE.manifestMultiplier();
        }
        return 0.0D;
    }

    private static double tierMultiplier(Tier tier) {
        if (tier == Tiers.WOOD) {
            return WuLingRealm.WOOD.manifestMultiplier();
        }
        if (tier == Tiers.STONE) {
            return WuLingRealm.STONE.manifestMultiplier();
        }
        if (tier == Tiers.GOLD) {
            return WuLingRealm.GOLD.manifestMultiplier();
        }
        if (tier == Tiers.IRON) {
            return WuLingRealm.METEOR_IRON.manifestMultiplier();
        }
        if (tier == Tiers.DIAMOND) {
            return WuLingRealm.DIAMOND.manifestMultiplier();
        }
        if (tier == Tiers.NETHERITE) {
            return WuLingRealm.NETHERITE.manifestMultiplier();
        }
        // 其它模组的材质：按 Tier#getLevel 就近折算（0 木 / 1 石 / 2 铁 / 3 钻 / 4 下界）
        switch (tier.getLevel()) {
            case 1: return WuLingRealm.STONE.manifestMultiplier();
            case 2: return WuLingRealm.METEOR_IRON.manifestMultiplier();
            case 3: return WuLingRealm.DIAMOND.manifestMultiplier();
            case 4:
            case 5: return WuLingRealm.NETHERITE.manifestMultiplier();
            default: return WuLingRealm.WOOD.manifestMultiplier();
        }
    }

    private static double materialMultiplier(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) {
            return WuLingRealm.WOOD.manifestMultiplier();
        }
        if (material == ArmorMaterials.CHAIN) {
            return WuLingRealm.STONE.manifestMultiplier();
        }
        if (material == ArmorMaterials.GOLD) {
            return WuLingRealm.GOLD.manifestMultiplier();
        }
        if (material == ArmorMaterials.IRON) {
            return WuLingRealm.METEOR_IRON.manifestMultiplier();
        }
        if (material == ArmorMaterials.DIAMOND || material == ArmorMaterials.TURTLE) {
            return WuLingRealm.DIAMOND.manifestMultiplier();
        }
        if (material == ArmorMaterials.NETHERITE) {
            return WuLingRealm.NETHERITE.manifestMultiplier();
        }
        // 其它材质（含别的模组）：给个中档，别让它无加成
        return WuLingRealm.DIAMOND.manifestMultiplier();
    }

    // ===================== 掷品质 =====================

    /**
     * 按玩家的武灵境界掷一次锻造品质。
     *
     * <p>没开启武灵 → 必然凡品；火武灵按自己那一档查表；其它武灵先按
     * {@code smith.foreignDivisor} 折算境界（默认除以 3），所以很难出好货。
     */
    public static SpiritQuality roll(Player player) {
        if (!Config.SMITH_ENABLED.get() || player == null) {
            return SpiritQuality.FAN;
        }
        var holder = player.getCapability(ModCapabilities.WU_LING).orElse(null);
        if (holder == null || !holder.data().isBound()) {
            return SpiritQuality.FAN;
        }
        WuLingData data = holder.data();
        int level = Math.max(0, data.realmOrdinal());
        if (data.type() != WuLingType.FIRE) {
            level /= Math.max(1, Config.SMITH_FOREIGN_DIVISOR.get());
        }
        int[] weights = Config.smithWeights(level);
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        if (total <= 0) {
            return SpiritQuality.FAN;
        }
        SpiritQuality[] qualities = SpiritQuality.values();
        int pick = player.getRandom().nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return qualities[i];
            }
        }
        return SpiritQuality.FAN;
    }

    // ===================== 施加 =====================

    /** 这件装备在某品质下的总倍率（凡品 = 1.0，且此时不该被调用） */
    public static double multiplierFor(ItemStack stack, SpiritQuality quality) {
        double base = manifestMultiplierOf(stack);
        if (base <= 0.0D) {
            return 1.0D;
        }
        return 1.0D + (base - 1.0D) * Config.smithFactor(quality);
    }

    /**
     * 把品质写到装备上：打 NBT 标记 + 挂属性修饰符。
     *
     * @return 是否真的加成了（凡品 / 不可加成的物品返回 false）
     */
    public static boolean apply(ItemStack stack, SpiritQuality quality) {
        if (!canHaveQuality(stack) || quality == null || quality == SpiritQuality.FAN) {
            return false;
        }
        double multiplier = multiplierFor(stack, quality);
        if (multiplier <= 1.0D + 1.0E-6D) {
            return false;
        }
        stack.getOrCreateTag().putString(KEY_QUALITY, quality.key());
        empower(stack, multiplier);
        return true;
    }

    /**
     * 按倍率补属性 —— 与 {@link WuLingType#empower} 同一套算法，但用本类自己的 UUID。
     *
     * <p>攻击力的计算基数要补上玩家自身的基础攻击力 1（游戏里「木剑 4」=
     * 修正值 3 + 基础 1），否则「显示值 × 倍率」会差 1 点；护甲值与韧性没有这层基础。
     */
    private static void empower(ItemStack stack, double multiplier) {
        double addPct = multiplier - 1.0D;

        amplify(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND,
                (double) Config.MANIFEST_ATTACK_BONUS.get() * addPct, UUID_ATTACK_MAINHAND, 1.0D);

        EquipmentSlot armorSlot = LivingEntity.getEquipmentSlotForItem(stack);
        if (armorSlot.getType() == EquipmentSlot.Type.ARMOR) {
            int index = Math.max(0, Math.min(UUID_ARMOR.length - 1, armorSlot.getIndex()));
            amplify(stack, Attributes.ARMOR, armorSlot,
                    (double) Config.MANIFEST_ARMOR_BONUS.get() * addPct, UUID_ARMOR[index], 0.0D);
            amplify(stack, Attributes.ARMOR_TOUGHNESS, armorSlot,
                    (double) Config.MANIFEST_ARMOR_BONUS.get() * addPct,
                    UUID_ARMOR_TOUGHNESS[index], 0.0D);
        }
    }

    /**
     * 把该物品「原版自带的」某项属性放大 pct%，作为一条额外修饰符挂到 NBT 上。
     *
     * @param referenceOffset 攻击力补 1.0（玩家基础攻击力），护甲 / 韧性补 0
     */
    private static void amplify(ItemStack stack, Attribute attribute, EquipmentSlot slot,
                                double pct, UUID uuid, double referenceOffset) {
        if (pct <= 0.0D) {
            return;
        }
        Multimap<Attribute, AttributeModifier> defaults =
                stack.getItem().getDefaultAttributeModifiers(slot);
        if (!defaults.containsKey(attribute)) {
            return;
        }
        double base = 0.0D;
        for (AttributeModifier modifier : defaults.get(attribute)) {
            base += modifier.getAmount();
        }
        if (base <= 0.0D) {
            return;
        }
        double extra = (base + referenceOffset) * pct / 100.0D;
        stack.addAttributeModifier(attribute,
                new AttributeModifier(uuid, "WuLing forged bonus", extra,
                        AttributeModifier.Operation.ADDITION),
                slot);
    }

    // ===================== 显示 =====================

    /** tooltip 里那行「锻造品质：上品」，颜色取自品质本身 */
    public static Component qualityLine(SpiritQuality quality) {
        return Component.translatable("tooltip.wulingdiguo.forged_quality",
                Component.translatable(quality.translationKey())
                        .withStyle(style -> style.withColor(quality.color())));
    }
}
