package com.wuling.empire;

import com.wuling.empire.item.SpiritQuality;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 模组配置（COMMON）。
 *
 * 所有灵珠系统的可调数值集中在此，方便后续数值调整而无需改代码。
 */
public final class Config {

    private Config() {
    }

    /**
     * 设定层面「永不出灵珠」的生物 —— 硬编码，**不受 config 文件影响**。
     *
     * 为什么不直接靠 {@link #EXCLUDED_MOBS}：Forge 的配置文件一旦生成就固定在
     * 存档/config 目录里，改代码里的默认值对已生成的旧配置**无效**，玩家会遇到
     * 「代码里明明排除了，游戏里还掉」的怪事。这几只是设定上就不该有灵珠的，
     * 所以在这里再钉死一遍，与 EXCLUDED_MOBS 取并集。
     *   蠹虫、恼鬼 —— 原版设定点名排除；
     *   末影螨 —— 2026-09-26 用户明确「末影螨也不要」；
     *   武灵僵尸（腐肉武灵召唤出的随从）—— 2026-10-02 用户「腐肉僵尸被打死不掉灵珠」。
     *     不封的话可以「召唤一批 → 打死 → 捡珠」白刷灵珠。
     */
    public static final Set<String> NEVER_DROPS = Set.of(
            "minecraft:silverfish",
            "minecraft:vex",
            "minecraft:endermite",
            "wulingdiguo:wu_ling_zombie"
    );

    /**
     * 建筑系统里<b>永不坠落</b>的方块 —— 同样硬编码，不受 config 影响。
     *
     * <p>按说这些方块玩家在生存里也放不出来，列在这里是给创造模式和指令兜底：
     * 基岩、屏障、结构方块这类「世界骨架」如果因为悬空掉下去，会很难解释。
     * 火把 / 花 / 门那类「依附型」方块不在这里 —— 它们由
     * {@code BuildingSystem#isAttached} 按方块标签统一豁免。
     */
    public static final Set<String> BUILDING_NEVER_FALLS = Set.of(
            "minecraft:bedrock",
            "minecraft:barrier",
            "minecraft:structure_block",
            "minecraft:structure_void",
            "minecraft:light",
            "minecraft:jigsaw",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block"
    );

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ===================== 灵力面板 =====================
    public static final ForgeConfigSpec.DoubleValue MAX_SPIRIT;
    public static final ForgeConfigSpec.DoubleValue REGEN_PER_MINUTE;

    /**
     * 灵力已满时，仍然吸收灵珠能换到的<b>少量修炼进度</b>（2026-10-01 用户口径
     * 「在灵力满时吸收灵珠可小幅度增加修为」）。
     *
     * <p>真正累加的进度 = 该值 × 灵珠品质的修炼倍率，所以极品珠比凡品珠划算。
     * 设为 0 即关闭（回到「灵力满时吸收只会提示灵力已满」的老行为）。
     */
    public static final ForgeConfigSpec.DoubleValue BEAD_FULL_PROGRESS;

    // ===================== 灵珠回复量：品质倍率 =====================
    // 最终回复量 = 怪物基准（item/BeadPower 的 BASE 表）× 这里的品质倍率
    public static final ForgeConfigSpec.DoubleValue RESTORE_MULT_FAN;
    public static final ForgeConfigSpec.DoubleValue RESTORE_MULT_LIANG;
    public static final ForgeConfigSpec.DoubleValue RESTORE_MULT_YOU;
    public static final ForgeConfigSpec.DoubleValue RESTORE_MULT_SHANG;
    public static final ForgeConfigSpec.DoubleValue RESTORE_MULT_JI;

    // ===================== 掉落权重 =====================
    public static final ForgeConfigSpec.DoubleValue WEIGHT_FAN;
    public static final ForgeConfigSpec.DoubleValue WEIGHT_LIANG;
    public static final ForgeConfigSpec.DoubleValue WEIGHT_YOU;
    public static final ForgeConfigSpec.DoubleValue WEIGHT_SHANG;
    public static final ForgeConfigSpec.DoubleValue WEIGHT_JI;
    public static final ForgeConfigSpec.DoubleValue STRENGTH_INFLUENCE;

    // ===================== 怪物尸体 =====================
    public static final ForgeConfigSpec.IntValue CORPSE_LIFETIME_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_MOBS;
    public static final ForgeConfigSpec.BooleanValue DROP_BEADS_TO_GROUND_ON_TIMEOUT;

    // ===================== 第二部：武灵系统 =====================

    public static final ForgeConfigSpec.IntValue BINDING_CONSUMES_BEAD_COUNT;
    public static final ForgeConfigSpec.BooleanValue BINDING_REQUIRES_NPC;

    /**
     * 武灵的统一境界上限（所有玩家都一样）。
     *
     * 2026-09-27 用户修订：<b>上限不再由所交灵珠的品质决定</b>，
     * 谁来开启、拿什么品质的灵珠来开启，能修到的最高境界都相同；
     * 品质只影响<b>修炼速度</b>（见下面的 cultivationBonus*）。
     *
     * ⚠️ 键名从旧的 capFan / capLiang / capYou / capShang / capJi 换成单一的 maxRealm：
     * 老配置文件里旧键仍有值，而 Forge 不会跟随代码默认值更新，
     * 沿用旧键会出现「改了上限却没生效」的假象。
     */
    public static final ForgeConfigSpec.IntValue MAX_REALM;

    /** 灵珠品质 -> 修炼速度加成倍率（乘在每次修炼动作的进度上） */
    public static final ForgeConfigSpec.DoubleValue CULTIVATION_BONUS_FAN;
    public static final ForgeConfigSpec.DoubleValue CULTIVATION_BONUS_LIANG;
    public static final ForgeConfigSpec.DoubleValue CULTIVATION_BONUS_YOU;
    public static final ForgeConfigSpec.DoubleValue CULTIVATION_BONUS_SHANG;
    public static final ForgeConfigSpec.DoubleValue CULTIVATION_BONUS_JI;

