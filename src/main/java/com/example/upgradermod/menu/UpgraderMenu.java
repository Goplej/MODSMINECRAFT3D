package com.example.upgradermod.menu;

import com.example.upgradermod.ModConfig;
import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SpinResultPacket;
import com.example.upgradermod.registry.ModMenus;
import com.example.upgradermod.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Контейнер (меню) апгрейдера предметов.
 * Управляет слотом ставки, слотами инвентаря игрока, целевым предметом и логикой прокрутки рулетки.
 *
 * @author Popipok
 */
public class UpgraderMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Container inputContainer = new SimpleContainer(1);
    private final Inventory playerInventory;
    private ItemStack targetStack = ItemStack.EMPTY;
    private int multiplier = 1;

    /**
     * Конструктор меню апгрейдера.
     *
     * @param containerId     идентификатор контейнера
     * @param playerInventory инвентарь игрока
     */
    public UpgraderMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.UPGRADER_MENU.get(), containerId);
        this.playerInventory = playerInventory;

        // Слот 0: входной слот (ставка) x=50, y=40
        this.addSlot(new InputSlot(this.inputContainer, 0, 50, 40));

        // Слоты 1-27: основной инвентарь игрока (3 ряда по 9 слотов)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 48 + col * 18, 138 + row * 18));
            }
        }

        // Слоты 28-36: хотбар игрока (9 слотов)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 48 + col * 18, 196));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.inputContainer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            // Если перемещаем из слота ставки (слот 0)
            if (index == 0) {
                if (!this.moveItemStackTo(stackInSlot, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, itemstack);
            } else {
                // Если перемещаем из инвентаря игрока в слот ставки
                if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }

        return itemstack;
    }

    /**
     * @return текущий целевой предмет (targetStack)
     */
    public ItemStack getTargetStack() {
        return targetStack;
    }

    /**
     * Устанавливает целевой предмет (targetStack).
     *
     * @param targetStack предмет цели
     */
    public void setTargetStack(ItemStack targetStack) {
        if (targetStack == null || targetStack.isEmpty() || ModConfig.isBlacklisted(targetStack)) {
            this.targetStack = ItemStack.EMPTY;
        } else {
            this.targetStack = targetStack.copy();
            this.targetStack.setCount(1);
        }
        broadcastChanges();
    }

    /**
     * @return текущий множитель ставки (1, 2, 4, 8 или 10)
     */
    public int getMultiplier() {
        return multiplier;
    }

    /**
     * Устанавливает множитель ставки. Допустимые значения: 1, 2, 4, 8, 10.
     *
     * @param multiplier множитель
     */
    public void setMultiplier(int multiplier) {
        if (multiplier == 1 || multiplier == 2 || multiplier == 4 || multiplier == 8 || multiplier == 10) {
            this.multiplier = multiplier;
            broadcastChanges();
        }
    }

    /**
     * @return предмет в слоте ставки
     */
    public ItemStack getInputStack() {
        return this.inputContainer.getItem(0);
    }

    /**
     * Запуск прокрутки рулетки апгрейда.
     * Выполняет проверку всех 6 правил безопасности, рассчитывает шанс,
     * определяет исход и начисляет награду либо сжигает ставку.
     *
     * @param player игрок, выполняющий апгрейд
     */
    public void doSpin(ServerPlayer player) {
        ItemStack input = getInputStack();

        // Правило 1: input.isEmpty() || target.isEmpty() → cancel
        if (input.isEmpty() || targetStack.isEmpty()) {
            return;
        }

        // Чёрный список проверяется на сервере повторно, даже если клиент изменён.
        if (ModConfig.isBlacklisted(input) || ModConfig.isBlacklisted(targetStack)) {
            return;
        }

        // Вычисление стоимости ставки и цели
        double inputValue = ValueCalculator.getItemStackValue(input);
        double targetValue = ValueCalculator.getItemStackValue(targetStack);

        // Правило 2: inputValue <= 0 || targetValue <= 0 → cancel
        if (inputValue <= 0.0 || targetValue <= 0.0) {
            return;
        }

        // Правило 3: ItemStack.isSameItemSameTags(input, target) && count equal → cancel
        if (ItemStack.isSameItemSameTags(input, targetStack) && input.getCount() == targetStack.getCount()) {
            return;
        }

        // Правило 4: player.isCreative() && targetValue >= 1_000_000 → cancel
        if (player.isCreative() && targetValue >= 1000000.0 && !ModConfig.isAllowCreativeEndgame()) {
            return;
        }

        // Правило 5: inputValue > targetValue * maxDowngradeRatio → cancel
        if (inputValue > targetValue * ModConfig.getMaxDowngradeRatio()) {
            return;
        }

        // Правило 6: inputValue > logThreshold → log в logs/upgradermod_suspicious.log
        if (inputValue > ModConfig.getLogThreshold()) {
            logSuspiciousAction(player, input, inputValue, targetStack, targetValue);
        }

        // Проверка комиссии/налога (если включён)
        if (ModConfig.isTaxEnabled() && !player.isCreative()) {
            String taxItemId = ModConfig.getTaxItem();
            int taxCount = ModConfig.getTaxAmount();
            Item taxItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(taxItemId));
            if (taxItem != null) {
                int availableTax = player.getInventory().countItem(taxItem);
                if (availableTax < taxCount) {
                    return; // Недостаточно предметов для оплаты налога
                }
                player.getInventory().clearOrCountMatchingItems(s -> s.is(taxItem), taxCount, player.inventoryMenu.getCraftSlots());
            }
        }

        // Расчёт шанса успеха
        double chance = ChanceCalculator.calculateChance(inputValue, targetValue, this.multiplier);

        // Получение генератора случайных чисел с сервера
        RandomSource random = player.serverLevel().getRandom();
        double roll = random.nextDouble() * 100.0;
        boolean success = roll < chance;

        // Расчёт угла остановки стрелки (в градусах от 0 до 360)
        float winSector = (float) ((chance / 100.0) * 360.0);
        float rollAngle;
        if (success) {
            rollAngle = random.nextFloat() * Math.max(1.0f, winSector);
        } else {
            rollAngle = winSector + random.nextFloat() * Math.max(1.0f, 360.0f - winSector);
        }

        // Ставка сгорает всегда (при успехе игрок получает цель, при провале - теряет ставку)
        this.inputContainer.setItem(0, ItemStack.EMPTY);

        if (success) {
            ItemStack reward = targetStack.copy();
            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
            }
        }

        player.playNotifySound(
                success ? ModSounds.SPIN_SUCCESS.get() : ModSounds.SPIN_FAILURE.get(),
                SoundSource.PLAYERS,
                0.85F,
                success ? 1.0F : 0.8F);

        this.broadcastChanges();
        player.getInventory().setChanged();

        // Отправка клиенту пакета с результатом
        NetworkHandler.sendToPlayer(player, new SpinResultPacket(success, chance, rollAngle));
    }

    private void logSuspiciousAction(ServerPlayer player, ItemStack input, double inputVal, ItemStack target, double targetVal) {
        try {
            File logFile = new File("logs/upgradermod_suspicious.log");
            if (!logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                writer.write(String.format(Locale.ROOT, "[%s] Подозрительная ставка игрока %s (UUID: %s): Ставка '%s' x%d (ценность: %.0f), Цель '%s' x%d (ценность: %.0f)%n",
                        timestamp,
                        player.getName().getString(),
                        player.getUUID(),
                        ForgeRegistries.ITEMS.getKey(input.getItem()),
                        input.getCount(),
                        inputVal,
                        ForgeRegistries.ITEMS.getKey(target.getItem()),
                        target.getCount(),
                        targetVal));
            }
        } catch (IOException e) {
            LOGGER.error("Не удалось записать в logs/upgradermod_suspicious.log: {}", e.getMessage(), e);
        }
    }

    /** Слот ставки, не принимающий предметы из чёрного списка. */
    private static final class InputSlot extends Slot {

        private InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !ModConfig.isBlacklisted(stack);
        }
    }
}
