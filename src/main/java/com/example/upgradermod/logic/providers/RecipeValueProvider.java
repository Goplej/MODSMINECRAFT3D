package com.example.upgradermod.logic.providers;

import com.example.upgradermod.ModConfig;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.*;

/**
 * Провайдер ценности предметов на основе рецептов крафта.
 * Рекурсивно вычисляет стоимость ингредиентов с учётом глубины вложенности и множителей depthMultiplier.
 * Приоритет: 700.
 *
 * @author Popipok
 */
public class RecipeValueProvider implements ValueProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Item, Double> recipeCache = new HashMap<>();
    private final Set<Item> calculating = new HashSet<>();

    @Override
    public double getValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        Item item = stack.getItem();
        if (recipeCache.containsKey(item)) {
            return recipeCache.get(item);
        }

        double val = calculateRecipeValue(item, 0, new HashSet<>());
        if (val > 0) {
            recipeCache.put(item, val);
        }
        return val;
    }

    /**
     * Рекурсивно вычисляет стоимость предмета по его рецептам крафта.
     *
     * @param item    целевой предмет
     * @param depth   текущая глубина рекурсии
     * @param visited множество посещённых предметов для предотвращения циклов
     * @return вычисленная стоимость
     */
    public double calculateRecipeValue(Item item, int depth, Set<Item> visited) {
        if (depth > ModConfig.getRecipeMaxDepth()) {
            return 0.0;
        }

        if (visited.contains(item)) {
            return 0.0;
        }

        RecipeManager recipeManager = getRecipeManager();
        if (recipeManager == null) {
            return 0.0;
        }

        visited.add(item);
        RegistryAccess registryAccess = RegistryAccess.EMPTY;

        double minCost = Double.MAX_VALUE;
        List<? extends Double> depthMultipliers = ModConfig.getRecipeDepthMultipliers();

        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }

            ItemStack result = craftingRecipe.getResultItem(registryAccess);
            if (result.isEmpty() || result.getItem() != item) {
                continue;
            }

            double totalIngredientsCost = 0.0;
            boolean validRecipe = true;

            for (Ingredient ingredient : craftingRecipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }

                ItemStack[] matchingStacks = ingredient.getItems();
                if (matchingStacks.length == 0) {
                    validRecipe = false;
                    break;
                }

                double bestIngredientVal = Double.MAX_VALUE;
                for (ItemStack match : matchingStacks) {
                    double val = ValueCalculator.getSingleItemValue(match);
                    if (val <= 0) {
                        val = calculateRecipeValue(match.getItem(), depth + 1, new HashSet<>(visited));
                    }
                    if (val > 0 && val < bestIngredientVal) {
                        bestIngredientVal = val;
                    }
                }

                if (bestIngredientVal == Double.MAX_VALUE || bestIngredientVal <= 0) {
                    validRecipe = false;
                    break;
                }

                totalIngredientsCost += bestIngredientVal;
            }

            if (validRecipe && totalIngredientsCost > 0) {
                double multiplier = 1.0;
                if (!depthMultipliers.isEmpty()) {
                    int index = Math.min(depth, depthMultipliers.size() - 1);
                    multiplier = depthMultipliers.get(index);
                }

                int count = Math.max(1, result.getCount());
                double costPerItem = (totalIngredientsCost * multiplier) / count;
                if (costPerItem < minCost) {
                    minCost = costPerItem;
                }
            }
        }

        visited.remove(item);

        if (minCost != Double.MAX_VALUE && minCost > 0) {
            return Math.min(ModConfig.getRecipeMaxPrice(), minCost);
        }

        return 0.0;
    }

    /**
     * Очистить кэш рецептов (например, при смене мира или перезагрузке данных).
     */
    public void clearCache() {
        recipeCache.clear();
    }

    /**
     * Безопасное получение менеджера рецептов на стороне сервера или клиента.
     *
     * @return RecipeManager или null
     */
    public static RecipeManager getRecipeManager() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getRecipeManager();
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientHelper.getClientRecipeManager();
        }
        return null;
    }

    private static class ClientHelper {
        @OnlyIn(Dist.CLIENT)
        public static RecipeManager getClientRecipeManager() {
            if (Minecraft.getInstance().level != null) {
                return Minecraft.getInstance().level.getRecipeManager();
            }
            return null;
        }
    }

    @Override
    public int getPriority() {
        return 700;
    }

    @Override
    public String getName() {
        return "RecipeValueProvider";
    }
}