    /** 小境界晋升所需进度曲线 */
    public static final ForgeConfigSpec.DoubleValue THRESHOLD_BASE;
    public static final ForgeConfigSpec.DoubleValue THRESHOLD_GROWTH;

    /** 各类修炼动作的进度产出 */
    public static final ForgeConfigSpec.DoubleValue PROGRESS_ATTACK;
    public static final ForgeConfigSpec.DoubleValue PROGRESS_MINE;
    public static final ForgeConfigSpec.DoubleValue PROGRESS_SHOOT;
    public static final ForgeConfigSpec.DoubleValue PROGRESS_GUARD;
    public static final ForgeConfigSpec.DoubleValue PROGRESS_USE;

    /**
     * 突破是否消耗（清空）灵力。
     * 2026-09-26 起默认 false —— 灵力改由「凝聚武灵」消耗。
     * ⚠️ 键名从旧的 breakthroughClearsSpirit 改成 breakthroughConsumesSpirit：
     * 老配置文件里旧键是 true，Forge 不会跟随代码默认值更新，沿用旧键会导致「改了默认值却没生效」。
     */
    public static final ForgeConfigSpec.BooleanValue BREAKTHROUGH_CONSUMES_SPIRIT;

    /** 凝聚一次实体武灵消耗的灵力（百分制） */
    public static final ForgeConfigSpec.DoubleValue CONDENSE_SPIRIT_COST;

    /**
     * 自选附魔书的消耗规则（2026-10-02 用户口径：
     * 「附魔书凝聚等级越高，附魔书越稀有消耗越大」）。
     *
     * <p>实际消耗 = 基础凝聚消耗 × <b>稀有度倍率</b> × (1 + {@code bookCostPerLevel} × (等级 − 1))，
     * 最后封顶到灵力上限 —— 不封顶的话高等级书永远做不出来（灵力上限默认只有 100）。
     * 计算收口在 {@code wuling/BookCost}。
     */
    public static final ForgeConfigSpec.DoubleValue BOOK_COST_PER_LEVEL;
    /** 附魔稀有度倍率：普通 / 少见 / 稀有 / 极稀有（原版 Enchantment.Rarity） */
    public static final ForgeConfigSpec.DoubleValue BOOK_RARITY_COMMON;
    public static final ForgeConfigSpec.DoubleValue BOOK_RARITY_UNCOMMON;
    public static final ForgeConfigSpec.DoubleValue BOOK_RARITY_RARE;
    public static final ForgeConfigSpec.DoubleValue BOOK_RARITY_VERY_RARE;

    /** 凝聚出的武灵比原版同款多出的攻击力（按原版数值的百分比） */
    public static final ForgeConfigSpec.DoubleValue MANIFEST_ATTACK_BONUS;

    /** 凝聚出的武灵比原版同款多出的护甲值（按原版数值的百分比） */
    public static final ForgeConfigSpec.DoubleValue MANIFEST_ARMOR_BONUS;

    /** 每提升一个小境界，凝聚物基础属性额外增加的倍率（0.1 = 每级 +10%） */
    public static final ForgeConfigSpec.DoubleValue MANIFEST_STAGE_STEP;

    /**
     * 「ALL_BEADS / ALL_JI_BEADS（每种怪物灵珠各 N 个）」类突破条件里额外排除的怪物 ID。
     * 这些怪物照样掉灵珠，只是不参与这几档突破的收集要求。
     * 绿宝石档用的是 BEADS_TOTAL（任意灵珠 100 颗），本名单对它不起作用。
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BREAKTHROUGH_EXCLUDED_MOBS;

    /** 6 段大境界突破的物资需求 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQ_WOOD_TO_STONE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQ_STONE_TO_GOLD;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQ_GOLD_TO_METEOR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQ_METEOR_TO_DIAMOND;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQ_DIAMOND_TO_NETHERITE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQ_NETHERITE_TO_EMERALD;

    // ===================== 腐肉武灵（召唤类，2026-10-02） =====================

    /**
     * 传说档（腐肉武灵）在抽取池里的权重。
     *
     * <p>推导：其余 10 类的权重合计 = 5 个稀有 ×1 + 5 个常见 ×10 = <b>55</b>。
     * 要让腐肉的实际概率 ≈ 1/100000，需要
     * {@code w / (55 + w) = 1e-5} → {@code w ≈ 55e-5 = 0.00055}。
     *
     * <p>所以这个值不是「1/100000」的字面数，而是与上面那 55 配平后的结果 ——
     * 将来调整基类权重时，这里要跟着重算（或直接按「想要的实际概率」反推填进来）。
     */
    public static final ForgeConfigSpec.DoubleValue MYTHIC_WEIGHT;

    /** 腐肉武灵每一个大境界可召唤的僵尸只数（用户口径：每有一个大境界 5 只） */
    public static final ForgeConfigSpec.IntValue ROTTEN_FLESH_ZOMBIES_PER_REALM;

    /** 腐肉武灵击杀一只僵尸获得的修为进度 */
    public static final ForgeConfigSpec.DoubleValue ROTTEN_FLESH_KILL_PROGRESS;

    /** 腐肉武灵第一档突破所需击杀数；第 n 档 = 该值 × 增长率^(n-1) */
    public static final ForgeConfigSpec.IntValue ROTTEN_FLESH_KILL_BASE;
    /** 腐肉武灵每升一个大境界，突破所需击杀数的增长倍率 */
    public static final ForgeConfigSpec.DoubleValue ROTTEN_FLESH_KILL_GROWTH;

    /** 召唤出的僵尸从哪个大境界起可以飞行（用户口径：钻石境界） */
    public static final ForgeConfigSpec.IntValue ROTTEN_FLESH_FLY_REALM;

