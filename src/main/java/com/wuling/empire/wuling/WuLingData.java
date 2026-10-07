package com.wuling.empire.wuling;

import com.wuling.empire.Config;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家武灵数据（数据载体，不直接挂 Savings，由 Capability 持有）。
 *
 * 境界索引体系：一个大境界内含三个小境界，
 *   总索引 = 大境界序号 × 3 + 小境界序号      （取值 0 ~ 20）
 *
 * 晋升规则（原文）：
 *   前 → 中 → 后：无渡劫，累积使用武灵的修炼进度，面板到阈值自动升。
 *   后 → 下一大境界：属于大境界突破，必须走提交物资 + 清空灵力的流程，
 *                    本类的自动累积不会跨过大境界。
 */
public class WuLingData {

    private static final String KEY_BOUND = "Bound";
    private static final String KEY_TYPE = "Type";
    private static final String KEY_SOURCE = "Source";
    private static final String KEY_REALM = "Realm";
    private static final String KEY_STAGE = "Stage";
    private static final String KEY_PROGRESS = "Progress";
    /** 修炼速度加成（由开启武灵那批灵珠的品质决定；2026-09-27 取代旧的 "Cap" 境界上限） */
    private static final String KEY_CULTIVATION = "CultivationBonus";
    private static final String KEY_SUBMIT = "Submit";
    private static final String KEY_SUBMIT_TARGET = "SubmitTarget";
    /** 腐肉武灵的突破货币：累计击杀僵尸数 */
    private static final String KEY_ZOMBIE_KILLS = "ZombieKills";
    private static final String KEY_REDSTONE_CHARGE = "RedstoneCharge";

    private boolean bound = false;
    private String typeKey = WuLingType.SWORD.key();
    private String sourceKey = "";
    private int realmOrdinal = 0;
    private int stageOrdinal = 0;
    private double progress = 0.0D;
    /**
     * 修炼速度加成倍率：由开启武灵时交出那批灵珠的品质决定。
     *
     * 2026-09-27 用户修订：<b>品质不再决定境界上限</b>（上限所有人一样，见
     * {@link Config#maxRealm()}），品质只决定修炼快多少 —— 极品 3 倍速不代表能修得更高，
     * 只是同样的动作攒进度更快。
     */
    private double cultivationBonus = 1.0D;

    /**
     * 突破物资「缴纳池」：背包放不下的物资可以先分批缴进来，攒齐后再突破。
     * 键为 {@link BreakthroughRequirement#itemKey} / {@link BreakthroughRequirement#beadKey} 生成的字符串。
     */
    private CompoundTag submitted = new CompoundTag();
    /** 缴纳池对应的目标大境界序号；与当前目标不一致时说明配置变了或已突破，需要退还重来 */
    private int submitTarget = -1;

    /**
     * 腐肉武灵的「累计击杀僵尸数」。
     *
     * <p>它同时是<b>突破的唯一货币</b> —— 其他武灵交物资，腐肉武灵只认这个数
     * （用户 2026-10-02 口径：「升级不消耗材料，只消耗击杀僵尸的量」）。
     * 击杀数只增不减，突破时扣除对应档位的数量。
     */
    private int zombieKills = 0;

    /**
     * 红石装备的充能（点，0 ~ {@code Config#REDSTONE_MAX_CHARGE}）。
     *
     * <p>2026-10-02 用户口径：红石装备（护甲四件 + 剑斧镐铲）共用<b>一个</b>充能池，
     * 用尽后手持任意一件红石装备右键打开充能菜单、用灵珠换充能。
     * 池子挂在玩家身上而不是物品上 —— 因为它是「整套共享」的，
     * 记在某一件物品的 NBT 上没法共享。
     */
    private int redstoneCharge = 0;

    // ===================== 查询 =====================

    public boolean isBound() {
        return bound;
    }

    public WuLingType type() {
        return WuLingType.byKey(typeKey);
    }

    public String typeKey() {
        return typeKey;
    }

    /** 绑定时使用的灵珠来源（决定修炼方向），可能为空 */
    public String sourceKey() {
        return sourceKey;
    }

    public WuLingRealm realm() {
        return WuLingRealm.byOrdinal(realmOrdinal);
    }

    public WuLingStage stage() {
        return WuLingStage.byOrdinal(stageOrdinal);
    }

    public int realmOrdinal() {
        return realmOrdinal;
    }

    public int stageOrdinal() {
        return stageOrdinal;
    }

    public int combinedIndex() {
        return realmOrdinal * 3 + stageOrdinal;
    }

