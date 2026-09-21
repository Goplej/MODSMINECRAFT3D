package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Провайдер ценности предметов на основе EMC из мода ProjectE.
 * Использует рефлексию (Reflection) и безопасную проверку наличия мода в рантайме.
 * Приоритет: 900.
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

        try {
            Class<?> apiClass = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            Method getEMCProxyMethod = apiClass.getMethod("getEMCProxy");
            Object emcProxy = getEMCProxyMethod.invoke(null);

            if (emcProxy != null) {
                Method getValueMethod = emcProxy.getClass().getMethod("getValue", ItemStack.class);
                Object result = getValueMethod.invoke(emcProxy, stack);
                if (result instanceof Number num) {
                    long emc = num.longValue();
                    if (emc > 0) {
                        return (double) emc;
                    }
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
