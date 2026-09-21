package com.example.upgradermod;

import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueProviderRegistry;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.registry.ModItems;
import com.example.upgradermod.registry.ModMenus;
import com.example.upgradermod.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Главный класс мода Upgrader Mod.
 * Отвечает за инициализацию реестров, конфигурации, сетевого канала и провайдеров ценностей.
 *
 * @author Popipok
 */
@Mod(UpgraderMod.MOD_ID)
public class UpgraderMod {

    /**
     * Уникальный идентификатор мода.
     */
    public static final String MOD_ID = "upgradermod";

    /**
     * Основной логгер мода.
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Конструктор мода, вызываемый загрузчиком FML.
     */
    public UpgraderMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрация конфигурации
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC, "upgradermod-common.toml");

        // Регистрация отложенных реестров предметов и меню
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModSounds.register(modEventBus);

        // Регистрация слушателей жизненного цикла
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        // Регистрация на шине событий Forge
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        MinecraftForge.EVENT_BUS.addListener(this::playerLogout);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, this::playerDeath);

        LOGGER.info("Upgrader Mod успешно загружен и ожидает commonSetup.");
    }

    /**
     * Общая настройка мода. Выполняется на клиенте и сервере.
     *
     * @param event событие общего этапа инициализации
     */
    private void serverTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            com.example.upgradermod.menu.UpgraderMenu.tickPending(event.getServer());
        }
    }

    private void playerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        com.example.upgradermod.menu.UpgraderMenu.settlePending(event.getEntity());
    }

    private void playerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.example.upgradermod.menu.UpgraderMenu.settlePending(player);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Регистрация пакетов
            NetworkHandler.register();

            // Инициализация провайдеров ценностей
            ValueProviderRegistry.init();

            // Инициализация кэша предметов
            ItemRegistryCache.init();

            LOGGER.info("Upgrader Mod commonSetup завершён успешно.");
        });
    }

    /**
     * Добавление предметов мода в стандартные вкладки творческого режима.
     *
     * @param event событие наполнения творческих вкладок
     */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES ||
                event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(ModItems.UPGRADER.get());
        }
    }
}
