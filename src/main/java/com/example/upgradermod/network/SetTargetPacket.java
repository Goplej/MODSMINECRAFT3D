package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет от клиента к серверу (C->S) для установки выбранного целевого предмета (targetStack).
 *
 * @author Popipok
 */
public class SetTargetPacket {

    private final ItemStack target;

    /**
     * Конструктор пакета установки цели.
     *
     * @param target выбранный предмет цели
     */
    public SetTargetPacket(ItemStack target) {
        this.target = target != null ? target.copy() : ItemStack.EMPTY;
    }

    /**
     * @return предмет цели
     */
    public ItemStack getTarget() {
        return target;
    }

    /**
     * Кодирует пакет в сетевой буфер.
     *
     * @param msg пакет
     * @param buf сетевой буфер
     */
    public static void encode(SetTargetPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.target);
    }

    /**
     * Декодирует пакет из сетевого буфера.
     *
     * @param buf сетевой буфер
     * @return декодированный пакет
     */
    public static SetTargetPacket decode(FriendlyByteBuf buf) {
        return new SetTargetPacket(buf.readItem());
    }

    /**
     * Обрабатывает пакет на стороне сервера.
     *
     * @param msg     пакет
     * @param ctxSupp контекст сетевого события
     */
    public static void handle(SetTargetPacket msg, Supplier<NetworkEvent.Context> ctxSupp) {
        NetworkEvent.Context ctx = ctxSupp.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer player = ctx.getSender();
                if (player != null && player.containerMenu instanceof UpgraderMenu menu) {
                    menu.setTargetStack(msg.target);
                }
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().error("SetTargetPacket error", t);
            }
        });
        ctx.setPacketHandled(true);
    }
}
