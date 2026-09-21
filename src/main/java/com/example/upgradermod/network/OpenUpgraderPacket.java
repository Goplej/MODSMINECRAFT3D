package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * Пакет быстрого открытия апгрейдера клавишей 0.
 * Меню открывается сервером, чтобы горячая клавиша не создавала локальный
 * контейнер, рассинхронизированный с серверным инвентарём.
 *
 * @author Popipok
 */
public final class OpenUpgraderPacket {

    public OpenUpgraderPacket() {
    }

    public static void encode(OpenUpgraderPacket msg, FriendlyByteBuf buf) {
        // Пакет не содержит данных.
    }

    public static OpenUpgraderPacket decode(FriendlyByteBuf buf) {
        return new OpenUpgraderPacket();
    }

    public static void handle(OpenUpgraderPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            boolean hasUpgrader = player.getInventory().items.stream()
                    .anyMatch(stack -> stack.is(ModItems.UPGRADER.get()));
            if (!hasUpgrader) {
                return;
            }

            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new UpgraderMenu(containerId, inventory),
                    Component.translatable("gui.upgradermod.title")
            ));
        });
        ctx.setPacketHandled(true);
    }
}
