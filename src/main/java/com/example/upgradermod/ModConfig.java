package com.example.upgradermod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Конфигурация мода Upgrader Mod.
 * Содержит параметры шансов, расчёта ценности предметов, рецептов и защиты от абуза.
 *
 * @author Popipok
 */
public class ModConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec SPEC;

    /**
     * Постоянный чёрный список, который нельзя отключить через конфигурацию.
     * Сюда входят bedrock, barrier, сам апгрейдер и все служебные/читерские предметы.
     * Они не появляются в каталоге, не принимаются в input slot и отклоняются
     * сервером как ставка или цель даже при изменённом клиенте.
     */
    private static final Set<String> PERMANENT_BLACKLIST = Set.of(
            "minecraft:air",
            "minecraft:bedrock",
            "minecraft:barrier",
            "minecraft:light",
            "minecraft:structure_void",
            "minecraft:structure_block",
            "minecraft:jigsaw",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:command_block_minecart",
            "minecraft:debug_stick",
            "minecraft:knowledge_book",
            "upgradermod:upgrader"
    );

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

        public final ForgeConfigSpec.LongValue recipeMaxPrice;

        public final ForgeConfigSpec.BooleanValue taxEnabled;
        public final ForgeConfigSpec.ConfigValue<String> taxItem;
        public final ForgeConfigSpec.IntValue taxAmount;

        public final ForgeConfigSpec.BooleanValue allowCreativeEndgame;
        public final ForgeConfigSpec.LongValue logThreshold;
        public final ForgeConfigSpec.DoubleValue maxDowngradeRatio;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklistItems;

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

            builder.comment("Ограничение максимальной ценности предмета (защита от абузов)").push("recipes");
            recipeMaxPrice = builder.comment("Максимальная допустимая цена предмета")
                    .defineInRange("recipeMaxPrice", 10000000000000L, 1L, Long.MAX_VALUE);
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
            blacklistItems = builder.comment(
                            "Предметы, запрещённые в ставке, цели и каталоге (полные registry ID через запятую)")
                    .defineListAllowEmpty(List.of("blacklistItems"),
                            Arrays.asList(
                                    "minecraft:bedrock",
                                    "minecraft:barrier",
                                    "minecraft:command_block",
                                    "minecraft:chain_command_block",
                                    "minecraft:repeating_command_block",
                                    "minecraft:structure_block",
                                    "minecraft:structure_void",
                                    "minecraft:jigsaw",
                                    "minecraft:debug_stick",
                                    "minecraft:light"),
                            value -> value instanceof String
                                    && ResourceLocation.tryParse((String) value) != null);
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
     * @return Максимальная допустимая цена предмета
     */
    public static long getRecipeMaxPrice() {
        return COMMON.recipeMaxPrice.get();
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

    /**
     * Проверяет, запрещён ли предмет настройкой blacklistItems.
     *
     * @param stack предмет для проверки
     * @return true, если предмет отсутствует в разрешённом пуле апгрейдера
     */
    public static boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && isBlacklisted(id);
    }

    /**
     * Проверяет registry ID по списку из upgradermod-common.toml.
     * Сравнение нечувствительно к регистру и пробелам вокруг ID.
     *
     * @param id registry ID предмета
     * @return true, если ID есть в чёрном списке
     */
    public static boolean isBlacklisted(ResourceLocation id) {
        if (id == null) {
            return false;
        }

        String idString = id.toString().toLowerCase(Locale.ROOT);

        // Постоянный blacklist проверяется первым и не зависит от конфигурации.
        if (PERMANENT_BLACKLIST.contains(idString)) {
            return true;
        }

        return COMMON.blacklistItems.get().stream()
                .filter(value -> value != null)
                .map(value -> value.trim())
                .anyMatch(value -> value.equalsIgnoreCase(idString));
    }

    /**
     * @return список ID предметов, запрещённых для рулетки
     */
    public static List<? extends String> getBlacklistItems() {
        return COMMON.blacklistItems.get();
    }
}
