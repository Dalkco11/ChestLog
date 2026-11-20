package com.dalkco.chestlog;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** "Where is it?": search remembered containers by item name; click a result to highlight it in the world. */
public class SearchScreen extends Screen {
    private static final int ROW = 22;

    private record Result(String key, ChestStore.Record record, String item, int count, double distance,
                          boolean sameDim) {
    }

    private final Screen parent;
    private String query;
    private final List<Result> results = new ArrayList<>();
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private double scroll;

    public SearchScreen(Screen parent, String query) {
        super(Component.translatable("chestlog.search.title"));
        this.parent = parent;
        this.query = query == null ? "" : query;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        ChestStore.ensureLoaded(mc);
        listW = Math.min(380, this.width - 20);
        listX = this.width / 2 - listW / 2;
        EditBox box = new EditBox(this.font, listX, 26, listW, 20, Component.translatable("chestlog.search.hint"));
        box.setHint(Component.translatable("chestlog.search.hint").withStyle(ChatFormatting.DARK_GRAY));
        box.setMaxLength(64);
        box.setValue(query);
        box.setResponder(s -> {
            query = s;
            scroll = 0;
            refresh();
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        listY = 52;
        listH = this.height - listY - 34;
        addRenderableWidget(Button.builder(Component.translatable("chestlog.search.highlight_all"), b -> {
                    int n = 0;
                    for (Result r : results) {
                        if (r.sameDim() && n++ < 30) {
                            Highlights.add(r.record(), label(r));
                        }
                    }
                    onClose();
                }).bounds(this.width / 2 - 154, this.height - 26, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 26, 150, 20).build());
        refresh();
    }

    private void refresh() {
        results.clear();
        Minecraft mc = Minecraft.getInstance();
        String q = query.trim().toLowerCase(Locale.ROOT);
        String dim = mc.level == null ? "" : mc.level.dimension().identifier().toString();
        for (Map.Entry<String, ChestStore.Record> e : ChestStore.all().entrySet()) {
            ChestStore.Record r = e.getValue();
            boolean same = Tracker.ENDER.equals(e.getKey()) || dim.equals(r.dim);
            double dist = mc.player == null || Tracker.ENDER.equals(e.getKey()) ? 0
                    : Math.sqrt(mc.player.distanceToSqr(r.x + 0.5, r.y + 0.5, r.z + 0.5));
            if (q.isEmpty()) {
                // nothing typed: every container with its biggest item
                String top = null;
                int topCount = 0;
                for (Map.Entry<String, Integer> it : r.items.entrySet()) {
                    if (it.getValue() > topCount) {
                        top = it.getKey();
                        topCount = it.getValue();
                    }
                }
                results.add(new Result(e.getKey(), r, top, topCount, dist, same));
                continue;
            }
            for (Map.Entry<String, Integer> it : r.items.entrySet()) {
                String name = ItemUtil.name(it.getKey()).getString().toLowerCase(Locale.ROOT);
                if (name.contains(q) || it.getKey().toLowerCase(Locale.ROOT).contains(q)) {
                    results.add(new Result(e.getKey(), r, it.getKey(), it.getValue(), dist, same));
                }
            }
        }
        results.sort(Comparator.comparing((Result r) -> !r.sameDim())
                .thenComparingDouble(Result::distance));
    }

    private static String label(Result r) {
        return r.item() == null ? Component.translatable("chestlog.search.empty_chest").getString()
                : r.count() + " × " + ItemUtil.name(r.item()).getString();
    }

    private Component place(Result r) {
        if (Tracker.ENDER.equals(r.key())) {
            return Component.translatable("chestlog.search.ender");
        }
        Component where = Component.literal(r.record().x + " " + r.record().y + " " + r.record().z);
        if (!r.sameDim()) {
            return Component.translatable("chestlog.search.other_dim", where, dimName(r.record().dim));
        }
        return Component.translatable("chestlog.search.distance", where, (int) Math.round(r.distance()));
    }

    private static Component dimName(String dim) {
        if (dim.endsWith("the_nether")) {
            return Component.translatable("chestlog.dim.nether");
        }
        if (dim.endsWith("the_end")) {
            return Component.translatable("chestlog.dim.end");
        }
        if (dim.endsWith("overworld")) {
            return Component.translatable("chestlog.dim.overworld");
        }
        return Component.literal(dim);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        int total = results.size() * ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - listH)));
        if (results.isEmpty()) {
            Component msg = ChestStore.all().isEmpty() ? Component.translatable("chestlog.search.none_saved")
                    : Component.translatable("chestlog.search.nothing");
            g.centeredText(this.font, msg, this.width / 2, listY + 20, 0xFF9A9AA6);
            return;
        }
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY - (int) scroll;
        ItemStack hovered = null;
        for (Result r : results) {
            if (y + ROW >= listY && y <= listY + listH) {
                boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= Math.max(y, listY)
                        && mouseY < Math.min(y + ROW - 2, listY + listH);
                LogPanel.round(g, listX, y, listW, ROW - 2, 4, hover ? 0xC03A2C18 : 0x90141016);
                ItemStack icon = r.item() == null ? ItemUtil.stack(r.record().block) : ItemUtil.stack(r.item());
                g.item(icon, listX + 3, y + 2);
                g.text(this.font, LogPanel.trim(this.font, label(r), listW / 2 - 30), listX + 23, y + 6,
                        r.sameDim() ? 0xFFFFFFFF : 0xFF9A9AA6, false);
                Component where = place(r);
                g.text(this.font, where, listX + listW - this.font.width(where) - 6, y + 2,
                        r.sameDim() ? LogPanel.ACCENT : 0xFF9A9AA6, false);
                Component seen = Component.translatable("chestlog.search.seen", ItemUtil.ago(r.record().lastSeen));
                g.text(this.font, seen, listX + listW - this.font.width(seen) - 6, y + 11, 0xFF80808C, false);
                if (hover && mouseX < listX + 20) {
                    hovered = icon;
                }
            }
            y += ROW;
        }
        g.disableScissor();
        if (hovered != null) {
            g.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= listX && event.x() < listX + listW && event.y() >= listY
                && event.y() < listY + listH) {
            int idx = (int) ((event.y() - listY + scroll) / ROW);
            if (idx >= 0 && idx < results.size()) {
                Result r = results.get(idx);
                Minecraft mc = Minecraft.getInstance();
                if (!Tracker.ENDER.equals(r.key())) {
                    Highlights.add(r.record(), label(r));
                }
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("[Chest Log] ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(label(r) + " — ").withStyle(ChatFormatting.WHITE))
                            .append(place(r).copy().withStyle(ChatFormatting.YELLOW)));
                }
                onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= vertical * ROW;
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
