package com.example.upgradermod.registry;

import com.example.upgradermod.UpgraderMod;
import com.example.upgradermod.item.UpgraderItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Регистрация предметов мода Upgrader Mod.
 *
 * @author Popipok
 */
public class ModItems {

    /**
     * Отложенный реестр для предметов.
     */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, UpgraderMod.MOD_ID);

    /**
     * Предмет апгрейдера, открывающий интерфейс рулетки.
     */
    public static final RegistryObject<Item> UPGRADER =
            ITEMS.register("upgrader", UpgraderItem::new);

    /**
     * Регистрирует предметы мода в шине событий.
     *
     * @param bus шина событий мода
     */
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
