package com.example.upgradermod.logic;

import com.example.upgradermod.ModConfig;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Калькулятор общей ценности предметов и стаков.
 * Учитывает провайдеры ценностей, размер стака, зачарования, атрибуты и прочность предмета.
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
            // Не вызываем RecipeValueProvider внутри себя если это приведет к рекурсии
            if (provider instanceof com.example.upgradermod.logic.providers.RecipeValueProvider) {
                continue;
            }
            try {
                double val = provider.getValue(stack);
                if (val > 0) {
                    return Math.min(ModConfig.getRecipeMaxPrice(), val);
                }
            } catch (Throwable t) {
                LOGGER.debug("Provider {} threw in getSingleItemValue: {}", provider.getName(), t.getMessage());
            }
        }

        // Если другие провайдеры не дали результат, пробуем RecipeValueProvider
        for (ValueProvider provider : ValueProviderRegistry.getProviders()) {
            if (provider instanceof com.example.upgradermod.logic.providers.RecipeValueProvider) {
                try {
                    double val = provider.getValue(stack);
                    if (val > 0) {
                        return Math.min(ModConfig.getRecipeMaxPrice(), val);
                    }
                } catch (Throwable t) {
                    LOGGER.debug("RecipeValueProvider threw in getSingleItemValue: {}", t.getMessage());
                }
            }
        }

        return 0.0;
    }

    /**
     * Вычисляет полную ценность стака предметов с учётом количества,
     * зачарований, атрибутов и износа.
     *
     * @param stack стак предметов для оценки
     * @return полная стоимость стака
     */
    public static double getItemStackValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }

        double singleVal = 0.0;
        for (ValueProvider provider : ValueProviderRegistry.getProviders()) {
            try {
                double val = provider.getValue(stack);
                if (val > 0) {
                    singleVal = val;
                    break;
                }
            } catch (Throwable t) {
                LOGGER.debug("Provider {} threw in getItemStackValue: {}", provider.getName(), t.getMessage());
            }
        }

        if (singleVal <= 0) {
            singleVal = 1.0;
        }

        // Умножаем на размер стака
        double totalValue = singleVal * stack.getCount();

        try {
            // Добавляем бонус за зачарования
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                Enchantment ench = entry.getKey();
                int level = entry.getValue();

                int weight = switch (ench.getRarity()) {
                    case COMMON -> ModConfig.getEnchantWeightCommon();
                    case UNCOMMON -> (ModConfig.getEnchantWeightCommon() + ModConfig.getEnchantWeightRare()) / 2;
                    case RARE -> ModConfig.getEnchantWeightRare();
                    case VERY_RARE -> ModConfig.getEnchantWeightLegendary();
                };

                totalValue += (double) weight * level;
            }
        } catch (Throwable t) {
            LOGGER.debug("Enchantment bonus calculation failed: {}", t.getMessage());
        }

        try {
            // Бонус за атрибуты (например, дополнительный урон или броня)
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
                if (!modifiers.isEmpty()) {
                    totalValue += modifiers.size() * ModConfig.getAttributeWeight();
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Attribute bonus calculation failed: {}", t.getMessage());
        }

        // Учёт повреждения (прочности)
        try {
            if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                double durabilityRatio = 1.0 - ((double) stack.getDamageValue() / (double) stack.getMaxDamage());
                durabilityRatio = Math.max(0.1, Math.min(1.0, durabilityRatio));
                totalValue *= durabilityRatio;
            }
        } catch (Throwable t) {
            LOGGER.debug("Durability calculation failed: {}", t.getMessage());
        }

        // Ограничение максимальной стоимости
        return Math.min(ModConfig.getRecipeMaxPrice(), Math.max(0.0, totalValue));
    }
}
