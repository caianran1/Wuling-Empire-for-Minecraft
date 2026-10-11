package com.wuling.empire.client;

import com.wuling.empire.network.ModMessages;
import com.wuling.empire.network.WuLingBookPacket;
import com.wuling.empire.wuling.BookCost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 附魔书凝聚界面 —— <b>书武灵专属的 Shift+M 入口</b>（2026-10-01 用户口径）。
 *
 * <p>其余武灵的 Shift+M 是「按下去就掉实物」，但书武灵的实物是一本附魔书，
 * 附魔和等级都得玩家说了算，所以这一档改为先开这个界面：
 * 列出全部附魔，每行自带 {@code −} / {@code +} 调等级，点行尾的「凝聚」生成。
 * 界面只负责选择 —— 判定（是否已开启武灵、是不是书武灵、灵力够不够、等级是否越界）
 * 全都在服务端 {@code WuLingBinding#condenseBook} 再做一次。
 *
 * <p>2026-10-02 用户口径：「附魔书凝聚等级越高，附魔书越稀有消耗越大」——
 * 所以每行都会实时显示该等级的消耗灵力值，<b>附魔越稀有、等级越高数字越大</b>，
 * 灵力不够时数字标红（算法见 {@link BookCost}，与服务端同一份）。
 *
 * <p>纯手写渲染 + 自己算命中区，不用 {@code ObjectSelectionList}：
 * 行内要塞两组小按钮，走原版列表反而更绕。
 */
public class WuLingBookScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 210;
    private static final int ROW_H = 18;
    private static final int LIST_PAD = 8;
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 18;

    /** 行尾三件套的宽度：− / 等级 / + / 凝聚 */
    private static final int STEP_W = 12;
    private static final int LEVEL_W = 30;
    private static final int BTN_W = 34;
    private static final int GAP = 4;

    private static final int COL_TEXT = 0xFFE6E6E6;
    private static final int COL_DIM = 0xFF9A9A9A;
    private static final int COL_TITLE = 0xFFAA66FF;
    private static final int COL_BTN = 0xFF3A3A6A;
    private static final int COL_BTN_HOVER = 0xFF5A5A9A;
    private static final int COL_BTN_BORDER = 0xFF8080C0;
    private static final int COL_ROW_HOVER = 0x30FFFFFF;
    /** 消耗数字：灵力够 / 不够 */
    private static final int COL_COST = 0xFFE0C060;
    private static final int COL_COST_LOW = 0xFFE06060;

    /** 一行 = 一条附魔 + 当前选的等级 */
    private static final class Row {
        final Enchantment ench;
        int level = 1;

        Row(Enchantment ench) {
            this.ench = ench;
        }

        Component name() {
            return Component.translatable(ench.getDescriptionId());
        }

        /**
         * 当前等级要消耗的灵力；附魔越稀有、等级越高越贵，境界越高越省
         * （见 {@link BookCost} 与 ClientWuLingData#realmOrdinal()）
         */
        float cost() {
            return BookCost.of(ench, level, ClientWuLingData.realmOrdinal());
        }

        String sortKey() {
            return name().getString();
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int scroll = 0;

    public WuLingBookScreen() {
        super(Component.translatable("wuling.book.title"));
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
            rows.add(new Row(ench));
        }
        // 按本地化名字排，找起来顺一点（几十条，人工扫的时候差别很大）
        rows.sort(Comparator.comparing(Row::sortKey));
        scroll = 0;
    }

    // ===================== 布局 =====================

    private int left() {
        return (this.width - PANEL_W) / 2;
    }

    private int top() {
        return (this.height - PANEL_H) / 2;
    }

    private int listTop() {
        return top() + HEADER_H;
    }

    private int listBottom() {
        return top() + PANEL_H - FOOTER_H;
    }

    private int listHeight() {
        return listBottom() - listTop();
    }

    private int listLeft() {
        return left() + LIST_PAD;
    }

    private int listRight() {
        return left() + PANEL_W - LIST_PAD;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - listHeight());
    }

    private int condenseX() {
        return listRight() - BTN_W;
    }

    private int plusX() {
        return condenseX() - GAP - STEP_W;
    }

    private int levelX() {
        return plusX() - LEVEL_W;
    }

    private int minusX() {
        return levelX() - GAP - STEP_W;
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int l = left();
        int t = top();
        // 底：外圈描边 + 半透明深色面板
        graphics.fill(l - 1, t - 1, l + PANEL_W + 1, t + PANEL_H + 1, 0xFF000000);
        graphics.fill(l, t, l + PANEL_W, t + PANEL_H, 0xC8101018);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, t + 8, COL_TITLE);

        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        int lt = listTop();
        int lb = listBottom();
        graphics.enableScissor(listLeft(), lt, listRight(), lb);
        int first = Math.max(0, scroll / ROW_H - 1);
        int last = Math.min(rows.size(), (scroll + listHeight()) / ROW_H + 2);
        for (int i = first; i < last; i++) {
            int rowY = lt + i * ROW_H - scroll;
            drawRow(graphics, i, rowY, mouseX, mouseY, lt, lb);
        }
        graphics.disableScissor();

        drawScrollbar(graphics, lt, lb);

        graphics.drawCenteredString(this.font,
                Component.translatable("wuling.book.hint",
                        trim(ClientSpiritData.getSpirit()), trim(ClientSpiritData.getMax())),
                this.width / 2, t + PANEL_H - 13, COL_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics graphics, int index, int y,
                         int mouseX, int mouseY, int listTop, int listBottom) {
        Row row = rows.get(index);
        int x0 = listLeft();
        int x1 = listRight();

        boolean hovered = mouseX >= x0 && mouseX < x1
                && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + ROW_H, listBottom);
        if (hovered) {
            graphics.fill(x0, y, x1, y + ROW_H, COL_ROW_HOVER);
        }

        // 消耗数字：附魔越稀有 / 等级越高越大，灵力不够时标红
        float cost = row.cost();
        String costText = trim(cost);
        int costX = minusX() - 6 - this.font.width(costText);
        boolean affordable = ClientSpiritData.getSpirit() >= cost - 0.001F;
        graphics.drawString(this.font, costText, costX, y + 5,
                affordable ? COL_COST : COL_COST_LOW, false);

        // 附魔名（太长就截断，别压到消耗数字与右边的按钮上）
        String name = this.font.plainSubstrByWidth(row.name().getString(), costX - 6 - (x0 + 4));
        graphics.drawString(this.font, name, x0 + 4, y + 5, COL_TEXT, false);

        int btnY = y + 2;
        int btnH = ROW_H - 4;
        drawButton(graphics, minusX(), btnY, STEP_W, btnH, "-", hit(mouseX, mouseY, minusX(), btnY, STEP_W, btnH));
        drawButton(graphics, plusX(), btnY, STEP_W, btnH, "+", hit(mouseX, mouseY, plusX(), btnY, STEP_W, btnH));

        String level = Component.translatable("enchantment.level." + row.level).getString();
        graphics.drawCenteredString(this.font, level, levelX() + LEVEL_W / 2, y + 5,
                row.level > 1 ? 0xFFFFD060 : COL_DIM);

        drawButton(graphics, condenseX(), btnY, BTN_W, btnH,
                Component.translatable("wuling.book.condense").getString(),
                hit(mouseX, mouseY, condenseX(), btnY, BTN_W, btnH));
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            String label, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, hovered ? COL_BTN_HOVER : COL_BTN);
        graphics.fill(x, y, x + w, y + 1, COL_BTN_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COL_BTN_BORDER);
        graphics.fill(x, y, x + 1, y + h, COL_BTN_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COL_BTN_BORDER);
        graphics.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2 + 1, COL_TEXT);
    }

    private void drawScrollbar(GuiGraphics graphics, int lt, int lb) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackH = lb - lt;
        int barH = Math.max(12, trackH * trackH / (rows.size() * ROW_H));
        int barY = lt + (trackH - barH) * scroll / max;
        int x = left() + PANEL_W - 5;
        graphics.fill(x, lt, x + 2, lb, 0x40FFFFFF);
        graphics.fill(x, barY, x + 2, barY + barH, 0xFFAAAAAA);
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 25.0 → "25"，22.5 → "22.5" */
    private static String trim(double value) {
        return Math.abs(value - Math.round(value)) < 0.005D
                ? String.valueOf(Math.round(value))
                : String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }

    // ===================== 交互 =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int lt = listTop();
        int lb = listBottom();
        if (mouseX < listLeft() || mouseX >= listRight() || mouseY < lt || mouseY >= lb) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int index = (int) ((mouseY - lt + scroll) / ROW_H);
        if (index < 0 || index >= rows.size()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int y = lt + index * ROW_H - scroll;
        // 只认完整落在列表里的那几行，半截行（滚动边界）不响应点击
        if (y < lt || y + ROW_H > lb) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Row row = rows.get(index);
        int btnY = y + 2;
        int btnH = ROW_H - 4;

        if (hit((int) mouseX, (int) mouseY, minusX(), btnY, STEP_W, btnH)) {
            row.level = Math.max(1, row.level - 1);
            return true;
        }
        if (hit((int) mouseX, (int) mouseY, plusX(), btnY, STEP_W, btnH)) {
            row.level = Math.min(row.ench.getMaxLevel(), row.level + 1);
            return true;
        }
        if (hit((int) mouseX, (int) mouseY, condenseX(), btnY, BTN_W, btnH)) {
            condense(row);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * ROW_H * 2)));
        return true;
    }

    /** 把选择发给服务端；服务端生成后由它自己发提示，这里直接关界面 */
    private void condense(Row row) {
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(row.ench);
        if (id == null) {
            return;
        }
        ModMessages.INSTANCE.sendToServer(new WuLingBookPacket(id.toString(), row.level));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
