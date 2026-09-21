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
     * clamp minChance...maxChance
     *
     * @param inputValue  ценность ставки (всего стака)
     * @param targetValue суммарная ценность цели (стоимость единицы * количество цели)
     * @param multiplier  множитель награды (1, 2, 4, 8 или 10)
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
     * Серверный подбор количества цели, при котором фактический шанс максимально
     * приближается к желаемому проценту (пресеты 30%, 50%, 80%).
     * Учитывает clamp minChance/maxChance: если желаемый процент недостижим,
     * возвращается количество с ближайшим реальным шансом.
     *
     * @param inputValue       ценность ставки (всего стака)
     * @param unitTargetValue  ценность одной единицы целевого предмета
     * @param multiplier       множитель награды (1, 2, 4, 8 или 10)
     * @param desiredPercent   желаемый шанс в процентах
     * @param maxCount         максимально допустимое количество цели
     * @return подобранное количество цели (1..maxCount) или 0, если подбор невозможен
     */
    public static int solveCountForChance(double inputValue, double unitTargetValue,
                                          int multiplier, double desiredPercent, int maxCount) {
        if (inputValue <= 0.0 || unitTargetValue <= 0.0 || multiplier <= 0 || maxCount <= 0) {
            return 0;
        }

        int bestCount = 0;
        double bestDelta = Double.MAX_VALUE;
        for (int count = 1; count <= maxCount; count++) {
            double chance = calculateChance(inputValue, unitTargetValue * (double) count, multiplier);
            double delta = Math.abs(chance - desiredPercent);
            if (delta < bestDelta) {
                bestDelta = delta;
                bestCount = count;
            }
        }
        return bestCount;
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
