package com.example.upgradermod.client;

import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.ChancePresetPacket;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetMultiplierPacket;
import com.example.upgradermod.network.SetTargetCountPacket;
import com.example.upgradermod.network.SpinPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

/**
 * Графический интерфейс апгрейдера с тёмной темой, компасом и рулеткой.
 * Сервер присылает сюда рассчитанный шанс через UpdateChancePacket и
 * подтверждённое состояние через SyncStatePacket; клиент не предсказывает
 * результат спина и не считает шанс самостоятельно.
 * Стрелка компаса вращается с постоянной скоростью, а после ответа сервера
 * плавно замедляется и останавливается ровно на выпавшем угле — без телепортации.
 * Финальная компоновка рассчитана на GUI 256x272.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
public class UpgraderScreen extends AbstractContainerScreen<UpgraderMenu> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 272;
    private static final long RESULT_DISPLAY_MS = 2000L;
    /** Сколько показывать красное «✗ Отклонено» при reject сервера. */
    private static final long REJECT_DISPLAY_MS = 2500L;
    /** Клиентский таймаут ожидания результата: если сервер молчит дольше, интерфейс разблокируется. */
    private static final long CLIENT_SPIN_TIMEOUT_MS = 10000L;

    /**
     * Скорость стрелки в фазе ожидания, градусов за миллисекунду.
     * 0.72 град/мс = 720 град/с = 2 оборота в секунду.
     */
    private static final float SPIN_SPEED_DEG_PER_MS = 0.72F;
    /**
     * Минимальный доворот при остановке: стрелка делает как минимум один
     * дополнительный оборот и плавно тормозит до нуля ровно на угле сервера.
     */
    private static final float LANDING_MIN_TRAVEL_DEG = 360.0F;
    /** Короткое торможение при reject или таймауте: стрелка плавно замирает на месте. */
    private static final float BRAKE_TRAVEL_DEG = 108.0F;

    private static final int BACKGROUND_COLOR = 0xFF1A1A2E;
    private static final int PANEL_COLOR = 0xFF211B30;
    private static final int FRAME_COLOR = 0xFF806746;
    private static final int INNER_COMPASS_COLOR = 0xFF15101F;
    private static final int ACCENT_COLOR = 0xFFD5AF65;
    private static final int ACCENT_HOVER_COLOR = 0xFFE8C887;
    private static final int GOLD_COLOR = 0xFFE8C887;
    private static final int TEXT_COLOR = 0xFFE8E8E8;
    private static final int MUTED_COLOR = 0xFF9A9AB0;
    private static final int DISABLED_COLOR = 0xFF3A3A4A;
    private static final int SUCCESS_COLOR = 0xFF44FF88;
    private static final int FAILURE_COLOR = 0xFFFF6E6E;
    private static final int REJECT_COLOR = 0xFFFF5555;
    private static final int REJECT_REASON_COLOR = 0xFFB87A7A;

    /**
     * Ключи локализации причин отклонения. Индексы строго соответствуют
     * кодам REJECT_* из UpgraderMenu.
     */
    private static final String[] REJECT_REASON_KEYS = {
            "gui.upgradermod.reject.generic",
            "gui.upgradermod.reject.locked",
            "gui.upgradermod.reject.empty",
            "gui.upgradermod.reject.blacklist",
            "gui.upgradermod.reject.value",
            "gui.upgradermod.reject.identical",
            "gui.upgradermod.reject.creative",
            "gui.upgradermod.reject.downgrade",
            "gui.upgradermod.reject.tax",
            "gui.upgradermod.reject.error"
    };

    // ---- Координаты элементов (относительно leftPos/topPos) ----
    private static final int INPUT_SLOT_X = 29;
    private static final int INPUT_SLOT_Y = 29;
    private static final int TARGET_SLOT_X = 211;
    private static final int TARGET_SLOT_Y = 29;
    private static final int COMPASS_X = 128;
    private static final int COMPASS_Y = 64;

    private static final int ROW1_Y = 132;
    private static final int ROW1_H = 16;
    private static final int ROW2_Y = 154;
    private static final int ROW2_H = 18;

    private static final int INV_START_Y = 187;

    private Button spinButton;
    private Button countMinusButton;
    private Button countPlusButton;
    private Button preset30Button;
    private Button preset50Button;
    private Button preset80Button;
    private Button btnX1;
    private Button btnX2;
    private Button btnX4;
    private Button btnX8;
    private Button btnX10;

    /** Значение, которое показывается как актуальный шанс после окончания результата. */
    private double displayedChance;
    /** Момент, до которого поверх шанса показывается результат броска. */
    private long resultDisplayUntil;
    /** Результат последнего броска; true — успех, false — провал. */
    private boolean lastResult;
    private boolean hasResult;
    private double resultChance;
    /** Результат получен, но показывается только после полной остановки стрелки. */
    private boolean resultPendingDisplay;
    /** Момент, до которого показывается красное уведомление об отклонении спина. */
    private long rejectDisplayUntil;
    /** Код причины последнего отклонения (REJECT_* из UpgraderMenu). */
    private int rejectReason;

    /** Текущий угол стрелки компаса, 0 градусов — вверх. */
    private float arrowAngle;
    private float spinStartAngle;
    private long spinStartTime;
    /** Фаза ожидания ответа сервера: стрелка вращается с постоянной скоростью. */
    private boolean isSpinning;
    /** Фаза остановки: стрелка замедляется и замирает ровно на целевом угле. */
    private boolean isLanding;
    private long landStartTime;
    private long landDuration;
    private float landStartAngle;
    private float landTargetAngle;

    public UpgraderScreen(UpgraderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.displayedChance = 0.0D;
        this.arrowAngle = 0.0F;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        this.spinButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.upgradermod.spin"), button -> trySpin())
                .bounds(x + 16, y + ROW2_Y, 90, ROW2_H)
                .build());

        this.countMinusButton = this.addRenderableWidget(Button.builder(
                        Component.literal("-"), button -> changeTargetCount(-1))
                .bounds(x + 16, y + ROW1_Y, 18, ROW1_H).build());
        this.countPlusButton = this.addRenderableWidget(Button.builder(
                        Component.literal("+"), button -> changeTargetCount(1))
                .bounds(x + 70, y + ROW1_Y, 18, ROW1_H).build());

        this.preset30Button = this.addRenderableWidget(Button.builder(
                        Component.literal("30%"), button -> requestPreset(30))
                .bounds(x + 96, y + ROW1_Y, 30, ROW1_H).build());
        this.preset50Button = this.addRenderableWidget(Button.builder(
                        Component.literal("50%"), button -> requestPreset(50))
                .bounds(x + 128, y + ROW1_Y, 30, ROW1_H).build());
        this.preset80Button = this.addRenderableWidget(Button.builder(
                        Component.literal("80%"), button -> requestPreset(80))
                .bounds(x + 160, y + ROW1_Y, 30, ROW1_H).build());

        this.btnX1 = this.addRenderableWidget(Button.builder(Component.literal("x1"), button -> setMultiplier(1))
                .bounds(x + 116, y + ROW2_Y, 24, ROW2_H).build());
        this.btnX2 = this.addRenderableWidget(Button.builder(Component.literal("x2"), button -> setMultiplier(2))
                .bounds(x + 142, y + ROW2_Y, 24, ROW2_H).build());
        this.btnX4 = this.addRenderableWidget(Button.builder(Component.literal("x4"), button -> setMultiplier(4))
                .bounds(x + 168, y + ROW2_Y, 24, ROW2_H).build());
        this.btnX8 = this.addRenderableWidget(Button.builder(Component.literal("x8"), button -> setMultiplier(8))
                .bounds(x + 194, y + ROW2_Y, 24, ROW2_H).build());
        this.btnX10 = this.addRenderableWidget(Button.builder(Component.literal("x10"), button -> setMultiplier(10))
                .bounds(x + 220, y + ROW2_Y, 24, ROW2_H).build());

        updateButtonStates();
    }

    /** Интерфейс занят: ждём ответ сервера или стрелка ещё останавливается. */
    private boolean isBusy() {
        return this.isSpinning || this.isLanding;
    }

    /** Запрашивает изменение количества цели; сервер подтвердит его через SyncStatePacket. */
    private void changeTargetCount(int delta) {
        if (isBusy() || this.menu.getTargetStack().isEmpty()) {
            return;
        }
        int requested = Mth.clamp(this.menu.getTargetCount() + delta, 1, UpgraderMenu.MAX_TARGET_COUNT);
        NetworkHandler.sendToServer(new SetTargetCountPacket(requested));
    }

    /** Отправляет серверу пресет шанса; сервер сам подберёт количество цели. */
    private void requestPreset(int percent) {
        if (isBusy()) {
            return;
        }
        NetworkHandler.sendToServer(new ChancePresetPacket(percent));
    }

    private void setMultiplier(int multiplier) {
        if (isBusy()) {
            return;
        }
        NetworkHandler.sendToServer(new SetMultiplierPacket(multiplier));
    }

    /** До ответа сервера стрелка просто вращается — итог не предсказывается. */
    private void startWaitingSpin() {
        this.isSpinning = true;
        this.hasResult = false;
        this.resultPendingDisplay = false;
        this.rejectDisplayUntil = 0L;
        this.spinStartTime = System.currentTimeMillis();
        this.spinStartAngle = this.arrowAngle;
        updateButtonStates();
    }

    /**
     * Отправляет запрос спина на сервер. Локальная превалидация отсекает
     * заведомо отклоняемые запросы (пустая ставка или цель), а ошибка
     * отправки пакета не оставляет интерфейс заблокированным.
     */
    private void trySpin() {
        if (isBusy()) {
            return;
        }
        if (this.menu.getInputStack().isEmpty() || this.menu.getTargetStack().isEmpty()) {
            // Не отправляем спин, который сервер гарантированно отклонит.
            return;
        }
        startWaitingSpin();
        try {
            NetworkHandler.sendToServer(new SpinPacket());
        } catch (Throwable t) {
            LOGGER.error("Failed to send SpinPacket", t);
            this.isSpinning = false;
            startBrake();
            updateButtonStates();
        }
    }

    /**
     * Начинает плавную остановку стрелки ровно на угле, выпавшем на сервере.
     * Стрелка проходит вперёд как минимум один дополнительный оборот и
     * замедляется с постоянным замедлением до полной остановки.
     *
     * @param rollAngle конечный угол стрелки (0..360), рассчитанный сервером
     */
    private void startLanding(float rollAngle) {
        float current = this.arrowAngle;
        // Угловое расстояние вперёд до целевого угла (0..360).
        float forwardGap = normalizeAngle(rollAngle - current);
        beginLanding(current, LANDING_MIN_TRAVEL_DEG + forwardGap);
    }

    /** Короткое плавное торможение на месте: reject, таймаут или ошибка отправки. */
    private void startBrake() {
        beginLanding(this.arrowAngle, BRAKE_TRAVEL_DEG);
    }

    /**
     * Физика остановки: равнозамедленное движение от текущей скорости
     * SPIN_SPEED_DEG_PER_MS до нуля точно на дистанции travelDeg.
     * Длительность D = 2 * travel / v0, угол(t) = start + v0*t - v0*t^2 / (2D),
     * поэтому скорость в начале торможения в точности равна скорости вращения —
     * переход без рывка, в конце — ровно ноль и ровно целевой угол.
     */
    private void beginLanding(float startAngle, float travelDeg) {
        this.landStartAngle = startAngle;
        this.landTargetAngle = startAngle + travelDeg;
        this.landStartTime = System.currentTimeMillis();
        this.landDuration = Math.max(1L, (long) (2.0 * travelDeg / SPIN_SPEED_DEG_PER_MS));
        this.isLanding = true;
    }

    /**
     * Текущий угол стрелки как функция времени. Вызывается каждый кадр рендера,
     * поэтому вращение и остановка плавные даже между тиками контейнера.
     */
    private float computeArrowAngle(long now) {
        if (this.isSpinning) {
            return this.spinStartAngle + (now - this.spinStartTime) * SPIN_SPEED_DEG_PER_MS;
        }
        if (this.isLanding) {
            long elapsed = now - this.landStartTime;
            if (elapsed >= this.landDuration) {
                return this.landTargetAngle;
            }
            float t = elapsed;
            float duration = this.landDuration;
            return this.landStartAngle + SPIN_SPEED_DEG_PER_MS * t
                    - SPIN_SPEED_DEG_PER_MS * t * t / (2.0F * duration);
        }
        return this.arrowAngle;
    }

    /**
     * Получает рассчитанный сервером шанс при изменении входного предмета,
     * цели, количества цели или множителя.
     */
    public void onChanceUpdate(double chance) {
        this.displayedChance = Mth.clamp(chance, 0.0D, 100.0D);
    }

    /**
     * Получает результат броска и фиксирует рассчитанные на сервере шанс и угол.
     * Только этот пакет определяет, что увидит игрок: успех или провал.
     * Стрелка плавно тормозит и останавливается ровно на выпавшем угле;
     * результат показывается после полной остановки.
     * Reject показывается красным «✗ Отклонено» с причиной — но только если
     * клиент реально ждал результат: запоздалые reject-пакеты игнорируются,
     * чтобы уведомление не вспыхивало при обычной работе с кнопками.
     *
     * @param rejected  отклонён ли спин сервером
     * @param success   успешен ли апгрейд (имеет смысл только без rejected)
     * @param chance    шанс в процентах
     * @param rollAngle угол остановки стрелки (0..360)
     * @param reason    код причины отклонения (REJECT_* из UpgraderMenu)
     */
    public void onSpinResult(boolean rejected, boolean success, double chance, float rollAngle, int reason) {
        long now = System.currentTimeMillis();
        if (rejected) {
            if (!this.isSpinning) {
                // Клиент не ждёт результат: reject пришёл с опозданием и ничего не значит.
                LOGGER.debug("Ignoring stale spin reject (reason code {})", reason);
                return;
            }
            this.isSpinning = false;
            this.hasResult = false;
            this.resultPendingDisplay = false;
            this.rejectReason = reason;
            this.rejectDisplayUntil = now + REJECT_DISPLAY_MS;
            // Сектор шанса не должен схлопываться: ставка не была принята.
            this.resultChance = this.displayedChance;
            startBrake();
            updateButtonStates();
            return;
        }

        this.isSpinning = false;
        this.rejectDisplayUntil = 0L;
        this.lastResult = success;
        this.resultChance = Mth.clamp(chance, 0.0D, 100.0D);
        this.resultPendingDisplay = true;
        startLanding(rollAngle);
        updateButtonStates();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        long now = System.currentTimeMillis();
        if (this.isSpinning && now - this.spinStartTime >= CLIENT_SPIN_TIMEOUT_MS) {
            // Сервер молчит дольше 10 секунд — плавно тормозим и разблокируем интерфейс.
            this.isSpinning = false;
            this.resultPendingDisplay = false;
            LOGGER.warn("Spin wait cancelled: no result from server within {} ms", CLIENT_SPIN_TIMEOUT_MS);
            startBrake();
        }
        if (this.isLanding && now >= this.landStartTime + this.landDuration) {
            // Стрелка остановилась ровно на целевом угле — можно показать результат.
            this.isLanding = false;
            this.arrowAngle = this.landTargetAngle;
            if (this.resultPendingDisplay) {
                this.resultPendingDisplay = false;
                this.hasResult = true;
                this.resultDisplayUntil = now + RESULT_DISPLAY_MS;
            }
        }
        if (this.hasResult && now >= this.resultDisplayUntil) this.hasResult = false;
        updateButtonStates();
    }

    private static float normalizeAngle(float angle) {
        angle %= 360.0F;
        return angle < 0.0F ? angle + 360.0F : angle;
    }

    /** Показывается ли сейчас красное уведомление об отклонении спина. */
    private boolean isRejectShowing(long now) {
        return !isBusy() && now < this.rejectDisplayUntil;
    }

    /** Ключ локализации причины последнего отклонения. */
    private String rejectReasonKey() {
        int index = Mth.clamp(this.rejectReason, 0, REJECT_REASON_KEYS.length - 1);
        return REJECT_REASON_KEYS[index];
    }

    /** Подсказка, объясняющая игроку, почему кнопка КРУТИТЬ неактивна. */
    private Component inactiveHint() {
        if (isBusy()) {
            return null;
        }
        if (this.menu.getInputStack().isEmpty()) {
            return Component.translatable("gui.upgradermod.hint_need_input");
        }
        if (this.menu.getTargetStack().isEmpty()) {
            return Component.translatable("gui.upgradermod.hint_need_target");
        }
        return null;
    }

    private void updateButtonStates() {
        boolean busy = isBusy();
        boolean hasInput = !this.menu.getInputStack().isEmpty();
        boolean hasTarget = !this.menu.getTargetStack().isEmpty();

        if (this.spinButton != null) {
            this.spinButton.active = !busy && hasInput && hasTarget;
        }
        if (this.countMinusButton != null) {
            this.countMinusButton.active = !busy && hasTarget
                    && this.menu.getTargetCount() > 1;
        }
        if (this.countPlusButton != null) {
            this.countPlusButton.active = !busy && hasTarget
                    && this.menu.getTargetCount() < UpgraderMenu.MAX_TARGET_COUNT;
        }
        boolean presetActive = !busy && hasInput && hasTarget;
        if (this.preset30Button != null) this.preset30Button.active = presetActive;
        if (this.preset50Button != null) this.preset50Button.active = presetActive;
        if (this.preset80Button != null) this.preset80Button.active = presetActive;

        boolean canChangeMultiplier = !busy;
        if (this.btnX1 != null) this.btnX1.active = canChangeMultiplier;
        if (this.btnX2 != null) this.btnX2.active = canChangeMultiplier;
        if (this.btnX4 != null) this.btnX4.active = canChangeMultiplier;
        if (this.btnX8 != null) this.btnX8.active = canChangeMultiplier;
        if (this.btnX10 != null) this.btnX10.active = canChangeMultiplier;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        try {
            renderInternal(guiGraphics, mouseX, mouseY, partialTick);
        } catch (Throwable t) {
            LOGGER.error("UpgraderScreen render error", t);
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
        }
    }

    private void renderInternal(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Угол пересчитывается каждый кадр: вращение и торможение плавные.
        this.arrowAngle = computeArrowAngle(System.currentTimeMillis());
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        drawThemedButton(guiGraphics, this.spinButton,
                Component.translatable(isBusy() ? "gui.upgradermod.spinning" : "gui.upgradermod.spin"),
                true, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.countMinusButton, Component.literal("-"), false, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.countPlusButton, Component.literal("+"), false, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.preset30Button, Component.literal("30%"), false, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.preset50Button, Component.literal("50%"), false, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.preset80Button, Component.literal("80%"), false, mouseX, mouseY);
        int multiplier = this.menu.getMultiplier();
        drawThemedButton(guiGraphics, this.btnX1, Component.literal("x1"), multiplier == 1, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX2, Component.literal("x2"), multiplier == 2, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX4, Component.literal("x4"), multiplier == 4, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX8, Component.literal("x8"), multiplier == 8, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX10, Component.literal("x10"), multiplier == 10, mouseX, mouseY);
        // Tooltip слотов рисуется внутри super.render() (AbstractContainerScreen).
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, BACKGROUND_COLOR);
        guiGraphics.fill(x + 2, y + 2, x + GUI_WIDTH - 2, y + GUI_HEIGHT - 2, PANEL_COLOR);
        drawFrame(guiGraphics, x, y, GUI_WIDTH, GUI_HEIGHT);

        // Слот ставки и слот цели.
        drawSlot(guiGraphics, x + INPUT_SLOT_X - 1, y + INPUT_SLOT_Y - 1);
        drawSlot(guiGraphics, x + TARGET_SLOT_X - 1, y + TARGET_SLOT_Y - 1);

        ItemStack target = this.menu.getTargetStack();
        if (!target.isEmpty()) {
            guiGraphics.renderItem(target, x + TARGET_SLOT_X, y + TARGET_SLOT_Y);
            guiGraphics.renderItemDecorations(this.font, target, x + TARGET_SLOT_X, y + TARGET_SLOT_Y);
        }

        // Инвентарь игрока сохраняет стандартные слоты меню и получает ту же тёмную подложку.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                drawSlot(guiGraphics, x + 47 + col * 18, y + INV_START_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; ++col) {
            drawSlot(guiGraphics, x + 47 + col * 18, y + 244);
        }

        drawCompass(guiGraphics, x + COMPASS_X, y + COMPASS_Y);
    }

    private void drawFrame(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + 2, FRAME_COLOR);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, FRAME_COLOR);
        guiGraphics.fill(x, y, x + 2, y + height, FRAME_COLOR);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, FRAME_COLOR);
    }

    private void drawSlot(GuiGraphics guiGraphics, int slotX, int slotY) {
        guiGraphics.fill(slotX, slotY, slotX + 18, slotY + 18, FRAME_COLOR);
        guiGraphics.fill(slotX + 2, slotY + 2, slotX + 16, slotY + 16, INNER_COMPASS_COLOR);
    }

    /**
     * Компасоподобный индикатор рулетки: концентрические кольца, деления
     * и вращающаяся стрелка. Без букв и обозначений сторон света.
     * Во время остановки стрелки и показа результата сектор соответствует
     * шансу именно этого броска.
     */
    private double visibleChance() {
        return (this.isLanding || this.hasResult) ? this.resultChance : this.displayedChance;
    }

    private int chanceSectorColor() {
        return visibleChance() < 30.0 ? 0xFF9B354A
                : visibleChance() < 60.0 ? 0xFFAD882D : 0xFF287B52;
    }

    private void drawCompass(GuiGraphics guiGraphics, int centerX, int centerY) {
        for (int dx = -38; dx <= 38; dx++) {
            for (int dy = -38; dy <= 38; dy++) {
                if (dx * dx + dy * dy <= 38 * 38) {
                    guiGraphics.fill(centerX + dx, centerY + dy,
                            centerX + dx + 1, centerY + dy + 1,
                            (Math.toDegrees(Math.atan2(dx, -dy)) + 360.0) % 360.0 < visibleChance() * 3.6
                                    ? chanceSectorColor() : INNER_COMPASS_COLOR);
                }
            }
        }

        for (int angle = 0; angle < 360; angle++) {
            double radians = Math.toRadians(angle);
            for (int radius = 42; radius <= 45; radius++) {
                int px = centerX + (int) (Math.cos(radians) * radius);
                int py = centerY + (int) (Math.sin(radians) * radius);
                guiGraphics.fill(px, py, px + 1, py + 1, FRAME_COLOR);
            }
        }

        for (int angle = 0; angle < 360; angle += 6) {
            double radians = Math.toRadians(angle);
            int length = angle % 30 == 0 ? 5 : 3;
            int color = angle % 30 == 0 ? ACCENT_COLOR : FRAME_COLOR;
            for (int radius = 38; radius < 38 + length; radius++) {
                int px = centerX + (int) (Math.cos(radians) * radius);
                int py = centerY + (int) (Math.sin(radians) * radius);
                guiGraphics.fill(px, py, px + 1, py + 1, color);
            }
        }

        double arrowRadians = Math.toRadians(this.arrowAngle - 90.0F);
        for (int radius = 0; radius <= 35; radius++) {
            int px = centerX + (int) (Math.cos(arrowRadians) * radius);
            int py = centerY + (int) (Math.sin(arrowRadians) * radius);
            int thickness = radius < 20 ? 2 : 1;
            guiGraphics.fill(px - thickness / 2, py - thickness / 2,
                    px + thickness / 2 + 1, py + thickness / 2 + 1, ACCENT_COLOR);
        }
        guiGraphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, ACCENT_COLOR);
    }

    private void drawThemedButton(GuiGraphics guiGraphics, Button button, Component label,
                                  boolean selected, double mouseX, double mouseY) {
        if (button == null) {
            return;
        }

        int buttonX = button.getX();
        int buttonY = button.getY();
        boolean hovered = mouseInside(button, mouseX, mouseY);
        int background;
        int textColor;

        if (!button.active) {
            background = DISABLED_COLOR;
            textColor = MUTED_COLOR;
        } else if (selected) {
            background = hovered ? ACCENT_HOVER_COLOR : ACCENT_COLOR;
            textColor = 0xFFFFFFFF;
        } else {
            background = hovered ? INNER_COMPASS_COLOR : PANEL_COLOR;
            textColor = hovered ? TEXT_COLOR : MUTED_COLOR;
        }

        guiGraphics.fill(buttonX, buttonY, buttonX + button.getWidth(), buttonY + button.getHeight(), FRAME_COLOR);
        guiGraphics.fill(buttonX + 1, buttonY + 1,
                buttonX + button.getWidth() - 1, buttonY + button.getHeight() - 1, background);
        guiGraphics.drawCenteredString(this.font, label,
                buttonX + button.getWidth() / 2,
                buttonY + (button.getHeight() - 8) / 2, textColor);
    }

    private boolean mouseInside(Button button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.title"), 128, 8, GOLD_COLOR);

        // Подписи слотов.
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.input_label"),
                INPUT_SLOT_X + 8, 18, TEXT_COLOR);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.target_label"),
                TARGET_SLOT_X + 8, 18, TEXT_COLOR);

        // Ценности ставки и цели (только отображение, шанс считает сервер).
        ItemStack input = this.menu.getInputStack();
        ItemStack target = this.menu.getTargetStack();
        String inputValue = ChanceCalculator.formatNumber(ValueCalculator.getItemStackValue(input));
        String targetValue = ChanceCalculator.formatNumber(ValueCalculator.getItemStackValue(target));
        guiGraphics.drawCenteredString(this.font, inputValue, INPUT_SLOT_X + 8, 50, MUTED_COLOR);
        guiGraphics.drawCenteredString(this.font, targetValue, TARGET_SLOT_X + 8, 50, MUTED_COLOR);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.target_count", this.menu.getTargetCount()),
                TARGET_SLOT_X + 8, 60, GOLD_COLOR);

        // Percentage stays inside the ring; result text is below it.
        guiGraphics.fill(107, 80, 149, 91, INNER_COMPASS_COLOR);
        guiGraphics.drawCenteredString(this.font,
                ChanceCalculator.formatChance(visibleChance()), 128, 81, TEXT_COLOR);

        // Строка состояния под компасом: отклонение сервера с причиной,
        // результат броска, ожидание/торможение стрелки, подсказка или шанс.
        long now = System.currentTimeMillis();
        boolean busy = isBusy();
        String statusText;
        int statusColor;
        String secondLine = null;
        int secondLineColor = MUTED_COLOR;
        boolean showChanceLabel = false;
        if (this.isRejectShowing(now)) {
            statusText = Component.translatable("gui.upgradermod.result_rejected").getString();
            statusColor = REJECT_COLOR;
            secondLine = Component.translatable(rejectReasonKey()).getString();
            secondLineColor = REJECT_REASON_COLOR;
        } else if (this.hasResult && !busy && now < this.resultDisplayUntil) {
            statusText = this.lastResult
                    ? Component.translatable("gui.upgradermod.result_success",
                    ChanceCalculator.formatChance(this.resultChance)).getString()
                    : Component.translatable("gui.upgradermod.result_failure").getString();
            statusColor = this.lastResult ? SUCCESS_COLOR : FAILURE_COLOR;
        } else if (busy) {
            statusText = "...";
            statusColor = MUTED_COLOR;
        } else {
            Component hint = inactiveHint();
            if (hint != null) {
                // Кнопка неактивна: подсказываем, чего не хватает для спина.
                statusText = hint.getString();
                statusColor = ACCENT_HOVER_COLOR;
            } else {
                statusText = ChanceCalculator.formatChance(this.displayedChance);
                statusColor = ACCENT_COLOR;
                showChanceLabel = true;
            }
        }

        guiGraphics.drawCenteredString(this.font, statusText, 128, 112, statusColor);
        if (secondLine != null) {
            guiGraphics.drawCenteredString(this.font, secondLine, 128, 122, secondLineColor);
        } else if (showChanceLabel) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.upgradermod.chance_label"), 128, 122, MUTED_COLOR);
        }

        // Подпись блока количества цели.
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.count_label"), 52, 122, MUTED_COLOR);
        guiGraphics.drawCenteredString(this.font,
                Component.literal(String.valueOf(this.menu.getTargetCount())), 52, ROW1_Y + 4, TEXT_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Пока стрелка крутится или останавливается — клики по GUI игнорируются.
        if (isBusy()) return true;
        int x = this.leftPos;
        int y = this.topPos;
        // Клик по слоту цели открывает каталог предметов.
        if (mouseX >= x + TARGET_SLOT_X - 1 && mouseX <= x + TARGET_SLOT_X + 17
                && mouseY >= y + TARGET_SLOT_Y - 1 && mouseY <= y + TARGET_SLOT_Y + 17) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new CatalogScreen(this));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public double getDisplayedChance() {
        return this.displayedChance;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
