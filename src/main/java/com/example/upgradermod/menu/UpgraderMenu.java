package com.example.upgradermod.menu;

import com.example.upgradermod.ModConfig;
import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SpinResultPacket;
import com.example.upgradermod.network.SyncStatePacket;
import com.example.upgradermod.network.UpdateChancePacket;
import com.example.upgradermod.registry.ModItems;
import com.example.upgradermod.registry.ModMenus;
import com.example.upgradermod.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 * Управляет слотом ставки, слотами инвентаря игрока, целевым предметом,
 * количеством цели, множителем награды и логикой прокрутки рулетки.
 * Все расчёты шанса и результата выполняются только на сервере; клиентские
 * значения считаются недоверенными и повторно проверяются.
 *
 * @author Popipok
 */
public class UpgraderMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Максимальное безопасное количество целевого предмета, которое разрешает сервер. */
    public static final int MAX_TARGET_COUNT = 256;

    private final Container inputContainer = new SimpleContainer(1);
    private final Inventory playerInventory;

    /** Целевой предмет, всегда хранится в количестве 1; количество задаётся targetCount. */
    private ItemStack targetStack = ItemStack.EMPTY;
    /** Серверное количество цели (1..MAX_TARGET_COUNT), входит в стоимость и шанс. */
    private int targetCount = 1;
    /** Серверный множитель награды (1, 2, 4, 8 или 10). */
    private int multiplier = 1;

    // Accessed only on the logical server thread, including closed menus.
    private static final Map<UUID, UpgraderMenu> PENDING = new HashMap<>();
    private boolean spinning;
    private int resolveTick;
    private boolean pendingSuccess;
    private double pendingChance;
    private float pendingAngle;
    private ItemStack pendingTarget = ItemStack.EMPTY;
    private int pendingCount;

    public static void tickPending(net.minecraft.server.MinecraftServer server) {
        for (UpgraderMenu menu : java.util.List.copyOf(PENDING.values())) {
            if (server.getTickCount() >= menu.resolveTick) menu.finalizeSpin();
        }
    }

    // Settle before logout saves player data or death drops the inventory.
    public static void settlePending(Player player) {
        UpgraderMenu menu = PENDING.get(player.getUUID());
        if (menu != null) menu.finalizeSpin();
    }

    private boolean isLocked() {
        return spinning || (playerInventory.player instanceof ServerPlayer
                && PENDING.containsKey(playerInventory.player.getUUID()));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!isLocked()) super.clicked(slot, button, type, player);
    }

    private double lastSentChance = Double.NaN;
    private ItemStack lastSyncedTarget = ItemStack.EMPTY;
    private int lastSyncedCount = -1;
    private int lastSyncedMultiplier = -1;

    /**
     * Конструктор меню апгрейдера.
     *
     * @param containerId     идентификатор контейнера
     * @param playerInventory инвентарь игрока
     */
    public UpgraderMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.UPGRADER_MENU.get(), containerId);
        this.playerInventory = playerInventory;

        // Слот 0: входной слот (ставка), соответствует раскладке GUI 256x272.
        this.addSlot(new InputSlot(this.inputContainer, 0, 29, 29));

        // Слоты 1-27: основной инвентарь игрока (3 ряда по 9 слотов).
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 48 + col * 18, 187 + row * 18));
            }
        }

        // Слоты 28-36: хотбар игрока (9 слотов).
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 48 + col * 18, 245));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Синхронизирует шанс и подтверждённое сервером состояние (цель, количество,
     * множитель) только при их изменении. Это держит расчёт на сервере и не
     * создаёт сетевой поток из повторяющихся одинаковых значений.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (this.playerInventory.player instanceof ServerPlayer serverPlayer) {
            double chance = spinning ? pendingChance : calculateChance();
            if (Double.doubleToLongBits(chance) != Double.doubleToLongBits(this.lastSentChance)) {
                this.lastSentChance = chance;
                NetworkHandler.sendToPlayer(serverPlayer, new UpdateChancePacket(chance));
            }

            boolean sameTarget = ItemStack.isSameItemSameTags(this.lastSyncedTarget, this.targetStack);
            if (!sameTarget || this.lastSyncedCount != this.targetCount
                    || this.lastSyncedMultiplier != this.multiplier) {
                this.lastSyncedTarget = this.targetStack.copy();
                this.lastSyncedCount = this.targetCount;
                this.lastSyncedMultiplier = this.multiplier;
                NetworkHandler.sendToPlayer(serverPlayer,
                        new SyncStatePacket(this.targetStack, this.targetCount, this.multiplier));
            }
        }
    }

    /** Расчёт шанса выполняется только на сервере. */
    private double calculateChance() {
        if (this.targetStack.isEmpty()) {
            return 0.0;
        }
        int count = Mth.clamp(this.targetCount, 1, MAX_TARGET_COUNT);
        ItemStack targetWithCount = this.targetStack.copy();
        targetWithCount.setCount(count);
        return ChanceCalculator.calculateChance(
                ValueCalculator.getItemStackValue(getInputStack()),
                ValueCalculator.getItemStackValue(targetWithCount),
                this.multiplier);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.inputContainer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isLocked() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
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
     * @return текущий целевой предмет (targetStack) в количестве 1
     */
    public ItemStack getTargetStack() {
        return targetStack;
    }

    /**
     * @return подтверждённое сервером количество цели
     */
    public int getTargetCount() {
        return targetCount;
    }

    /**
     * Устанавливает целевой предмет (targetStack).
     * Предметы из постоянного и конфигурационного blacklist, а также сам апгрейдер
     * отклоняются. Текущее количество цели сохраняется.
     *
     * @param targetStack предмет цели
     */
    public void setTargetStack(ItemStack targetStack) {
        if (isLocked()) return;
        if (targetStack == null || targetStack.isEmpty()
                || ModConfig.isBlacklisted(targetStack)
                || targetStack.is(ModItems.UPGRADER.get())) {
            this.targetStack = ItemStack.EMPTY;
        } else {
            this.targetStack = targetStack.copy();
            this.targetStack.setCount(1);
        }
        broadcastChanges();
    }

    /**
     * Устанавливает количество цели. Клиентское значение не считается доверенным:
     * сервер всегда ограничивает его безопасным диапазоном 1..MAX_TARGET_COUNT.
     *
     * @param count запрашиваемое количество цели
     */
    public void setTargetCount(int count) {
        if (isLocked()) return;
        this.targetCount = Mth.clamp(count, 1, MAX_TARGET_COUNT);
        broadcastChanges();
    }

    /**
     * @return текущий множитель награды (1, 2, 4, 8 или 10)
     */
    public int getMultiplier() {
        return multiplier;
    }

    /**
     * Проверяет, что множитель входит в разрешённый сервером набор.
     *
     * @param value проверяемое значение
     * @return true, если значение допустимо
     */
    public static boolean isAllowedMultiplier(int value) {
        return value == 1 || value == 2 || value == 4 || value == 8 || value == 10;
    }

    /**
     * Устанавливает множитель награды. Допустимые значения: 1, 2, 4, 8, 10.
     * Любые другие значения (в том числе из поддельных пакетов) отклоняются.
     *
     * @param multiplier множитель
     */
    public void setMultiplier(int multiplier) {
        if (isLocked()) return;
        if (isAllowedMultiplier(multiplier)) {
            this.multiplier = multiplier;
            broadcastChanges();
        }
    }

    /**
     * Применяет подтверждённое сервером состояние на клиенте без обратной рассылки.
     *
     * @param target     подтверждённая цель
     * @param count      подтверждённое количество цели
     * @param multiplier подтверждённый множитель
     */
    public void applyServerSync(ItemStack target, int count, int multiplier) {
        this.targetStack = (target == null || target.isEmpty()) ? ItemStack.EMPTY : target.copy();
        if (!this.targetStack.isEmpty()) {
            this.targetStack.setCount(1);
        }
        this.targetCount = Mth.clamp(count, 1, MAX_TARGET_COUNT);
        this.multiplier = isAllowedMultiplier(multiplier) ? multiplier : 1;
    }

    /**
     * Серверный подбор количества цели под пресет шанса (30%, 50% или 80%).
     * Количество подбирается так, чтобы фактический шанс был максимально близок
     * к выбранному проценту с учётом clamp minChance/maxChance.
     *
     * @param percent желаемый шанс в процентах
     */
    public void applyChancePreset(int percent) {
        if (isLocked()) return;
        if (percent != 30 && percent != 50 && percent != 80) {
            return;
        }

        ItemStack input = getInputStack();
        if (input.isEmpty() || this.targetStack.isEmpty()) {
            return;
        }
        if (ModConfig.isBlacklisted(input) || ModConfig.isBlacklisted(this.targetStack)) {
            return;
        }

        double inputValue = ValueCalculator.getItemStackValue(input);
        double unitTargetValue = ValueCalculator.getItemStackValue(this.targetStack);
        if (inputValue <= 0.0 || unitTargetValue <= 0.0) {
            return;
        }

        int count = ChanceCalculator.solveCountForChance(
                inputValue, unitTargetValue, this.multiplier, percent, MAX_TARGET_COUNT);
        if (count > 0) {
            setTargetCount(count);
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
     * Выполняет проверку всех правил безопасности, рассчитывает шанс,
     * определяет исход и начисляет награду (цель * количество * множитель,
     * с разбиением на стаки) либо сжигает ставку.
     *
     * @param player игрок, выполняющий апгрейд
     */
    public void doSpin(ServerPlayer player) {
        try {
            doSpinInternal(player);
        } catch (Throwable t) {
            LOGGER.error("doSpin error for player {}", player.getName().getString(), t);
            rejectSpin(player);
        }
    }

    private void doSpinInternal(ServerPlayer player) {
        if (isLocked()) {
            // A duplicate request must not stop the currently running animation.
            if (!spinning) rejectSpin(player);
            return;
        }
        ItemStack input = getInputStack();

        // Правило 1: input.isEmpty() || target.isEmpty() → cancel
        if (input.isEmpty() || targetStack.isEmpty()) {
            rejectSpin(player);
            return;
        }

        // Чёрный список и сам апгрейдер проверяются на сервере повторно,
        // даже если клиент изменён или прислал поддельные пакеты.
        if (ModConfig.isBlacklisted(input) || ModConfig.isBlacklisted(targetStack)
                || input.is(ModItems.UPGRADER.get()) || targetStack.is(ModItems.UPGRADER.get())) {
            rejectSpin(player);
            return;
        }

        // Защита от недоверенных значений множителя и количества цели.
        if (!isAllowedMultiplier(this.multiplier)) {
            this.multiplier = 1;
        }
        int count = Mth.clamp(this.targetCount, 1, MAX_TARGET_COUNT);
        this.targetCount = count;

        ItemStack targetWithCount = this.targetStack.copy();
        targetWithCount.setCount(count);

        // Вычисление стоимости ставки и цели (количество цели входит в стоимость)
        double inputValue = ValueCalculator.getItemStackValue(input);
        double targetValue = ValueCalculator.getItemStackValue(targetWithCount);

        // Правило 2: inputValue <= 0 || targetValue <= 0 → cancel
        if (!Double.isFinite(inputValue) || !Double.isFinite(targetValue)
                || inputValue <= 0.0 || targetValue <= 0.0) {
            rejectSpin(player);
            return;
        }

        // Итоговая награда при успехе: количество цели * множитель.
        int rewardTotal = count * this.multiplier;

        // Правило 3: одинаковый предмет → cancel
        if (ItemStack.isSameItemSameTags(input, targetWithCount)) {
            rejectSpin(player);
            return;
        }

        // Правило 4: player.isCreative() && targetValue >= 1_000_000 → cancel
        if (player.isCreative() && targetValue >= 1000000.0 && !ModConfig.isAllowCreativeEndgame()) {
            rejectSpin(player);
            return;
        }

        // Правило 5: inputValue > targetValue * maxDowngradeRatio → cancel
        if (inputValue > targetValue * ModConfig.getMaxDowngradeRatio()) {
            rejectSpin(player);
            return;
        }

        // Правило 6: inputValue > logThreshold → log в logs/upgradermod_suspicious.log
        if (inputValue > ModConfig.getLogThreshold()) {
            logSuspiciousAction(player, input, inputValue, targetWithCount, targetValue);
        }

        // Проверка комиссии/налога (если включён)
        if (ModConfig.isTaxEnabled() && !player.isCreative()) {
            String taxItemId = ModConfig.getTaxItem();
            int taxCount = ModConfig.getTaxAmount();
            ResourceLocation taxId = ResourceLocation.tryParse(taxItemId);
            Item taxItem = taxId == null ? null : ForgeRegistries.ITEMS.getValue(taxId);
            if (taxItem == null || taxItem == net.minecraft.world.item.Items.AIR || taxCount < 1) {
                rejectSpin(player);
                return;
            }
            if (taxItem != null) {
                int availableTax = player.getInventory().countItem(taxItem);
                if (availableTax < taxCount) {
                    rejectSpin(player);
                    return; // Недостаточно предметов для оплаты налога
                }
                player.getInventory().clearOrCountMatchingItems(s -> s.is(taxItem), taxCount, player.inventoryMenu.getCraftSlots());
            }
        }

        // Расчёт шанса успеха выполняется только на сервере
        double chance = ChanceCalculator.calculateChance(inputValue, targetValue, this.multiplier);

        // Получение генератора случайных чисел с сервера
        RandomSource random = player.serverLevel().getRandom();
        double roll = random.nextDouble() * 100.0;
        boolean success = roll < chance;

        // Расчёт угла остановки стрелки (в градусах от 0 до 360)
        float winSector = (float) ((chance / 100.0) * 360.0);
        float rollAngle;
        if (success) {
            rollAngle = random.nextFloat() * winSector;
        } else {
            rollAngle = winSector + random.nextFloat() * (360.0f - winSector);
        }

        // Snapshot the accepted transaction, then consume the stake once.
        this.pendingSuccess = success;
        this.pendingChance = chance;
        this.pendingAngle = rollAngle;
        this.pendingTarget = this.targetStack.copy();
        this.pendingCount = rewardTotal;
        this.resolveTick = player.getServer().getTickCount() + 40;
        this.spinning = true;
        PENDING.put(player.getUUID(), this);
        this.inputContainer.setItem(0, ItemStack.EMPTY);
        this.broadcastChanges();
    }

    private void rejectSpin(ServerPlayer player) {
        NetworkHandler.sendToPlayer(player,
                new SpinResultPacket(containerId, true, false, 0.0, 0.0F));
    }

    private void finalizeSpin() {
        if (!spinning) return;
        // Clear the pending marker before awarding: repeat ticks/close/logout cannot duplicate it.
        spinning = false;
        ServerPlayer player = (ServerPlayer) playerInventory.player;
        PENDING.remove(player.getUUID(), this);
        boolean success = pendingSuccess;
        if (success) {
            // Награда разбивается на несколько ItemStack, если превышает размер стака.
            int remaining = pendingCount;
            int maxStackSize = Math.max(1, this.pendingTarget.getMaxStackSize());
            while (remaining > 0) {
                int size = Math.min(remaining, maxStackSize);
                ItemStack reward = this.pendingTarget.copy();
                reward.setCount(size);
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
                remaining -= size;
            }
        }

        player.playNotifySound(
                success ? ModSounds.SPIN_SUCCESS.get() : ModSounds.SPIN_FAILURE.get(),
                SoundSource.PLAYERS,
                0.85F,
                success ? 1.0F : 0.8F);

        pendingTarget = ItemStack.EMPTY;
        pendingCount = 0;
        if (player.containerMenu == this) this.broadcastChanges();
        player.getInventory().setChanged();

        // Отправка клиенту пакета с результатом
        NetworkHandler.sendToPlayer(player, new SpinResultPacket(containerId, false, success, pendingChance, pendingAngle));
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

    /** Слот ставки, не принимающий предметы из чёрного списка и сам апгрейдер. */
    private final class InputSlot extends Slot {

        private InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !isLocked();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !isLocked() && !ModConfig.isBlacklisted(stack)
                    && !stack.is(ModItems.UPGRADER.get());
        }
    }
}