    // ===================== 红石装备（充能类，2026-10-02） =====================
    /** 红石装备的充能上限（点） */
    public static final ForgeConfigSpec.IntValue REDSTONE_MAX_CHARGE;
    /** 灵珠 → 充能的换算系数：灵珠回复量（%）× 本值 = 充能点数 */
    public static final ForgeConfigSpec.IntValue REDSTONE_CHARGE_PER_PERCENT;
    /** 攻击命中消耗 */
    public static final ForgeConfigSpec.IntValue REDSTONE_COST_ATTACK;
    /** 挖掉方块消耗 */
    public static final ForgeConfigSpec.IntValue REDSTONE_COST_MINE;
    /** 挨打受击消耗 */
    public static final ForgeConfigSpec.IntValue REDSTONE_COST_HURT;
    /** 飞行每秒消耗（点） */
    public static final ForgeConfigSpec.IntValue REDSTONE_COST_FLY;
    /** 充能耗尽时的攻击力惩罚比例（0.6 = 攻击力砍到 40%） */
    public static final ForgeConfigSpec.DoubleValue REDSTONE_EMPTY_ATTACK_PENALTY;
    /** 充能耗尽时的移动速度惩罚比例（0.3 = 移速掉到 70%） */
    public static final ForgeConfigSpec.DoubleValue REDSTONE_EMPTY_SPEED_PENALTY;

    // ===================== 建筑系统（悬空坠落，2026-10-02） =====================
    /** 是否启用建筑系统 */
    public static final ForgeConfigSpec.BooleanValue BUILDING_ENABLED;
    /** 悬空方块开始坠落前的延迟（tick） */
    public static final ForgeConfigSpec.IntValue BUILDING_DELAY_TICKS;
    /** 用粘液球固定时是否消耗一颗 */
    public static final ForgeConfigSpec.BooleanValue BUILDING_SLIME_CONSUMED;
    /** 创造 / 旁观玩家放置的方块是否豁免 */
    public static final ForgeConfigSpec.BooleanValue BUILDING_CREATIVE_IMMUNE;
    /** 倒计时中的方块是否冒粒子提示 */
    public static final ForgeConfigSpec.BooleanValue BUILDING_SHOW_PARTICLES;
    /** 玩家额外排除的方块 ID */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BUILDING_EXCLUDED;

    // ===================== 怪物等级（武灵属性，2026-09-27） =====================
    /** 怪物带武灵等级的概率 */
    public static final ForgeConfigSpec.DoubleValue MONSTER_TIER_CHANCE;
    /** 等级越高的稀有个衰减比（几何衰减的底数） */
    public static final ForgeConfigSpec.DoubleValue MONSTER_TIER_RARITY;
    /** 每高一个大境界，最大生命值的追加比例 */
    public static final ForgeConfigSpec.DoubleValue MONSTER_TIER_HEALTH_BONUS;
    /** 不参与怪物等级系统的生物 ID */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MONSTER_TIER_EXCLUDED_MOBS;

