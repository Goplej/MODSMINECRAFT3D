package com.example.upgradermod.client;

import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetTargetPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран каталога предметов для выбора цели апгрейда.
 * Размер 256x220, содержит строку поиска, сетку 8x6 (48 предметов на страницу) и пагинацию.
 *
 * @author Popipok
 */
@OnlyIn(Dist.CLIENT)
public class CatalogScreen extends Screen {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 220;
    private static final int ITEMS_PER_PAGE = 48; // Сетка 8x6 = 48 предметов
    private static final int COLS = 8;
    private static final int ROWS = 6;

    private final UpgraderScreen parent;
    private EditBox searchBox;
    private Button btnPrev;
    private Button btnNext;

    private int leftPos;
    private int topPos;
    private int currentPage = 0;
    private List<ItemStack> filteredItems = new ArrayList<>();

    /**
     * Конструктор экрана каталога.
     *
     * @param parent родительский экран UpgraderScreen
     */
    public CatalogScreen(UpgraderScreen parent) {
        super(Component.translatable("gui.upgradermod.catalog"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        // Поле ввода поиска сверху
        this.searchBox = new EditBox(this.font, this.leftPos + 24, this.topPos + 18, 208, 16, Component.translatable("gui.upgradermod.search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        // Кнопки пагинации
        this.btnPrev = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.upgradermod.prev"), b -> changePage(-1))
                        .bounds(this.leftPos + 24, this.topPos + 185, 60, 20)
                        .build()
        );

        this.btnNext = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.upgradermod.next"), b -> changePage(1))
                        .bounds(this.leftPos + 172, this.topPos + 185, 60, 20)
                        .build()
        );

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

        // Фон GUI 256x220
        guiGraphics.fill(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(x + 2, y + 2, x + GUI_WIDTH - 2, y + GUI_HEIGHT - 2, 0xFF3C3F41);
        guiGraphics.fill(x + 5, y + 5, x + GUI_WIDTH - 5, y + GUI_HEIGHT - 5, 0xFF2B2B2B);

        // Заголовок
        Component titleComp = Component.translatable("gui.upgradermod.catalog");
        guiGraphics.drawString(this.font, titleComp, x + (GUI_WIDTH - this.font.width(titleComp)) / 2, y + 6, 0xFFD700, true);

        // Отрисовка сетки 8x6 = 48 слотов
        int startIndex = this.currentPage * ITEMS_PER_PAGE;
        ItemStack hoveredStack = ItemStack.EMPTY;

        int gridStartX = x + 24;
        int gridStartY = y + 42;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int itemIndex = startIndex + row * COLS + col;
                int slotX = gridStartX + col * 26;
                int slotY = gridStartY + row * 23;

                // Фон ячейки
                boolean hovered = mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= slotY && mouseY <= slotY + 18;
                int slotColor = hovered ? 0xFFFFFFFF : 0xFF373737;
                guiGraphics.fill(slotX - 1, slotY - 1, slotX + 19, slotY + 19, slotColor);
                guiGraphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);

                if (itemIndex < this.filteredItems.size()) {
                    ItemStack stack = this.filteredItems.get(itemIndex);
                    guiGraphics.renderItem(stack, slotX + 1, slotY + 1);
                    guiGraphics.renderItemDecorations(this.font, stack, slotX + 1, slotY + 1);

                    if (hovered) {
                        hoveredStack = stack;
                    }
                }
            }
        }

        // Индикатор текущей страницы
        Component pageText = Component.translatable("gui.upgradermod.page", this.currentPage + 1, getMaxPages());
        int pageTextWidth = this.font.width(pageText);
        guiGraphics.drawString(this.font, pageText, x + (GUI_WIDTH - pageTextWidth) / 2, y + 191, 0xFFFFFF, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Тултип предмета при наведении
        if (!hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;

        int gridStartX = x + 24;
        int gridStartY = y + 42;
        int startIndex = this.currentPage * ITEMS_PER_PAGE;

        // Проверяем клик по ячейкам сетки
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotX = gridStartX + col * 26;
                int slotY = gridStartY + row * 23;

                if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= slotY && mouseY <= slotY + 18) {
                    int itemIndex = startIndex + row * COLS + col;
                    if (itemIndex < this.filteredItems.size()) {
                        ItemStack chosen = this.filteredItems.get(itemIndex).copy();
                        chosen.setCount(1);

                        // Отправка пакета на сервер
                        NetworkHandler.sendToServer(new SetTargetPacket(chosen));
                        this.parent.getMenu().setTargetStack(chosen);

                        // Закрываем каталог и возвращаемся в интерфейс апгрейдера
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
