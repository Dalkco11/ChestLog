package com.dalkco.chestlog.mixin;

import com.dalkco.chestlog.Tracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The moment the server sends the full contents of an opened container. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void chestlog$onContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        Tracker.onContent(packet);
    }
}
