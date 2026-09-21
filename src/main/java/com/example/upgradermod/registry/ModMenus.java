package com.example.upgradermod.registry;

import com.example.upgradermod.UpgraderMod;
import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Регистрация типов меню (контейнеров) мода Upgrader Mod.
 *
 * @author Popipok
 */
public class ModMenus {

    /**
     * Отложенный реестр для типов контейнеров меню.
     */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, UpgraderMod.MOD_ID);

    /**
     * Тип меню для контейнера апгрейдера.
     */
    public static final RegistryObject<MenuType<UpgraderMenu>> UPGRADER_MENU =
            MENU_TYPES.register("upgrader_menu", () -> IForgeMenuType.create((id, inv, data) -> new UpgraderMenu(id, inv)));

    /**
     * Регистрирует типы меню в шине событий.
     *
     * @param bus шина событий мода
     */
    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}
