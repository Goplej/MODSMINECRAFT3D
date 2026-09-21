package com.example.upgradermod.logic;

import com.example.upgradermod.logic.providers.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Реестр всех поставщиков ценностей предметов (ValueProvider).
 * Автоматически сортирует провайдеры в порядке убывания их приоритета.
 *
 * @author Popipok
 */
public class ValueProviderRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ValueProvider> PROVIDERS = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * Инициализирует стандартные провайдеры мода в порядке приоритета.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }

        register(new OverrideValueProvider());     // 1000
        register(new ManualJsonValueProvider());  // 800
        register(new StatsValueProvider());       // 600: урон + прочность + скорость + броня
        register(new TagValueProvider());         // 500
        register(new AnalogyValueProvider());     // 300
        register(new HeuristicValueProvider());   // 100

        initialized = true;
        LOGGER.info("ValueProviderRegistry успешно инициализирован с {} провайдерами", PROVIDERS.size());
    }

    /**
     * Регистрирует новый провайдер ценностей и пересортировывает список.
     *
     * @param provider регистрируемый провайдер
     */
    public static synchronized void register(ValueProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
            PROVIDERS.sort(Comparator.comparingInt(ValueProvider::getPriority).reversed());
        }
    }

    /**
     * Возвращает неизменяемый список зарегистрированных провайдеров, отсортированных по приоритету.
     *
     * @return список провайдеров
     */
    public static List<ValueProvider> getProviders() {
        if (!initialized) {
            init();
        }
        return Collections.unmodifiableList(PROVIDERS);
    }
}
