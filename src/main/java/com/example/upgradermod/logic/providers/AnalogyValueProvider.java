package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Провайдер аналогии для ванильных предметов.
 * Оценивает предметы по сходным материалам (дерево, броня, инструменты, камень, шерсть и т.д.).
 * Приоритет: 300.
 *
 * @author Popipok
 */
public class AnalogyValueProvider implements ValueProvider {

    @Override
    public double getValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return 0.0;
        }

        String path = key.getPath();

        // 1. Древесина и производные (доски, плиты, заборы и т.д.)
        if (path.endsWith("_planks")) {
            return 2.0; // 1 бревно (8) = 4 доски (по 2)
        }
        if (path.endsWith("_slab")) {
            return 1.0;
        }
        if (path.endsWith("_stairs")) {
            return 1.5;
        }
        if (path.endsWith("_fence")) {
            return 2.5;
        }
        if (path.endsWith("_door")) {
            return 4.0;
        }
        if (path.endsWith("_trapdoor")) {
            return 6.0;
        }
        if (path.endsWith("_boat") || path.endsWith("_chest_boat")) {
            return path.endsWith("_chest_boat") ? 26.0 : 10.0;
        }
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem")) {
            return 8.0;
        }

        // 2. Броня (шлем: 5, нагрудник: 8, поножи: 7, ботинки: 4)
        if (path.startsWith("leather_")) {
            return getArmorToolValue(path, "leather", 5.0);
        }
        if (path.startsWith("golden_") || path.startsWith("gold_")) {
            return getArmorToolValue(path, path.startsWith("golden_") ? "golden" : "gold", 50.0);
        }
        if (path.startsWith("chainmail_")) {
            return getArmorToolValue(path, "chainmail", 40.0);
        }
        if (path.startsWith("iron_")) {
            return getArmorToolValue(path, "iron", 30.0);
        }
        if (path.startsWith("diamond_")) {
            return getArmorToolValue(path, "diamond", 400.0);
        }
        if (path.startsWith("netherite_")) {
            // Незеритовые предметы = алмазный аналог + незеритовый слиток (5000)
            if (path.equals("netherite_helmet")) return 400.0 * 5 + 5000.0;
            if (path.equals("netherite_chestplate")) return 400.0 * 8 + 5000.0;
            if (path.equals("netherite_leggings")) return 400.0 * 7 + 5000.0;
            if (path.equals("netherite_boots")) return 400.0 * 4 + 5000.0;
            if (path.equals("netherite_sword")) return 400.0 * 2 + 5000.0;
            if (path.equals("netherite_pickaxe")) return 400.0 * 3 + 5000.0;
            if (path.equals("netherite_axe")) return 400.0 * 3 + 5000.0;
            if (path.equals("netherite_shovel")) return 400.0 * 1 + 5000.0;
            if (path.equals("netherite_hoe")) return 400.0 * 2 + 5000.0;
        }

        // 3. Каменные разновидности
        if (path.contains("granite") || path.contains("diorite") || path.contains("andesite") ||
                path.contains("deepslate") || path.contains("blackstone") || path.contains("tuff") ||
                path.contains("basalt") || path.contains("sandstone")) {
            return 2.0;
        }

        // 4. Окрашенные блоки (шерсть, стекло, терракота, бетон)
        if (path.endsWith("_wool")) {
            return 10.0;
        }
        if (path.endsWith("_carpet")) {
            return 6.6;
        }
        if (path.endsWith("_stained_glass")) {
            return 4.0;
        }
        if (path.endsWith("_stained_glass_pane")) {
            return 1.5;
        }
        if (path.endsWith("_terracotta")) {
            return 8.0;
        }
        if (path.endsWith("_concrete")) {
            return 5.0;
        }
        if (path.endsWith("_concrete_powder")) {
            return 4.0;
        }
        if (path.endsWith("_candle")) {
            return 15.0;
        }
        if (path.endsWith("_bed")) {
            return 35.0;
        }
        if (path.endsWith("_dye")) {
            return 5.0;
        }

        return 0.0;
    }

    private double getArmorToolValue(String path, String materialPrefix, double materialCost) {
        String suffix = path.substring(materialPrefix.length() + 1);
        return switch (suffix) {
            case "helmet" -> materialCost * 5;
            case "chestplate" -> materialCost * 8;
            case "leggings" -> materialCost * 7;
            case "boots" -> materialCost * 4;
            case "sword" -> materialCost * 2 + 1;
            case "pickaxe", "axe" -> materialCost * 3 + 2;
            case "shovel" -> materialCost * 1 + 2;
            case "hoe" -> materialCost * 2 + 2;
            default -> 0.0;
        };
    }

    @Override
    public int getPriority() {
        return 300;
    }

    @Override
    public String getName() {
        return "AnalogyValueProvider";
    }
}
