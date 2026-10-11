package com.wuling.empire.item;

import com.wuling.empire.Config;
import com.wuling.empire.capability.ISpiritPower;
import com.wuling.empire.capability.ModCapabilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 灵珠物品。
 *
 * 交互规则：Shift + 右键 = 吸收（消耗一颗灵珠，按「来源怪物 × 品质」回复灵力）。
 * 回复量见 {@link BeadPower}：每种怪物基准不同（僵尸凡品 5%，末影龙极品 100%），
 * 品质只是在此基础上乘倍率。
 * 未按 Shift 的右键不触发任何效果，避免误触。
 */
public class SpiritBeadItem extends Item {

    private final SpiritQuality quality;

    public SpiritBeadItem(SpiritQuality quality) {
        super(new Item.Properties().stacksTo(64));
        this.quality = quality;
    }

    public SpiritQuality getQuality() {
        return quality;
    }

    // ===================== 来源种类 =====================

    private static final String KEY_SOURCE = "Source";

    /**
     * 灵珠的来源生物（例如 minecraft:zombie）。
     *
     * 第一部原文只定义了品质；第四部的物价写的是「极品僵尸灵珠」「极品骷髅灵珠」，
     * 第二部又说「灵珠种类决定修炼方向」，可见灵珠除了品质还有「来源种类」这一维度。
     * 因此补记在物品 NBT 上。
     */
    public static String getSource(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return "";
        }
        return tag.getString(KEY_SOURCE);
    }

    public static void setSource(ItemStack stack, String sourceId) {
        if (sourceId == null || sourceId.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().putString(KEY_SOURCE, sourceId);
        applyModel(stack, sourceId);
    }

    /**
     * 按来源选专属图标。
     *
     * 一种怪物一张灵珠图（不再按品质分图），选图走物品 NBT 的
     * `CustomModelData`，值 = {@link BeadModels} 里的序号；
     * 没有专属图的来源（例如其它模组的怪物）不写这个键，模型就退回品质图。
     */
    public static void applyModel(ItemStack stack, String sourceId) {
        int index = BeadModels.modelIndex(sourceId);
        if (index > 0) {
            stack.getOrCreateTag().putInt("CustomModelData", index);
        }
    }

    public static SpiritQuality getQuality(ItemStack stack) {
        if (stack.getItem() instanceof SpiritBeadItem bead) {
            return bead.getQuality();
        }
        return null;
    }

    // ===================== 显示名 =====================

    /**
     * 物品名：「<怪物名>灵珠」，例如「僵尸灵珠」。
     *
     * 品质已经体现在图标颜色之外的信息上（图标按怪物分，不按品质分），
     * 所以品质挪到 tooltip 副栏里显示，见 {@link #appendHoverText}。
     * 没有来源的灵珠（创造物品栏里直接拿出来的）退化为「<品质>灵珠」。
     */
    @Override
    public Component getName(ItemStack stack) {
        String source = getSource(stack);
        if (source.isEmpty()) {
            return Component.translatable("item.wulingdiguo.spirit_bead",
                    Component.translatable(quality.translationKey()));
        }
        return Component.translatable("item.wulingdiguo.spirit_bead", sourceName(source));
    }

    /** 由来源生物 ID 取它的本地化名（走原版 entity.* 语言键） */
    public static Component sourceName(String source) {
        ResourceLocation id = ResourceLocation.tryParse(source);
        if (id == null) {
            return Component.literal(source);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return type == null ? Component.literal(source) : type.getDescription();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 必须按住 Shift 才会触发吸收
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(held);
        }

        // 客户端不做逻辑，交由服务端处理（服务端才是数据权威）
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(held);
        }

        ISpiritPower power = player.getCapability(ModCapabilities.SPIRIT_POWER).orElse(null);
        if (power == null) {
            return InteractionResultHolder.fail(held);
        }

        float max = Config.maxSpirit();
        if (power.getSpirit() >= max - 0.001F) {
            // 2026-10-01 用户口径：灵力满时吸收灵珠改为「小幅度增加修为」，
            // 不再直接拒绝（免得珠子白费）
            return absorbIntoProgress(level, player, held);
        }

        float restore = BeadPower.restore(getSource(held), quality);
        float before = power.getSpirit();
        power.addSpirit(restore);

        Component beadName = held.getHoverName();
        held.shrink(1);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, player.getSoundSource(), 0.6F, 1.0F);
        player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);

        player.sendSystemMessage(Component.translatable(
                "message.wulingdiguo.bead_absorb",
                beadName,
                String.format("%.1f", before),
                String.format("%.1f", power.getSpirit())));

        return InteractionResultHolder.success(held);
    }

    /**
     * 灵力已满时的吸收：不涨灵力，改成累积<b>少量修炼进度</b>。
     *
     * <p>2026-10-01 用户口径「在灵力满时吸收灵珠可小幅度增加修为」。
     * 进度量 = {@code Config.BEAD_FULL_PROGRESS} × 该灵珠品质的<b>吸珠倍率</b>
     * （{@link Config#beadAbsorbBonus}：凡 1 / 良 1.5 / 优 2 / 上 3.5 / <b>极 6</b>）。
     *
     * <p>2026-10-11 用户口径「吸收极品灵珠时给的修为可以多一点」：这条曲线做得比
     * 修炼速度倍率更陡，而且<b>不</b>再乘修炼速度倍率 —— 喂修为看的是珠子本身多稀有，
     * 与玩家自己用什么品质开启的武灵无关（走 {@link com.wuling.empire.wuling.WuLingData#addProgressRaw}
     * 而不是 {@code addProgress}）。
     *
     * <p>只有<b>已开启武灵</b>的玩家才换得到修为；没开武灵的人吸满灵力的珠子
     * 依然会被拒绝（免得白费珠子）。这条路对任何武灵种类都算数 ——
     * 不走 {@code IWuLing#addProgress} 的「动作匹配」判定，因为吸珠不分种类。
     */
    private InteractionResultHolder<ItemStack> absorbIntoProgress(Level level, Player player,
                                                                  ItemStack held) {
        var holder = player.getCapability(ModCapabilities.WU_LING).orElse(null);
        double gain = Config.BEAD_FULL_PROGRESS.get() * Config.beadAbsorbBonus(quality);
        if (holder == null || !holder.data().isBound() || gain <= 0.0D) {
            player.sendSystemMessage(Component.translatable("message.wulingdiguo.spirit_full"));
            return InteractionResultHolder.fail(held);
        }

        var data = holder.data();
        boolean promoted = data.addProgressRaw(gain);

        Component beadName = held.getHoverName();
        held.shrink(1);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, player.getSoundSource(), 0.6F, 1.0F);
        player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);

        player.sendSystemMessage(Component.translatable(
                "message.wulingdiguo.bead_full_cultivation",
                beadName, trim(gain)));

        if (promoted) {
            player.sendSystemMessage(Component.translatable("message.wulingdiguo.stage_up",
                    Component.translatable(data.realm().translationKey())
                            .append(" · ")
                            .append(Component.translatable(data.stage().translationKey()))));
            com.wuling.empire.wuling.WuLingBinding.refreshManifestItems(player);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.wuling.empire.network.ModMessages.sendWuLingTo(serverPlayer);
        }
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 品质：图标按怪物分，所以品质放在名字下面的副栏里
        tooltip.add(Component.translatable("tooltip.wulingdiguo.bead_quality",
                Component.translatable(quality.translationKey())
                        .withStyle(style -> style.withColor(quality.color()))));
        // 回复量按「来源怪物 × 品质」算，所以同品质不同怪的珠子数值不同
        tooltip.add(Component.translatable("tooltip.wulingdiguo.bead_restore",
                trim(BeadPower.restore(getSource(stack), quality))));
        // 2026-09-27 起品质只影响修炼速度，不影响能修到多高（上限所有人一致）
        tooltip.add(Component.translatable("tooltip.wulingdiguo.bead_cultivation",
                trim(Config.cultivationBonus(quality))));
        // 灵力已满时吸珠换到的修为（按珠子品质给，极品最划算）
        tooltip.add(Component.translatable("tooltip.wulingdiguo.bead_full_progress",
                trim(Config.BEAD_FULL_PROGRESS.get() * Config.beadAbsorbBonus(quality))));
        tooltip.add(Component.translatable("tooltip.wulingdiguo.bead_usage"));
        // 生存玩家开启武灵的唯一入口：请村民牧师抽取
        tooltip.add(Component.translatable("tooltip.wulingdiguo.bead_cleric"));
    }

    /** 3.0 → "3"，1.25 → "1.25"：修炼速度倍率的显示 */
    private static String trim(double value) {
        return Math.abs(value - Math.round(value)) < 0.005D
                ? String.valueOf(Math.round(value))
                : String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }

    /** 5.0 → "5"，7.5 → "7.5"：整数不带小数点 */
    private static String trim(float value) {
        return Math.abs(value - Math.round(value)) < 0.05F
                ? String.valueOf(Math.round(value))
                : String.format("%.1f", value);
    }
}
