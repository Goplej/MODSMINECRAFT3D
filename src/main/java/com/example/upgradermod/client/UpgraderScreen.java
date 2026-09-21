package com.example.upgradermod.client;

import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetMultiplierPacket;
import com.example.upgradermod.network.SpinPacket;
import com.example.upgradermod.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Графический интерфейс апгрейдера с тёмной темой, компасом и анимацией открытия.
 * Сервер присылает сюда рассчитанный шанс через UpdateChancePacket.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
public class UpgraderScreen extends AbstractContainerScreen<UpgraderMenu> {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 220;
    private static final long SPIN_DURATION_MS = 2000L;
    private static final long RESULT_DISPLAY_MS = 2000L;

    private static final int BACKGROUND_COLOR = 0xFF1A1A2E;
    private static final int PANEL_COLOR = 0xFF16213E;
    private static final int FRAME_COLOR = 0xFF533483;
    private static final int INNER_COMPASS_COLOR = 0xFF0F3460;
    private static final int ACCENT_COLOR = 0xFFE94560;
    private static final int GOLD_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFE8E8E8;
    private static final int MUTED_COLOR = 0xFF888888;

    private Button spinButton;
    private Button btnX1;
    private Button btnX2;
    private Button btnX4;
    private Button btnX8;

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
    private float targetArrowAngle;
    private long spinStartTime;
    private boolean isSpinning;

    /** Прогресс появления GUI от 0 до 1. */
    private float openAnimation;

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
        this.openAnimation = 0.0F;

        int x = this.leftPos;
        int y = this.topPos;

