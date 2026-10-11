package com.wuling.empire.wuling;

import com.wuling.empire.Config;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 自选附魔书的凝聚消耗 —— 2026-10-02 用户口径：
 * 「附魔书凝聚等级越高，附魔书越稀有消耗越大」。
 *
 * <p>公式（客户端界面与服务端判定共用这一份，避免两边显示不一致）：
 *
 * <pre>
 * 消耗 = 基础凝聚消耗（condenseSpiritCost）
 *      × 境界折扣（Config#condenseCostFactor，2026-10-11 起：越大境界越省）
 *      × 稀有度倍率（bookRarityCommon / Uncommon / Rare / VeryRare）
 *      × (1 + bookCostPerLevel × (等级 − 1))
 * </pre>
 *
 * <p>最后<b>封顶到灵力上限</b>：灵力上限默认只有 100，不封顶的话
 * 「极稀有 + 高等级」那几档需要的量超过上限，玩家把灵力顶满也点不动，
 * 等于这些书被永久锁死。
 *
 * <p>默认值（基础 25 / 每级 +0.5 倍 / 木境界）下的实际消耗参考：
 *
 * <table>
 *   <tr><td>耐久 III</td><td>普通</td><td>50</td></tr>
 *   <tr><td>保护 IV</td><td>普通</td><td>62.5</td></tr>
 *   <tr><td>锋利 V</td><td>普通</td><td>75</td></tr>
 *   <tr><td>经验修补 I</td><td>稀有</td><td>62.5</td></tr>
 *   <tr><td>无限 I</td><td>极稀有</td><td>100（封顶）</td></tr>
 * </table>
 *
 * <p>同一本书在绿宝石期只要上表 ×0.4（境界折扣 40%）。
 */
public final class BookCost {

    private BookCost() {
    }

    /** 附魔稀有度对应的消耗倍率 */
    public static double rarityMultiplier(Enchantment enchantment) {
        return switch (enchantment.getRarity()) {
            case COMMON -> Config.BOOK_RARITY_COMMON.get();
            case UNCOMMON -> Config.BOOK_RARITY_UNCOMMON.get();
            case RARE -> Config.BOOK_RARITY_RARE.get();
            case VERY_RARE -> Config.BOOK_RARITY_VERY_RARE.get();
        };
    }

    /** 未封顶的原始消耗（界面想显示「需要多少」时也用这个） */
    public static double raw(Enchantment enchantment, int level, int realmOrdinal) {
        int max = Math.max(1, enchantment.getMaxLevel());
        int lvl = Math.max(1, Math.min(level, max));
        double base = Config.CONDENSE_SPIRIT_COST.get();
        double perLevel = Config.BOOK_COST_PER_LEVEL.get();
        return base * Config.condenseCostFactor(realmOrdinal)
                * rarityMultiplier(enchantment) * (1.0D + perLevel * (lvl - 1));
    }

    /** 实际扣除的灵力：不超过灵力上限 */
    public static float of(Enchantment enchantment, int level, int realmOrdinal) {
        double cap = Config.MAX_SPIRIT.get();
        double value = raw(enchantment, level, realmOrdinal);
        if (cap > 0.0D) {
            value = Math.min(value, cap);
        }
        return (float) Math.max(0.0D, value);
    }
}
