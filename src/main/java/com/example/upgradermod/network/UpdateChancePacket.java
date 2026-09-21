package com.example.upgradermod.network;

import com.example.upgradermod.client.UpgraderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Серверный пакет с актуальным шансом для открытого меню апгрейдера.
 * Значение рассчитывается только на сервере и используется GUI для отображения.
 *
 * @author Popipok
 */
public final class UpdateChancePacket {

    private final double chance;

    public UpdateChancePacket(double chance) {
        this.chance = chance;
    }

    public static void encode(UpdateChancePacket message, FriendlyByteBuf buffer) {
        buffer.writeDouble(message.chance);
    }

    public static UpdateChancePacket decode(FriendlyByteBuf buffer) {
        return new UpdateChancePacket(buffer.readDouble());
    }

    public static void handle(UpdateChancePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                if (Minecraft.getInstance().screen instanceof UpgraderScreen screen) {
                    screen.onChanceUpdate(message.chance);
                }
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().error("UpdateChancePacket error", t);
            }
        }));
        context.setPacketHandled(true);
    }
}