        this.spinButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.upgradermod.spin"), button -> {
                            if (!this.isSpinning) {
                                startSpinAnimation();
                                NetworkHandler.sendToServer(new SpinPacket());
                            }
                        })
                .bounds(x + 40, y + 165, 120, 20)
                .build());

        this.btnX1 = this.addRenderableWidget(Button.builder(Component.literal("x1"), button -> setMultiplier(1))
                .bounds(x + 166, y + 165, 20, 20).build());
        this.btnX2 = this.addRenderableWidget(Button.builder(Component.literal("x2"), button -> setMultiplier(2))
                .bounds(x + 188, y + 165, 20, 20).build());
        this.btnX4 = this.addRenderableWidget(Button.builder(Component.literal("x4"), button -> setMultiplier(4))
                .bounds(x + 210, y + 165, 20, 20).build());
        this.btnX8 = this.addRenderableWidget(Button.builder(Component.literal("x8"), button -> setMultiplier(8))
                .bounds(x + 232, y + 165, 20, 20).build());

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.55F, 1.0F);
        }
        updateButtonStates();
    }

    private void setMultiplier(int multiplier) {
        if (this.isSpinning) {
            return;
        }
        this.menu.setMultiplier(multiplier);
        NetworkHandler.sendToServer(new SetMultiplierPacket(multiplier));
    }

    private void startSpinAnimation() {
        this.isSpinning = true;
        this.spinStartTime = System.currentTimeMillis();
        this.spinStartAngle = this.arrowAngle;
        this.targetArrowAngle = this.arrowAngle;
        updateButtonStates();
    }

    /**
     * Получает рассчитанный сервером шанс при изменении входного предмета,
     * цели или множителя.
     */
    public void onChanceUpdate(double chance) {
        this.displayedChance = Mth.clamp(chance, 0.0D, 100.0D);
    }

    /**
     * Получает результат броска и фиксирует рассчитанный на сервере шанс.
     */
    public void onSpinResult(boolean success, double chance, float rollAngle) {
        this.lastResult = success;
        this.hasResult = true;
        this.resultChance = Mth.clamp(chance, 0.0D, 100.0D);
        this.displayedChance = this.resultChance;
        this.resultDisplayUntil = System.currentTimeMillis() + RESULT_DISPLAY_MS;

        this.isSpinning = true;
        this.spinStartTime = System.currentTimeMillis();
        this.spinStartAngle = this.arrowAngle;
        this.targetArrowAngle = rollAngle;
        updateButtonStates();
    }

    @Override
    public void tick() {
        super.tick();

        this.openAnimation = Math.min(1.0F, this.openAnimation + 0.15F);
        long now = System.currentTimeMillis();

        if (this.isSpinning) {
            long elapsed = now - this.spinStartTime;
            float progress = Mth.clamp((float) elapsed / SPIN_DURATION_MS, 0.0F, 1.0F);
            float easeOut = 1.0F - (float) Math.pow(1.0F - progress, 3.0F);

            if (this.hasResult) {
                float rotation = 720.0F * easeOut;
                this.arrowAngle = normalizeAngle(this.spinStartAngle
                        + rotation
                        + (this.targetArrowAngle - this.spinStartAngle) * easeOut);
            } else {
                // Пока сервер отвечает, стрелка продолжает вращаться, а шанс остаётся на экране.
                this.arrowAngle = normalizeAngle(this.spinStartAngle + (elapsed * 0.72F));
            }

            if (progress >= 1.0F) {
                this.arrowAngle = normalizeAngle(this.targetArrowAngle);
                this.isSpinning = false;
            }
        }

        if (this.hasResult && now >= this.resultDisplayUntil) {
            this.hasResult = false;
        }
        updateButtonStates();
    }

    private static float normalizeAngle(float angle) {
        angle %= 360.0F;
        return angle < 0.0F ? angle + 360.0F : angle;
    }

    private void updateButtonStates() {
        boolean canSpin = !this.isSpinning
                && !this.menu.getInputStack().isEmpty()
                && !this.menu.getTargetStack().isEmpty();
        if (this.spinButton != null) {
            this.spinButton.active = canSpin;
        }

        boolean canChangeMultiplier = !this.isSpinning;
        if (this.btnX1 != null) this.btnX1.active = canChangeMultiplier;
        if (this.btnX2 != null) this.btnX2.active = canChangeMultiplier;
        if (this.btnX4 != null) this.btnX4.active = canChangeMultiplier;
        if (this.btnX8 != null) this.btnX8.active = canChangeMultiplier;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        PoseStack poseStack = guiGraphics.pose();
        float scale = 0.85F + 0.15F * this.openAnimation;
        poseStack.pushPose();
        poseStack.translate(this.width / 2.0F, this.height / 2.0F, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        poseStack.translate(-this.width / 2.0F, -this.height / 2.0F, 0.0F);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawThemedButton(guiGraphics, this.spinButton, Component.translatable("gui.upgradermod.spin"), true, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX1, Component.literal("x1"), this.menu.getMultiplier() == 1, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX2, Component.literal("x2"), this.menu.getMultiplier() == 2, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX4, Component.literal("x4"), this.menu.getMultiplier() == 4, mouseX, mouseY);
        drawThemedButton(guiGraphics, this.btnX8, Component.literal("x8"), this.menu.getMultiplier() == 8, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        poseStack.popPose();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, BACKGROUND_COLOR);
        guiGraphics.fill(x + 2, y + 2, x + GUI_WIDTH - 2, y + GUI_HEIGHT - 2, PANEL_COLOR);
        drawFrame(guiGraphics, x, y, GUI_WIDTH, GUI_HEIGHT);

        // Слоты по новой раскладке 256x220.
        drawSlot(guiGraphics, x + 40, y + 45);
        drawSlot(guiGraphics, x + 198, y + 45);

        ItemStack target = this.menu.getTargetStack();
        if (!target.isEmpty()) {
            guiGraphics.renderItem(target, x + 198, y + 45);
            guiGraphics.renderItemDecorations(this.font, target, x + 198, y + 45);
        }

        // Инвентарь игрока сохраняет стандартные слоты меню и получает ту же тёмную подложку.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                drawSlot(guiGraphics, x + 47 + col * 18, y + 137 + row * 18);
            }
        }
        for (int col = 0; col < 9; ++col) {
            drawSlot(guiGraphics, x + 47 + col * 18, y + 195);
        }

        drawCompass(guiGraphics, x + 128, y + 110);
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

    /** Компас с концентрическими кольцами, делениями и вращающейся стрелкой. */
    private void drawCompass(GuiGraphics guiGraphics, int centerX, int centerY) {
        for (int dx = -38; dx <= 38; dx++) {
            for (int dy = -38; dy <= 38; dy++) {
                if (dx * dx + dy * dy <= 38 * 38) {
                    guiGraphics.fill(centerX + dx, centerY + dy,
                            centerX + dx + 1, centerY + dy + 1, INNER_COMPASS_COLOR);
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

        guiGraphics.drawCenteredString(this.font, "N", centerX, centerY - 34, GOLD_COLOR);
        guiGraphics.drawCenteredString(this.font, "S", centerX, centerY + 27, MUTED_COLOR);
        guiGraphics.drawString(this.font, "W", centerX - 34, centerY - 4, MUTED_COLOR);
        guiGraphics.drawString(this.font, "E", centerX + 29, centerY - 4, MUTED_COLOR);
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
            background = 0xFF555555;
            textColor = MUTED_COLOR;
        } else if (selected || button == this.spinButton) {
            background = hovered ? 0xFFFF6B8A : ACCENT_COLOR;
            textColor = 0xFFFFFFFF;
        } else {
            background = hovered ? INNER_COMPASS_COLOR : PANEL_COLOR;
            textColor = MUTED_COLOR;
        }

        guiGraphics.fill(buttonX, buttonY, buttonX + button.getWidth(), buttonY + button.getHeight(), FRAME_COLOR);
        guiGraphics.fill(buttonX + 2, buttonY + 2,
                buttonX + button.getWidth() - 2, buttonY + button.getHeight() - 2, background);
        guiGraphics.drawCenteredString(this.font, label,
                buttonX + button.getWidth() / 2, buttonY + 6, textColor);
    }

    private boolean mouseInside(Button button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Component title = Component.translatable("gui.upgradermod.title");
        guiGraphics.drawCenteredString(this.font, title, 128, 10, GOLD_COLOR);

        guiGraphics.drawString(this.font, Component.translatable("gui.upgradermod.input_hint_1"), 30, 70, TEXT_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("gui.upgradermod.input_hint_2"), 30, 82, MUTED_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("gui.upgradermod.target_hint_1"), 185, 70, TEXT_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("gui.upgradermod.target_hint_2"), 185, 82, MUTED_COLOR);

        String chanceText;
        int chanceColor;
        if (this.hasResult && System.currentTimeMillis() < this.resultDisplayUntil) {
            chanceText = this.lastResult
                    ? Component.translatable("gui.upgradermod.result_success",
                    ChanceCalculator.formatChance(this.resultChance)).getString()
                    : Component.translatable("gui.upgradermod.result_failure").getString();
            chanceColor = this.lastResult ? 0xFF44FF88 : ACCENT_COLOR;
        } else {
            chanceText = ChanceCalculator.formatChance(this.displayedChance);
            chanceColor = ACCENT_COLOR;
        }

        guiGraphics.drawCenteredString(this.font, chanceText, 128, 105, chanceColor);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.chance_label"), 128, 120, MUTED_COLOR);

        ItemStack input = this.menu.getInputStack();
        ItemStack target = this.menu.getTargetStack();
        String inputValue = String.format(Locale.ROOT, "%.0f", ValueCalculator.getItemStackValue(input));
        String targetValue = String.format(Locale.ROOT, "%.0f", ValueCalculator.getItemStackValue(target));
        guiGraphics.drawCenteredString(this.font, inputValue, 49, 64, MUTED_COLOR);
        guiGraphics.drawCenteredString(this.font, targetValue, 207, 64, MUTED_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;
        if (mouseX >= x + 198 && mouseX <= x + 216
                && mouseY >= y + 45 && mouseY <= y + 63) {
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
