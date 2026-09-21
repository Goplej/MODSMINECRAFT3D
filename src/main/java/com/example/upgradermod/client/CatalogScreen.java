package com.example.upgradermod.client;

import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetTargetPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Тёмный каталог предметов для выбора цели апгрейда.
 * Содержит поиск, сетку 8x6 с вертикальной прокруткой (кнопки вверх/вниз
 * и колёсико мыши) и подробный tooltip с ценностью и шансом.
 * Blacklist фильтруется до попадания предметов в список.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
public class CatalogScreen extends Screen {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 220;
    private static final int COLS = 8;
    private static final int ROWS = 6;
    private static final int GRID_X = 20;
    private static final int GRID_Y = 42;
    private static final int CELL_W = 25;
    private static final int CELL_H = 23;

    private static final int BACKGROUND_COLOR = 0xFF1A1A2E;
    private static final int PANEL_COLOR = 0xFF16213E;
    private static final int FRAME_COLOR = 0xFF533483;
    private static final int HOVER_COLOR = 0xFF0F3460;
    private static final int ACCENT_COLOR = 0xFFE94560;
    private static final int ACCENT_HOVER_COLOR = 0xFFFF6B8A;
    private static final int TEXT_COLOR = 0xFFE8E8E8;
    private static final int MUTED_COLOR = 0xFF9A9AB0;
    private static final int DISABLED_COLOR = 0xFF3A3A4A;
    private static final int GOLD_COLOR = 0xFFFFD700;

    private final UpgraderScreen parent;
    private EditBox searchBox;
    private Button btnUp;
    private Button btnDown;

    private int leftPos;
    private int topPos;
    /** Индекс верхней видимой строки списка. */
    private int scrollRow;
    private List<ItemStack> filteredItems = new ArrayList<>();

    public CatalogScreen(UpgraderScreen parent) {
        super(Component.translatable("gui.upgradermod.catalog"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        this.searchBox = new EditBox(this.font, this.leftPos + 24, this.topPos + 20,
                190, 16, Component.translatable("gui.upgradermod.search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(TEXT_COLOR);
        this.addRenderableWidget(this.searchBox);

        // Вертикальная навигация: вверх/вниз вместо горизонтальных Prev/Next.
        this.btnUp = this.addRenderableWidget(Button.builder(
                        Component.literal("\u25B2"), button -> scrollBy(-ROWS))
                .bounds(this.leftPos + 228, this.topPos + GRID_Y, 18, 20).build());
        this.btnDown = this.addRenderableWidget(Button.builder(
                        Component.literal("\u25BC"), button -> scrollBy(ROWS))
                .bounds(this.leftPos + 228, this.topPos + GRID_Y + ROWS * CELL_H - 20, 18, 20).build());

        updateSearch(this.searchBox != null ? this.searchBox.getValue() : "");
    }

    private void onSearchChanged(String query) {
        this.scrollRow = 0;
        updateSearch(query);
    }

    private void updateSearch(String query) {
        this.filteredItems = ItemRegistryCache.search(query);
        this.scrollRow = Math.min(this.scrollRow, getMaxScrollRow());
        updateButtonStates();
    }

    private int getTotalRows() {
        return Math.max(1, (int) Math.ceil((double) this.filteredItems.size() / COLS));
    }

    private int getMaxScrollRow() {
        return Math.max(0, getTotalRows() - ROWS);
    }

    private void scrollBy(int deltaRows) {
        this.scrollRow = Math.max(0, Math.min(getMaxScrollRow(), this.scrollRow + deltaRows));
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (this.btnUp != null) {
            this.btnUp.active = this.scrollRow > 0;
        }
        if (this.btnDown != null) {
            this.btnDown.active = this.scrollRow < getMaxScrollRow();
        }
    }

    /** Прокрутка колёсиком мыши (сигнатура Screen для Forge 1.20.1). */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0.0D) {
            scrollBy(-1);
        } else if (delta < 0.0D) {
            scrollBy(1);
        }
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int x = this.leftPos;
        int y = this.topPos;
        drawPanel(guiGraphics, x, y);

        guiGraphics.drawCenteredString(this.font, this.title, x + GUI_WIDTH / 2, y + 6, GOLD_COLOR);

        // Поле поиска получает ту же тему, что и панель каталога.
        guiGraphics.fill(x + 22, y + 16, x + 216, y + 38, FRAME_COLOR);
        guiGraphics.fill(x + 23, y + 17, x + 215, y + 37, PANEL_COLOR);

        // Сетка предметов с вертикальной прокруткой.
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int itemIndex = (this.scrollRow + row) * COLS + col;
                int slotX = x + GRID_X + col * CELL_W;
                int slotY = y + GRID_Y + row * CELL_H;
                boolean hovered = mouseX >= slotX && mouseX < slotX + 18
                        && mouseY >= slotY && mouseY < slotY + 18;

                int slotColor = hovered ? HOVER_COLOR : PANEL_COLOR;
                guiGraphics.fill(slotX - 1, slotY - 1, slotX + 19, slotY + 19, FRAME_COLOR);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, slotColor);

                if (itemIndex < this.filteredItems.size()) {
                    ItemStack stack = this.filteredItems.get(itemIndex);
                    guiGraphics.renderItem(stack, slotX + 1, slotY + 1);
                    guiGraphics.renderItemDecorations(this.font, stack, slotX + 1, slotY + 1);
                }
            }
        }

