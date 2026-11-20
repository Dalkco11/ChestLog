package com.dalkco.chestlog.mixin;

import com.dalkco.chestlog.LogPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse wheel over the log panel scrolls the log. Done before the screen sees the wheel, because inventory mods
 * (Mouse Tweaks, Item Scroller, Inventory Profiles Next) take wheel events in container screens for themselves.
 */
@Mixin(value = MouseHandler.class, priority = 500)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void chestlog$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (yOffset == 0 || !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
            return;
        }
        MouseHandler self = (MouseHandler) (Object) this;
        double x = self.getScaledXPos(mc.getWindow());
        double y = self.getScaledYPos(mc.getWindow());
        if (LogPanel.scrollAt(x, y, Math.signum(yOffset))) {
            ci.cancel();
        }
    }
}
