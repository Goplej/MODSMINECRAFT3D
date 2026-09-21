package com.example.upgradermod;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Конфигурация мода Upgrader Mod.
 * Содержит параметры шансов, расчёта ценности предметов, рецептов и защиты от абуза.
 *
 * @author Popipok
 */
public class ModConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = specPair.getLeft();
        SPEC = specPair.getRight();
    }

    /**
     * Внутренний класс настроек ForgeConfigSpec.
     */
    public static class Common {
        public final ForgeConfigSpec.DoubleValue maxChance;
        public final ForgeConfigSpec.DoubleValue minChance;

        public final ForgeConfigSpec.IntValue enchantWeightCommon;
        public final ForgeConfigSpec.IntValue enchantWeightRare;
        public final ForgeConfigSpec.IntValue enchantWeightLegendary;
        public final ForgeConfigSpec.IntValue attributeWeight;

        public final ForgeConfigSpec.IntValue recipeMaxDepth;
        public final ForgeConfigSpec.LongValue recipeMaxPrice;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> recipeDepthMultipliers;

        public final ForgeConfigSpec.BooleanValue taxEnabled;
        public final ForgeConfigSpec.ConfigValue<String> taxItem;
        public final ForgeConfigSpec.IntValue taxAmount;

        public final ForgeConfigSpec.BooleanValue allowCreativeEndgame;
        public final ForgeConfigSpec.LongValue logThreshold;
        public final ForgeConfigSpec.DoubleValue maxDowngradeRatio;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Настройки шансов рулетки апгрейдера").push("chances");
            maxChance = builder.comment("Максимальный шанс успеха (в процентах, от 0.0 до 100.0)")
                    .defineInRange("maxChance", 90.0, 0.0, 100.0);
            minChance = builder.comment("Минимальный возможный шанс успеха")
                    .defineInRange("minChance", 1e-21, 0.0, 100.0);
            builder.pop();

            builder.comment("Веса зачарований и атрибутов при оценке предметов").push("weights");
            enchantWeightCommon = builder.comment("Вес обычного зачарования")
                    .defineInRange("enchantWeightCommon", 30, 0, 1000000);
            enchantWeightRare = builder.comment("Вес редкого зачарования")
                    .defineInRange("enchantWeightRare", 100, 0, 1000000);
            enchantWeightLegendary = builder.comment("Вес очень редкого/легендарного зачарования")
                    .defineInRange("enchantWeightLegendary", 200, 0, 1000000);
            attributeWeight = builder.comment("Вес модификаторов атрибутов")
                    .defineInRange("attributeWeight", 50, 0, 1000000);
            builder.pop();

            builder.comment("Параметры рекурсивного расчёта ценности по рецептам").push("recipes");
            recipeMaxDepth = builder.comment("Максимальная глубина рекурсии при разборе рецепта")
                    .defineInRange("recipeMaxDepth", 10, 1, 50);
            recipeMaxPrice = builder.comment("Максимальная цена предмета")
                    .defineInRange("recipeMaxPrice", 10000000000000L, 1L, Long.MAX_VALUE);
            recipeDepthMultipliers = builder.comment("Множители цены в зависимости от глубины рецепта")
                    .defineListAllowEmpty(List.of("recipeDepthMultipliers"),
                            () -> Arrays.asList(1.0, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0),
                            o -> o instanceof Double);
            builder.pop();

            builder.comment("Параметры комиссии/налога за прокрутку").push("tax");
            taxEnabled = builder.comment("Включён ли налог за прокрутку")
                    .define("taxEnabled", false);
            taxItem = builder.comment("Идентификатор предмета налога")
                    .define("taxItem", "minecraft:diamond");
            taxAmount = builder.comment("Количество предметов налога")
                    .defineInRange("taxAmount", 1, 1, 64);
            builder.pop();

            builder.comment("Защита от абузов и безопасность").push("security");
            allowCreativeEndgame = builder.comment("Разрешить эндгейм апгрейды в творческом режиме")
                    .define("allowCreativeEndgame", false);
            logThreshold = builder.comment("Порог стоимости ставки для записи в подозрительный лог")
                    .defineInRange("logThreshold", 1000000000L, 0L, Long.MAX_VALUE);
            maxDowngradeRatio = builder.comment("Максимальное соотношение ставки к цели при даунгрейде")
                    .defineInRange("maxDowngradeRatio", 100.0, 1.0, 1000000.0);
            builder.pop();
        }
    }

    /**
     * @return Максимальный шанс успеха (по умолчанию 90.0)
     */
    public static double getMaxChance() {
        return COMMON.maxChance.get();
    }

    /**
     * @return Минимальный шанс успеха (по умолчанию 1e-21)
     */
    public static double getMinChance() {
        return COMMON.minChance.get();
    }

    /**
     * @return Вес обычного зачарования
     */
    public static int getEnchantWeightCommon() {
        return COMMON.enchantWeightCommon.get();
    }

    /**
     * @return Вес редкого зачарования
     */
    public static int getEnchantWeightRare() {
        return COMMON.enchantWeightRare.get();
    }

    /**
     * @return Вес легендарного зачарования
     */
    public static int getEnchantWeightLegendary() {
        return COMMON.enchantWeightLegendary.get();
    }

    /**
     * @return Вес атрибутов
     */
    public static int getAttributeWeight() {
        return COMMON.attributeWeight.get();
    }

    /**
     * @return Максимальная глубина рекурсии рецептов
     */
    public static int getRecipeMaxDepth() {
        return COMMON.recipeMaxDepth.get();
    }

    /**
     * @return Максимальная допустимая цена предмета
     */
    public static long getRecipeMaxPrice() {
        return COMMON.recipeMaxPrice.get();
    }

    /**
     * @return Список множителей глубины рецепта
     */
    public static List<? extends Double> getRecipeDepthMultipliers() {
        return COMMON.recipeDepthMultipliers.get();
    }

    /**
     * @return Включён ли налог
     */
    public static boolean isTaxEnabled() {
        return COMMON.taxEnabled.get();
    }

    /**
     * @return Идентификатор предмета налога
     */
    public static String getTaxItem() {
        return COMMON.taxItem.get();
    }

    /**
     * @return Количество предметов налога
     */
    public static int getTaxAmount() {
        return COMMON.taxAmount.get();
    }

    /**
     * @return Разрешён ли эндгейм в креативе
     */
    public static boolean isAllowCreativeEndgame() {
        return COMMON.allowCreativeEndgame.get();
    }

    /**
     * @return Порог логирования подозрительных ставок
     */
    public static long getLogThreshold() {
        return COMMON.logThreshold.get();
    }

    /**
     * @return Максимальное допустимое соотношение inputValue / targetValue
     */
    public static double getMaxDowngradeRatio() {
        return COMMON.maxDowngradeRatio.get();
    }
}
