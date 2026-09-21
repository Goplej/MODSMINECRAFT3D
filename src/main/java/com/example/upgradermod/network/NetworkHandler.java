package com.example.upgradermod.network;

import com.example.upgradermod.UpgraderMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Обработчик сетевых пакетов мода Upgrader Mod.
 * Использует SimpleChannel протокол "1".
 *
 * @author Popipok
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    /**
     * Сетевой канал мода.
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UpgraderMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId() {
        return packetId++;
    }

    /**
     * Регистрирует все сетевые пакеты мода.
     */
    public static void register() {
        // C->S: Запуск прокрутки рулетки
        CHANNEL.messageBuilder(SpinPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpinPacket::encode)
                .decoder(SpinPacket::decode)
                .consumerMainThread(SpinPacket::handle)
                .add();

        // S->C: Результат прокрутки рулетки
        CHANNEL.messageBuilder(SpinResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpinResultPacket::encode)
                .decoder(SpinResultPacket::decode)
                .consumerMainThread(SpinResultPacket::handle)
                .add();

        // C->S: Установка целевого предмета
        CHANNEL.messageBuilder(SetTargetPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTargetPacket::encode)
                .decoder(SetTargetPacket::decode)
                .consumerMainThread(SetTargetPacket::handle)
                .add();

        // C->S: Установка множителя
        CHANNEL.messageBuilder(SetMultiplierPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetMultiplierPacket::encode)
                .decoder(SetMultiplierPacket::decode)
                .consumerMainThread(SetMultiplierPacket::handle)
                .add();
    }

    /**
     * Отправляет пакет от клиента на сервер.
     *
     * @param msg отправляемое сообщение
     * @param <MSG> тип сообщения
     */
    public static <MSG> void sendToServer(MSG msg) {
        CHANNEL.sendToServer(msg);
    }

    /**
     * Отправляет пакет от сервера конкретному игроку.
     *
     * @param player получатель
     * @param msg    отправляемое сообщение
     * @param <MSG>  тип сообщения
     */
    public static <MSG> void sendToPlayer(ServerPlayer player, MSG msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
