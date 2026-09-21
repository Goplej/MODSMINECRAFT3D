package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Провайдер ценности на основе тегов предметов (config/upgradermod/tags.json).
 * Позволяет назначать цены группам предметов (например forge:ingots/iron).
 * Приоритет: 500.
 *
 * @author Popipok
 */
public class TagValueProvider implements ValueProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Double> tagValues = new HashMap<>();

    /**
     * Конструктор провайдера тегов.
     * Загружает конфигурационный файл config/upgradermod/tags.json.
     */
    public TagValueProvider() {
        loadConfig();
    }

    /**
     * Загружает файл тегов config/upgradermod/tags.json.
     */
    public void loadConfig() {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("upgradermod/tags.json").toFile();
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            initDefaults();
            saveConfig(configFile);
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            Type mapType = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> loaded = GSON.fromJson(reader, mapType);
            if (loaded != null) {
                tagValues.clear();
                boolean removedProjectE = false;
                for (Map.Entry<String, Double> entry : loaded.entrySet()) {
                    String key = entry.getKey();
                    if (key != null && key.startsWith("projecte:")) {
                        // На всякий случай вычищаем и теги ProjectE — ценности ProjectE не нужны.
                        LOGGER.info("Удаляю цену ProjectE из tags.json: {} = {}", key, entry.getValue());
                        removedProjectE = true;
                        continue;
                    }
                    tagValues.put(key, entry.getValue());
                }
                if (removedProjectE) {
                    saveConfig(configFile);
                    LOGGER.info("tags.json очищен от цен ProjectE");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Ошибка при чтении tags.json: {}", e.getMessage(), e);
        }
    }

    private void initDefaults() {
        tagValues.put("forge:ingots/iron", 30.0);
        tagValues.put("forge:ingots/gold", 50.0);
        tagValues.put("forge:gems/diamond", 400.0);
        tagValues.put("forge:storage_blocks/netherite", 45000.0);
    }

    private void saveConfig(File configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(tagValues, writer);
        } catch (IOException e) {
            LOGGER.error("Ошибка при сохранении tags.json по умолчанию: {}", e.getMessage(), e);
        }
    }

    @Override
    public double getValue(ItemStack stack) {
        try {
            if (stack.isEmpty()) {
                return 0.0;
            }

            double maxVal = 0.0;
            for (Map.Entry<String, Double> entry : tagValues.entrySet()) {
                ResourceLocation tagLoc = ResourceLocation.tryParse(entry.getKey());
                if (tagLoc != null) {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
                    if (stack.is(tagKey)) {
                        double val = entry.getValue();
                        if (val > maxVal) {
                            maxVal = val;
                        }
                    }
                }
            }

            return maxVal;
        } catch (Throwable t) {
            LOGGER.debug("TagValueProvider error: {}", t.getMessage());
            return 0.0;
        }
    }

    @Override
    public int getPriority() {
        return 500;
    }

    @Override
    public String getName() {
        return "TagValueProvider";
    }
}
