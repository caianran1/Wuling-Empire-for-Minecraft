package com.wuling.empire;

import com.mojang.logging.LogUtils;
import com.wuling.empire.capability.ModCapabilities;
import com.wuling.empire.entity.ModEntities;
import com.wuling.empire.events.ModEvents;
import com.wuling.empire.item.ManifestItems;
import com.wuling.empire.item.ModItems;
import com.wuling.empire.item.ModTabs;
import com.wuling.empire.item.RedstoneItems;
import com.wuling.empire.network.ModMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 《武灵帝国》Forge 模组主类 - Minecraft 1.20.1 / Forge 47.4.x
 *
 * 当前已实现：第一部分「灵珠系统」
 *  - 灵珠掉落（怪物死亡就地掉落；末影龙另留专属尸体）
 *  - 五品质灵珠（凡/良/优/上/极）
 *  - 百分制灵力面板与每分钟回复
 *  - Shift + 右键吸收灵珠回复灵力
 */
@Mod(WulingEmpire.MODID)
public class WulingEmpire {

    public static final String MODID = "wulingdiguo";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ⚠️ 构造函数必须是<b>无参</b>的，这是「兼容 1.20.1 全部 Forge 版本」的关键。
     *
     * <p>Forge 47.0.x 的 {@code FMLModContainer#constructMod()} 只做
     * {@code modClass.getDeclaredConstructor().newInstance()} —— 找不到无参构造函数就直接
     * 加载失败。而 47.1.0 之后才支持把 {@code FMLJavaModLoadingContext} 注入构造函数
     * （新版本是「先试带参、失败再退回无参」）。
     * 写无参构造函数在<b>新老版本都能被正确实例化</b>，代价只是要自己
     * {@code FMLJavaModLoadingContext.get()} 取上下文。</p>
     */
    public WulingEmpire() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modBus);
        // 武灵凝聚物（每个境界各一套，共 91 件）单独一个 DeferredRegister
        ManifestItems.ITEMS.register(modBus);
        // 红石装备（红石锭 + 红石战铠八件，充能类）
        RedstoneItems.ITEMS.register(modBus);
        // 本模组专属的创造模式物品栏
        ModTabs.TABS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);

        // 能力（Capability）注册：必须在值被获取之前完成
        ModCapabilities.init();
        modBus.addListener(ModCapabilities::onRegisterCapabilities);

        // 网络通道注册：ModMessages 自身带有 @Mod.EventBusSubscriber，
        // 这里不要再 addListener 一次，否则 onCommonSetup 会被调用两次，
        // 导致 newSimpleChannel 抛 "Channel already registered"。

        // 创造模式物品栏的内容在 ModTabs#MAIN 里一次性给全，不再往原版标签页里塞

        // 配置文件
        // 用 ModLoadingContext（而不是 FMLJavaModLoadingContext#registerConfig）：
        // 后者是 47.1.0 才把 FMLJavaModLoadingContext 挂到 ModLoadingContext 继承链上才有的，
        // 47.0.x 的 FMLJavaModLoadingContext 是个独立类，没有 registerConfig 方法。
        // ModLoadingContext#registerConfig 从 1.20.1 第一个 Forge 版本起就存在。
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // 配置加载完就把「真正生效的突破物资」打到日志里：
        // 老配置里的旧值不会跟着代码默认值走，打出来才好排查
        modBus.addListener(this::onConfigLoaded);

        // Forge 事件总线（游戏内事件）
        MinecraftForge.EVENT_BUS.register(new ModEvents());

        LOGGER.info("《武灵帝国》初始化完成，MODID = {}", MODID);
    }

    /** 配置加载 / 重载后，把生效中的突破物资需求打出来（排查「旧值没跟着代码走」用） */
    private void onConfigLoaded(net.minecraftforge.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON) {
            Config.logEffectiveRequirements();
        }
    }

    /**
     * 构造本模组命名空间下的 ResourceLocation。
     *
     * <p>⚠️ 这里<b>必须</b>用经典构造函数：{@code ResourceLocation.fromNamespaceAndPath} /
     * {@code parse} / {@code bySeparator} 这一批静态工厂是 Forge 在 1.20.1 后期才 backport
     * 进 MC 的（47.0.0、47.1.x、47.2.x、47.3.0~47.3.7 都没有），用了就会在那些版本上
     * 编译不过、运行 NoSuchMethodError。两参构造函数从 1.20.1 初版就在。</p>
     */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
