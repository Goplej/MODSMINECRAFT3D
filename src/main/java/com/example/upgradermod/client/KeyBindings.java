package com.example.upgradermod.client;

import com.example.upgradermod.UpgraderMod;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.OpenUpgraderPacket;
import com.example.upgradermod.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши управления мода Upgrader Mod.
 * Клавиша '0' открывает UpgraderScreen, если у игрока в инвентаре есть upgradermod:upgrader.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = UpgraderMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyBindings {

    /**
     * Привязка клавиши '0' для быстрого открытия интерфейса апгрейдера.
     */
    public static final KeyMapping OPEN_UPGRADER_KEY = new KeyMapping(
            "key.upgradermod.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.categories.upgradermod"
    );

    /**
     * Обработка нажатия клавиш на клиенте.
     * Проверяет нажатие клавиши '0' и наличие апгрейдера в инвентаре игрока.
     *
     * @param event событие ввода с клавиатуры
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (OPEN_UPGRADER_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                boolean hasUpgrader = false;
                for (ItemStack stack : mc.player.getInventory().items) {
                    if (!stack.isEmpty() && stack.is(ModItems.UPGRADER.get())) {
                        hasUpgrader = true;
                        break;
                    }
                }

                if (hasUpgrader) {
                    // Сервер создаёт настоящий контейнер и проверяет наличие предмета повторно.
                    NetworkHandler.sendToServer(new OpenUpgraderPacket());
                }
            }
        }
    }
}
