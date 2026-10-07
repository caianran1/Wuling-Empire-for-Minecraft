package com.wuling.empire.wuling;

import com.wuling.empire.Config;
import com.wuling.empire.item.BeadModels;
import com.wuling.empire.item.ModItems;
import com.wuling.empire.item.SpiritBeadItem;
import com.wuling.empire.item.SpiritQuality;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大境界突破所需物资的解析、缴纳与校验。
 *
 * 配置格式："物品注册名;数量"。另有三个特殊键代表灵珠需求：
 * <ul>
 *   <li>{@code ALL_JI_BEADS} —— 只要<b>极品</b>灵珠（旧口径，下界合金档现已不用，代码保留兼容）</li>
 *   <li>{@code ALL_BEADS} —— <b>不限品质</b>，凡品到极品都算数，但要求<b>每种怪物各 N 个</b></li>
 *   <li>{@code BEADS_TOTAL} —— <b>不限来源、不限品质，凑够 N 颗就行</b>
 *       （2026-10-02 起绿宝石档改用这个：原来「每种各 10 个」共 270 颗太难，
 *       改成任意灵珠 100 颗）</li>
 * </ul>
 * 前两种特殊键覆盖的怪物集合见 {@link #allRequiredBeadSources()}：
 * 注册表里的敌对生物 + 本模组出图怪物，再减去
 * {@link Config#NEVER_DROPS}、{@link Config#EXCLUDED_MOBS} 与
 * {@link Config#BREAKTHROUGH_EXCLUDED_MOBS}（巨人 / 疣猪兽 / 幻术师 / 远古守卫者 / 流浪者 / 监守者）。
 *
 * <b>分批缴纳</b>：后期突破动辄十几组物资，背包根本放不下，
 * 所以物资分两个去处 ——
 *   1. 背包（未缴纳）
 *   2. 玩家的「缴纳池」（{@link WuLingData} 里的 Submit 标签），可分批缴入，攒齐再突破
 * 面板上的「提交物资」＝把背包里能缴的都缴进池子；「突破」＝先把背包剩余的一并缴入，再判定池子是否齐全。
 *
 * <b>「不限品质」的记账方式</b>：池子里的键带品质段（{@code bead|<来源>|<品质>}），
 * 所以缴进去的是什么品质，退还时就还是什么品质，不会出现「凡品缴进去、极品退回来」的白嫖。
 * 旧存档里没有品质段的键（{@code bead|<来源>}）按极品处理。
 */
public final class BreakthroughRequirement {

    private BreakthroughRequirement() {
    }

    /** 只要极品灵珠 */
    public static final String ALL_JI_BEADS = "ALL_JI_BEADS";
    /** 不限品质的灵珠（凡品 ~ 极品都算），但每种怪物各 N 个 */
    public static final String ALL_BEADS = "ALL_BEADS";
    /** 任意灵珠共 N 个（不限来源、不限品质） */
    public static final String BEADS_TOTAL = "BEADS_TOTAL";

    /** 缴纳池键前缀：普通物品 */
    private static final String PREFIX_ITEM = "item|";
    /** 缴纳池键前缀：某来源的灵珠 */
    private static final String PREFIX_BEAD = "bead|";
    /** 缴纳池键里的字段分隔符：bead|<来源>|<品质> */
    private static final char KEY_SEP = '|';

    /** 一条物资需求 */
    public record Entry(String id, int count) {
    }

    /**
     * 面板上的一行展示数据。
     *
     * @param have      背包 + 池子，已凑到的数量（上限为 need）
     * @param deposited 其中已缴进池子的数量
     */
    public record Row(Component name, int need, int have, int deposited, boolean ok) {
    }

    /** 缴纳池里的一笔灵珠：来源 / 品质 / 数量 */
    private record PoolBead(String source, SpiritQuality quality, int count) {
    }

    public static String itemKey(String id) {
        return PREFIX_ITEM + id;
    }

    /** 某来源、某品质的灵珠在缴纳池里的键 */
    public static String beadKey(String source, SpiritQuality quality) {
        return PREFIX_BEAD + source + KEY_SEP + quality.key();
    }

    /** 兼容旧口径：不带品质就等于极品 */
    public static String beadKey(String source) {
        return beadKey(source, SpiritQuality.JI);
    }

    /** 解析配置原始字符串 */
    public static List<Entry> parse(List<? extends String> raw) {
        List<Entry> entries = new ArrayList<>();
        if (raw == null) {
            return entries;
        }
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            String[] parts = line.split(";");
            if (parts.length < 2) {
                continue;
            }
            int count;
            try {
                count = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (count <= 0) {
                continue;
            }
            entries.add(new Entry(parts[0].trim(), count));
        }
        return entries;
    }

    /** 是否是「每种怪物灵珠各 N 个」这类条目 */
    private static boolean isAllBeadsEntry(String id) {
        return ALL_JI_BEADS.equals(id) || ALL_BEADS.equals(id);
    }

    /** 是否是「任意灵珠共 N 个」这类条目 */
    private static boolean isTotalBeadsEntry(String id) {
        return BEADS_TOTAL.equals(id);
    }

    /** 该条目的品质限定；返回 null 表示不限品质 */
    private static SpiritQuality qualityLimit(String id) {
        return ALL_JI_BEADS.equals(id) ? SpiritQuality.JI : null;
    }

    // ===================== 灵珠全收集 =====================

    /**
     * 突破要求覆盖到的全部怪物来源 ID。
     *
     * 取的是「会掉灵珠的怪物」这一集合：
     *   ① 注册表里全部 MONSTER 分类实体（模组怪物自动纳入）；
     *   ② 本模组有专属灵珠图的怪物（疣猪兽是 CREATURE 分类却照样掉灵珠，靠这张表补进来）。
     * 再减去三份排除名单。
     */
    public static Set<String> allRequiredBeadSources() {
        Set<String> sources = new LinkedHashSet<>();

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key != null && type.getCategory() == MobCategory.MONSTER) {
                sources.add(key.toString());
            }
        }

        for (String source : BeadModels.SOURCES) {
            if (!BeadModels.RETIRED.contains(source)) {
                sources.add(source);
            }
        }

        List<? extends String> extra = Config.BREAKTHROUGH_EXCLUDED_MOBS.get();
        List<? extends String> configured = Config.EXCLUDED_MOBS.get();
        sources.removeIf(id -> Config.NEVER_DROPS.contains(id)
                || configured.contains(id)
                || extra.contains(id));
        return sources;
    }

    // ===================== 面板展示 =====================

    /**
     * 计算玩家达成情况，用于面板渲染。
     *
     * @param pool 已缴纳池（服务端取 {@link WuLingData#submittedSnapshot()}，
     *             客户端取同步下来的缓存），允许为 null
     */
    public static List<Row> evaluate(Player player, int targetRealmOrdinal, Map<String, Integer> pool) {
        List<Row> rows = new ArrayList<>();
        Inventory inventory = player.getInventory();
        Map<String, Integer> deposited = pool == null ? Map.of() : pool;

        for (Entry entry : parse(Config.breakthroughRequirement(targetRealmOrdinal))) {
            if (isTotalBeadsEntry(entry.id())) {
                int need = entry.count();
                int inPool = Math.min(need, pooledBeadsTotal(deposited, need));
                int inBag = countBeadsAny(inventory, need - inPool);
                int have = Math.min(need, inPool + inBag);
                rows.add(new Row(Component.translatable("wuling.require.beads_total", need),
                        need, have, inPool, have >= need));
            } else if (isAllBeadsEntry(entry.id())) {
                SpiritQuality only = qualityLimit(entry.id());
                int needTotal = 0;
                int haveTotal = 0;
                int depositedTotal = 0;
                int kinds = 0;
                for (String source : allRequiredBeadSources()) {
                    kinds++;
                    int need = entry.count();
                    int inPool = Math.min(need, pooledBeads(deposited, source, only, need));
                    int inBag = countBeads(inventory, source, only, need);
                    int have = Math.min(need, inPool + inBag);
                    needTotal += need;
                    haveTotal += have;
                    depositedTotal += inPool;
                }
                rows.add(new Row(Component.translatable(
                                only == null ? "wuling.require.all_beads" : "wuling.require.all_ji_beads",
                                kinds, entry.count()),
                        needTotal, haveTotal, depositedTotal, haveTotal >= needTotal));
            } else {
                int need = entry.count();
                int inPool = Math.min(need, deposited.getOrDefault(itemKey(entry.id()), 0));
                Item item = resolveItem(entry.id());
                int inBag = countItem(inventory, item, need);
                int have = Math.min(need, inPool + inBag);
                Component name = item == null
                        ? Component.literal(entry.id())
                        : new ItemStack(item).getHoverName();
                rows.add(new Row(name, need, have, inPool, have >= need));
            }
        }
        return rows;
    }

    // ===================== 缴纳 =====================

    public record Result(boolean success, Component message) {
    }

    /**
     * 把背包里「当前这档突破还缺的」物资缴进缴纳池。
     *
     * 只缴到需求上限为止，不会多吞；背包放不下的部分可以反复调用（分多批缴入）。
     */
    public static Result submitPartial(ServerPlayer player, int targetRealmOrdinal, WuLingData data) {
        // 目标境界变了（已突破 / 配置改动）→ 旧池子作废，先退还给玩家再重新开始
        if (data.submitTarget() != targetRealmOrdinal) {
            refundAll(player, data);
            data.setSubmitTarget(targetRealmOrdinal);
        }

        Inventory inventory = player.getInventory();
        int moved = 0;

        for (Entry entry : parse(Config.breakthroughRequirement(targetRealmOrdinal))) {
            if (isTotalBeadsEntry(entry.id())) {
                int remaining = entry.count() - pooledBeadsTotal(data.submittedSnapshot(), entry.count());
                if (remaining > 0) {
                    // 任意灵珠：抓到哪种算哪种，分「来源 + 品质」入账，退还时原样奉还
                    for (Map.Entry<String, Integer> taken : takeBeadsAny(inventory, remaining).entrySet()) {
                        data.addSubmitted(taken.getKey(), taken.getValue());
                        moved += taken.getValue();
                    }
                }
            } else if (isAllBeadsEntry(entry.id())) {
                SpiritQuality only = qualityLimit(entry.id());
                Map<String, Integer> pool = data.submittedSnapshot();
                for (String source : allRequiredBeadSources()) {
                    int remaining = entry.count() - pooledBeads(pool, source, only, entry.count());
                    if (remaining <= 0) {
                        continue;
                    }
                    // 不限品质时，一次可能取到多种品质，得分品质入账
                    Map<SpiritQuality, Integer> taken = takeBeads(inventory, source, only, remaining);
                    for (Map.Entry<SpiritQuality, Integer> takenEntry : taken.entrySet()) {
                        data.addSubmitted(beadKey(source, takenEntry.getKey()), takenEntry.getValue());
                        moved += takenEntry.getValue();
                    }
                }
            } else {
                String key = itemKey(entry.id());
                int remaining = entry.count() - data.submittedAmount(key);
                if (remaining <= 0) {
                    continue;
                }
                int taken = takeItem(inventory, resolveItem(entry.id()), remaining);
                if (taken > 0) {
                    data.addSubmitted(key, taken);
                    moved += taken;
                }
            }
        }

        if (moved <= 0) {
            return new Result(false, Component.translatable("message.wulingdiguo.submit_none"));
        }
        return new Result(true, Component.translatable("message.wulingdiguo.submit_ok", moved));
    }

    /** 缴纳池是否已满足全部需求 */
    public static boolean isPoolComplete(WuLingData data, int targetRealmOrdinal) {
        Map<String, Integer> pool = data.submittedSnapshot();
        for (Entry entry : parse(Config.breakthroughRequirement(targetRealmOrdinal))) {
            if (isTotalBeadsEntry(entry.id())) {
                if (pooledBeadsTotal(pool, entry.count()) < entry.count()) {
                    return false;
                }
            } else if (isAllBeadsEntry(entry.id())) {
                SpiritQuality only = qualityLimit(entry.id());
                for (String source : allRequiredBeadSources()) {
                    if (pooledBeads(pool, source, only, entry.count()) < entry.count()) {
                        return false;
                    }
                }
            } else if (data.submittedAmount(itemKey(entry.id())) < entry.count()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 条件满足则扣除物资并推进境界；不满足则原样返回 false。
     *
     * 会先把玩家背包里剩余的物资自动缴进池子，因此「一次性带齐」时点一次突破即可，
     * 无需先手动提交。
     */
    public static Result tryConsumeAndAdvance(ServerPlayer player, int targetRealmOrdinal, WuLingData data) {
        submitPartial(player, targetRealmOrdinal, data);

        if (!isPoolComplete(data, targetRealmOrdinal)) {
            return new Result(false, Component.translatable(
                    "message.wulingdiguo.breakthrough_missing"));
        }

        // 池子已满 → 视为消耗掉，清空
        data.clearSubmitted();
        return new Result(true, Component.translatable("message.wulingdiguo.breakthrough_ok",
                Component.translatable(WuLingRealm.byOrdinal(targetRealmOrdinal).translationKey())));
    }

    // ===================== 退还 =====================

    /**
     * 把缴纳池里的东西全部退还到玩家背包（放不下的掉在脚下）。
     * 用于解除武灵绑定、或突破目标发生变化时，避免物资凭空消失。
     * 灵珠按当初缴进来的原品质退还。
     */
    public static void refundAll(Player player, WuLingData data) {
        if (!data.hasSubmitted()) {
            data.clearSubmitted();
            return;
        }
        // 灵珠（bead|...）：按当初缴进来的来源 + 品质原样奉还
        for (PoolBead bead : poolBeads(data.submittedSnapshot())) {
            ItemStack stack = new ItemStack(ModItems.BEADS.get(bead.quality()).get(), bead.count());
            SpiritBeadItem.setSource(stack, bead.source());
            give(player, stack);
        }
        // 普通物资（item|...）：池子里不只有灵珠，漏了这段等于把玩家的矿石铁块全吞了
        refundItems(player, data.submittedSnapshot());
        data.clearSubmitted();
    }

    /** 退还缴纳池里的普通物资（灵珠由 {@link #poolBeads} 单独走一遍） */
    private static void refundItems(Player player, Map<String, Integer> pool) {
        for (Map.Entry<String, Integer> entry : pool.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0 || !key.startsWith(PREFIX_ITEM)) {
                continue;
            }
            Item item = resolveItem(key.substring(PREFIX_ITEM.length()));
            if (item == null) {
                continue;
            }
            int per = Math.max(1, item.getMaxStackSize());
            while (count > 0) {
                int take = Math.min(per, count);
                give(player, new ItemStack(item, take));
                count -= take;
            }
        }
    }

    /** 尽量塞进背包主栏，塞不下的掉在脚下 */
    private static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack rest = stack.copy();

        // 先并入已有同类堆
        for (int i = 0; i < 36 && !rest.isEmpty(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, rest)) {
                continue;
            }
            int move = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
            if (move > 0) {
                slot.grow(move);
                rest.shrink(move);
            }
        }
        // 再找空格
        for (int i = 0; i < 36 && !rest.isEmpty(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                continue;
            }
            int move = Math.min(rest.getCount(), rest.getMaxStackSize());
            inventory.setItem(i, rest.copyWithCount(move));
            rest.shrink(move);
        }
        if (!rest.isEmpty()) {
            player.drop(rest, false);
        }
    }

    // ===================== 内部工具 =====================

    private static Item resolveItem(String id) {
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == Items.AIR ? null : item;
    }

    private static int countItem(Container container, Item item, int limit) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
                if (total >= limit) {
                    return total;
                }
            }
        }
        return total;
    }

    /** 从容器里取走至多 amount 个，返回实际取走的数量 */
    private static int takeItem(Container container, Item item, int amount) {
        if (item == null || amount <= 0) {
            return 0;
        }
        int taken = 0;
        for (int i = 0; i < container.getContainerSize() && taken < amount; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(amount - taken, stack.getCount());
            stack.shrink(take);
            taken += take;
        }
        return taken;
    }

    /** 把缴纳池里的键拆成一条条灵珠记录 */
    private static List<PoolBead> poolBeads(Map<String, Integer> pool) {
        List<PoolBead> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pool.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0 || !key.startsWith(PREFIX_BEAD)) {
                continue;
            }
            String rest = key.substring(PREFIX_BEAD.length());
            int sep = rest.lastIndexOf(KEY_SEP);
            // 旧格式 bead|<来源> 没有品质段，按极品处理
            String source = sep < 0 ? rest : rest.substring(0, sep);
            SpiritQuality quality = sep < 0
                    ? SpiritQuality.JI
                    : SpiritQuality.byKey(rest.substring(sep + 1));
            list.add(new PoolBead(source, quality, count));
        }
        return list;
    }

    /**
     * 某个来源在缴纳池里已有多少颗灵珠。
     *
     * @param only 只算这一种品质；null = 不限品质
     * @param limit 数到这么多就够，避免无谓的累加
     */
    private static int pooledBeads(Map<String, Integer> pool, String source, SpiritQuality only, int limit) {
        if (limit <= 0) {
            return 0;
        }
        int total = 0;
        for (PoolBead bead : poolBeads(pool)) {
            if (!bead.source().equals(source)) {
                continue;
            }
            if (only != null && bead.quality() != only) {
                continue;
            }
            total += bead.count();
            if (total >= limit) {
                return limit;
            }
        }
        return total;
    }

    /** 缴纳池里共有多少颗灵珠（{@code BEADS_TOTAL} 用：不限来源、不限品质） */
    private static int pooledBeadsTotal(Map<String, Integer> pool, int limit) {
        if (limit <= 0) {
            return 0;
        }
        int total = 0;
        for (PoolBead bead : poolBeads(pool)) {
            total += bead.count();
            if (total >= limit) {
                return limit;
            }
        }
        return total;
    }

    /** 统计某个来源的灵珠数量（最多数到 limit） */
    private static int countBeads(Container container, String source, SpiritQuality only, int limit) {
        if (limit <= 0) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!isBeadOf(stack, source, only)) {
                continue;
            }
            total += stack.getCount();
            if (total >= limit) {
                return total;
            }
        }
        return total;
    }

    /**
     * 取走至多 amount 颗某来源的灵珠，按品质分别记账返回。
     * （不限品质时，一次可能取到多种品质，必须分开记才能原样退还。）
     */
    private static Map<SpiritQuality, Integer> takeBeads(
            Container container, String source, SpiritQuality only, int amount) {
        Map<SpiritQuality, Integer> taken = new LinkedHashMap<>();
        if (amount <= 0) {
            return taken;
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize() && total < amount; i++) {
            ItemStack stack = container.getItem(i);
            if (!isBeadOf(stack, source, only)) {
                continue;
            }
            SpiritQuality quality = SpiritBeadItem.getQuality(stack);
            if (quality == null) {
                continue;
            }
            int take = Math.min(amount - total, stack.getCount());
            stack.shrink(take);
            total += take;
            taken.merge(quality, take, Integer::sum);
        }
        return taken;
    }

    /** 背包里任意灵珠的总数（最多数到 limit） */
    private static int countBeadsAny(Container container, int limit) {
        if (limit <= 0) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!(stack.getItem() instanceof SpiritBeadItem)) {
                continue;
            }
            total += stack.getCount();
            if (total >= limit) {
                return limit;
            }
        }
        return total;
    }

    /**
     * 取走至多 amount 颗任意灵珠（不限来源、不限品质），按缴纳池键分别记账返回。
     * 键里带上「来源 + 品质」，所以退还时不会串味。
     */
    private static Map<String, Integer> takeBeadsAny(Container container, int amount) {
        Map<String, Integer> taken = new LinkedHashMap<>();
        if (amount <= 0) {
            return taken;
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize() && total < amount; i++) {
            ItemStack stack = container.getItem(i);
            if (!(stack.getItem() instanceof SpiritBeadItem)) {
                continue;
            }
            String source = SpiritBeadItem.getSource(stack);
            SpiritQuality quality = SpiritBeadItem.getQuality(stack);
            if (source == null || quality == null) {
                continue;
            }
            int take = Math.min(amount - total, stack.getCount());
            stack.shrink(take);
            total += take;
            taken.merge(beadKey(source, quality), take, Integer::sum);
        }
        return taken;
    }

    private static boolean isBeadOf(ItemStack stack, String source, SpiritQuality only) {
        if (!(stack.getItem() instanceof SpiritBeadItem)) {
            return false;
        }
        if (only != null && SpiritBeadItem.getQuality(stack) != only) {
            return false;
        }
        return source.equals(SpiritBeadItem.getSource(stack));
    }

    /** 供面板显示的标题 */
    public static MutableComponent titleFor(int targetRealmOrdinal) {
        WuLingRealm target = WuLingRealm.byOrdinal(targetRealmOrdinal);
        return Component.translatable("wuling.panel.breakthrough_to",
                Component.translatable(target.translationKey()));
    }
}
