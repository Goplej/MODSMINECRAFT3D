package com.example.upgradermod;

import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueProviderRegistry;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.registry.ModItems;
import com.example.upgradermod.registry.ModMenus;
import com.example.upgradermod.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

        // Регистрация на шине событий Forge: методы с @SubscribeEvent ниже
        // (onServerTick, onPlayerLogout, onPlayerDeath) подписываются именно здесь.
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Upgrader Mod успешно загружен и ожидает commonSetup.");
    }

    /**
     * Тик логического сервера. Завершает отложенные спины (PENDING) ровно через
     * 40 тиков после принятия ставки. Без этого события отложенный спин
     * никогда не завершится и игрок не увидит результат.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            try {
                UpgraderMenu.tickPending(event.getServer());
            } catch (Throwable t) {
                LOGGER.error("onServerTick error", t);
            }
        }
    }

    /**
     * Выход игрока с сервера: завершаем отложенный спин до сохранения данных игрока.
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        try {
            UpgraderMenu.settlePending(event.getEntity());
        } catch (Throwable t) {
            LOGGER.error("onPlayerLogout error", t);
        }
    }

    /**
     * Смерть игрока: завершаем отложенный спин до выпадения инвентаря,
     * чтобы награда попала в дроп вместе с остальными предметами.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                UpgraderMenu.settlePending(player);
            } catch (Throwable t) {
                LOGGER.error("onPlayerDeath error", t);
            }
        }
    }

    /**
     * Общая настройка мода. Выполняется на клиенте и сервере.
     *
     * @param event событие общего этапа инициализации
     */
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
