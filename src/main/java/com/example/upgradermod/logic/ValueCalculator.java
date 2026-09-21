package com.example.upgradermod.logic;

import com.example.upgradermod.ModConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Калькулятор ценности предметов и стаков.
 * Простой подсчёт: ценность одного предмета от провайдеров × количество.
 * Никакой дополнительной математики — зачарования, атрибуты и износ
 * не меняют цену, ценность предсказуема и совпадает с values.json.
 *
 * @author Popipok
 */
public class ValueCalculator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Вычисляет базовую ценность одного предмета без учёта размера стака,
     * используя зарегистрированные провайдеры в порядке их приоритета.
     *
     * @param stack предмет для оценки
     * @return базовая цена одного предмета
     */
    public static double getSingleItemValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }

        for (ValueProvider provider : ValueProviderRegistry.getProviders()) {
            try {
                double val = provider.getValue(stack);
                if (val > 0) {
                    return Math.min(ModConfig.getRecipeMaxPrice(), val);
                }
            } catch (Throwable t) {
                LOGGER.debug("Provider {} threw in getSingleItemValue: {}", provider.getName(), t.getMessage());
            }
        }

        return 0.0;
    }

    /**
     * Вычисляет полную ценность стака предметов простым подсчётом:
     * ценность одного предмета × количество. Без бонусов за зачарования,
     * атрибуты и без поправок на прочность.
     *
     * @param stack стак предметов для оценки
     * @return полная стоимость стака
     */
    public static double getItemStackValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }

        double unitValue = 0.0;
        for (ValueProvider provider : ValueProviderRegistry.getProviders()) {
            try {
                double val = provider.getValue(stack);
                if (val > 0) {
                    unitValue = val;
                    break;
                }
            } catch (Throwable t) {
                LOGGER.debug("Provider {} threw in getItemStackValue: {}", provider.getName(), t.getMessage());
            }
        }

        if (unitValue <= 0) {
            unitValue = 1.0;
        }

        // Простой подсчёт: ценность одного предмета × количество.
        double totalValue = unitValue * stack.getCount();

        // Ограничение максимальной стоимости (защита от абузов)
        return Math.min(ModConfig.getRecipeMaxPrice(), Math.max(0.0, totalValue));
    }
}
