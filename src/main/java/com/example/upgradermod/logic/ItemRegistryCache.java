package com.example.upgradermod.logic;

import com.example.upgradermod.ModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Кэш реестра всех зарегистрированных предметов для быстрого поиска и отображения в каталоге.
 *
 * @author Popipok
 */
public class ItemRegistryCache {

    private static final List<ItemStack> ALL_ITEMS = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * Инициализирует кэш зарегистрированных предметов из ForgeRegistries.ITEMS.
     */
    public static synchronized void init() {
        if (initialized && !ALL_ITEMS.isEmpty()) {
            return;
        }

        ALL_ITEMS.clear();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty() && !ModConfig.isBlacklisted(stack)) {
                    ALL_ITEMS.add(stack);
                }
            }
        }
        initialized = true;
    }

    /**
     * Возвращает список всех зарегистрированных предметов в виде ItemStack.
     *
     * @return неизменяемый список предметов
     */
    public static List<ItemStack> getAllItems() {
        if (!initialized || ALL_ITEMS.isEmpty()) {
            init();
        }
        return Collections.unmodifiableList(ALL_ITEMS);
    }

    /**
     * Выполняет поиск предметов по строковому запросу (по названию или идентификатору реестра).
     *
     * @param query поисковый запрос
     * @return отфильтрованный список подходящих предметов
     */
    public static List<ItemStack> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllItems();
        }

        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack stack : getAllItems()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.toString().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                result.add(stack);
                continue;
            }

            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(lowerQuery)) {
                result.add(stack);
            }
        }

        return result;
    }
}
