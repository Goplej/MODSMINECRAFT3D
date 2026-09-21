package com.example.upgradermod.client;

import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.ChancePresetPacket;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetMultiplierPacket;
import com.example.upgradermod.network.SetTargetCountPacket;
import com.example.upgradermod.network.SpinPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Графический интерфейс апгрейдера с тёмной темой, компасом и рулеткой.
 * Сервер присылает сюда рассчитанный шанс через UpdateChancePacket и
 * подтверждённое состояние через SyncStatePacket; клиент не предсказывает
 * результат спина и не считает шанс самостоятельно.
 * Финальная компоновка рассчитана на GUI 256x272.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
public class UpgraderScreen extends AbstractContainerScreen<UpgraderMenu> {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 272;
    private static final long RESULT_DISPLAY_MS = 2000L;

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

    /** Текущий угол стрелки компаса, 0 градусов — вверх. */
    private float arrowAngle;
    private float spinStartAngle;
    private long spinStartTime;
    private boolean isSpinning;

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
                        Component.translatable("gui.upgradermod.spin"), button -> {
                            if (!this.isSpinning) {
                                startWaitingSpin();
                                NetworkHandler.sendToServer(new SpinPacket());
                            }
                        })
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

    /** Запрашивает изменение количества цели; сервер подтвердит его через SyncStatePacket. */
    private void changeTargetCount(int delta) {
        if (this.isSpinning || this.menu.getTargetStack().isEmpty()) {
            return;
        }
        int requested = Mth.clamp(this.menu.getTargetCount() + delta, 1, UpgraderMenu.MAX_TARGET_COUNT);
        NetworkHandler.sendToServer(new SetTargetCountPacket(requested));
    }

    /** Отправляет серверу пресет шанса; сервер сам подберёт количество цели. */
    private void requestPreset(int percent) {
        if (this.isSpinning) {
            return;
        }
        NetworkHandler.sendToServer(new ChancePresetPacket(percent));
    }

    private void setMultiplier(int multiplier) {
        if (this.isSpinning) {
            return;
        }
        NetworkHandler.sendToServer(new SetMultiplierPacket(multiplier));
    }

    /** До ответа сервера стрелка просто вращается — итог не предсказывается. */
    private void startWaitingSpin() {
        this.isSpinning = true;
        this.hasResult = false;
        this.spinStartTime = System.currentTimeMillis();
        this.spinStartAngle = this.arrowAngle;
        updateButtonStates();
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
     */
    public void onSpinResult(boolean rejected, boolean success, double chance, float rollAngle) {
        this.isSpinning = false;
        this.hasResult = !rejected;
        this.lastResult = success;
        this.resultChance = Mth.clamp(chance, 0.0D, 100.0D);
        if (!rejected) {
            this.arrowAngle = normalizeAngle(rollAngle);
        }
        this.resultDisplayUntil = System.currentTimeMillis() + RESULT_DISPLAY_MS;
        updateButtonStates();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        long now = System.currentTimeMillis();
        if (this.isSpinning) {
            // No local result prediction or timeout unlock: the server settles after 40 ticks.
            this.arrowAngle = normalizeAngle(this.spinStartAngle + (now - this.spinStartTime) * 0.72F);
        }
        if (this.hasResult && now >= this.resultDisplayUntil) this.hasResult = false;
        updateButtonStates();
    }

    private static float normalizeAngle(float angle) {
        angle %= 360.0F;
        return angle < 0.0F ? angle + 360.0F : angle;
    }

    private void updateButtonStates() {
        boolean hasInput = !this.menu.getInputStack().isEmpty();
        boolean hasTarget = !this.menu.getTargetStack().isEmpty();

        if (this.spinButton != null) {
            this.spinButton.active = !this.isSpinning && hasInput && hasTarget;
        }
        if (this.countMinusButton != null) {
            this.countMinusButton.active = !this.isSpinning && hasTarget
                    && this.menu.getTargetCount() > 1;
        }
        if (this.countPlusButton != null) {
            this.countPlusButton.active = !this.isSpinning && hasTarget
                    && this.menu.getTargetCount() < UpgraderMenu.MAX_TARGET_COUNT;
        }
        boolean presetActive = !this.isSpinning && hasInput && hasTarget;
        if (this.preset30Button != null) this.preset30Button.active = presetActive;
        if (this.preset50Button != null) this.preset50Button.active = presetActive;
        if (this.preset80Button != null) this.preset80Button.active = presetActive;

        boolean canChangeMultiplier = !this.isSpinning;
        if (this.btnX1 != null) this.btnX1.active = canChangeMultiplier;
        if (this.btnX2 != null) this.btnX2.active = canChangeMultiplier;
        if (this.btnX4 != null) this.btnX4.active = canChangeMultiplier;
        if (this.btnX8 != null) this.btnX8.active = canChangeMultiplier;
        if (this.btnX10 != null) this.btnX10.active = canChangeMultiplier;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        drawThemedButton(guiGraphics, this.spinButton, Component.translatable(this.isSpinning ? "gui.upgradermod.spinning" : "gui.upgradermod.spin"), true, mouseX, mouseY);
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
     */
    private double visibleChance() {
        return this.hasResult ? this.resultChance : this.displayedChance;
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

        // Шанс и его подпись.
        String chanceText;
        int chanceColor;
        if (this.hasResult && !this.isSpinning
                && System.currentTimeMillis() < this.resultDisplayUntil) {
            chanceText = this.lastResult
                    ? Component.translatable("gui.upgradermod.result_success",
                    ChanceCalculator.formatChance(this.resultChance)).getString()
                    : Component.translatable("gui.upgradermod.result_failure").getString();
            chanceColor = this.lastResult ? SUCCESS_COLOR : ACCENT_COLOR;
        } else if (this.isSpinning) {
            chanceText = "...";
            chanceColor = MUTED_COLOR;
        } else {
            chanceText = ChanceCalculator.formatChance(this.displayedChance);
            chanceColor = ACCENT_COLOR;
        }

        guiGraphics.drawCenteredString(this.font, chanceText, 128, 112, chanceColor);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.chance_label"), 128, 122, MUTED_COLOR);

        // Подпись блока количества цели.
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.count_label"), 52, 122, MUTED_COLOR);
        guiGraphics.drawCenteredString(this.font,
                Component.literal(String.valueOf(this.menu.getTargetCount())), 52, ROW1_Y + 4, TEXT_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isSpinning) return true;
        int x = this.leftPos;
        int y = this.topPos;
        // Клик по слоту цели открывает каталог предметов.
        if (mouseX >= x + TARGET_SLOT_X - 1 && mouseX <= x + TARGET_SLOT_X + 17
                && mouseY >= y + TARGET_SLOT_Y - 1 && mouseY <= y + TARGET_SLOT_Y + 17) {
            if (this.minecraft != null && !this.isSpinning) {
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
