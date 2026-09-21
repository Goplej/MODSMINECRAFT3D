package com.example.upgradermod.client;

import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetTargetPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Тёмный каталог предметов для выбора цели апгрейда.
 * Содержит поиск, сетку 8x6 и подробный tooltip с ценностью и шансом.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
public class CatalogScreen extends Screen {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 220;
    private static final int ITEMS_PER_PAGE = 48;
    private static final int COLS = 8;
    private static final int ROWS = 6;

    private static final int BACKGROUND_COLOR = 0xFF1A1A2E;
    private static final int PANEL_COLOR = 0xFF16213E;
    private static final int FRAME_COLOR = 0xFF533483;
    private static final int HOVER_COLOR = 0xFF0F3460;
    private static final int ACCENT_COLOR = 0xFFE94560;
    private static final int TEXT_COLOR = 0xFFE8E8E8;
    private static final int GOLD_COLOR = 0xFFFFD700;

    private final UpgraderScreen parent;
    private EditBox searchBox;
    private Button btnPrev;
    private Button btnNext;

    private int leftPos;
    private int topPos;
    private int currentPage;
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

        this.searchBox = new EditBox(this.font, this.leftPos + 24, this.topPos + 18,
                208, 16, Component.translatable("gui.upgradermod.search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(TEXT_COLOR);
        this.addRenderableWidget(this.searchBox);

        this.btnPrev = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.upgradermod.prev"), button -> changePage(-1))
                .bounds(this.leftPos + 24, this.topPos + 185, 60, 20)
                .build());
        this.btnNext = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.upgradermod.next"), button -> changePage(1))
                .bounds(this.leftPos + 172, this.topPos + 185, 60, 20)
                .build());

        updateSearch("");
    }

    private void onSearchChanged(String query) {
        this.currentPage = 0;
        updateSearch(query);
    }

    private void updateSearch(String query) {
        this.filteredItems = ItemRegistryCache.search(query);
        updateButtonStates();
    }

    private void changePage(int delta) {
        int maxPages = getMaxPages();
        int newPage = this.currentPage + delta;
        if (newPage >= 0 && newPage < maxPages) {
            this.currentPage = newPage;
            updateButtonStates();
        }
    }

    private int getMaxPages() {
        return Math.max(1, (int) Math.ceil((double) this.filteredItems.size() / ITEMS_PER_PAGE));
    }

    private void updateButtonStates() {
        int maxPages = getMaxPages();
        if (this.btnPrev != null) {
            this.btnPrev.active = this.currentPage > 0;
        }
        if (this.btnNext != null) {
            this.btnNext.active = this.currentPage < maxPages - 1;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int x = this.leftPos;
        int y = this.topPos;
        drawPanel(guiGraphics, x, y);

        // Поле поиска получает ту же тему, что и панель каталога.
        guiGraphics.fill(x + 22, y + 16, x + 234, y + 36, FRAME_COLOR);
        guiGraphics.fill(x + 24, y + 18, x + 232, y + 34, PANEL_COLOR);

        Component title = Component.translatable("gui.upgradermod.catalog");
        guiGraphics.drawCenteredString(this.font, title, x + GUI_WIDTH / 2, y + 6, GOLD_COLOR);

        int startIndex = this.currentPage * ITEMS_PER_PAGE;
        int gridStartX = x + 24;
        int gridStartY = y + 42;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int itemIndex = startIndex + row * COLS + col;
                int slotX = gridStartX + col * 26;
                int slotY = gridStartY + row * 23;
                boolean hovered = mouseX >= slotX && mouseX <= slotX + 18
                        && mouseY >= slotY && mouseY <= slotY + 18;

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

        Component pageText = Component.translatable("gui.upgradermod.page",
                this.currentPage + 1, getMaxPages());
        guiGraphics.drawCenteredString(this.font, pageText,
                x + GUI_WIDTH / 2, y + 191, TEXT_COLOR);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawCatalogButton(guiGraphics, this.btnPrev,
                Component.translatable("gui.upgradermod.prev"), mouseX, mouseY);
        drawCatalogButton(guiGraphics, this.btnNext,
                Component.translatable("gui.upgradermod.next"), mouseX, mouseY);

        ItemStack hovered = getHoveredItem(mouseX, mouseY);
        if (!hovered.isEmpty()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hovered.getHoverName());

            long value = Math.round(ValueCalculator.getItemStackValue(hovered));
            tooltip.add(Component.literal(Component.translatable("gui.upgradermod.tooltip_value",
                    formatWithSpaces(value)).getString()).withStyle(ChatFormatting.YELLOW));

            ItemStack input = this.parent.getMenu().getInputStack();
            if (!input.isEmpty() && value > 0L) {
                double inputValue = ValueCalculator.getItemStackValue(input);
                double chance = ChanceCalculator.calculateChance(
                        inputValue, value, this.parent.getMenu().getMultiplier());
                tooltip.add(Component.literal(Component.translatable("gui.upgradermod.tooltip_chance",
                        ChanceCalculator.formatChance(chance)).getString()).withStyle(ChatFormatting.GREEN));
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
        int background = !button.active ? 0xFF555555 : (hovered ? 0xFFFF6B8A : ACCENT_COLOR);
        int textColor = button.active ? 0xFFFFFFFF : 0xFF888888;

        guiGraphics.fill(button.getX(), button.getY(),
                button.getX() + button.getWidth(), button.getY() + button.getHeight(), FRAME_COLOR);
        guiGraphics.fill(button.getX() + 2, button.getY() + 2,
                button.getX() + button.getWidth() - 2,
                button.getY() + button.getHeight() - 2, background);
        guiGraphics.drawCenteredString(this.font, label,
                button.getX() + button.getWidth() / 2, button.getY() + 6, textColor);
    }

    private ItemStack getHoveredItem(double mouseX, double mouseY) {
        int gridStartX = this.leftPos + 24;
        int gridStartY = this.topPos + 42;
        int startIndex = this.currentPage * ITEMS_PER_PAGE;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotX = gridStartX + col * 26;
                int slotY = gridStartY + row * 23;
                if (mouseX >= slotX && mouseX <= slotX + 18
                        && mouseY >= slotY && mouseY <= slotY + 18) {
                    int itemIndex = startIndex + row * COLS + col;
                    if (itemIndex < this.filteredItems.size()) {
                        return this.filteredItems.get(itemIndex);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Форматирует большие значения группами по три цифры через пробел. */
    private static String formatWithSpaces(long number) {
        String raw = String.format(Locale.ROOT, "%d", Math.max(0L, number));
        StringBuilder result = new StringBuilder(raw);
        for (int index = result.length() - 3; index > 0; index -= 3) {
            result.insert(index, ' ');
        }
        return result.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int gridStartX = this.leftPos + 24;
        int gridStartY = this.topPos + 42;
        int startIndex = this.currentPage * ITEMS_PER_PAGE;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotX = gridStartX + col * 26;
                int slotY = gridStartY + row * 23;
                if (mouseX >= slotX && mouseX <= slotX + 18
                        && mouseY >= slotY && mouseY <= slotY + 18) {
                    int itemIndex = startIndex + row * COLS + col;
                    if (itemIndex < this.filteredItems.size()) {
                        ItemStack chosen = this.filteredItems.get(itemIndex).copy();
                        chosen.setCount(1);
                        NetworkHandler.sendToServer(new SetTargetPacket(chosen));
                        this.parent.getMenu().setTargetStack(chosen);
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
