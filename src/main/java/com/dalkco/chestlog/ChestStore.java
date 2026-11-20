package com.dalkco.chestlog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Remembered containers of the current server / world, saved to config/chestlog/<world>.json. */
public final class ChestStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chestlog");

    /** One change: items that came in (+) or went out (−). */
    public static final class Entry {
        public long time;
        /** true = while you had it open, false = found when you opened it */
        public boolean you;
        /** from CoreProtect: who did it */
        public boolean cp;
        public String player = "";
        public Map<String, Integer> delta = new LinkedHashMap<>();
    }

    public static final class Record {
        /** "minecraft:overworld" etc., empty for the ender chest */
        public String dim = "";
        public int x;
        public int y;
        public int z;
        /** block id, e.g. minecraft:chest */
        public String block = "";
        /** title of the container screen (custom names show up here) */
        public String title = "";
        public long lastSeen;
        public Map<String, Integer> items = new HashMap<>();
        public List<Entry> history = new ArrayList<>();
        /** last CoreProtect lookup (newest first) and when it was made */
        public List<Entry> coreProtect = new ArrayList<>();
        public long cpChecked;
    }

    private static String worldKey = "";
    private static Map<String, Record> records = new HashMap<>();

    private ChestStore() {
    }

    /** "server:play.example.net" / "sp:My World". */
    public static String currentWorld(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            return "server_" + server.ip;
        }
        if (mc.getSingleplayerServer() != null) {
            return "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "unknown";
    }

    private static Path file(String key) {
        return DIR.resolve(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    /** Loads the data of the world we are in now (no-op if it is already loaded). */
    public static void ensureLoaded(Minecraft mc) {
        String key = currentWorld(mc);
        if (key.equals(worldKey)) {
            return;
        }
        worldKey = key;
        records = new HashMap<>();
        Path f = file(key);
        try {
            if (Files.exists(f)) {
                Map<String, Record> loaded = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Record>>() { }.getType());
                if (loaded != null) {
                    records = loaded;
                    for (Record r : records.values()) {
                        if (r.coreProtect == null) {
                            r.coreProtect = new ArrayList<>();
                        }
                        if (r.history == null) {
                            r.history = new ArrayList<>();
                        }
                    }
                }
            }
        } catch (Exception e) {
            ChestLogClient.LOGGER.warn("Failed to read {}", f, e);
        }
    }

    public static void unload() {
        worldKey = "";
        records = new HashMap<>();
    }

    public static void save() {
        if (worldKey.isEmpty()) {
            return;
        }
        Path f = file(worldKey);
        try {
            Files.createDirectories(DIR);
            Files.writeString(f, GSON.toJson(records), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ChestLogClient.LOGGER.warn("Failed to write {}", f, e);
        }
    }

    public static Record get(String key) {
        return records.get(key);
    }

    public static void put(String key, Record r) {
        records.put(key, r);
    }

    public static void forget(String key) {
        if (records.remove(key) != null) {
            save();
        }
    }

    public static void clearWorld() {
        records.clear();
        save();
    }

    public static Map<String, Record> all() {
        return records;
    }
}
