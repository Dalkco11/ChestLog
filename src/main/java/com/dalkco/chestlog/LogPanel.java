package com.dalkco.chestlog;

import com.dalkco.chestlog.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The log next to an open container: "Changes" (since last time + yours now) and "History". */
public final class LogPanel {
    public static final int ACCENT = 0xFFE8B04A;
    private static final int W = 150;
    private static final int ROW = 18;
    private static final int GRAY = 0xFF9A9AA6;
    private static final int PLUS = 0xFF6EE08A;
    private static final int MINUS = 0xFFFF6A6A;

    private static int tab;
    private static double scroll;
    private static int contentHeight;

    /** Panel rectangle of the last frame (null when not shown). */
    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static Rect panel;
    private static Rect collapseBtn;
    private static Rect tab0;
    private static Rect tab1;
    private static Rect content;
    private static Rect cpBtn;

    /** One line of the panel: a header text, an item with a count, or a history row "+64 [icon] Nick  2m". */
    private record Line(Component text, int color, String item, int amount, String who, long time) {
        static Line header(Component text, int color) {
            return new Line(text, color, null, 0, null, 0);
        }

        static Line item(String id, int amount) {
            return new Line(null, 0, id, amount, null, 0);
        }

        static Line history(String id, int amount, String who, int whoColor, long time) {
            return new Line(null, whoColor, id, amount, who, time);
        }

        int height() {
            return item == null ? 12 : ROW;
        }
    }

    private LogPanel() {
    }

    public static void reset() {
        tab = 0;
        scroll = 0;
    }