        // Полоса прокрутки между кнопками вверх/вниз.
        int trackTop = y + GRID_Y + 22;
        int trackBottom = y + GRID_Y + ROWS * CELL_H - 22;
        guiGraphics.fill(x + 234, trackTop, x + 240, trackBottom, BACKGROUND_COLOR);
        int maxScroll = getMaxScrollRow();
        int thumbHeight = Math.max(8, (trackBottom - trackTop) * ROWS / Math.max(ROWS, getTotalRows()));
        int thumbOffset = maxScroll == 0 ? 0
                : (trackBottom - trackTop - thumbHeight) * this.scrollRow / maxScroll;
        guiGraphics.fill(x + 234, trackTop + thumbOffset, x + 240,
                trackTop + thumbOffset + thumbHeight, ACCENT_COLOR);

        // Счётчик предметов и позиция списка.
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.items_count", this.filteredItems.size()),
                x + 120, y + 186, TEXT_COLOR);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.upgradermod.rows_position",
                        this.scrollRow + 1, Math.max(this.scrollRow + 1, getTotalRows())),
                x + 120, y + 198, MUTED_COLOR);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawCatalogButton(guiGraphics, this.btnUp, Component.literal("\u25B2"), mouseX, mouseY);
        drawCatalogButton(guiGraphics, this.btnDown, Component.literal("\u25BC"), mouseX, mouseY);

        ItemStack hovered = getHoveredItem(mouseX, mouseY);
        if (!hovered.isEmpty()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hovered.getHoverName());

            double unitValue = ValueCalculator.getItemStackValue(hovered);
            tooltip.add(Component.translatable("gui.upgradermod.tooltip_value",
                            ChanceCalculator.formatNumber(unitValue))
                    .withStyle(ChatFormatting.YELLOW));

            UpgraderMenu menu = this.parent.getMenu();
            ItemStack input = menu.getInputStack();
            if (!input.isEmpty() && unitValue > 0.0D) {
                // Отображаемый шанс детерминирован и учитывает подтверждённые сервером
                // количество цели и множитель; результат спина клиент не вычисляет.
                double inputValue = ValueCalculator.getItemStackValue(input);
                double chance = ChanceCalculator.calculateChance(
                        inputValue, unitValue * menu.getTargetCount(), menu.getMultiplier());
                tooltip.add(Component.translatable("gui.upgradermod.tooltip_chance",
                                ChanceCalculator.formatChance(chance))
                        .withStyle(ChatFormatting.GREEN));
                if (menu.getTargetCount() > 1) {
                    tooltip.add(Component.translatable("gui.upgradermod.tooltip_chance_count",
                                    menu.getTargetCount())
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, BACKGROUND_COLOR);
        guiGraphics.fill(x + 2, y + 2, x + GUI_WIDTH - 2, y + GUI_HEIGHT - 2, PANEL_COLOR);
        guiGraphics.fill(x, y, x + GUI_WIDTH, y + 2, FRAME_COLOR);
        guiGraphics.fill(x, y + GUI_HEIGHT - 2, x + GUI_WIDTH, y + GUI_HEIGHT, FRAME_COLOR);
        guiGraphics.fill(x, y, x + 2, y + GUI_HEIGHT, FRAME_COLOR);
        guiGraphics.fill(x + GUI_WIDTH - 2, y, x + GUI_WIDTH, y + GUI_HEIGHT, FRAME_COLOR);
    }

    private void drawCatalogButton(GuiGraphics guiGraphics, Button button, Component label,
                                   double mouseX, double mouseY) {
        if (button == null) {
            return;
        }

        boolean hovered = mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
        int background;
        int textColor;
        if (!button.active) {
            background = DISABLED_COLOR;
            textColor = MUTED_COLOR;
        } else {
            background = hovered ? ACCENT_HOVER_COLOR : ACCENT_COLOR;
            textColor = 0xFFFFFFFF;
        }

        guiGraphics.fill(button.getX(), button.getY(),
                button.getX() + button.getWidth(), button.getY() + button.getHeight(), FRAME_COLOR);
        guiGraphics.fill(button.getX() + 1, button.getY() + 1,
                button.getX() + button.getWidth() - 1,
                button.getY() + button.getHeight() - 1, background);
        guiGraphics.drawCenteredString(this.font, label,
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - 8) / 2, textColor);
    }

    private ItemStack getHoveredItem(double mouseX, double mouseY) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotX = this.leftPos + GRID_X + col * CELL_W;
                int slotY = this.topPos + GRID_Y + row * CELL_H;
                if (mouseX >= slotX && mouseX < slotX + 18
                        && mouseY >= slotY && mouseY < slotY + 18) {
                    int itemIndex = (this.scrollRow + row) * COLS + col;
                    if (itemIndex < this.filteredItems.size()) {
                        return this.filteredItems.get(itemIndex);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotX = this.leftPos + GRID_X + col * CELL_W;
                int slotY = this.topPos + GRID_Y + row * CELL_H;
                if (mouseX >= slotX && mouseX < slotX + 18
                        && mouseY >= slotY && mouseY < slotY + 18) {
                    int itemIndex = (this.scrollRow + row) * COLS + col;
                    if (itemIndex < this.filteredItems.size()) {
                        ItemStack chosen = this.filteredItems.get(itemIndex).copy();
                        chosen.setCount(1);
                        // Цель отправляется на сервер; сервер проверяет blacklist
                        // и подтверждает состояние через SyncStatePacket.
                        NetworkHandler.sendToServer(new SetTargetPacket(chosen));
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this.parent);
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