    public double progress() {
        return progress;
    }

    /** 当前进度在 0.0 ~ 1.0 之间的比例 */
    public double progressRatio() {
        double threshold = Config.stageThreshold(realmOrdinal);
        if (threshold <= 0.0D) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, progress / threshold));
    }

    /** 统一的最高境界序号（所有玩家一致，与灵珠品质无关） */
    public int capRealm() {
        return Config.maxRealm();
    }

    /** 修炼速度加成倍率（1.0 = 基准速度） */
    public double cultivationBonus() {
        return cultivationBonus;
    }

    /**
     * 武灵显示名：只有种类本身，例如「剑」，不带括号、不带来源。
     * （括号只用在灵珠上，武灵名称保持干净。）
     */
    public net.minecraft.network.chat.Component displayName() {
        return net.minecraft.network.chat.Component.translatable(type().translationKey());
    }

    /** 是否已抵达统一的境界上限（达到上限后只能靠大境界突破继续，而上限已到则到顶） */
    public boolean isAtCap() {
        return realmOrdinal >= capRealm();
    }

    /** 是否已抵达终极境界（绿宝石·后期） */
    public boolean isMaxed() {
        return realmOrdinal >= WuLingRealm.EMERALD.ordinal() && stageOrdinal >= WuLingStage.LATE.ordinal();
    }

    // ===================== 绑定 =====================

    /**
     * 开启武灵。
     *
     * @param cultivationBonus 修炼速度加成（由所交灵珠的品质决定，见
     *                         {@link Config#cultivationBonus}）；所有人能修到的上限都一样
     */
    public void bind(WuLingType type, String sourceKey, double cultivationBonus) {
        this.bound = true;
        this.typeKey = type.key();
        this.sourceKey = sourceKey == null ? "" : sourceKey;
        this.realmOrdinal = 0;
        this.stageOrdinal = 0;
        this.progress = 0.0D;
        this.cultivationBonus = Math.max(0.0D, cultivationBonus);
        // 换武灵 = 重头再来，击杀数不跟着走
        this.zombieKills = 0;
    }

    public void unbind() {
        this.bound = false;
        this.progress = 0.0D;
        this.realmOrdinal = 0;
        this.stageOrdinal = 0;
        this.zombieKills = 0;
        clearSubmitted();
    }

    // ===================== 突破物资缴纳池 =====================

    /** 某个键已缴纳的数量 */
    public int submittedAmount(String key) {
        return Math.max(0, submitted.getInt(key));
    }

    /** 累加已缴纳数量 */
    public void addSubmitted(String key, int amount) {
        if (key == null || key.isEmpty() || amount <= 0) {
            return;
        }
        submitted.putInt(key, submittedAmount(key) + amount);
    }

    /** 供渲染用的只读快照（会同步给客户端） */
    public Map<String, Integer> submittedSnapshot() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String key : submitted.getAllKeys()) {
            map.put(key, submitted.getInt(key));
        }
        return map;
    }

    public boolean hasSubmitted() {
        return !submitted.isEmpty();
    }

    public void clearSubmitted() {
        submitted = new CompoundTag();
        submitTarget = -1;
    }

    public int submitTarget() {
        return submitTarget;
    }

    public void setSubmitTarget(int target) {
        this.submitTarget = target;
    }

    // ===================== 腐肉武灵：击杀僵尸数 =====================

    /** 累计击杀的僵尸数（突破用） */
    public int zombieKills() {
        return zombieKills;
    }

    /** 记一次僵尸击杀；只对腐肉武灵有意义，但累加本身对所有武灵无害 */
    public void addZombieKills(int amount) {
        if (amount > 0) {
            this.zombieKills += amount;
        }
    }

    /**
     * 扣掉突破消耗的击杀数。
     *
     * @return true = 数量足够并已扣除；false = 不够，什么都没动
     */
    public boolean consumeZombieKills(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.zombieKills < amount) {
            return false;
        }
        this.zombieKills -= amount;
        return true;
    }

    // ===================== 红石装备：充能（2026-10-02） =====================

    /**
     * 红石装备当前充能（点）。
     *
     * <p>这套值和武灵本身无关 —— 未开启武灵的玩家也能穿红石装备、也能充能，
     * 所以 {@link #unbind()} 不会清它（不像击杀数那样跟着武灵走）。
     */
    public int redstoneCharge() {
        return redstoneCharge;
    }

    public void setRedstoneCharge(int value) {
        this.redstoneCharge = Math.max(0, value);
    }

    /**
     * 加减充能并夹在 [0, 上限] 内。
     *
     * @return 实际变化的点数（正 = 充进去多少，负 = 消耗掉多少），
     * 已经满 / 已经空时为 0，调用方可据此判断要不要发同步包
     */
    public int addRedstoneCharge(int delta) {
        int max = Config.REDSTONE_MAX_CHARGE.get();
        int before = redstoneCharge;
        int after = Math.max(0, Math.min(max, before + delta));
        this.redstoneCharge = after;
        return after - before;
    }

    // ===================== 修炼进度 =====================

    /**
     * 累积修炼进度并处理小境界自动晋升。
     *
     * @return 本次是否发生了小境界晋升（用于给玩家发提示）
     */
    public boolean addProgress(double amount) {
        if (!bound || amount <= 0.0D) {
            return false;
        }
        if (isMaxed()) {
            return false;
        }

        boolean promoted = false;
        // 灵珠品质只体现在这里：同样的动作，品质越高攒得越快
        progress += amount * cultivationBonus;

        while (true) {
            double threshold = Config.stageThreshold(realmOrdinal);
            if (progress < threshold) {
                break;
            }
            // 已达当前大境界的后期：不能靠累积跨到大境界，需要突破流程
            if (stageOrdinal >= WuLingStage.LATE.ordinal()) {
                progress = threshold;
                break;
            }
            // 统一上限：最高大境界仍然能升小境界（前 → 中 → 后），
            // 只是不能再往上跨大境界 —— 跨界由上面那道 LATE 守卫拦着。
            // ⚠️ 这里必须是 `>`：写成 `>=` 会把顶档玩家的中 / 后期一起锁死
            // （把 maxRealm 调到 5 时尤其明显：下界合金永远停在前期）。
            if (realmOrdinal > capRealm()) {
                progress = threshold;
                break;
            }
            stageOrdinal++;
            progress -= threshold;
            promoted = true;
            if (isMaxed()) {
                progress = 0.0D;
                break;
            }
        }
        return promoted;
    }

    /** 大境界突破成功后升级 */
    public void advanceRealm() {
        if (realmOrdinal >= WuLingRealm.EMERALD.ordinal()) {
            stageOrdinal = WuLingStage.LATE.ordinal();
            progress = 0.0D;
            return;
        }
        realmOrdinal++;
        stageOrdinal = 0;
        progress = 0.0D;
    }

    // ===================== 序列化 =====================

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_BOUND, bound);
        tag.putString(KEY_TYPE, typeKey);
        tag.putString(KEY_SOURCE, sourceKey);
        tag.putInt(KEY_REALM, realmOrdinal);
        tag.putInt(KEY_STAGE, stageOrdinal);
        tag.putDouble(KEY_PROGRESS, progress);
        tag.putDouble(KEY_CULTIVATION, cultivationBonus);
        tag.put(KEY_SUBMIT, submitted);
        tag.putInt(KEY_SUBMIT_TARGET, submitTarget);
        tag.putInt(KEY_ZOMBIE_KILLS, zombieKills);
        tag.putInt(KEY_REDSTONE_CHARGE, redstoneCharge);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        this.bound = tag.getBoolean(KEY_BOUND);
        this.typeKey = tag.getString(KEY_TYPE);
        if (this.typeKey.isEmpty()) {
            this.typeKey = WuLingType.SWORD.key();
        }
        this.sourceKey = tag.getString(KEY_SOURCE);
        this.realmOrdinal = Math.max(0, tag.getInt(KEY_REALM));
        this.stageOrdinal = Math.max(0, tag.getInt(KEY_STAGE));
        this.progress = Math.max(0.0D, tag.getDouble(KEY_PROGRESS));
        // 老存档只有旧的 "Cap"（品质上限），没有修炼加成 —— 按 1.0 基准速度读入，
        // 想要加成可以用创造绑定器重开一次武灵。
        this.cultivationBonus = tag.contains(KEY_CULTIVATION) ? tag.getDouble(KEY_CULTIVATION) : 1.0D;
        this.submitted = tag.contains(KEY_SUBMIT) ? tag.getCompound(KEY_SUBMIT) : new CompoundTag();
        this.submitTarget = tag.contains(KEY_SUBMIT_TARGET) ? tag.getInt(KEY_SUBMIT_TARGET) : -1;
        this.zombieKills = Math.max(0, tag.getInt(KEY_ZOMBIE_KILLS));
        this.redstoneCharge = Math.max(0, tag.getInt(KEY_REDSTONE_CHARGE));
    }
}
