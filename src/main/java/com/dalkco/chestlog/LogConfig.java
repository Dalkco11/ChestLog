package com.dalkco.chestlog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, saved to config/chestlog.json. */
public final class LogConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chestlog.json");

    public enum Panel { AUTO, RIGHT, LEFT, OFF }

    public static final class Data {
        public boolean enabled = true;
        public Panel panel = Panel.AUTO;
        public boolean collapsed = false;
        public boolean logOwn = true;
        public int historySize = 30;
        public int highlightSeconds = 60;
        public boolean trackChests = true;
        public boolean trackBarrels = true;
        public boolean trackShulkers = true;
        public boolean trackEnder = true;
        public boolean trackOther = false;
        /** "Who took it?" button that asks CoreProtect */
        public boolean coreProtect = true;
        /** how many CoreProtect pages to read */
        public int cpPages = 2;
    }

    private static Data data = new Data();

    private LogConfig() {
    }

    public static Data get() {
        return data;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                if (d != null) {
                    data = d;
                }
            }
        } catch (Exception e) {
            ChestLogClient.LOGGER.warn("Failed to read {}", FILE, e);
        }
        normalize();
    }

    private static void normalize() {
        if (data.panel == null) {
            data.panel = Panel.AUTO;
        }
        data.historySize = Mth.clamp(data.historySize, 5, 100);
        data.highlightSeconds = Mth.clamp(data.highlightSeconds, 10, 300);
        data.cpPages = Mth.clamp(data.cpPages, 1, 5);
    }

    public static void save() {
        normalize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ChestLogClient.LOGGER.warn("Failed to write {}", FILE, e);
        }
    }

    public static void reset() {
        data = new Data();
        save();
    }
}
