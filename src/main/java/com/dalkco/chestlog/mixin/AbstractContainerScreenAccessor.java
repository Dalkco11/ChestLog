package com.dalkco.chestlog.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the container GUI is, so the log panel can sit next to it. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int chestlog$leftPos();

    @Accessor("topPos")
    int chestlog$topPos();

    @Accessor("imageWidth")
    int chestlog$imageWidth();

    @Accessor("imageHeight")
    int chestlog$imageHeight();
}