    static {
        BUILDER.push("spirit");

        MAX_SPIRIT = BUILDER.comment("灵力上限（百分制）")
                .defineInRange("maxSpirit", 100.0D, 1.0D, 1000.0D);

        REGEN_PER_MINUTE = BUILDER.comment("灵力自然回复速度：每分钟回复百分比")
                .defineInRange("regenPerMinute", 1.0D, 0.0D, 100.0D);

        BEAD_FULL_PROGRESS = BUILDER.comment("灵力已满时吸收灵珠得到的少量修炼进度；",
                        "实际累加 = 该值 × 灵珠品质的修炼倍率。0 = 关闭。")
                .defineInRange("beadFullProgress", 3.0D, 0.0D, 10000.0D);

        BUILDER.pop();

        BUILDER.push("bead");

        RESTORE_MULT_FAN = BUILDER.comment("凡品灵珠的回复倍率（与怪物基准相乘）")
                .defineInRange("restoreMultiplierFan", 1.0D, 0.0D, 100.0D);
        RESTORE_MULT_LIANG = BUILDER.comment("良品灵珠的回复倍率")
                .defineInRange("restoreMultiplierLiang", 1.5D, 0.0D, 100.0D);
        RESTORE_MULT_YOU = BUILDER.comment("优品灵珠的回复倍率")
                .defineInRange("restoreMultiplierYou", 2.0D, 0.0D, 100.0D);
        RESTORE_MULT_SHANG = BUILDER.comment("上品灵珠的回复倍率")
                .defineInRange("restoreMultiplierShang", 3.0D, 0.0D, 100.0D);
        RESTORE_MULT_JI = BUILDER.comment("极品灵珠的回复倍率。" +
                        "回复量 = 怪物基准（item/BeadPower 的 BASE 表，凡品值）× 本倍率；" +
                        "例：僵尸基准 5 → 极品 20%，末影龙基准 25 → 极品 100%。")
                .defineInRange("restoreMultiplierJi", 4.0D, 0.0D, 100.0D);

        BUILDER.pop();

        BUILDER.push("drop");

        WEIGHT_FAN = BUILDER.comment("凡品灵珠基础权重")
                .defineInRange("weightFan", 50.0D, 0.0D, 100000.0D);
        WEIGHT_LIANG = BUILDER.comment("良品灵珠基础权重")
                .defineInRange("weightLiang", 30.0D, 0.0D, 100000.0D);
        WEIGHT_YOU = BUILDER.comment("优品灵珠基础权重")
                .defineInRange("weightYou", 14.0D, 0.0D, 100000.0D);
        WEIGHT_SHANG = BUILDER.comment("上品灵珠基础权重")
                .defineInRange("weightShang", 5.0D, 0.0D, 100000.0D);
        WEIGHT_JI = BUILDER.comment("极品灵珠基础权重")
                .defineInRange("weightJi", 1.0D, 0.0D, 100000.0D);

        STRENGTH_INFLUENCE = BUILDER.comment("怪物强度对高品质灵珠权重的影响系数。" +
                        "实际权重 = 基础权重 * (1 + (怪物最大生命值 - 20) / 40 * 该系数) ^ 品质序号，" +
                        "序号：凡品0 / 良品1 / 优品2 / 上品3 / 极品4。设为 0 则完全随机，与怪物强度无关。")
                .defineInRange("strengthInfluence", 1.0D, 0.0D, 5.0D);

        EXCLUDED_MOBS = BUILDER.comment("不掉落灵珠的生物 ID 列表（原版设定：蠹虫、恼鬼除外；末影螨另加）")
                .defineList("excludedMobs", Arrays.asList(
                        "minecraft:silverfish",
                        "minecraft:vex",
                        "minecraft:endermite"
                ), o -> o instanceof String);

        BUILDER.pop();

        BUILDER.push("corpse");

        // 2026-10-02：普通怪改为直接掉灵珠，尸体只剩末影龙在用，所以这两项只对它生效
        CORPSE_LIFETIME_TICKS = BUILDER.comment("尸体存在时长（tick），1200 tick = 1 分钟。",
                        "2026-10-02 起普通怪不再留尸体（改为直接掉落灵珠），本项只对末影龙尸体生效。")
                .defineInRange("lifetimeTicks", 12000, 20, Integer.MAX_VALUE);

        DROP_BEADS_TO_GROUND_ON_TIMEOUT = BUILDER.comment("末影龙尸体超时消失时是否把未取走的灵珠掉落到地面")
                .define("dropBeadsOnTimeout", true);

        BUILDER.pop();

        // ===================== 第二部：武灵系统 =====================

        BUILDER.push("wuling");

        BINDING_CONSUMES_BEAD_COUNT = BUILDER.comment("开启 / 绑定一次武灵需要消耗的灵珠数量")
                .defineInRange("bindingBeadCount", 1, 1, 64);

        BINDING_REQUIRES_NPC = BUILDER.comment("是否必须靠近「拥有水武灵的 NPC」才能开启武灵。" +
                        "NPC 实体属于后续阶段，因此当前默认 false——false 时拿着绑定器就能绑。")
                .define("bindingRequiresNpc", false);

        // 统一的境界上限 + 品质 -> 修炼速度。境界序号：木0 石1 黄金2 玄铁3 钻石4 下界合金5 绿宝石6
        MAX_REALM = BUILDER.comment(
                        "所有武灵共用的最高境界序号（0木 1石 2黄金 3玄铁 4钻石 5下界合金 6绿宝石）。" +
                        "2026-09-27 起上限与灵珠品质无关：不论谁来开启、交什么品质的灵珠，" +
                        "能修到的最高境界都一样；品质只影响修炼速度（见下面的 cultivationBonus*）。" +
                        "默认 6 —— 人人都能修到绿宝石。")
                .defineInRange("maxRealm", 6, 0, 6);

        CULTIVATION_BONUS_FAN = BUILDER.comment(
                        "凡品灵珠开启的武灵，修炼速度加成倍率（乘在每次修炼动作累积的进度上）。" +
                        "1.0 = 基准速度不变。")
                .defineInRange("cultivationBonusFan", 1.0D, 0.0D, 100.0D);
        CULTIVATION_BONUS_LIANG = BUILDER.comment("良品灵珠开启的武灵，修炼速度加成倍率")
                .defineInRange("cultivationBonusLiang", 1.25D, 0.0D, 100.0D);
        CULTIVATION_BONUS_YOU = BUILDER.comment("优品灵珠开启的武灵，修炼速度加成倍率")
                .defineInRange("cultivationBonusYou", 1.5D, 0.0D, 100.0D);
        CULTIVATION_BONUS_SHANG = BUILDER.comment("上品灵珠开启的武灵，修炼速度加成倍率")
                .defineInRange("cultivationBonusShang", 2.0D, 0.0D, 100.0D);
        CULTIVATION_BONUS_JI = BUILDER.comment(
                        "极品灵珠开启的武灵，修炼速度加成倍率。" +
                        "默认 3.0 —— 同一套动作，极品灵珠攒进度是凡品的 3 倍快，" +
                        "但不能突破上限（上限由 maxRealm 统一决定）。")
                .defineInRange("cultivationBonusJi", 3.0D, 0.0D, 100.0D);

        THRESHOLD_BASE = BUILDER.comment("小境界晋升基准进度；第 n 个境界所需进度 = 该值 * (n+1)")
                .defineInRange("thresholdBase", 120.0D, 1.0D, 1000000.0D);

        THRESHOLD_GROWTH = BUILDER.comment("每提升一个大境界，所需进度的增长倍率")
                .defineInRange("thresholdGrowth", 2.2D, 1.0D, 20.0D);

        PROGRESS_ATTACK = BUILDER.comment("修炼动作 - 造成伤害：每点伤害累积的进度")
                .defineInRange("progressAttack", 1.0D, 0.0D, 1000.0D);
        PROGRESS_MINE = BUILDER.comment("修炼动作 - 挖掘方块：每次累积的进度")
                .defineInRange("progressMine", 1.0D, 0.0D, 1000.0D);
        PROGRESS_SHOOT = BUILDER.comment("修炼动作 - 弓箭命中：每次累积的进度")
                .defineInRange("progressShoot", 6.0D, 0.0D, 1000.0D);
        PROGRESS_GUARD = BUILDER.comment("修炼动作 - 承受伤害：每次累积的进度")
                .defineInRange("progressGuard", 2.0D, 0.0D, 1000.0D);
        PROGRESS_USE = BUILDER.comment("修炼动作 - 使用道具 / 交互：每次累积的进度")
                .defineInRange("progressUse", 4.0D, 0.0D, 1000.0D);

        BREAKTHROUGH_CONSUMES_SPIRIT = BUILDER.comment("大境界突破完成后是否清空全部灵力。" +
                        "0.2.14 起默认 false：突破不消耗灵力，灵力改由「凝聚武灵」消耗。")
                .define("breakthroughConsumesSpirit", false);

        CONDENSE_SPIRIT_COST = BUILDER.comment("凝聚一次实体武灵消耗的灵力（百分制，100 = 灵力上限）。" +
                        "灵力不足则无法凝聚；设为 0 表示免费。")
                .defineInRange("condenseSpiritCost", 25.0D, 0.0D, 1000.0D);

        MANIFEST_ATTACK_BONUS = BUILDER.comment("凝聚出的武灵比原版同款多出的攻击力，" +
                        "按原版该物品的攻击力百分比计算（100 = 追加 100%，0 = 与原版一致）。" +
                        "只对剑 / 斧 / 镐 / 铲这类有攻击力的近战装备生效。" +
                        "实际追加量还会再乘一个「境界倍数-1」（木3 石4 黄金8 玄铁9 " +
                        "钻石11 下界合金14 绿宝石15，见 WuLingRealm#manifestMultiplier），" +
                        "所以配置 100 时凝聚物的最终数值 = 原版同款 × 境界倍数：" +
                        "每一档都比上一档强 1.43 倍以上，绿宝石剑约 240。")
                .defineInRange("manifestAttackBonus", 100.0D, 0.0D, 1000.0D);

        MANIFEST_ARMOR_BONUS = BUILDER.comment("凝聚出的武灵比原版同款多出的护甲值，" +
                        "按原版该物品的护甲值百分比计算（100 = 护甲翻倍）。只对盔甲武灵生效。" +
                        "同样会再乘 (境界倍数-1)（见 manifestAttackBonus 的说明）。")
                .defineInRange("manifestArmorBonus", 100.0D, 0.0D, 1000.0D);

        MANIFEST_STAGE_STEP = BUILDER.comment("每提升一个小境界（前期 → 中期 → 后期），" +
                        "凝聚物基础属性额外增加的倍率。2026-10-02 用户口径：「每升一等级基础属性都会提升」。",
                        "最终数值 = 原版同款 × 大境界倍数 × (1 + 本值 × 小境界序号)，" +
                        "小境界序号：前期 0 / 中期 1 / 后期 2。默认 0.1 = 每小境界 +10%。",
                        "例：木档剑 4 → 12（前期）→ 13.2（中期）→ 14.4（后期），" +
                        "大境界之间的断层照旧。设为 0 则小境界不影响属性。")
                .defineInRange("manifestStageStep", 0.1D, 0.0D, 5.0D);

        BOOK_COST_PER_LEVEL = BUILDER.comment("自选附魔书：每高 1 级，消耗在 1 级的基础上再增加的比例。" +
                        "默认 0.5 = 2 级 +50%、3 级 +100%、5 级 +200%（再加上稀有度倍率）。",
                        "2026-10-02 用户口径：「附魔书凝聚等级越高，附魔书越稀有消耗越大」。",
                        "设为 0 则等级不影响消耗（回到旧行为）。")
                .defineInRange("bookCostPerLevel", 0.5D, 0.0D, 100.0D);

        BOOK_RARITY_COMMON = BUILDER.comment("附魔书消耗：普通（Common）附魔的倍率")
                .defineInRange("bookRarityCommon", 1.0D, 0.0D, 100.0D);
        BOOK_RARITY_UNCOMMON = BUILDER.comment("附魔书消耗：少见（Uncommon）附魔的倍率")
                .defineInRange("bookRarityUncommon", 1.5D, 0.0D, 100.0D);
        BOOK_RARITY_RARE = BUILDER.comment("附魔书消耗：稀有（Rare）附魔的倍率，如经验修补、冰霜行者")
                .defineInRange("bookRarityRare", 2.5D, 0.0D, 100.0D);
        BOOK_RARITY_VERY_RARE = BUILDER.comment("附魔书消耗：极稀有（Very Rare）附魔的倍率，如无限、灵魂疾行")
                .defineInRange("bookRarityVeryRare", 4.0D, 0.0D, 100.0D);

        // 大境界突破所需物资。格式： "物品注册名;数量"，
        // 特殊项 "BEADS_TOTAL;数量"  = 任意灵珠共 N 个（不限来源、不限品质）；
        // 特殊项 "ALL_BEADS;数量"    = 除排除名单外，每种怪物（不限品质）的灵珠各若干个；
        // 特殊项 "ALL_JI_BEADS;数量" = 同上但只收极品灵珠（旧口径，保留兼容）。
        BREAKTHROUGH_EXCLUDED_MOBS = BUILDER.comment(
                        "「ALL_BEADS / ALL_JI_BEADS（每种怪物各 N 个）」类突破条件里额外排除的怪物 ID。" +
                        "这些怪物照样掉灵珠，只是不参与这几档突破的收集要求。" +
                        "注意：绿宝石档用的是 BEADS_TOTAL（任意灵珠 100 颗），本名单对它不起作用。" +
                        "2026-09-26 用户点名：巨人、疣猪兽、幻术师、远古守卫者、流浪者、监守者。")
                .defineList("breakthroughExcludedMobs", Arrays.asList(
                        "minecraft:giant",
                        "minecraft:hoglin",
                        "minecraft:illusioner",
                        "minecraft:elder_guardian",
                        "minecraft:stray",
                        "minecraft:warden"
                ), o -> o instanceof String);

        REQ_WOOD_TO_STONE = requirement("wood_to_stone",
                "木 → 石", Arrays.asList("minecraft:oak_log;64"));
        REQ_STONE_TO_GOLD = requirement("stone_to_gold",
                "石 → 黄金", Arrays.asList("minecraft:cobblestone;512", "minecraft:coal;64"));
        REQ_GOLD_TO_METEOR = requirement("gold_to_meteor",
                "黄金 → 玄铁", Arrays.asList("minecraft:gold_ingot;128", "minecraft:iron_ingot;256"));
        REQ_METEOR_TO_DIAMOND = requirement("meteor_to_diamond",
                "玄铁 → 钻石", Arrays.asList("minecraft:iron_block;128", "minecraft:obsidian;256"));
        REQ_DIAMOND_TO_NETHERITE = requirement("diamond_to_netherite",
                "钻石 → 下界合金（原文：约 18 组金锭 + 约 18 组远古残骸）",
                Arrays.asList("minecraft:gold_ingot;1152", "minecraft:ancient_debris;1152"));
        // ⚠️ 键名两度更换（netherite_to_emerald → emerald_beads_any_quality → emerald_beads_total）：
        // Forge 的配置一旦生成就固定在那里，改代码默认值对它无效。
        // 0.2.13 把默认值从 ALL_JI_BEADS 改成 ALL_BEADS 时没换键名，老存档照旧只收极品；
        // 2026-10-02 把要求从「每种怪物各 10 个（共 270 颗）」改成「任意灵珠 100 颗」，
        // 同样必须换键名，新默认值才会真正生效。
        REQ_NETHERITE_TO_EMERALD = requirement("emerald_beads_total",
                "下界合金 → 绿宝石（2026-10-02 修订：任意灵珠 100 颗，不限来源与品质 + 10 个绿宝石块）",
                Arrays.asList("BEADS_TOTAL;100", "minecraft:emerald_block;10"));

        BUILDER.pop();

        // ===================== 腐肉武灵（召唤类，2026-10-02） =====================

        BUILDER.push("rottenFlesh");

        MYTHIC_WEIGHT = BUILDER.comment(
                        "传说档（腐肉武灵）在牧师抽取池里的权重。",
                        "其余 10 类权重合计 55（5 稀有 ×1 + 5 常见 ×10），",
                        "所以 0.00055 时实际概率 ≈ 0.00055 / 55.00055 ≈ 1/100000。",
                        "想要更容易抽到就把这个值调大（概率 = 本值 / (55 + 本值)）。")
                .defineInRange("mythicWeight", 0.00055D, 0.0D, 1000.0D);

        ROTTEN_FLESH_ZOMBIES_PER_REALM = BUILDER.comment(
                        "腐肉武灵每个大境界能召唤的僵尸只数。",
                        "木 5 · 石 10 · 黄金 15 · 玄铁 20 · 钻石 25 · 下界合金 30 · 绿宝石 35。")
                .defineInRange("zombiesPerRealm", 5, 1, 100);

        ROTTEN_FLESH_KILL_PROGRESS = BUILDER.comment(
                        "腐肉武灵击杀一只僵尸得到的修为进度。",
                        "注意它会再乘一次灵珠品质的修炼速度倍率（见 cultivationBonus*）。")
                .defineInRange("killProgress", 8.0D, 0.0D, 100000.0D);

        ROTTEN_FLESH_KILL_BASE = BUILDER.comment(
                        "腐肉武灵第一次大境界突破（木→石）所需击杀的僵尸数。")
                .defineInRange("killBase", 20, 1, 1000000);

        ROTTEN_FLESH_KILL_GROWTH = BUILDER.comment(
                        "腐肉武灵每升一个大境界，突破所需击杀数的增长倍率。",
                        "默认 2.5 → 六档依次为 20 / 50 / 125 / 312 / 781 / 1953 只。")
                .defineInRange("killGrowth", 2.5D, 1.0D, 100.0D);

        ROTTEN_FLESH_FLY_REALM = BUILDER.comment(
                        "召唤出的僵尸从哪个大境界起可以飞行（0木 1石 2黄金 3玄铁 4钻石 5下界合金 6绿宝石）。",
                        "用户设定：钻石境界起。")
                .defineInRange("flyFromRealm", 4, 0, 6);

        BUILDER.pop();

        // ===================== 怪物等级（武灵属性） =====================

        BUILDER.push("monsterTier");

        MONSTER_TIER_CHANCE = BUILDER.comment(
                        "怪物带「武灵等级（属性）」的概率。0 = 关闭整个系统，1 = 每只怪都有等级。" +
                        "带等级的怪物会手持该等级的剑（木剑 → 石剑 → 金剑 → 铁剑 → 钻石剑 → " +
                        "下界合金剑 → 绿宝石剑），近战伤害随之提高，最大生命值也会按下面的配置放大。")
                .defineInRange("tierChance", 0.10D, 0.0D, 1.0D);

        MONSTER_TIER_RARITY = BUILDER.comment(
                        "「等级越高越非常稀有」的衰减比：第 n 阶的权重 = 1 / 本值的 n 次方。" +
                        "默认 2.5 时，在「有等级的怪」里木阶约 60%、石阶约 24%、黄金约 9.6%、" +
                        "玄铁约 3.8%、钻石约 1.5%、下界合金约 0.6%、绿宝石约 0.25%；" +
                        "再乘 tierChance（默认 0.1），绿宝石阶约每 4000 只怪出一只。" +
                        "调大 = 高阶更罕见，调成 1 = 七个等级等概率。")
                .defineInRange("tierRarityRatio", 2.5D, 1.0D, 20.0D);

        MONSTER_TIER_HEALTH_BONUS = BUILDER.comment(
                        "每高一个大境界，怪物最大生命值的追加比例：倍率 = 1 + 大境界序号 × 本值。" +
                        "默认 0.5 → 木阶 1.0 倍（不变）、石阶 1.5 倍、黄金 2 倍、玄铁 2.5 倍、" +
                        "钻石 3 倍、下界合金 3.5 倍、绿宝石 4 倍。设为 0 = 只换武器不加血。" +
                        "（灵珠品质按来源怪最大生命值推算，所以高阶怪的灵珠天然更好。）")
                .defineInRange("tierHealthBonusPerRealm", 0.5D, 0.0D, 100.0D);

        MONSTER_TIER_EXCLUDED_MOBS = BUILDER.comment(
                        "不参与怪物等级系统的生物 ID。默认排除两类：" +
                        "① 不近战的（苦力怕、史莱姆、岩浆怪、恶魂、烈焰人、女巫、潜影贝）——" +
                        "给它们剑也不会用；" +
                        "② 不渲染手持物或另有攻击方式的（幻翼、守卫者、远古守卫者、蠹虫、末影螨、" +
                        "凋灵、末影龙）。把它们删掉就会一起参与。")
                .defineList("tierExcludedMobs", Arrays.asList(
                        "minecraft:creeper",
                        "minecraft:slime",
                        "minecraft:magma_cube",
                        "minecraft:ghast",
                        "minecraft:blaze",
                        "minecraft:witch",
                        "minecraft:shulker",
                        "minecraft:phantom",
                        "minecraft:guardian",
                        "minecraft:elder_guardian",
                        "minecraft:silverfish",
                        "minecraft:endermite",
                        "minecraft:wither",
                        "minecraft:ender_dragon"
                ), o -> o instanceof String);

        // ===================== 红石装备（充能类，2026-10-02） =====================
        BUILDER.push("redstone");

        REDSTONE_MAX_CHARGE = BUILDER.comment("红石装备的充能上限（点）。",
                        "换算锚点：一颗末影龙极品灵珠的回复量是 25 × 4 = 100%，",
                        "按下面的 chargePerPercent=10 算，刚好把默认的 1000 点充满；",
                        "一颗僵尸凡品灵珠是 5% → 50 点。")
                .defineInRange("maxCharge", 1000, 1, 1000000);

        REDSTONE_CHARGE_PER_PERCENT = BUILDER.comment("灵珠 → 充能换算系数：",
                        "灵珠的回复量（%，见 BeadPower 的怪物基准 × 品质倍率）× 本值 = 充能点数。",
                        "默认 10，即「这颗珠子能回多少百分比灵力，就充 10 倍点数的装备充能」。")
                .defineInRange("chargePerPercent", 10, 1, 1000);

        REDSTONE_COST_ATTACK = BUILDER.comment("手持红石武器命中一次目标消耗的充能（点）")
                .defineInRange("costAttack", 10, 0, 1000000);
        REDSTONE_COST_MINE = BUILDER.comment("手持红石工具挖掉一个方块消耗的充能（点）")
                .defineInRange("costMine", 5, 0, 1000000);
        REDSTONE_COST_HURT = BUILDER.comment("穿着红石护甲被击中一次消耗的充能（点）")
                .defineInRange("costHurt", 10, 0, 1000000);
        REDSTONE_COST_FLY = BUILDER.comment("穿着红石靴子飞行时【每秒】消耗的充能（点）",
                        "默认 20 —— 也就是飞行 50 秒耗光默认的 1000 点。")
                .defineInRange("costFly", 20, 0, 1000000);

        REDSTONE_EMPTY_ATTACK_PENALTY = BUILDER.comment("充能耗尽时的攻击力惩罚（按当前攻击力的比例扣）。",
                        "默认 0.6 = 攻击力只剩 40%；0 = 不惩罚。不用药水效果，走属性修饰符。")
                .defineInRange("emptyAttackPenalty", 0.6D, 0.0D, 1.0D);
        REDSTONE_EMPTY_SPEED_PENALTY = BUILDER.comment("充能耗尽时的移动速度惩罚。默认 0.3 = 移速只剩 70%。")
                .defineInRange("emptySpeedPenalty", 0.3D, 0.0D, 1.0D);

        BUILDER.pop();

        // ===================== 建筑系统（悬空坠落，2026-10-02） =====================
        BUILDER.push("building");

        BUILDING_ENABLED = BUILDER.comment("是否启用建筑系统：",
                        "玩家放下的方块如果正下方没有支撑，延迟一段时间后像沙子一样坠落。",
                        "自然生成的地形 / 建筑不受影响（只有玩家放置的方块会进系统台账）。")
                .define("enabled", true);

        BUILDING_DELAY_TICKS = BUILDER.comment("悬空方块开始坠落前的延迟（tick），20 tick = 1 秒。",
                        "默认 40 = 2 秒。这段倒计时内，用粘液球右键那一格可以把它固定住。")
                .defineInRange("delayTicks", 40, 0, 12000);

        BUILDING_SLIME_CONSUMED = BUILDER.comment("用粘液球固定方块时是否消耗一颗（创造模式始终不消耗）")
                .define("consumeSlimeBall", true);

        BUILDING_CREATIVE_IMMUNE = BUILDER.comment("创造 / 旁观玩家放置的方块是否免于坠落。",
                        "默认 true —— 免得盖东西时被自己的规则烦到；想在创造里测试就关掉它。")
                .define("creativeImmune", true);

        BUILDING_SHOW_PARTICLES = BUILDER.comment("倒计时中的方块是否冒粒子提示（每 10 tick 一个）")
                .define("showParticles", true);

        BUILDING_EXCLUDED = BUILDER.comment("额外排除的方块 ID —— 除了代码里那份「依附型方块」硬名单之外，",
                        "这些方块玩家放下后也不会坠落。每项格式：\"minecraft:stone\"")
                .defineList("excludedBlocks", List.of(), o -> o instanceof String);

        BUILDER.pop();

        BUILDER.pop();
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends String>> requirement(
            String key, String displayName, List<String> defaults) {
        return BUILDER.comment("大境界突破物资 · " + displayName + "。每项格式：\"物品注册名;数量\"")
                .defineList(key, defaults, o -> o instanceof String);
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // ===================== 便捷读取 =====================

    /** 灵力上限 */
    public static float maxSpirit() {
        return (float) (double) MAX_SPIRIT.get();
    }

    /**
     * 某个品质灵珠的回复倍率。
     *
     * 实际回复量还要乘上怪物基准，见 {@code item/BeadPower#restore}。
     */
    public static float restoreMultiplier(SpiritQuality quality) {
        switch (quality) {
            case LIANG:
                return (float) (double) RESTORE_MULT_LIANG.get();
            case YOU:
                return (float) (double) RESTORE_MULT_YOU.get();
            case SHANG:
                return (float) (double) RESTORE_MULT_SHANG.get();
            case JI:
                return (float) (double) RESTORE_MULT_JI.get();
            case FAN:
            default:
                return (float) (double) RESTORE_MULT_FAN.get();
        }
    }

    /** 某个品质灵珠的基础权重 */
    public static double baseWeight(SpiritQuality quality) {
        switch (quality) {
            case LIANG:
                return WEIGHT_LIANG.get();
            case YOU:
                return WEIGHT_YOU.get();
            case SHANG:
                return WEIGHT_SHANG.get();
            case JI:
                return WEIGHT_JI.get();
            case FAN:
            default:
                return WEIGHT_FAN.get();
        }
    }

    /** 所有武灵共用的最高境界序号（与灵珠品质无关） */
    public static int maxRealm() {
        return MAX_REALM.get();
    }

    /**
     * 某品质灵珠开启的武灵的<b>修炼速度加成</b>。
     *
     * 2026-09-27 起品质不再决定境界上限，只决定「攒进度快多少倍」：
     * 最终进度 = 动作基准（progressAttack / progressMine / …）× 本倍率。
     */
    public static double cultivationBonus(SpiritQuality quality) {
        if (quality == null) {
            return 1.0D;
        }
        switch (quality) {
            case LIANG: return CULTIVATION_BONUS_LIANG.get();
            case YOU: return CULTIVATION_BONUS_YOU.get();
            case SHANG: return CULTIVATION_BONUS_SHANG.get();
            case JI: return CULTIVATION_BONUS_JI.get();
            case FAN:
            default: return CULTIVATION_BONUS_FAN.get();
        }
    }

    /** 第 realmOrdinal 个大境界内，晋升一个小境界所需的累积进度 */
    public static double stageThreshold(int realmOrdinal) {
        return THRESHOLD_BASE.get() * Math.pow(THRESHOLD_GROWTH.get(), Math.max(0, realmOrdinal));
    }

    /** 六段突破的中文名，只用于启动日志 */
    private static final String[] REALM_STEP_NAMES = {
            "?", "木→石", "石→黄金", "黄金→玄铁", "玄铁→钻石", "钻石→下界合金", "下界合金→绿宝石"
    };

    /**
     * 把「当前真正生效的」突破物资打进日志。
     *
     * <p>存在的意义：Forge 的配置文件一旦生成就固定在那里，<b>代码改默认值对它无效</b>，
     * 这个坑已经踩过好几次（灵力门槛、capFan…、绿宝石只要极品）。
     * 启动时打一行日志，下次就能一眼看出「配置里的生效值」与「代码里写的」是否一致，
     * 不必进游戏一间间试。
     */
    public static void logEffectiveRequirements() {
        for (int realm = 1; realm <= 6; realm++) {
            WulingEmpire.LOGGER.info("[武灵帝国] 突破物资生效值 · {} = {}（BEADS_TOTAL = 任意灵珠共 N 个，"
                    + "ALL_BEADS = 每种怪物各 N 个且不限品质，ALL_JI_BEADS = 只要极品）",
                    REALM_STEP_NAMES[realm], breakthroughRequirement(realm));
            WulingEmpire.LOGGER.info("[武灵帝国] 腐肉武灵突破生效值 · {} = 击杀僵尸 {} 只",
                    REALM_STEP_NAMES[realm], zombieKillsFor(realm));
        }
    }

    /** 突破到第 realmIndex 个大境界所需的物资清单原始字符串 */
    public static List<? extends String> breakthroughRequirement(int realmOrdinal) {
        switch (realmOrdinal) {
            case 1: return REQ_WOOD_TO_STONE.get();
            case 2: return REQ_STONE_TO_GOLD.get();
            case 3: return REQ_GOLD_TO_METEOR.get();
            case 4: return REQ_METEOR_TO_DIAMOND.get();
            case 5: return REQ_DIAMOND_TO_NETHERITE.get();
            case 6: return REQ_NETHERITE_TO_EMERALD.get();
            default: return java.util.Collections.emptyList();
        }
    }

    /**
     * 腐肉武灵突破所需的僵尸击杀数（第 realmOrdinal 个大境界，从 1 起）。
     *
     * <p>= {@code killBase × killGrowth^(realmOrdinal - 1)}，默认 20 / 50 / 125 / 312 / 781 / 1953。
     * 走击杀数而不是物资，是用户 2026-10-02 的设定
     * 「升级不消耗材料，只消耗击杀僵尸的量」。
     */
    public static int zombieKillsFor(int realmOrdinal) {
        if (realmOrdinal <= 0) {
            return 0;
        }
        double value = ROTTEN_FLESH_KILL_BASE.get()
                * Math.pow(ROTTEN_FLESH_KILL_GROWTH.get(), realmOrdinal - 1);
        // 向下取整：默认 20 / 50 / 125 / 312 / 781 / 1953（沿用注释与文档口径）。
        // 用 ceil 会变成 313 / 782 / 1954，每档白多杀一只。
        return (int) Math.floor(value + 1.0E-6D);
    }
}
