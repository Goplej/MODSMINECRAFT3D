package com.example.upgradermod.client;

import com.example.upgradermod.UpgraderMod;
import com.example.upgradermod.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Клиентская инициализация мода Upgrader Mod.
 * Регистрирует экраны контейнеров и привязки клавиш.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = UpgraderMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    /**
     * Обработка события клиентской настройки FMLClientSetupEvent.
     * Привязывает экран UpgraderScreen к типу меню UPGRADER_MENU.
     *
     * @param event событие настройки клиента
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.UPGRADER_MENU.get(), UpgraderScreen::new);
        });
    }

    /**
     * Регистрация привязок горячих клавиш мода.
     *
     * @param event событие регистрации клавиш
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_UPGRADER_KEY);
    }
}
