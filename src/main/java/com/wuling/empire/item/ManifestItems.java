package com.wuling.empire.item;

import com.wuling.empire.WulingEmpire;
import com.wuling.empire.wuling.WuLingRealm;
import com.wuling.empire.wuling.WuLingType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * 武灵凝聚物的物品注册表。
 *
 * <p><b>2026-10-01 用户口径：「武灵物品重新注册为新物品」</b> —— 大境界提升时
 * 手上的武灵实物要跟着升一档，所以每个大境界的每个武灵种类都是
 * <b>注册表里一件独立的物品</b>，而不是「拿原版木剑改个名字」。
 *
 * <p>命名规则 {@code <境界 key>_<种类/部位 key>}，例如
 * {@code wulingdiguo:wood_sword}、{@code wulingdiguo:netherite_chestplate}、
 * {@code wulingdiguo:emerald_boots}。绿宝石档沿用 0.2.x 就有的 id
 * （{@code emerald_sword} 等），只是现在统一由这里注册。
 *
 * <pre>
 *   7 个境界 × (剑 斧 镐 铲 弓 水 火 红石 书 = 9 件) + 7 × 护甲 4 件 = 91 件
 * </pre>
 *
 * <p><b>外观不重复做贴图</b>：低境界的模型 json 直接以原版同名物品模型为
 * parent（{@code {"parent":"minecraft:item/wooden_sword"}}），贴图仍是原版的；
 * 只有绿宝石档是单独染色的一档实物（见 {@link ModArmorMaterials}、
 * {@code tools/gen_emerald_gear.py}）。模型由
 * {@code tools/gen_manifest_models.py} 生成，勿手改。
 *
 * <p>物品名不写 lang 表，而是覆盖 {@link Item#getName(ItemStack)} 现算
 * （{@code wuling.manifest.name} + 护甲部位后缀），省掉 91 × 2 条语言键。
 */
public final class ManifestItems {

    private ManifestItems() {
    }

    /** 给凝聚物打标记用的 NBT 键，值为武灵种类 key（护甲固定为 "armor"） */
    public static final String TAG_MANIFEST = "WuLingManifest";

    /**
     * 凝聚时的小境界序号（前 0 / 中 1 / 后 2）。
     *
     * <p>2026-10-02 起小境界也会影响基础属性（{@code Config#MANIFEST_STAGE_STEP}），
     * 而小境界升级时物品<b>还是同一件</b>，没法靠物品 ID 判断该不该换，
     * 所以把「生成这一件时的小境界」记下来：
     * {@code WuLingBinding#refreshManifestItems} 拿它和玩家当前小境界比对，不一样就重算属性。
     * 0.3.3 及更早的旧物没有这个标签（读到 -1），下次升级会顺带被强化一次。
     */
    public static final String TAG_MANIFEST_STAGE = "WuLingManifestStage";

    /**
     * 自选附魔书用的 NBT 键：玩家在附魔书凝聚界面里挑了哪条附魔、几级。
     *
     * <p>必须记下来 —— 书武灵的凝聚物默认只带「耐久」，境界提升时
     * {@code WuLingBinding#refreshManifestItems} 会把旧件换成新境界的那件，
     * 若不还原附魔，玩家挑的附魔会被冲成默认耐久。
     */
    public static final String TAG_BOOK_ENCHANT = "WuLingBookEnchant";
    public static final String TAG_BOOK_LEVEL = "WuLingBookLevel";

    /** 护甲四件的部位翻译键，下标与 {@link #armorIndex(ArmorItem.Type)} 一致 */
    public static final String[] ARMOR_PART_KEYS = {
            "wuling.armor.helmet", "wuling.armor.chestplate",
            "wuling.armor.leggings", "wuling.armor.boots"};

    private static final String[] ARMOR_ID_SUFFIX = {
            "helmet", "chestplate", "leggings", "boots"};

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WulingEmpire.MODID);

    /** 非护甲：境界 → 种类 → 物品 */
    private static final Map<WuLingRealm, EnumMap<WuLingType, RegistryObject<Item>>> SINGLE =
            new EnumMap<>(WuLingRealm.class);

    /** 护甲：境界 → [头盔, 胸甲, 护腿, 靴子] */
    private static final Map<WuLingRealm, RegistryObject<Item>[]> ARMOR =
            new EnumMap<>(WuLingRealm.class);

    static {
        for (WuLingRealm realm : WuLingRealm.values()) {
            EnumMap<WuLingType, RegistryObject<Item>> byType = new EnumMap<>(WuLingType.class);
            Tier tier = tierOf(realm);

            byType.put(WuLingType.SWORD, register(realm, "sword", () -> sword(realm, tier)));
            byType.put(WuLingType.AXE, register(realm, "axe", () -> axe(realm, tier)));
            byType.put(WuLingType.PICKAXE, register(realm, "pickaxe", () -> pickaxe(realm, tier)));
            byType.put(WuLingType.SHOVEL, register(realm, "shovel", () -> shovel(realm, tier)));
            byType.put(WuLingType.BOW, register(realm, "bow", () -> bow(realm)));
            byType.put(WuLingType.WATER, register(realm, "water", () -> plain(realm, WuLingType.WATER)));
            byType.put(WuLingType.FIRE, register(realm, "fire", () -> plain(realm, WuLingType.FIRE)));
            byType.put(WuLingType.REDSTONE, register(realm, "redstone", () -> plain(realm, WuLingType.REDSTONE)));
            byType.put(WuLingType.BOOK, register(realm, "book", () -> plain(realm, WuLingType.BOOK)));
            SINGLE.put(realm, byType);

            RegistryObject<Item>[] pieces = new RegistryObject[4];
            for (int i = 0; i < 4; i++) {
                final ArmorItem.Type armorType = armorTypeOf(i);
                final String partKey = ARMOR_PART_KEYS[i];
                pieces[i] = register(realm, ARMOR_ID_SUFFIX[i],
                        () -> armorPiece(realm, armorType, partKey));
            }
            ARMOR.put(realm, pieces);
        }
    }

    // ===================== 取用 =====================

    /** 某境界某武灵的凝聚物（护甲请用 {@link #armor(WuLingRealm, ArmorItem.Type)}） */
    public static Item single(WuLingRealm realm, WuLingType type) {
        EnumMap<WuLingType, RegistryObject<Item>> byType = SINGLE.get(realm);
        if (byType == null) {
            return net.minecraft.world.item.Items.STICK;
        }
        RegistryObject<Item> item = byType.get(type);
        return item == null ? net.minecraft.world.item.Items.STICK : item.get();
    }

    /** 某境界的护甲单件 */
    public static Item armor(WuLingRealm realm, ArmorItem.Type type) {
        RegistryObject<Item>[] pieces = ARMOR.get(realm);
        int index = armorIndex(type);
        if (pieces == null || index < 0) {
            return net.minecraft.world.item.Items.LEATHER_CHESTPLATE;
        }
        return pieces[index].get();
    }

    /**
     * 反查：这件物品属于哪个大境界；不是凝聚物则返回 {@code null}。
     *
     * <p>给「合成出绿宝石装备」那条事件用 —— 合成表里拿到的只是物品本身，
     * 没有 NBT 标记，必须靠注册表反查才知道它是哪一档的凝聚物。
     */
    public static WuLingRealm realmOf(Item item) {
        if (item == null) {
            return null;
        }
        for (Map.Entry<WuLingRealm, EnumMap<WuLingType, RegistryObject<Item>>> entry : SINGLE.entrySet()) {
            for (RegistryObject<Item> registered : entry.getValue().values()) {
                if (registered.get() == item) {
                    return entry.getKey();
                }
            }
        }
        for (Map.Entry<WuLingRealm, RegistryObject<Item>[]> entry : ARMOR.entrySet()) {
            for (RegistryObject<Item> registered : entry.getValue()) {
                if (registered.get() == item) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /** 反查：这件物品对应哪种武灵；护甲返回 {@link WuLingType#ARMOR}，不是凝聚物返回 {@code null} */
    public static WuLingType typeOf(Item item) {
        if (item == null) {
            return null;
        }
        for (EnumMap<WuLingType, RegistryObject<Item>> byType : SINGLE.values()) {
            for (Map.Entry<WuLingType, RegistryObject<Item>> entry : byType.entrySet()) {
                if (entry.getValue().get() == item) {
                    return entry.getKey();
                }
            }
        }
        for (RegistryObject<Item>[] pieces : ARMOR.values()) {
            for (RegistryObject<Item> registered : pieces) {
                if (registered.get() == item) {
                    return WuLingType.ARMOR;
                }
            }
        }
        return null;
    }

    /** 反查：护甲部位 0 头 / 1 胸 / 2 腿 / 3 靴；非护甲或不是凝聚物返回 -1 */
    public static int armorPartOf(Item item) {
        if (item == null) {
            return -1;
        }
        for (RegistryObject<Item>[] pieces : ARMOR.values()) {
            for (int i = 0; i < pieces.length; i++) {
                if (pieces[i].get() == item) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** ArmorItem.Type → 0 头 / 1 胸 / 2 腿 / 3 靴 */
    public static int armorIndex(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    /** 0 头 / 1 胸 / 2 腿 / 3 靴 → ArmorItem.Type */
    public static ArmorItem.Type armorTypeOf(int index) {
        return switch (index) {
            case 0 -> ArmorItem.Type.HELMET;
            case 1 -> ArmorItem.Type.CHESTPLATE;
            case 2 -> ArmorItem.Type.LEGGINGS;
            default -> ArmorItem.Type.BOOTS;
        };
    }

    // ===================== 标记（识别凝聚物用） =====================

    /** 给凝聚物打标：说明它是「凝聚出的武灵实物」 */
    public static void markManifest(ItemStack stack, WuLingType type) {
        stack.getOrCreateTag().putString(TAG_MANIFEST, type.key());
    }

    /** 是不是凝聚出的武灵实物 */
    public static boolean isManifest(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_MANIFEST, Tag.TAG_STRING);
    }

    /** 读取凝聚物的武灵种类；没打标返回 null */
    public static WuLingType manifestType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MANIFEST, Tag.TAG_STRING)) {
            return null;
        }
        return WuLingType.byKey(tag.getString(TAG_MANIFEST));
    }

    /** 记下这一件是在哪个小境界凝聚出来的 */
    public static void markManifestStage(ItemStack stack, int stageOrdinal) {
        stack.getOrCreateTag().putInt(TAG_MANIFEST_STAGE, Math.max(0, stageOrdinal));
    }

    /** 这一件凝聚时的小境界；旧物（0.3.3 及更早）返回 -1 = 未知，按「需要重新强化」处理 */
    public static int manifestStage(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_MANIFEST_STAGE, Tag.TAG_INT)
                ? tag.getInt(TAG_MANIFEST_STAGE)
                : -1;
    }

    /** 给「自选附魔书」记下所选附魔与等级 */
    public static void markBook(ItemStack stack, String enchantId, int level) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_BOOK_ENCHANT, enchantId);
        tag.putInt(TAG_BOOK_LEVEL, Math.max(1, level));
    }

    /** 自选附魔书所选附魔的注册名（如 {@code minecraft:sharpness}）；不是自选附魔书则返回 null */
    public static String bookEnchant(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOOK_ENCHANT, Tag.TAG_STRING)) {
            return null;
        }
        String id = tag.getString(TAG_BOOK_ENCHANT);
        return id.isEmpty() ? null : id;
    }

    /** 自选附魔书所选等级；缺省 1 */
    public static int bookLevel(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_BOOK_LEVEL, Tag.TAG_INT)
                ? Math.max(1, tag.getInt(TAG_BOOK_LEVEL))
                : 1;
    }

    // ===================== 内部：构造物品 =====================

    private static RegistryObject<Item> register(WuLingRealm realm, String suffix,
                                                java.util.function.Supplier<Item> factory) {
        return ITEMS.register(realm.key() + "_" + suffix, factory);
    }

    /** 剑：与原版同款同参数（基础伤害档 3、速度 -2.4） */
    private static Item sword(WuLingRealm realm, Tier tier) {
        return new SwordItem(tier, 3, -2.4F, new Item.Properties()) {
            @Override
            public Component getName(ItemStack stack) {
                return displayName(realm, WuLingType.SWORD, null);
            }
        };
    }

    /** 斧：攻击力 / 速度照抄原版对应材质，保证「原版数值 × 境界倍数」的口径成立 */
    private static Item axe(WuLingRealm realm, Tier tier) {
        float damage = switch (realm) {
            case WOOD -> 6.0F;
            case STONE -> 7.0F;
            case GOLD, METEOR_IRON -> 6.0F;
            default -> 5.0F;
        };
        float speed = switch (realm) {
            case WOOD, STONE -> -3.2F;
            case METEOR_IRON -> -3.1F;
            default -> -3.0F;
        };
        return new AxeItem(tier, damage, speed, new Item.Properties()) {
            @Override
            public Component getName(ItemStack stack) {
                return displayName(realm, WuLingType.AXE, null);
            }
        };
    }

    private static Item pickaxe(WuLingRealm realm, Tier tier) {
        return new PickaxeItem(tier, 1, -2.8F, new Item.Properties()) {
            @Override
            public Component getName(ItemStack stack) {
                return displayName(realm, WuLingType.PICKAXE, null);
            }
        };
    }

    private static Item shovel(WuLingRealm realm, Tier tier) {
        return new ShovelItem(tier, 1.5F, -3.0F, new Item.Properties()) {
            @Override
            public Component getName(ItemStack stack) {
                return displayName(realm, WuLingType.SHOVEL, null);
            }
        };
    }

    private static Item bow(WuLingRealm realm) {
        return new BowItem(new Item.Properties().durability(384)) {
            @Override
            public Component getName(ItemStack stack) {
                return displayName(realm, WuLingType.BOW, null);
            }
        };
    }

    /** 水 / 火 / 红石 / 书：抽象类，物品本体不带额外行为 */
    private static Item plain(WuLingRealm realm, WuLingType type) {
        return new Item(new Item.Properties()) {
            @Override
            public Component getName(ItemStack stack) {
                return displayName(realm, type, null);
            }
        };
    }

    private static Item armorPiece(WuLingRealm realm, ArmorItem.Type type, String partKey) {
        return new ArmorItem(armorMaterialOf(realm), type, new Item.Properties()) {
            @Override
            public Component getName(ItemStack stack) {
                return displayName(realm, WuLingType.ARMOR, partKey);
            }
        };
    }

    /** 凝聚物显示名：「<境界><种类>武灵」，护甲再拼上部位 */
    public static Component displayName(WuLingRealm realm, WuLingType type, String partKey) {
        Component base = Component.translatable("wuling.manifest.name",
                Component.translatable(realm.translationKey()),
                Component.translatable(type.translationKey()));
        return partKey == null ? base
                : Component.translatable("wuling.manifest.armor_piece", base,
                Component.translatable(partKey));
    }

    // ===================== 境界 → 材质 =====================

    /** 工具材质：木 / 石 / 黄金 / 玄铁(铁) / 钻石 / 下界合金 / 绿宝石 */
    public static Tier tierOf(WuLingRealm realm) {
        return switch (realm) {
            case WOOD -> Tiers.WOOD;
            case STONE -> Tiers.STONE;
            case GOLD -> Tiers.GOLD;
            case METEOR_IRON -> Tiers.IRON;
            case DIAMOND -> Tiers.DIAMOND;
            case NETHERITE -> Tiers.NETHERITE;
            case EMERALD -> ModTiers.EMERALD;
        };
    }

    /** 护甲材质：皮革 / 锁链 / 黄金 / 铁 / 钻石 / 下界合金 / 绿宝石 */
    public static ArmorMaterial armorMaterialOf(WuLingRealm realm) {
        return switch (realm) {
            case WOOD -> ArmorMaterials.LEATHER;
            case STONE -> ArmorMaterials.CHAIN;
            case GOLD -> ArmorMaterials.GOLD;
            case METEOR_IRON -> ArmorMaterials.IRON;
            case DIAMOND -> ArmorMaterials.DIAMOND;
            case NETHERITE -> ArmorMaterials.NETHERITE;
            case EMERALD -> ModArmorMaterials.EMERALD;
        };
    }
}
