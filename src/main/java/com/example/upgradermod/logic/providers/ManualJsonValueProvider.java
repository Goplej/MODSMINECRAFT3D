package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueProvider;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Провайдер базовых ценностей, считывающий значения из data/upgradermod/values.json.
 * Содержит базовые цены для более чем 60 ванильных предметов (алмазы, незерит, элитры и т.д.).
 * Приоритет: 800.
 *
 * @author Popipok
 */
public class ManualJsonValueProvider implements ValueProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private final Map<String, Double> baseValues = new HashMap<>();

    /**
     * Конструктор провайдера ручных цен.
     * Загружает values.json из ресурсов мода.
     */
    public ManualJsonValueProvider() {
        loadValues();
    }

    /**
     * Загружает значения цен из data/upgradermod/values.json.
     */
    public void loadValues() {
        try (InputStream stream = getClass().getResourceAsStream("/data/upgradermod/values.json")) {
            if (stream == null) {
                LOGGER.warn("Ресурс values.json не найден в classpath: /data/upgradermod/values.json");
                return;
            }

            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, Double>>() {}.getType();
                Map<String, Double> map = GSON.fromJson(reader, type);
                if (map != null) {
                    baseValues.clear();
                    baseValues.putAll(map);
                    LOGGER.info("Успешно загружено {} базовых цен из values.json", baseValues.size());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Ошибка ввода-вывода при чтении values.json: {}", e.getMessage(), e);
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
                Double val = baseValues.get(key.toString());
                if (val != null && val > 0) {
                    return val;
                }
            }

            return 0.0;
        } catch (Throwable t) {
            LOGGER.debug("ManualJsonValueProvider error: {}", t.getMessage());
            return 0.0;
        }
    }

    @Override
    public int getPriority() {
        return 800;
    }

    @Override
    public String getName() {
        return "ManualJsonValueProvider";
    }
}
