package com.dalkco.chestlog;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Item counting, differences and display helpers. */
public final class ItemUtil {
    private ItemUtil() {
    }

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Total count per item id of the first {@code n} stacks. */
    public static Map<String, Integer> count(List<ItemStack> stacks, int n) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < Math.min(n, stacks.size()); i++) {
            ItemStack s = stacks.get(i);
            if (!s.isEmpty()) {
                out.merge(id(s), s.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /** after − before for every item that changed, biggest changes first. */
    public static Map<String, Integer> diff(Map<String, Integer> before, Map<String, Integer> after) {
        Set<String> keys = new HashSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        for (String k : keys) {
            int d = after.getOrDefault(k, 0) - before.getOrDefault(k, 0);
            if (d != 0) {
                list.add(Map.entry(k, d));
            }
        }
        list.sort((a, b) -> Integer.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : list) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public static ItemStack stack(String id) {
        if (id.startsWith("raw:")) {
            // an item CoreProtect named in a way we could not match: show a paper with its text
            return new ItemStack(Items.PAPER);
        }
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) {
            return new ItemStack(Items.BARRIER);
        }
        Item item = BuiltInRegistries.ITEM.getValue(rl);
        return item == null || item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
    }

    public static Component name(String id) {
        if (id.startsWith("raw:")) {
            return Component.literal(id.substring(4));
        }
        return stack(id).getHoverName();
    }

    /** "just now", "5 min ago", "2 h ago", "3 d ago". */
    public static Component ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 5) {
            return Component.translatable("chestlog.ago.now");
        }
        if (s < 60) {
            return Component.translatable("chestlog.ago.seconds", s);
        }
        if (s < 3600) {
            return Component.translatable("chestlog.ago.minutes", s / 60);
        }
        if (s < 86400) {
            return Component.translatable("chestlog.ago.hours", s / 3600);
        }
        return Component.translatable("chestlog.ago.days", s / 86400);
    }

    /** Compact age for list rows: "5s", "3m", "2h", "4d" (localised units). */
    public static String agoShort(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        String key;
        long v;
        if (s < 60) {
            key = "chestlog.short.s";
            v = s;
        } else if (s < 3600) {
            key = "chestlog.short.m";
            v = s / 60;
        } else if (s < 86400) {
            key = "chestlog.short.h";
            v = s / 3600;
        } else {
            key = "chestlog.short.d";
            v = s / 86400;
        }
        return Component.translatable(key, v).getString();
    }

    public static String signed(int n) {
        return n > 0 ? "+" + n : "−" + Math.abs(n);
    }
}
