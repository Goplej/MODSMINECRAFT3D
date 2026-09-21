package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет от сервера к клиенту (S->C) с подтверждённым состоянием меню:
 * целевой предмет, количество цели и множитель награды.
 * Клиент обязан использовать эти значения вместо собственных расчётов.
 *
 * @author Popipok
 */
public final class SyncStatePacket {

    private final ItemStack target;
    private final int targetCount;
    private final int multiplier;

    /**
     * Конструктор пакета синхронизации состояния.
     *
     * @param target      подтверждённая цель
     * @param targetCount подтверждённое количество цели
     * @param multiplier  подтверждённый множитель награды
     */
    public SyncStatePacket(ItemStack target, int targetCount, int multiplier) {
        this.target = target != null ? target.copy() : ItemStack.EMPTY;
        this.targetCount = targetCount;
        this.multiplier = multiplier;
    }

    /**
     * Кодирует пакет в сетевой буфер.
     *
     * @param msg пакет
     * @param buf сетевой буфер
     */
    public static void encode(SyncStatePacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.target);
        buf.writeVarInt(msg.targetCount);
        buf.writeVarInt(msg.multiplier);
    }

    /**
     * Декодирует пакет из сетевого буфера.
     *
     * @param buf сетевой буфер
     * @return декодированный пакет
     */
    public static SyncStatePacket decode(FriendlyByteBuf buf) {
        return new SyncStatePacket(buf.readItem(), buf.readVarInt(), buf.readVarInt());
    }

    /**
     * Обрабатывает пакет на стороне клиента.
     *
     * @param msg     пакет
     * @param ctxSupp контекст сетевого события
     */
    public static void handle(SyncStatePacket msg, Supplier<NetworkEvent.Context> ctxSupp) {
        NetworkEvent.Context ctx = ctxSupp.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null
                    && minecraft.player.containerMenu instanceof UpgraderMenu menu) {
                menu.applyServerSync(msg.target, msg.targetCount, msg.multiplier);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
