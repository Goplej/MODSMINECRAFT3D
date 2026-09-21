package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Пакет от клиента к серверу (C->S) для запуска прокрутки рулетки.
 *
 * @author Popipok
 */
public class SpinPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Конструктор пакета прокрутки.
     */
    public SpinPacket() {
    }

    /**
     * Кодирует пакет в сетевой буфер.
     *
     * @param msg пакет
     * @param buf сетевой буфер
     */
    public static void encode(SpinPacket msg, FriendlyByteBuf buf) {
        // Данных нет, пустой пакет
    }

    /**
     * Декодирует пакет из сетевого буфера.
     *
     * @param buf сетевой буфер
     * @return новый экземпляр SpinPacket
     */
    public static SpinPacket decode(FriendlyByteBuf buf) {
        return new SpinPacket();
    }

    /**
     * Обрабатывает пакет на стороне сервера.
     *
     * @param msg     пакет
     * @param ctxSupp контекст сетевого события
     */
    public static void handle(SpinPacket msg, Supplier<NetworkEvent.Context> ctxSupp) {
        NetworkEvent.Context ctx = ctxSupp.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer player = ctx.getSender();
                if (player != null && player.containerMenu instanceof UpgraderMenu menu) {
                    menu.doSpin(player);
                }
            } catch (Throwable t) {
                LOGGER.error("SpinPacket handle error", t);
            }
        });
        ctx.setPacketHandled(true);
    }
}
