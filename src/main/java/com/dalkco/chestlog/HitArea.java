package com.dalkco.chestlog;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.DoublePredicate;

/**
 * Invisible clickable area over a part of the log panel. A real widget, so the screen hands it the click
 * (and the click does not drop the item you are holding).
 */
public class HitArea extends AbstractWidget {
    private final Runnable onClick;
    private final DoublePredicate onScroll;

    public HitArea(Runnable onClick, DoublePredicate onScroll) {
        super(0, 0, 0, 0, Component.empty());
        this.onClick = onClick;
        this.onScroll = onScroll;
        this.visible = false;
    }

    /** Move over the given rectangle, or hide when it is null. */
    public void place(int[] rect) {
        if (rect == null) {
            this.visible = false;
            return;
        }
        this.visible = true;
        setX(rect[0]);
        setY(rect[1]);
        setWidth(rect[2]);
        setHeight(rect[3]);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // drawn by LogPanel
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (onClick != null) {
            onClick.run();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return this.visible && isMouseOver(mouseX, mouseY) && onScroll != null && onScroll.test(vertical);
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        if (onClick != null) {
            super.playDownSound(sounds);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
