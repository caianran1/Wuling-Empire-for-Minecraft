package com.wuling.empire.wuling;

import com.wuling.empire.Config;
import com.wuling.empire.capability.ISpiritPower;
import com.wuling.empire.capability.ModCapabilities;
import com.wuling.empire.entity.ModEntities;
import com.wuling.empire.entity.WuLingZombieEntity;
import com.wuling.empire.item.ManifestItems;
import com.wuling.empire.item.ModItems;
import com.wuling.empire.item.SpiritBeadItem;
import com.wuling.empire.item.SpiritQuality;
import com.wuling.empire.network.ModMessages;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 武灵的「开启」与「凝聚」。
 *
 * 原文：开启武灵需要「武灵绑定器」（仅限创造模式），消耗灵珠，
 *       灵珠种类决定修炼方向，品质决定境界上限。
 * <b>2026-09-27 用户修订：品质不再决定境界上限 —— 所有人的上限都一样，
 * 灵珠品质只决定修炼速度（见 {@link Config#cultivationBonus}）。</b>
 *
 * 两种动作：
 *   - {@link #bind}     ：选定种类并绑定。入口是绑定器的 Shift+右键 / 创造绑定器右键。
 *   - {@link #condense} ：把武灵凝聚成实物（Shift+M）。<b>不再需要绑定器</b>，改为消耗灵力。
 *
 * 2026-09-27 起多一条路：{@link #openByNpc} —— 生存玩家拿灵珠右键<b>村民牧师</b>，
 * 由牧师替他开启武灵，种类<b>抽取</b>而非自选（绑定器是创造模式的自选入口）。
 */
public final class WuLingBinding {

    private WuLingBinding() {
    }

    /**
     * 绑定武灵（选定种类）。
     *
     * @param typeKey  玩家选定的种类；为空或非法时回退到随机抽取
     * @param creative true=创造绑定器触发，强制覆盖且免费，并立即凝聚出实体
     */
    public static void bind(Player player, String typeKey, boolean creative) {
        player.getCapability(ModCapabilities.WU_LING).ifPresent(holder -> {
            if (holder.data().isBound() && !creative) {
                // 已经拥有武灵，普通绑定器不能重复绑定（用创造绑定器切换）
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.bind_already",
                        holder.data().displayName()));
                return;
            }
            if (Config.BINDING_REQUIRES_NPC.get()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.bind_need_npc"));
                return;
            }

            if (creative) {
                WuLingType type = typeKey == null || typeKey.isEmpty()
                        ? WuLingType.roll(player.getRandom())
                        : WuLingType.byKey(typeKey);
                // 创造绑定器不消耗灵珠，所以没有品质加成，按基准速度（×1）开启；
                // 上限与所有玩家一致（Config#maxRealm）
                holder.data().bind(type, "creative", 1.0D);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8F, 1.0F);
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.creative_bind",
                        Component.translatable(type.translationKey())));
                // 创造模式：绑定后立即凝聚出实体武灵（这次免费，不扣灵力）
                condense(player, true);
            } else {
                if (!hasBinder(player)) {
                    player.sendSystemMessage(Component.translatable("message.wulingdiguo.bind_need_binder"));
                    return;
                }
                int need = Config.BINDING_CONSUMES_BEAD_COUNT.get();
                ItemStack bead = findBead(player, need);
                if (bead.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("message.wulingdiguo.bind_need_bead"));
                    return;
                }

                SpiritQuality quality = SpiritBeadItem.getQuality(bead);
                String source = SpiritBeadItem.getSource(bead);

                WuLingType type = typeKey == null || typeKey.isEmpty()
                        ? WuLingType.roll(player.getRandom())
                        : WuLingType.byKey(typeKey);
                // 品质只决定修炼速度，不决定上限
                double bonus = Config.cultivationBonus(quality);

                holder.data().bind(type, source, bonus);
                bead.shrink(need);

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8F, 1.0F);
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.bind_success",
                        Component.translatable(type.translationKey()),
                        Component.translatable(quality.translationKey()),
                        trim(bonus)));
            }

            if (player instanceof ServerPlayer serverPlayer) {
                ModMessages.sendWuLingTo(serverPlayer);
            }
        });
    }

    /**
     * 村民牧师「开启武灵」—— <b>抽取，而不是选择</b>（2026-09-27 用户设定）。
     *
     * <p>原文里开启武灵得有引路人，这里就落在村民牧师身上：玩家<b>手持灵珠右键牧师</b>，
     * 牧师替他开武灵，修炼方向<b>由武灵自己抽</b>（权重见 {@link WuLingType#roll}，
     * 稀有 1 : 常见 10），玩家不能挑。想要自己挑种类，只能走创造模式的
     * {@link com.wuling.empire.item.WuLingBinderItem 武灵绑定器} ——
     * 也就是「生存靠牧师抽签，创造靠绑定器自选」。
     *
     * <p>只对「还没有武灵」的玩家生效：已开启的不会被覆盖（要换先去
     * {@code /wuling spirit unbind}）。
     *
     * @param npc 触发这次开启的牧师，仅用于音效 / 粒子的位置
     * @return true = 这次交互被牧师接管了（调用方应取消原版交互，别弹出交易界面）
     */
    public static boolean openByNpc(Player player, LivingEntity npc) {
        ItemStack offered = heldBead(player);
        if (offered.isEmpty()) {
            // 手里没拿灵珠 → 不打扰，让原版交易界面正常打开
            return false;
        }

        player.getCapability(ModCapabilities.WU_LING).ifPresent(holder -> {
            if (holder.data().isBound()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.bind_already",
                        holder.data().displayName()));
                npcRefuse(player, npc);
                return;
            }

            int need = Config.BINDING_CONSUMES_BEAD_COUNT.get();
            if (offered.getCount() < need) {
                player.sendSystemMessage(Component.translatable(
                        "message.wulingdiguo.npc_open_need_bead", need));
                npcRefuse(player, npc);
                return;
            }

            SpiritQuality quality = SpiritBeadItem.getQuality(offered);
            String source = SpiritBeadItem.getSource(offered);
            // 抽取：不由玩家指定种类
            WuLingType type = WuLingType.roll(player.getRandom());
            // 品质只决定修炼速度，不决定上限
            double bonus = Config.cultivationBonus(quality);

            holder.data().bind(type, source, bonus);
            offered.shrink(need);

            npcRitual(player, npc);
            player.sendSystemMessage(Component.translatable("message.wulingdiguo.npc_open_success",
                    Component.translatable(type.translationKey()),
                    Component.translatable(type.rarity().labelKey()),
                    Component.translatable(quality.translationKey()),
                    trim(bonus)));

            if (player instanceof ServerPlayer serverPlayer) {
                ModMessages.sendWuLingTo(serverPlayer);
            }
        });
        return true;
    }

    /** 手里（主手 / 副手）拿着的灵珠；没拿返回空 */
    private static ItemStack heldBead(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isBead(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return isBead(off) ? off : ItemStack.EMPTY;
    }

    /** 开启仪式：附魔台音效 + 牧师点头 + 一圈符文与绿色粒子 */
    private static void npcRitual(Player player, LivingEntity npc) {
        player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, player.getSoundSource(), 1.0F, 1.0F);
        player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.VILLAGER_YES, player.getSoundSource(), 1.0F, 1.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8F, 1.0F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    npc.getX(), npc.getY() + 1.4D, npc.getZ(), 60, 0.6D, 0.9D, 0.6D, 0.8D);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    npc.getX(), npc.getY() + 1.8D, npc.getZ(), 8, 0.4D, 0.3D, 0.4D, 0.0D);
        }
    }

    /** 牧师拒绝：摇头音 + 头顶冒火 */
    private static void npcRefuse(Player player, LivingEntity npc) {
        player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.VILLAGER_NO, player.getSoundSource(), 1.0F, 1.0F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    npc.getX(), npc.getY() + 1.9D, npc.getZ(), 6, 0.4D, 0.2D, 0.4D, 0.0D);
        }
    }

    /**
     * 凝聚出实体武灵：按武灵种类 + 当前境界，在玩家面前生成对应的实物
     * （剑武灵→剑、斧武灵→斧……境界决定材质档次，如钻石境界→钻石剑）。
     *
     * 0.2.14 起的两条新口径：
     *   - <b>不需要武灵绑定器</b>（绑定器只用于「选择种类」，绑定完成后再也不依赖它）；
     *   - <b>消耗灵力</b>（Config#CONDENSE_SPIRIT_COST），灵力不足就无法凝聚。
     */
    public static void condense(Player player) {
        condense(player, false);
    }

    /**
     * @param free true = 免费凝聚（创造绑定器绑定后自动送的那一次），不扣灵力、也不校验
     */
    public static void condense(Player player, boolean free) {
        player.getCapability(ModCapabilities.WU_LING).ifPresent(holder -> {
            if (!holder.data().isBound()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.condense_need_bind"));
                return;
            }

            float cost = free ? 0.0F : (float) (double) Config.CONDENSE_SPIRIT_COST.get();
            ISpiritPower spirit = player.getCapability(ModCapabilities.SPIRIT_POWER).orElse(null);
            if (!free) {
                if (spirit == null) {
                    return;
                }
                if (spirit.getSpirit() < cost - 0.001F) {
                    player.sendSystemMessage(Component.translatable(
                            "message.wulingdiguo.condense_need_spirit", trim(cost)));
                    return;
                }
            }

            WuLingData data = holder.data();

            // 腐肉武灵是召唤类（2026-10-02）：不产物品，改成一队僵尸随从
            if (data.type().isSummoner()) {
                summonZombies(player, data, cost, spirit, free);
                return;
            }

            // 护甲武灵一次给整套四件（头 / 胸 / 腿 / 靴），其余种类只有一件。
            java.util.List<ItemStack> items =
                    data.type().manifest(data.realmOrdinal(), data.stageOrdinal());
            boolean isSet = items.size() > 1;
            Component setName = Component.translatable("wuling.manifest.name",
                    Component.translatable(data.realm().translationKey()),
                    Component.translatable(data.type().translationKey()));

            if (cost > 0.0F && spirit != null) {
                spirit.addSpirit(-cost);
                if (player instanceof ServerPlayer serverPlayer) {
                    ModMessages.sendSpiritTo(serverPlayer);
                }
            }

            double x = player.getX();
            double y = player.getY() + 1.0D;
            double z = player.getZ();
            for (ItemStack item : items) {
                ItemEntity entity = new ItemEntity(player.level(), x, y, z, item);
                entity.setPickUpDelay(0);
                player.level().addFreshEntity(entity);
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8F, 1.2F);
            if (cost > 0.0F) {
                player.sendSystemMessage(isSet
                        ? Component.translatable("message.wulingdiguo.condense_success_set",
                                setName, items.size(), trim(cost),
                                trim(spirit == null ? 0.0F : spirit.getSpirit()))
                        : Component.translatable("message.wulingdiguo.condense_success",
                                items.get(0).getHoverName(), trim(cost),
                                trim(spirit == null ? 0.0F : spirit.getSpirit())));
            } else {
                player.sendSystemMessage(isSet
                        ? Component.translatable("message.wulingdiguo.condense_success_set_free",
                                setName, items.size())
                        : Component.translatable("message.wulingdiguo.condense_success_free",
                                items.get(0).getHoverName()));
            }
        });
    }

    /**
     * 腐肉武灵的凝聚：召唤一队僵尸随从（2026-10-02 用户设定）。
     *
     * <p>数量随<b>大境界</b>递增（默认每个大境界 5 只：木 5 → 绿宝石 35），
     * 并在设定档位以上（默认钻石）赋予飞行能力。
     *
     * <p>与物品型凝聚共用同一份灵力消耗 —— 一次召唤一整队，只收一次费。
     */
    private static void summonZombies(Player player, WuLingData data, float cost,
                                      ISpiritPower spirit, boolean free) {
        int count = RottenFleshRule.summonsFor(data.realmOrdinal());
        boolean flying = RottenFleshRule.canFly(data.realmOrdinal());

        if (cost > 0.0F && spirit != null) {
            spirit.addSpirit(-cost);
            if (player instanceof ServerPlayer serverPlayer) {
                ModMessages.sendSpiritTo(serverPlayer);
            }
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            WuLingZombieEntity zombie = ModEntities.WU_LING_ZOMBIE.get().create(player.level());
            if (zombie == null) {
                continue;
            }
            // 围成一圈落在玩家身边，免得几十只全挤在同一格互相推挤
            double angle = (Math.PI * 2.0D / count) * i;
            double radius = 1.5D;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + (flying ? 1.5D : 0.0D);

            zombie.moveTo(x, y, z, player.getYRot(), 0.0F);
            zombie.setOwner(player);
            zombie.setFlying(flying);
            player.level().addFreshEntity(zombie);
            spawned++;
        }

        if (spawned <= 0) {
            return;
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ZOMBIE_AMBIENT, player.getSoundSource(), 1.0F, 0.6F);

        if (cost > 0.0F) {
            player.sendSystemMessage(Component.translatable("message.wulingdiguo.summon_zombies",
                    spawned, trim(cost), trim(spirit == null ? 0.0F : spirit.getSpirit())));
        } else {
            player.sendSystemMessage(Component.translatable("message.wulingdiguo.summon_zombies_free",
                    spawned));
        }
    }

    /**
     * 凝聚「自选附魔书」—— 书武灵的专属入口。
     *
     * <p>2026-10-01 用户口径：书武灵按 Shift+M 不再直接掉一本附魔书，
     * 而是先打开 {@code client/WuLingBookScreen}，让玩家把<b>附魔和等级一起挑</b>，
     * 挑完再走这里生成（界面只负责选择，判定与消耗都在服务端）。
     *
     * <p>与 {@link #condense} 的区别只有三点：附魔由玩家指定（不再固定给耐久）、
     * 把选择记进物品 NBT（境界提升换新时靠它还原，否则会被冲成默认附魔）、
     * 以及<b>消耗随附魔稀有度与等级递增</b>（2026-10-02 用户口径：
     * 「附魔书凝聚等级越高，附魔书越稀有消耗越大」，算法见 {@link BookCost}）。
     *
     * @param enchantId 附魔的注册名（如 {@code minecraft:sharpness}）
     * @param level     附魔等级；服务端会再夹一次 [1, 该附魔上限]
     */
    public static void condenseBook(Player player, String enchantId, int level) {
        player.getCapability(ModCapabilities.WU_LING).ifPresent(holder -> {
            WuLingData data = holder.data();
            if (!data.isBound()) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.condense_need_bind"));
                return;
            }
            if (data.type() != WuLingType.BOOK) {
                player.sendSystemMessage(Component.translatable("message.wulingdiguo.book_need_book_type"));
                return;
            }

            // enchantId 来自客户端 → 用 tryParse，别让空串 / 非法字符把服务端主线程炸了
            ResourceLocation enchantKey = ResourceLocation.tryParse(enchantId);
            Enchantment enchantment = enchantKey == null
                    ? null
                    : ForgeRegistries.ENCHANTMENTS.getValue(enchantKey);
            if (enchantment == null) {
                return;
            }
            int lvl = Math.max(1, Math.min(level, enchantment.getMaxLevel()));

            float cost = BookCost.of(enchantment, lvl);
            ISpiritPower spirit = player.getCapability(ModCapabilities.SPIRIT_POWER).orElse(null);
            if (spirit == null) {
                return;
            }
            if (spirit.getSpirit() < cost - 0.001F) {
                player.sendSystemMessage(Component.translatable(
                        "message.wulingdiguo.condense_need_spirit", trim(cost)));
                return;
            }
            spirit.addSpirit(-cost);
            if (player instanceof ServerPlayer serverPlayer) {
                ModMessages.sendSpiritTo(serverPlayer);
            }

            // 与普通凝聚同一件物品（<境界>_书），只是附魔换成玩家挑的那条
            ItemStack book = new ItemStack(ManifestItems.single(data.realm(), WuLingType.BOOK));
            book.enchant(enchantment, lvl);
            ManifestItems.markManifest(book, WuLingType.BOOK);
            ManifestItems.markBook(book, enchantId, lvl);

            ItemEntity entity = new ItemEntity(player.level(),
                    player.getX(), player.getY() + 1.0D, player.getZ(), book);
            entity.setPickUpDelay(0);
            player.level().addFreshEntity(entity);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 0.8F, 1.2F);
            player.sendSystemMessage(Component.translatable("message.wulingdiguo.book_condensed",
                    enchantment.getFullname(lvl), trim(cost), trim(spirit.getSpirit())));
        });
    }

    /**
     * 境界提升后，把背包里所有凝聚出的武灵实物<b>换成当前境界/小境界的那件</b>。
     *
     * <p>2026-10-01 用户口径：「每一级升级时手上的武灵物品也会一同升级」；
     * 2026-10-02 追加「每升一等级基础属性都会提升」—— 因此小境界（前 / 中 / 后）
     * 也会改数值，不再只是进度条上的刻度。
     *
     * <p>分两种情况处理：
     * <ul>
     *   <li><b>大境界突破</b>：换的是<b>另一件物品</b>（{@code wood_sword → stone_sword}），
     *       只能整件替换；自选附魔书的选择按 NBT 还原回去。</li>
     *   <li><b>小境界晋升</b>：还是同一件物品，只是重新算一遍属性修饰符 ——
     *       耐久、附魔、玩家自己起的名字都原样搬过去，
     *       既不靠升级白刷耐久，也不会吞掉玩家后来自己加的附魔。</li>
     * </ul>
     *
     * <p>判定「要不要动」靠两样：物品 ID 是不是当前大境界那件
     * ＋ 物品上记的小境界（{@link ManifestItems#TAG_MANIFEST_STAGE}）是不是当前小境界。
     *
     * @return 换掉的件数
     */
    public static int refreshManifestItems(Player player) {
        var holder = player.getCapability(ModCapabilities.WU_LING).orElse(null);
        if (holder == null || !holder.data().isBound()) {
            return 0;
        }
        WuLingData data = holder.data();
        Container inventory = player.getInventory();
        int changed = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack old = inventory.getItem(i);
            WuLingType type = ManifestItems.manifestType(old);
            if (type == null) {
                continue;
            }
            ArmorItem.Type armorType = armorTypeOf(old);
            boolean sameRealm = old.is(currentItem(data.realm(), type, armorType));
            // 大境界、小境界都对得上 → 已经是最新的，不用动
            if (sameRealm && ManifestItems.manifestStage(old) == data.stageOrdinal()) {
                continue;
            }
            ItemStack fresh = type.manifestPiece(data.realmOrdinal(), data.stageOrdinal(),
                    armorType == null ? -1 : ManifestItems.armorIndex(armorType));
            if (fresh.isEmpty()) {
                continue;
            }
            if (sameRealm) {
                carryOver(old, fresh);
            } else {
                // 自选附魔书：换新会把附魔冲成默认的耐久，得把玩家挑的那条还原回去
                String bookEnchantId = ManifestItems.bookEnchant(old);
                if (bookEnchantId != null) {
                    Enchantment enchantment =
                            ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(bookEnchantId));
                    if (enchantment != null) {
                        int lvl = Math.min(ManifestItems.bookLevel(old), enchantment.getMaxLevel());
                        fresh.getEnchantmentTags().clear();
                        fresh.enchant(enchantment, lvl);
                        ManifestItems.markBook(fresh, bookEnchantId, lvl);
                    }
                }
            }
            inventory.setItem(i, fresh);
            changed++;
        }
        return changed;
    }

    /**
     * 小境界重算属性时，把旧件上的「玩家痕迹」搬到新件上。
     *
     * <p>属性修饰符与 tooltip 加成行由 {@code WuLingType#empower} 在新建时就写好，
     * 这里只搬那些跟属性无关、丢了会让玩家难受的东西。
     */
    private static void carryOver(ItemStack old, ItemStack fresh) {
        // 耐久进度原样保留（上限按新件截断，避免出现负数剩余耐久）
        if (fresh.isDamageableItem()) {
            fresh.setDamageValue(Math.min(old.getDamageValue(),
                    Math.max(0, fresh.getMaxDamage() - 1)));
        }
        // 附魔整体搬运：包含本模组给的默认附魔，也包含玩家后来自己加上去的
        EnchantmentHelper.setEnchantments(EnchantmentHelper.getEnchantments(old), fresh);
        // 玩家自己改的名字
        if (old.hasCustomHoverName()) {
            fresh.setHoverName(old.getHoverName());
        }
        // 自选附魔书的记录（书武灵）
        String bookEnchantId = ManifestItems.bookEnchant(old);
        if (bookEnchantId != null) {
            ManifestItems.markBook(fresh, bookEnchantId, ManifestItems.bookLevel(old));
        }
    }

    /** 当前境界下，这种武灵的实物是哪一件（护甲要分部位） */
    private static net.minecraft.world.item.Item currentItem(WuLingRealm realm, WuLingType type,
                                                             ArmorItem.Type armorType) {
        if (type == WuLingType.ARMOR && armorType != null) {
            return ManifestItems.armor(realm, armorType);
        }
        return ManifestItems.single(realm, type);
    }

    /** 这件物品是护甲的话，取它的部位；不是则 null */
    private static ArmorItem.Type armorTypeOf(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor ? armor.getType() : null;
    }

    /** 3.0 → "3"，7.5 → "7.5" */
    private static String trim(double value) {
        return Math.abs(value - Math.round(value)) < 0.005D
                ? String.valueOf(Math.round(value))
                : String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }

    /** 5.0 → "5"，7.5 → "7.5" */
    private static String trim(float value) {
        return Math.abs(value - Math.round(value)) < 0.05F
                ? String.valueOf(Math.round(value))
                : String.format("%.1f", value);
    }

    private static boolean hasBinder(Player player) {
        Container inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.WU_LING_BINDER.get())
                    || stack.is(ModItems.CREATIVE_WU_LING_BINDER.get())) {
                return true;
            }
        }
        return false;
    }

    /** 取用顺序：副手 → 主手 → 背包自上而下 */
    private static ItemStack findBead(Player player, int need) {
        ItemStack offhand = player.getOffhandItem();
        if (isBead(offhand) && offhand.getCount() >= need) {
            return offhand;
        }
        ItemStack main = player.getMainHandItem();
        if (isBead(main) && main.getCount() >= need) {
            return main;
        }
        Container inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isBead(stack) && stack.getCount() >= need) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isBead(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SpiritBeadItem;
    }
}
