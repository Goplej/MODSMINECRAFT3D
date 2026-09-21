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
 * Использует SimpleChannel протокол "3".
 *
 * @author Popipok
 */
public class NetworkHandler {

    // Формат SpinResultPacket изменился (добавлен код причины reject) — версия 3.
    private static final String PROTOCOL_VERSION = "3";
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
        // C->S: Быстрое открытие апгрейдера клавишей 0
        CHANNEL.messageBuilder(OpenUpgraderPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenUpgraderPacket::encode)
                .decoder(OpenUpgraderPacket::decode)
                .consumerMainThread(OpenUpgraderPacket::handle)
                .add();

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

        // S->C: Актуальный шанс открытого меню
        CHANNEL.messageBuilder(UpdateChancePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UpdateChancePacket::encode)
                .decoder(UpdateChancePacket::decode)
                .consumerMainThread(UpdateChancePacket::handle)
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

        // C->S: Запрос количества цели (сервер ограничивает безопасным значением)
        CHANNEL.messageBuilder(SetTargetCountPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTargetCountPacket::encode)
                .decoder(SetTargetCountPacket::decode)
                .consumerMainThread(SetTargetCountPacket::handle)
                .add();

        // C->S: Пресет шанса 30%, 50% или 80% (сервер подбирает количество цели)
        CHANNEL.messageBuilder(ChancePresetPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChancePresetPacket::encode)
                .decoder(ChancePresetPacket::decode)
                .consumerMainThread(ChancePresetPacket::handle)
                .add();

        // S->C: Подтверждённое сервером состояние (цель, количество, множитель)
        CHANNEL.messageBuilder(SyncStatePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncStatePacket::encode)
                .decoder(SyncStatePacket::decode)
                .consumerMainThread(SyncStatePacket::handle)
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
