package com.example.upgradermod.logic;

import net.minecraft.world.item.ItemStack;

/**
 * Интерфейс поставщика ценности предметов.
 * Провайдеры опрашиваются в порядке убывания приоритета.
 *
 * @author Popipok
 */
public interface ValueProvider {

    /**
     * Возвращает базовую ценность одного предмета из стака.
     *
     * @param stack оцениваемый предмет
     * @return ценность предмета (0.0 если данный провайдер не может оценить предмет)
     */
    double getValue(ItemStack stack);

    /**
     * Приоритет провайдера. Чем выше число, тем раньше вызывается провайдер.
     *
     * @return целое число приоритета
     */
    int getPriority();

    /**
     * Название провайдера для отладки и идентификации.
     *
     * @return строковое имя
     */
    String getName();
}
