package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет от клиента к серверу (C->S) для изменения множителя прокрутки (x1, x2, x4, x8).
 *
 * @author Popipok
 */
public class SetMultiplierPacket {

    private final int multiplier;

    /**
     * Конструктор пакета установки множителя.
     *
     * @param multiplier множитель (1, 2, 4 или 8)
     */
    public SetMultiplierPacket(int multiplier) {
        this.multiplier = multiplier;
    }

    /**
     * @return установленный множитель
     */
    public int getMultiplier() {
        return multiplier;
    }

    /**
     * Кодирует пакет в сетевой буфер.
     *
     * @param msg пакет
     * @param buf сетевой буфер
     */
    public static void encode(SetMultiplierPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.multiplier);
    }

    /**
     * Декодирует пакет из сетевого буфера.
     *
     * @param buf сетевой буфер
     * @return декодированный пакет
     */
    public static SetMultiplierPacket decode(FriendlyByteBuf buf) {
        return new SetMultiplierPacket(buf.readInt());
    }

    /**
     * Обрабатывает пакет на стороне сервера.
     *
     * @param msg     пакет
     * @param ctxSupp контекст сетевого события
     */
    public static void handle(SetMultiplierPacket msg, Supplier<NetworkEvent.Context> ctxSupp) {
        NetworkEvent.Context ctx = ctxSupp.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer player = ctx.getSender();
                if (player != null && player.containerMenu instanceof UpgraderMenu menu) {
                    menu.setMultiplier(msg.multiplier);
                }
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().error("SetMultiplierPacket error", t);
            }
        });
        ctx.setPacketHandled(true);
    }
}
