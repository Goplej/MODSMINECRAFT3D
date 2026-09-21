package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;

/**
 * Эвристический провайдер ценности предметов (fallback).
 * Оценивает предмет на основе его редкости (Rarity) и максимального размера стака.
 * Приоритет: 100.
 *
 * @author Popipok
 */
public class HeuristicValueProvider implements ValueProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public double getValue(ItemStack stack) {
        try {
            if (stack.isEmpty()) {
                return 0.0;
            }

            // Базовая цена по редкости предмета (default для модовых Rarity)
            Rarity rarity = stack.getRarity();
            double baseRarityValue = switch (rarity) {
                case COMMON -> 10.0;
                case UNCOMMON -> 50.0;
                case RARE -> 200.0;
                case EPIC -> 1000.0;
                default -> 10.0;
            };

            // Множитель по размеру стака (нестакаемые предметы обычно ценнее)
            int maxStackSize = stack.getMaxStackSize();
            double stackMultiplier = 1.0;
            if (maxStackSize == 1) {
                stackMultiplier = 5.0;
            } else if (maxStackSize <= 16) {
                stackMultiplier = 2.5;
            }

            // Дополнительный множитель для предметов с прочностью
            if (stack.isDamageableItem()) {
                stackMultiplier *= 1.5;
            }

            return Math.max(5.0, baseRarityValue * stackMultiplier);
        } catch (Throwable t) {
            LOGGER.debug("HeuristicValueProvider error for {}: {}", stack, t.getMessage());
            return 10.0;
        }
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getName() {
        return "HeuristicValueProvider";
    }
}
