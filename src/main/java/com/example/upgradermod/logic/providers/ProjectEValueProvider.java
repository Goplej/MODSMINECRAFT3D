package com.example.upgradermod.logic.providers;

import com.example.upgradermod.ModConfig;
import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Провайдер ценности предметов на основе EMC из мода ProjectE.
 * По умолчанию ВЫКЛЮЧЕН: ценность предметов берётся из собственных источников
 * мода (overrides.json, values.json, рецепты, теги, аналоги, эвристика).
 * Включается опцией useProjectEValues в конфиге.
 * Возвращает удельную EMC одного предмета, чтобы подсчёт стака оставался
 * простым: ценность × количество (без двойного умножения на размер стака).
 * Использует рефлексию и безопасную проверку наличия мода в рантайме.
 * Приоритет: 900 (участвует только когда включён в конфиге).
 *
 * @author Popipok
 */
public class ProjectEValueProvider implements ValueProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public double getValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        // Проверяем загружен ли мод ProjectE в текущей сборке
        if (!ModList.get().isLoaded("projecte")) {
            return 0.0;
        }

        // По умолчанию ценности ProjectE не используются вообще:
        // работают собственные ценности мода.
        if (!ModConfig.isUseProjectEValues()) {
            return 0.0;
        }

        try {
            Class<?> apiClass = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            Method getEMCProxyMethod = apiClass.getMethod("getEMCProxy");
            Object emcProxy = getEMCProxyMethod.invoke(null);

            if (emcProxy == null) {
                return 0.0;
            }

            // Предпочитаем удельную EMC одного предмета, а не всего стака.
            try {
                Method getEmcValueMethod = emcProxy.getClass().getMethod("getEmcValue", ItemStack.class);
                Object result = getEmcValueMethod.invoke(emcProxy, stack);
                if (result instanceof Number num) {
                    long emc = num.longValue();
                    if (emc > 0) {
                        return (double) emc;
                    }
                }
            } catch (NoSuchMethodException e) {
                // Старый API без getEmcValue: ниже делим стоимость стака на количество.
                LOGGER.debug("ProjectE getEmcValue not available, falling back to getValue");
            }

            // Фолбэк: getValue(ItemStack) возвращает EMC всего стака — делим на количество.
            Method getValueMethod = emcProxy.getClass().getMethod("getValue", ItemStack.class);
            Object result = getValueMethod.invoke(emcProxy, stack);
            if (result instanceof Number num) {
                long emc = num.longValue();
                int count = stack.getCount();
                if (emc > 0 && count > 0) {
                    return (double) (emc / count);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            LOGGER.debug("ProjectE API not found or incompatible: {}", e.getMessage());
        } catch (Throwable t) {
            LOGGER.debug("ProjectE reflection error: {}", t.getMessage());
        }

        return 0.0;
    }

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    public String getName() {
        return "ProjectEValueProvider";
    }
}
