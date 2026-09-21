package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Провайдер переопределения ценностей предметов из конфигурационного файла overrides.json.
 * Имеет наивысший приоритет (1000) и предназначен для ручной настройки эндгейм-предметов.
 *
 * @author Popipok
 */
public class OverrideValueProvider implements ValueProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Double> overrides = new HashMap<>();

    /**
     * Конструктор провайдера переопределений.
     * Загружает цены из config/upgradermod/overrides.json.
     */
    public OverrideValueProvider() {
        loadConfig();
    }

    /**
     * Загружает файл переопределений config/upgradermod/overrides.json.
     */
    public void loadConfig() {
        File configFile = FMLPaths.CONFIGDIR.get().resolve("upgradermod/overrides.json").toFile();
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
                overrides.clear();
                overrides.putAll(loaded);
            }
        } catch (IOException e) {
            LOGGER.error("Ошибка при чтении overrides.json: {}", e.getMessage(), e);
        }
    }

    private void initDefaults() {
        overrides.put("avaritia:crystal_matrix_ingot", 2000000.0);
        overrides.put("avaritia:infinity_ingot", 25000000.0);
        overrides.put("avaritia:infinity_catalyst", 100000000.0);
        overrides.put("avaritia:cosmic_neutronium_ingot", 500000000.0);
        overrides.put("avaritia:infinity_sword", 2560000000.0);
    }

    private void saveConfig(File configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(overrides, writer);
        } catch (IOException e) {
            LOGGER.error("Ошибка при записи overrides.json по умолчанию: {}", e.getMessage(), e);
        }
    }

    @Override
    public double getValue(ItemStack stack) {
        try {
            if (stack.isEmpty()) {
                return 0.0;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key != null) {
                Double val = overrides.get(key.toString());
                if (val != null && val > 0) {
                    return val;
                }
            }

            return 0.0;
        } catch (Throwable t) {
            LOGGER.debug("OverrideValueProvider error: {}", t.getMessage());
            return 0.0;
        }
    }

    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public String getName() {
        return "OverrideValueProvider";
    }
}
