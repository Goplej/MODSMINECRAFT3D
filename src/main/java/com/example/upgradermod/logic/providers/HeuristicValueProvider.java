package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Эвристический провайдер ценности предметов (fallback).
 * Оценивает предмет на основе его редкости (Rarity) и максимального размера стака.
 * Приоритет: 100.
 *
 * @author Popipok
 */
public class HeuristicValueProvider implements ValueProvider {

    @Override
    public double getValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        // Базовая цена по редкости предмета
        Rarity rarity = stack.getRarity();
        double baseRarityValue = switch (rarity) {
            case COMMON -> 10.0;
            case UNCOMMON -> 50.0;
            case RARE -> 200.0;
            case EPIC -> 1000.0;
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
