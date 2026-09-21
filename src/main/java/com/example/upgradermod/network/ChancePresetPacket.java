package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет от клиента к серверу (C->S) с пресетом шанса (30%, 50% или 80%).
 * Сервер сам подбирает количество цели, максимально приближая фактический
 * шанс к выбранному проценту, и возвращает подтверждённое количество
 * через SyncStatePacket.
 *
 * @author Popipok
 */
public final class ChancePresetPacket {

    private final int percent;

    /**
     * Конструктор пакета пресета шанса.
     *
     * @param percent желаемый шанс в процентах (30, 50 или 80)
     */
    public ChancePresetPacket(int percent) {
        this.percent = percent;
    }

    /**
     * @return желаемый шанс в процентах
     */
    public int getPercent() {
        return percent;
    }

    /**
     * Кодирует пакет в сетевой буфер.
     *
     * @param msg пакет
     * @param buf сетевой буфер
     */
    public static void encode(ChancePresetPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.percent);
    }

    /**
     * Декодирует пакет из сетевого буфера.
     *
     * @param buf сетевой буфер
     * @return декодированный пакет
     */
    public static ChancePresetPacket decode(FriendlyByteBuf buf) {
        return new ChancePresetPacket(buf.readVarInt());
    }

    /**
     * Обрабатывает пакет на стороне сервера.
     *
     * @param msg     пакет
     * @param ctxSupp контекст сетевого события
     */
    public static void handle(ChancePresetPacket msg, Supplier<NetworkEvent.Context> ctxSupp) {
        NetworkEvent.Context ctx = ctxSupp.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer player = ctx.getSender();
                if (player != null && player.containerMenu instanceof UpgraderMenu menu) {
                    menu.applyChancePreset(msg.percent);
                }
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().error("ChancePresetPacket error", t);
            }
        });
        ctx.setPacketHandled(true);
    }
}
