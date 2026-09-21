package com.example.upgradermod.client;

import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetMultiplierPacket;
import com.example.upgradermod.network.SpinPacket;
import com.mojang.blaze3d.systems.RenderSystem;
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
 * Графический интерфейс апгрейдера (рулетки).
 * Размер 256x220, содержит слот ставки, слот цели, колесо рулетки, кнопки управления.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
public class UpgraderScreen extends AbstractContainerScreen<UpgraderMenu> {

    private Button spinButton;
    private Button btnX1, btnX2, btnX4, btnX8;

    // Параметры анимации прокрутки
    private boolean isSpinning = false;
    private long spinStartTime = 0;
    private float targetRollAngle = 0;
    private float currentNeedleAngle = 0;
    private Boolean lastSpinResult = null;
    private static final long SPIN_DURATION_MS = 2500;

    /**
     * Конструктор экрана апгрейдера.
     *
     * @param menu            контейнер меню
     * @param playerInventory инвентарь игрока
     * @param title           заголовок окна
     */
    public UpgraderScreen(UpgraderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        // Кнопка "КРУТИТЬ" (30, 145) размером 140x20
        spinButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.upgradermod.spin"), btn -> {
                    if (!isSpinning) {
                        NetworkHandler.sendToServer(new SpinPacket());
                    }
                }).bounds(x + 30, y + 145, 140, 20).build()
        );

        // Кнопки множителей x1, x2, x4, x8
        int multY = y + 145;
        btnX1 = this.addRenderableWidget(Button.builder(Component.literal("x1"), b -> setMultiplier(1)).bounds(x + 175, multY, 18, 20).build());
        btnX2 = this.addRenderableWidget(Button.builder(Component.literal("x2"), b -> setMultiplier(2)).bounds(x + 194, multY, 18, 20).build());
        btnX4 = this.addRenderableWidget(Button.builder(Component.literal("x4"), b -> setMultiplier(4)).bounds(x + 213, multY, 18, 20).build());
        btnX8 = this.addRenderableWidget(Button.builder(Component.literal("x8"), b -> setMultiplier(8)).bounds(x + 232, multY, 18, 20).build());
    }

    private void setMultiplier(int mult) {
        this.menu.setMultiplier(mult);
        NetworkHandler.sendToServer(new SetMultiplierPacket(mult));
    }

    /**
     * Вызывается клиентом при получении пакета SpinResultPacket.
     *
     * @param success   успешен ли спин
     * @param chance    рассчитанный шанс
     * @param rollAngle целевой угол остановки
     */
    public void onSpinResult(boolean success, double chance, float rollAngle) {
        this.isSpinning = true;
        this.spinStartTime = System.currentTimeMillis();
        this.targetRollAngle = rollAngle;
        this.lastSpinResult = success;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = this.leftPos;
        int y = this.topPos;

        // Отрисовка всплывающей подсказки над слотом цели при наведении
        ItemStack target = this.menu.getTargetStack();
        if (!target.isEmpty() && mouseX >= x + 189 && mouseX <= x + 207 && mouseY >= y + 39 && mouseY <= y + 57) {
            guiGraphics.renderTooltip(this.font, target, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Основной фон GUI 256x220
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFF3C3F41);
        guiGraphics.fill(x + 5, y + 5, x + this.imageWidth - 5, y + this.imageHeight - 5, 0xFF2B2B2B);

        // Левый слот ставки (50, 40)
        drawSlot(guiGraphics, x + 49, y + 39);

        // Правый слот цели (190, 40) - рисуется вручную
        drawSlot(guiGraphics, x + 189, y + 39);

        // Отрисовка предмета в правом слоте цели
        ItemStack target = this.menu.getTargetStack();
        if (!target.isEmpty()) {
            guiGraphics.renderItem(target, x + 190, y + 40);
            guiGraphics.renderItemDecorations(this.font, target, x + 190, y + 40);
        }

        // Инвентарь игрока (слоты)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                drawSlot(guiGraphics, x + 47 + col * 18, y + 137 + row * 18);
            }
        }
        for (int col = 0; col < 9; ++col) {
            drawSlot(guiGraphics, x + 47 + col * 18, y + 195);
        }

        // Круг-компас рулетки: центр (128, 90), радиус 45
        drawRouletteWheel(guiGraphics, x + 128, y + 90, 45);
    }

    private void drawSlot(GuiGraphics guiGraphics, int sx, int sy) {
        guiGraphics.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
        guiGraphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
    }

    private void drawRouletteWheel(GuiGraphics guiGraphics, int centerX, int centerY, int radius) {
        ItemStack input = this.menu.getInputStack();
        ItemStack target = this.menu.getTargetStack();
        double inputVal = ValueCalculator.getItemStackValue(input);
        double targetVal = ValueCalculator.getItemStackValue(target);
        int multiplier = this.menu.getMultiplier();

        double chance = ChanceCalculator.calculateChance(inputVal, targetVal, multiplier);
        float winSectorDegrees = (float) ((chance / 100.0) * 360.0);

        // Обновление угла стрелки во время прокрутки
        if (isSpinning) {
            long elapsed = System.currentTimeMillis() - spinStartTime;
            if (elapsed >= SPIN_DURATION_MS) {
                isSpinning = false;
                currentNeedleAngle = targetRollAngle;
            } else {
                float progress = (float) elapsed / SPIN_DURATION_MS;
                float easeOut = 1.0f - (float) Math.pow(1.0f - progress, 3);
                currentNeedleAngle = (360.0f * 5.0f * easeOut + targetRollAngle * easeOut) % 360.0f;
            }
        }

        // Отрисовка сектора рулетки пикселями
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distSq = dx * dx + dy * dy;
                if (distSq <= radius * radius) {
                    if (distSq >= (radius - 2) * (radius - 2)) {
                        // Обод колеса
                        guiGraphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, 0xFFE0E0E0);
                    } else {
                        // Угол точки в градусах от 0 до 360 (0 градусов вверх)
                        double angle = Math.toDegrees(Math.atan2(dx, -dy));
                        if (angle < 0) {
                            angle += 360.0;
                        }

                        if (angle < winSectorDegrees) {
                            // Зелёный сектор победы
                            guiGraphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, 0xFF2ECC71);
                        } else {
                            // Красный сектор проигрыша
                            guiGraphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, 0xFFE74C3C);
                        }
                    }
                }
            }
        }

        // Отрисовка стрелки-указателя от центра к текущему углу
        double rad = Math.toRadians(currentNeedleAngle - 90.0);
        int needleLength = radius - 6;
        int tipX = centerX + (int) (Math.cos(rad) * needleLength);
        int tipY = centerY + (int) (Math.sin(rad) * needleLength);

        // Рисуем линию стрелки
        guiGraphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFFFFD700);
        guiGraphics.fill(tipX - 1, tipY - 1, tipX + 2, tipY + 2, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Заголовок "АПГРЕЙД ПРЕДМЕТОВ" по центру вверху
        Component titleComp = Component.translatable("gui.upgradermod.title");
        int titleWidth = this.font.width(titleComp);
        guiGraphics.drawString(this.font, titleComp, (this.imageWidth - titleWidth) / 2, 8, 0xFFD700, true);

        // Подписи под слотами ставки и цели
        ItemStack input = this.menu.getInputStack();
        ItemStack target = this.menu.getTargetStack();
        double inputVal = ValueCalculator.getItemStackValue(input);
        double targetVal = ValueCalculator.getItemStackValue(target);
        int mult = this.menu.getMultiplier();

        String inputValStr = String.format(Locale.ROOT, "%.0f", inputVal);
        String targetValStr = String.format(Locale.ROOT, "%.0f", targetVal);

        guiGraphics.drawString(this.font, inputValStr, 59 - this.font.width(inputValStr) / 2, 60, 0xAAAAAA, false);
        guiGraphics.drawString(this.font, targetValStr, 198 - this.font.width(targetValStr) / 2, 60, 0xAAAAAA, false);

        // Отображение шанса
        double chance = ChanceCalculator.calculateChance(inputVal, targetVal, mult);
        String chanceStr = ChanceCalculator.formatChance(chance);
        Component chanceComp = Component.translatable("gui.upgradermod.chance", chanceStr);
        int chanceWidth = this.font.width(chanceComp);
        guiGraphics.drawString(this.font, chanceComp, (this.imageWidth - chanceWidth) / 2, 23, 0x55FF55, true);

        // Баннер результата последнего спина
        if (lastSpinResult != null && !isSpinning) {
            Component resComp = lastSpinResult ? Component.translatable("gui.upgradermod.win") : Component.translatable("gui.upgradermod.loss");
            int resColor = lastSpinResult ? 0x2ECC71 : 0xE74C3C;
            int rw = this.font.width(resComp);
            guiGraphics.drawString(this.font, resComp, (this.imageWidth - rw) / 2, 70, resColor, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;

        // Клик по правому слоту (190, 40) открывает CatalogScreen
        if (mouseX >= x + 189 && mouseX <= x + 207 && mouseY >= y + 39 && mouseY <= y + 57) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new CatalogScreen(this));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
