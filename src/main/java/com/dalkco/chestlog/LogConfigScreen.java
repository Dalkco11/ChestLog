package com.dalkco.chestlog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/** Settings: behaviour on the left, which containers to track on the right. */
public class LogConfigScreen extends Screen {
    private static final int COL_W = 160;
    private static final int H = 20;
    private static final int GAP = 22;

    private final Screen parent;
    private int top;
    private boolean confirmClear;

    public LogConfigScreen(Screen parent) {
        super(Component.translatable("chestlog.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LogConfig.Data c = LogConfig.get();
        top = Math.max(32, this.height / 2 - 4 * GAP - 20);
        int lx = this.width / 2 - COL_W - 4;
        int rx = this.width / 2 + 4;

        int y = top;
        y = toggle(lx, y, "enabled", c.enabled, v -> c.enabled = v);
        addRenderableWidget(tip(CycleButton.<LogConfig.Panel>builder(
                        p -> Component.translatable("chestlog.panel_side." + p.name().toLowerCase(Locale.ROOT)), c.panel)
                .withValues(LogConfig.Panel.values())
                .create(lx, y, COL_W, H, Component.translatable("chestlog.config.panel"),
                        (b, v) -> { c.panel = v; LogConfig.save(); }), "panel"));
        y += GAP;
        y = toggle(lx, y, "log_own", c.logOwn, v -> c.logOwn = v);
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 5, 100, 5, c.historySize,
                v -> Component.translatable("chestlog.config.history", v), v -> c.historySize = v), "history"));
        y += GAP;
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 10, 300, 10, c.highlightSeconds,
                v -> Component.translatable("chestlog.config.highlight", v), v -> c.highlightSeconds = v), "highlight"));
        y += GAP;
        y = toggle(lx, y, "coreprotect", c.coreProtect, v -> c.coreProtect = v);
        addRenderableWidget(tip(new IntSlider(lx, y, COL_W, H, 1, 5, 1, c.cpPages,
                v -> Component.translatable("chestlog.config.cp_pages", v), v -> c.cpPages = v), "cp_pages"));

        y = top;
        y = toggle(rx, y, "chests", c.trackChests, v -> c.trackChests = v);
        y = toggle(rx, y, "barrels", c.trackBarrels, v -> c.trackBarrels = v);
        y = toggle(rx, y, "shulkers", c.trackShulkers, v -> c.trackShulkers = v);
        y = toggle(rx, y, "ender", c.trackEnder, v -> c.trackEnder = v);
        toggle(rx, y, "other", c.trackOther, v -> c.trackOther = v);

        int by = top + 7 * GAP + 12;
        addRenderableWidget(Button.builder(Component.translatable("chestlog.config.search"),
                        b -> this.minecraft.gui.setScreen(new SearchScreen(this, "")))
                .bounds(lx, by, COL_W, H).build());
        Minecraft mc = Minecraft.getInstance();
        Button clear = addRenderableWidget(Button.builder(Component.translatable(
                        confirmClear ? "chestlog.config.clear.confirm" : "chestlog.config.clear"), b -> {
                    if (confirmClear) {
                        ChestStore.ensureLoaded(mc);
                        ChestStore.clearWorld();
                        Highlights.clear();
                        confirmClear = false;
                    } else {
                        confirmClear = true;
                    }
                    rebuildWidgets();
                }).bounds(rx, by, COL_W, H)
                .tooltip(Tooltip.create(Component.translatable("chestlog.config.clear.tooltip"))).build());
        clear.active = mc.level != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, by + 26, 200, H).build());
    }

    private int toggle(int x, int y, String key, boolean value, Consumer<Boolean> setter) {
        addRenderableWidget(tip(CycleButton.onOffBuilder(value)
                .create(x, y, COL_W, H, Component.translatable("chestlog.config." + key), (b, v) -> {
                    setter.accept(v);
                    LogConfig.save();
                }), key));
        return y + GAP;
    }

    private static <T extends AbstractWidget> T tip(T widget, String key) {
        widget.setTooltip(Tooltip.create(Component.translatable("chestlog.config." + key + ".tooltip")));
        return widget;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            ChestStore.ensureLoaded(mc);
            g.centeredText(this.font, Component.translatable("chestlog.config.count", ChestStore.all().size()),
                    this.width / 2, top - 14, 0xFF9A9AA6);
        }
        g.text(this.font, Component.translatable("chestlog.config.track"), this.width / 2 + 4, top - 12, 0xFFE8B04A, false);
    }

    @Override
    public void onClose() {
        LogConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
