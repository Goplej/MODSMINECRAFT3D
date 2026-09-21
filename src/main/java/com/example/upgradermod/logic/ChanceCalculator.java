package com.example.upgradermod.logic;

import com.example.upgradermod.ModConfig;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Калькулятор шанса апгрейда и форматирования отображения вероятности.
 *
 * @author Popipok
 */
public class ChanceCalculator {

    /**
     * Вычисляет шанс успешного апгрейда в процентах (от minChance до maxChance).
     * ratio = inputValue / (targetValue * multiplier)
     * chance = ratio * 100
     * clamp 1e-21...90.0
     *
     * @param inputValue  ценность ставки
     * @param targetValue ценность цели
     * @param multiplier  множитель (1, 2, 4, 8)
     * @return шанс в процентах
     */
    public static double calculateChance(double inputValue, double targetValue, int multiplier) {
        if (inputValue <= 0.0 || targetValue <= 0.0 || multiplier <= 0) {
            return 0.0;
        }

        double ratio = inputValue / (targetValue * (double) multiplier);
        double chance = ratio * 100.0;

        double minChance = ModConfig.getMinChance();
        double maxChance = ModConfig.getMaxChance();

        return Math.max(minChance, Math.min(maxChance, chance));
    }

    /**
     * Форматирует шанс для отображения в интерфейсе.
     * Если chance >= 0.0001 — "%.4f%%",
     * иначе "1 к <formatNumber(100/chance)>".
     *
     * @param chance шанс в процентах
     * @return отформатированная строка
     */
    public static String formatChance(double chance) {
        if (chance <= 0.0) {
            return "0.0000%";
        }

        if (chance >= 0.0001) {
            return String.format(Locale.ROOT, "%.4f%%", chance);
        } else {
            double odds = 100.0 / chance;
            return "1 к " + formatNumber(odds);
        }
    }

    /**
     * Форматирует число шанса или соотношения.
     *
     * @param number число для форматирования
     * @return отформатированное представление числа
     */
    public static String formatNumber(double number) {
        if (Double.isInfinite(number) || Double.isNaN(number)) {
            return "∞";
        }

        if (number >= 1e12) {
            return String.format(Locale.ROOT, "%.2e", number);
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,###", symbols);
        return df.format(Math.round(number));
    }
}
