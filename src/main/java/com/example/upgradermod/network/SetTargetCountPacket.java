package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет от клиента к серверу (C->S) с запрошенным количеством цели.
 * Значение не считается доверенным: сервер ограничивает его диапазоном
 * 1..UpgraderMenu.MAX_TARGET_COUNT и возвращает подтверждённое количество
 * через SyncStatePacket.
 *
 * @author Popipok
 */
public final class SetTargetCountPacket {

    private final int count;

    /**
     * Конструктор пакета количества цели.
     *
     * @param count запрошенное количество цели
     */
    public SetTargetCountPacket(int count) {
        this.count = count;
    }

    /**
     * @return запрошенное количество цели
     */
    public int getCount() {
        return count;
    }

    /**
     * Кодирует пакет в сетевой буфер.
     *
     * @param msg пакет
     * @param buf сетевой буфер
     */
    public static void encode(SetTargetCountPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.count);
    }

    /**
     * Декодирует пакет из сетевого буфера.
     *
     * @param buf сетевой буфер
     * @return декодированный пакет
     */
    public static SetTargetCountPacket decode(FriendlyByteBuf buf) {
        return new SetTargetCountPacket(buf.readVarInt());
    }

    /**
     * Обрабатывает пакет на стороне сервера.
     *
     * @param msg     пакет
     * @param ctxSupp контекст сетевого события
     */
    public static void handle(SetTargetCountPacket msg, Supplier<NetworkEvent.Context> ctxSupp) {
        NetworkEvent.Context ctx = ctxSupp.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.containerMenu instanceof UpgraderMenu menu) {
                menu.setTargetCount(msg.count);
            }
        });
        ctx.setPacketHandled(true);
    }
}
