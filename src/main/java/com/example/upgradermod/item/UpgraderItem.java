package com.example.upgradermod.item;

import com.example.upgradermod.menu.UpgraderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Предмет апгрейдера.
 * При использовании правой кнопкой мыши открывает графический интерфейс рулетки.
 *
 * @author Popipok
 */
public class UpgraderItem extends Item {

    /**
     * Конструктор предмета апгрейдера.
     * Задаёт максимальный размер стака 1 и эпическую редкость.
     */
    public UpgraderItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    /**
     * Обработка нажатия ПКМ с предметом в руке.
     * Открывает интерфейс рулетки на стороне сервера.
     *
     * @param level  мир
     * @param player игрок
     * @param hand   рука, в которой находится предмет
     * @return результат взаимодействия
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(
                    serverPlayer,
                    new SimpleMenuProvider(
                            (containerId, playerInventory, p) -> new UpgraderMenu(containerId, playerInventory),
                            Component.translatable("gui.upgradermod.title")
                    )
            );
        }

        return InteractionResultHolder.sidedSuccess(heldItem, level.isClientSide);
    }
}