    private static Rect place(AbstractContainerScreen<?> screen, int w, int h) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        int left = a.chestlog$leftPos();
        int top = a.chestlog$topPos();
        int iw = a.chestlog$imageWidth();
        LogConfig.Panel side = LogConfig.get().panel;
        int right = left + iw + 4;
        int leftX = left - 4 - w;
        int x;
        if (side == LogConfig.Panel.LEFT) {
            x = leftX >= 2 ? leftX : right;
        } else if (side == LogConfig.Panel.RIGHT) {
            x = right + w <= screen.width - 2 ? right : leftX;
        } else {
            x = right + w <= screen.width - 2 ? right : leftX;
        }
        x = Math.max(2, Math.min(screen.width - w - 2, x));
        return new Rect(x, Math.max(2, top), w, h);
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        panel = null;
        LogConfig.Data c = LogConfig.get();
        Tracker.Session s = Tracker.session(screen);
        if (!c.enabled || c.panel == LogConfig.Panel.OFF || s == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;

        if (c.collapsed) {
            Rect r = place(screen, 22, 22);
            panel = r;
            collapseBtn = r;
            boolean hover = r.contains(mouseX, mouseY);
            round(g, r.x, r.y, r.w, r.h, 4, hover ? 0xF0302418 : 0xE0141016);
            g.item(ItemUtil.stack(s.record.block), r.x + 3, r.y + 3);
            if (!s.changes.isEmpty()) {
                round(g, r.x + r.w - 7, r.y + 1, 6, 6, 3, ACCENT);
            }
            return;
        }

        int h = Math.max(120, a.chestlog$imageHeight());
        Rect r = place(screen, W, h);
        panel = r;
        round(g, r.x - 1, r.y - 1, r.w + 2, r.h + 2, 6, 0x60E8B04A);
        round(g, r.x, r.y, r.w, r.h, 5, 0xF0121016);

        // header: icon, title, "last time …"
        g.item(ItemUtil.stack(s.record.block), r.x + 5, r.y + 5);
        Component when = s.first ? Component.translatable("chestlog.panel.first")
                : Component.translatable("chestlog.panel.last", ItemUtil.ago(s.previousSeen));
        g.text(font, trim(font, when.getString(), r.w - 30), r.x + 25, r.y + 16, GRAY, false);
        g.text(font, trim(font, Component.translatable("chestlog.panel.title").getString(), r.w - 46), r.x + 25, r.y + 5,
                0xFFFFFFFF, false);
        collapseBtn = new Rect(r.x + r.w - 17, r.y + 4, 13, 13);
        boolean ch = collapseBtn.contains(mouseX, mouseY);
        round(g, collapseBtn.x, collapseBtn.y, collapseBtn.w, collapseBtn.h, 3, ch ? ACCENT : 0x40FFFFFF);
        g.fill(collapseBtn.x + 3, collapseBtn.y + 6, collapseBtn.x + collapseBtn.w - 3, collapseBtn.y + 7,
                ch ? 0xFF101016 : 0xFFE6E6EE);

        // tabs
        int ty = r.y + 28;
        int half = (r.w - 10) / 2;
        tab0 = new Rect(r.x + 5, ty, half, 13);
        tab1 = new Rect(r.x + 5 + half, ty, half, 13);
        drawTab(g, font, tab0, Component.translatable("chestlog.panel.changes"), tab == 0, mouseX, mouseY);
        drawTab(g, font, tab1, Component.translatable("chestlog.panel.history"), tab == 1, mouseX, mouseY);

        if (CoreProtectCheck.consumeShowHistory()) {
            tab = 1;
            scroll = 0;
        }

        // "Who took it?" (CoreProtect) button at the bottom
        int bottom = r.y + r.h - 4;
        cpBtn = null;
        if (c.coreProtect && !Tracker.ENDER.equals(s.key)) {
            cpBtn = new Rect(r.x + 5, r.y + r.h - 20, r.w - 10, 15);
            boolean bh = cpBtn.contains(mouseX, mouseY);
            boolean busy = CoreProtectCheck.running();
            round(g, cpBtn.x, cpBtn.y, cpBtn.w, cpBtn.h, 4, bh && !busy ? ACCENT : 0x30E8B04A);
            Component label = Component.translatable(busy ? "chestlog.cp.busy" : "chestlog.cp.button");
            g.text(font, label, cpBtn.x + (cpBtn.w - font.width(label)) / 2, cpBtn.y + 4,
                    bh && !busy ? 0xFF101016 : 0xFFF2D49A, false);
            bottom = cpBtn.y - 3;
        }

        // content (scrollable)
        content = new Rect(r.x + 3, ty + 17, r.w - 6, bottom - (ty + 17));
        List<Line> lines = tab == 0 ? changesLines(screen, s) : historyLines(s);
        contentHeight = 0;
        for (Line l : lines) {
            contentHeight += l.height();
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - content.h)));
        g.enableScissor(content.x, content.y, content.x + content.w, content.y + content.h);
        int y = content.y - (int) scroll;
        ItemStack hovered = null;
        for (Line l : lines) {
            if (y + l.height() >= content.y && y <= content.y + content.h) {
                if (l.item == null) {
                    g.text(font, trim(font, l.text.getString(), content.w - 4), content.x + 2, y + 2, l.color, false);
                } else if (l.who != null) {
                    // history row: "+64 [icon] Nick ........ 2m"
                    String count = ItemUtil.signed(l.amount);
                    int countW = Math.max(font.width("−999"), font.width(count));
                    g.text(font, count, content.x + 2 + countW - font.width(count), y + 5,
                            l.amount > 0 ? PLUS : MINUS, false);
                    ItemStack stack = ItemUtil.stack(l.item);
                    int ix = content.x + 5 + countW;
                    g.item(stack, ix, y + 1);
                    String age = ItemUtil.agoShort(l.time);
                    int ww = font.width(age);
                    g.text(font, age, content.x + content.w - ww - 3, y + 5, 0xFF7C7C88, false);
                    int nx = ix + 19;
                    g.text(font, trim(font, l.who, content.x + content.w - ww - 7 - nx), nx, y + 5, l.color, false);
                    if (mouseX >= ix && mouseX < ix + 16 && mouseY >= Math.max(y, content.y)
                            && mouseY < Math.min(y + ROW, content.y + content.h)) {
                        hovered = stack;
                    }
                } else {
                    ItemStack stack = ItemUtil.stack(l.item);
                    g.item(stack, content.x + 2, y + 1);
                    String count = ItemUtil.signed(l.amount);
                    int cw = font.width(count);
                    g.text(font, trim(font, ItemUtil.name(l.item).getString(), content.w - 26 - cw - 4),
                            content.x + 21, y + 5, 0xFFE6E6EE, false);
                    g.text(font, count, content.x + content.w - cw - 3, y + 5, l.amount > 0 ? PLUS : MINUS, false);
                    if (mouseX >= content.x && mouseX < content.x + content.w && mouseY >= Math.max(y, content.y)
                            && mouseY < Math.min(y + ROW, content.y + content.h)) {
                        hovered = stack;
                    }
                }
            }
            y += l.height();
        }
        g.disableScissor();
        if (contentHeight > content.h) {
            int barH = Math.max(10, content.h * content.h / contentHeight);
            int barY = content.y + (int) ((content.h - barH) * (scroll / Math.max(1, contentHeight - content.h)));
            g.fill(r.x + r.w - 3, barY, r.x + r.w - 2, barY + barH, 0x80E8B04A);
        }
        if (hovered != null) {
            g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        }
    }

    private static void drawTab(GuiGraphicsExtractor g, Font font, Rect t, Component label, boolean active,
                                int mouseX, int mouseY) {
        boolean hover = t.contains(mouseX, mouseY);
        int color = active ? 0xFFFFFFFF : hover ? 0xFFD0D0D8 : GRAY;
        g.text(font, label, t.x + (t.w - font.width(label)) / 2, t.y + 2, color, false);
        g.fill(t.x + 4, t.y + t.h - 1, t.x + t.w - 4, t.y + t.h, active ? ACCENT : 0x30FFFFFF);
    }

    private static List<Line> changesLines(AbstractContainerScreen<?> screen, Tracker.Session s) {
        List<Line> out = new ArrayList<>();
        if (s.first) {
            out.add(Line.header(Component.translatable("chestlog.panel.first_hint1"), GRAY));
            out.add(Line.header(Component.translatable("chestlog.panel.first_hint2"), GRAY));
        } else {
            out.add(Line.header(Component.translatable("chestlog.panel.since"), ACCENT));
            if (s.changes.isEmpty()) {
                out.add(Line.header(Component.translatable("chestlog.panel.nothing"), GRAY));
            } else {
                for (Map.Entry<String, Integer> e : s.changes.entrySet()) {
                    out.add(Line.item(e.getKey(), e.getValue()));
                }
            }
        }
        Map<String, Integer> yours = ItemUtil.diff(s.openItems, Tracker.current(screen, s));
        if (!yours.isEmpty()) {
            out.add(Line.header(Component.empty(), GRAY));
            out.add(Line.header(Component.translatable("chestlog.panel.now"), 0xFF8FC8FF));
            for (Map.Entry<String, Integer> e : yours.entrySet()) {
                out.add(Line.item(e.getKey(), e.getValue()));
            }
        }
        return out;
    }

    private static final int CP_COLOR = 0xFFB98CFF;

    private static List<Line> historyLines(Tracker.Session s) {
        List<Line> out = new ArrayList<>();
        // own log + CoreProtect entries (with player names), newest first
        List<ChestStore.Entry> all = new ArrayList<>(s.record.history);
        all.addAll(s.record.coreProtect);
        all.sort((a, b) -> Long.compare(b.time, a.time));
        if (all.isEmpty()) {
            out.add(Line.header(Component.translatable("chestlog.panel.no_history"), GRAY));
            return out;
        }
        // one flat list: "+amount [icon] who  time", newest first
        String me = Minecraft.getInstance().player == null ? ""
                : Minecraft.getInstance().player.getName().getString();
        // CoreProtect rows, used to hide our own nameless rows that describe the same change
        List<long[]> cpRows = new ArrayList<>();
        List<String> cpItems = new ArrayList<>();
        for (ChestStore.Entry e : s.record.coreProtect) {
            for (Map.Entry<String, Integer> d : e.delta.entrySet()) {
                cpRows.add(new long[]{e.time, d.getValue()});
                cpItems.add(d.getKey());
            }
        }
        for (ChestStore.Entry e : all) {
            if (!e.cp && !cpRows.isEmpty()) {
                Map<String, Integer> left = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Integer> d : e.delta.entrySet()) {
                    int match = -1;
                    for (int i = 0; i < cpRows.size(); i++) {
                        if (cpItems.get(i).equals(d.getKey()) && cpRows.get(i)[1] == d.getValue()
                                && Math.abs(cpRows.get(i)[0] - e.time) < 30 * 60_000L) {
                            match = i;
                            break;
                        }
                    }
                    if (match >= 0) {
                        cpRows.remove(match);
                        cpItems.remove(match);
                    } else {
                        left.put(d.getKey(), d.getValue());
                    }
                }
                if (left.isEmpty()) {
                    continue;
                }
                ChestStore.Entry copy = new ChestStore.Entry();
                copy.time = e.time;
                copy.you = e.you;
                copy.delta = left;
                e = copy;
            }
            String who;
            int color;
            if (e.cp) {
                who = e.player;
                color = e.player.equalsIgnoreCase(me) ? 0xFF8FC8FF : 0xFFE6E6EE;
            } else if (e.you) {
                who = me.isEmpty() ? Component.translatable("chestlog.who.you").getString() : me;
                color = 0xFF8FC8FF;
            } else {
                who = Component.translatable("chestlog.who.someone").getString();
                color = GRAY;
            }
            for (Map.Entry<String, Integer> d : e.delta.entrySet()) {
                out.add(Line.history(d.getKey(), d.getValue(), who, color, e.time));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ clickable areas (real widgets)

    private static int[] arr(Rect r) {
        return r == null ? null : new int[]{r.x, r.y, r.w, r.h};
    }

    /** Puts the invisible widgets over the parts drawn this frame. */
    public static void syncHitAreas(HitArea collapse, HitArea t0, HitArea t1, HitArea cp, HitArea blocker) {
        boolean shown = panel != null;
        boolean full = shown && !LogConfig.get().collapsed;
        collapse.place(shown ? arr(collapseBtn) : null);
        t0.place(full ? arr(tab0) : null);
        t1.place(full ? arr(tab1) : null);
        cp.place(full ? arr(cpBtn) : null);
        blocker.place(shown ? arr(panel) : null);
    }

    /** The "Who took it?" button: run the CoreProtect check for the open container. */
    public static void checkCoreProtect(AbstractContainerScreen<?> screen) {
        Tracker.Session s = Tracker.session(screen);
        if (s != null && !CoreProtectCheck.running()) {
            CoreProtectCheck.start(s.key, s.record);
        }
    }

    public static void toggleCollapsed() {
        LogConfig.Data c = LogConfig.get();
        c.collapsed = !c.collapsed;
        LogConfig.save();
    }

    public static void setTab(int t) {
        tab = t;
        scroll = 0;
    }

    /** Mouse wheel at GUI position (x, y): scroll the panel if the cursor is over it. */
    public static boolean scrollAt(double x, double y, double amount) {
        Rect p = panel;
        if (p == null || !p.contains(x, y) || LogConfig.get().collapsed) {
            return false;
        }
        scroll -= amount * ROW;
        return true;
    }

    public static boolean scrollBy(double amount) {
        if (panel == null || LogConfig.get().collapsed) {
            return false;
        }
        scroll -= amount * ROW;
        return true;
    }

    /** Clicks on the panel; true if it was ours (also stops items being dropped outside the GUI). */
    public static boolean click(double mx, double my) {
        Rect p = panel;
        if (p == null || !p.contains(mx, my)) {
            return false;
        }
        LogConfig.Data c = LogConfig.get();
        if (collapseBtn != null && collapseBtn.contains(mx, my)) {
            c.collapsed = !c.collapsed;
            LogConfig.save();
        } else if (!c.collapsed && tab0 != null && tab0.contains(mx, my)) {
            tab = 0;
            scroll = 0;
        } else if (!c.collapsed && tab1 != null && tab1.contains(mx, my)) {
            tab = 1;
            scroll = 0;
        }
        return true;
    }

    public static boolean scroll(double mx, double my, double amount) {
        Rect p = panel;
        if (p == null || !p.contains(mx, my) || LogConfig.get().collapsed) {
            return false;
        }
        scroll -= amount * ROW;
        return true;
    }

    static String trim(Font font, String s, int max) {
        if (font.width(s) <= max) {
            return s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, max - font.width("…"))) + "…";
    }

    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }
}
