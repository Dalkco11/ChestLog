package com.dalkco.chestlog;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestLogClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("chestlog");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chestlog", "chestlog"));

    private static KeyMapping searchKey;
    /** screen to open on the next tick (after chat has closed) */
    private static Screen pending;

    @Override
    public void onInitializeClient() {
        LogConfig.load();
        searchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.chestlog.search",
                InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) {
                Tracker.onUse(level, hit.getBlockPos());
            }
            return InteractionResult.PASS;
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Tracker.reset();
            ChestStore.ensureLoaded(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Tracker.reset();
            Highlights.clear();
            ChestStore.unload();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (searchKey.consumeClick()) {
                pending = new SearchScreen(null, "");
            }
            if (pending != null && client.gui.screen() == null) {
                Screen s = pending;
                pending = null;
                client.gui.setScreen(s);
            }
            Highlights.tick(client);
            CoreProtectCheck.tick(client);
        });

        // CoreProtect answers in chat while a "Who took it?" check runs
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                CoreProtectCheck.onMessage(message);
            }
        });

        // the log panel next to container screens
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) {
                return;
            }
            LogPanel.reset();
            // invisible widgets over the panel: the screen hands clicks and scrolling to widgets reliably
            HitArea collapse = new HitArea(LogPanel::toggleCollapsed, null);
            HitArea tab0 = new HitArea(() -> LogPanel.setTab(0), null);
            HitArea tab1 = new HitArea(() -> LogPanel.setTab(1), null);
            HitArea cp = new HitArea(() -> LogPanel.checkCoreProtect(cs), null);
            HitArea blocker = new HitArea(null, LogPanel::scrollBy);
            // order matters: the first widget under the mouse gets the click, the blocker is last
            Screens.getWidgets(screen).add(collapse);
            Screens.getWidgets(screen).add(tab0);
            Screens.getWidgets(screen).add(tab1);
            Screens.getWidgets(screen).add(cp);
            Screens.getWidgets(screen).add(blocker);
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
                LogPanel.render(cs, g, mouseX, mouseY);
                LogPanel.syncHitAreas(collapse, tab0, tab1, cp, blocker);
            });
            ScreenEvents.remove(screen).register(s -> Tracker.onClose(cs));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("chestlog")
                        .executes(ctx -> open(new SearchScreen(null, "")))
                        .then(ClientCommands.literal("config").executes(ctx -> open(new LogConfigScreen(null))))
                        .then(ClientCommands.literal("clear").executes(ctx -> {
                            Highlights.clear();
                            return 1;
                        }))
                        .then(ClientCommands.literal("find")
                                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                                        .executes(ctx -> open(new SearchScreen(null,
                                                StringArgumentType.getString(ctx, "item"))))))));
    }

    private static int open(Screen screen) {
        pending = screen;
        return 1;
    }
}
