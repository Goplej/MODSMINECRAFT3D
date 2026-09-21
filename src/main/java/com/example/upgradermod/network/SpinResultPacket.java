package com.example.upgradermod.network;

import com.example.upgradermod.client.UpgraderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет от сервера к клиенту (S->C) с результатом прокрутки рулетки.
 * Содержит флаг успеха, процент шанса и угол остановки стрелки.
 *
 * @author Popipok
 */
public class SpinResultPacket {

    private final int containerId;
    private final boolean rejected;
    private final boolean success;
    private final double chance;
    private final float rollAngle;

    /**
     * Конструктор пакета результата спина.
     *
     * @param success   успешен ли апгрейд
     * @param chance    рассчитанный шанс в процентах
     * @param rollAngle угол остановки стрелки рулетки (в градусах)
     */
    public SpinResultPacket(int containerId, boolean rejected, boolean success, double chance, float rollAngle) {
        this.containerId = containerId;
        this.rejected = rejected;
        this.success = success;
        this.chance = chance;
        this.rollAngle = rollAngle;
    }

    /**
     * @return успешен ли апгрейд
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return рассчитанный шанс
     */
    public double getChance() {
        return chance;
    }

    /**
     * @return угол остановки стрелки
     */
    public float getRollAngle() {
        return rollAngle;
    }

    /**
     * Кодирует пакет в сетевой буфер.
     *
     * @param msg пакет
     * @param buf сетевой буфер
     */
    public static void encode(SpinResultPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.rejected);
        buf.writeBoolean(msg.success);
        buf.writeDouble(msg.chance);
        buf.writeFloat(msg.rollAngle);
    }

    /**
     * Декодирует пакет из сетевого буфера.
     *
     * @param buf сетевой буфер
     * @return декодированный пакет
     */
    public static SpinResultPacket decode(FriendlyByteBuf buf) {
        return new SpinResultPacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readFloat());
    }

    /**
     * Обрабатывает пакет на стороне клиента.
     *
     * @param msg     пакет
     * @param ctxSupp контекст сетевого события
     */
    public static void handle(SpinResultPacket msg, Supplier<NetworkEvent.Context> ctxSupp) {
        NetworkEvent.Context ctx = ctxSupp.get();
        ctx.enqueueWork(() -> {
            try {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    try {
                        if (Minecraft.getInstance().screen instanceof UpgraderScreen upgraderScreen
                                && upgraderScreen.getMenu().containerId == msg.containerId) {
                            upgraderScreen.onSpinResult(msg.rejected, msg.success, msg.chance, msg.rollAngle);
                        }
                    } catch (Throwable t) {
                        com.mojang.logging.LogUtils.getLogger().error("SpinResultPacket client error", t);
                    }
                });
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().error("SpinResultPacket error", t);
            }
        });
        ctx.setPacketHandled(true);
    }
}
