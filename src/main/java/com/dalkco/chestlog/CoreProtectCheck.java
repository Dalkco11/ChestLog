package com.dalkco.chestlog;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Who took it?": turns the CoreProtect inspector on (/co i), right-clicks the chest, reads the container
 * transactions CoreProtect prints in chat (several pages if needed), turns the inspector back off and opens the
 * chest again. The result goes into the chest's history with player names.
 */
public final class CoreProtectCheck {
    private enum Step { IDLE, ENABLE, CLICK, COLLECT, DISABLE, REOPEN }

    /** "0.52/h ago - Steve removed x12 iron_ingot." / "1,5/д назад · Steve -x3 #diamond" … */
    // not anchored: lines may start with "[!]" (rolled back) and the name may follow a player-head icon
    // \s also matches non-breaking and other unicode spaces (UNICODE_CHARACTER_CLASS); no end anchor
    private static final Pattern LINE = Pattern.compile(
            "(?<num>\\d+(?:[.,]\\d+)?)\\s*/\\s*(?<unit>[smhdwсмчдн])\\p{L}*\\.?(?:\\s+(?:ago|назад))?\\s*"
                    + "(?<sign1>[+\\-−–])?.*?(?<![A-Za-z0-9_])(?<user>[A-Za-z0-9_*.]{2,32})\\s+(?:(?<verb>\\p{L}+)\\s+)?"
                    + "(?<sign2>[+\\-−–])?\\s*x?(?<amount>\\d+)\\s*[x×]?\\s+"
                    + "(?<item>[#\\p{L}\\p{N}_:'\\-]+(?:\\s[\\p{L}\\p{N}_'\\-]+){0,3})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final java.nio.file.Path DEBUG = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("chestlog").resolve("coreprotect-last.txt");
    private static final Pattern PAGE = Pattern.compile("(?:page|страница|стр\\.?)\\s*(\\d+)\\s*/\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static Step step = Step.IDLE;
    private static String key;
    private static ChestStore.Record record;
    private static BlockPos pos;
    private static long deadline;
    private static long lastLine;
    private static boolean wasOn;
    private static boolean askedPage;
    private static int page = 1;
    private static int pages = 1;
    private static boolean sawHeader;
    private static final List<ChestStore.Entry> found = new ArrayList<>();
    /** after the chest reopens: show the History tab */
    private static boolean showHistory;

    private CoreProtectCheck() {
    }

    public static boolean running() {
        return step != Step.IDLE;
    }

    public static boolean consumeShowHistory() {
        boolean b = showHistory;
        showHistory = false;
        return b;
    }

    private static void say(Component msg, ChatFormatting color) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            p.sendSystemMessage(Component.literal("[Chest Log] ").withStyle(ChatFormatting.GOLD)
                    .append(msg.copy().withStyle(color)));
        }
    }

    private static void command(String cmd) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            p.connection.sendCommand(cmd);
        }
    }

    /** Button in the log panel. */
    public static void start(String containerKey, ChestStore.Record r) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || running() || Tracker.ENDER.equals(containerKey)) {
            return;
        }
        BlockPos at = new BlockPos(r.x, r.y, r.z);
        if (p.getEyePosition().distanceTo(Vec3.atCenterOf(at)) > p.blockInteractionRange() - 0.2) {
            say(Component.translatable("chestlog.cp.too_far"), ChatFormatting.RED);
            return;
        }
        key = containerKey;
        record = r;
        pos = at;
        found.clear();
        wasOn = false;
        askedPage = false;
        sawHeader = false;
        page = 1;
        pages = 1;
        try {
            java.nio.file.Files.deleteIfExists(DEBUG);
        } catch (Exception ignored) {
            // diagnostics only
        }
        debug("check " + containerKey + " at " + System.currentTimeMillis());
        Tracker.carryOver(containerKey);
        p.closeContainer();
        step = Step.ENABLE;
        deadline = System.currentTimeMillis() + 1500;
        command("co i");
    }

    /** Every game (system) chat message while a check runs. */
    public static void onMessage(Component message) {
        if (step == Step.IDLE) {
            return;
        }
        String raw = message.getString();
        debug(step + ": " + raw);
        // servers often put legacy colour codes (§c, §f …) right into the text: drop them before parsing
        String text = raw.replaceAll("§.", "").trim();
        String low = text.toLowerCase(Locale.ROOT);
        if (low.contains("unknown or incomplete command") || low.contains("неизвестная или неполная команда")
                || low.contains("unknown command") || (low.contains("permission") && low.contains("coreprotect"))
                || low.contains("недостаточно прав")) {
            say(Component.translatable("chestlog.cp.unavailable"), ChatFormatting.RED);
            step = Step.IDLE;
            return;
        }
        switch (step) {
            case ENABLE -> {
                if (isDisabledMsg(low)) {
                    // it was already on: our toggle switched it off, switch it back on and leave it on afterwards
                    wasOn = true;
                    command("co i");
                    deadline = System.currentTimeMillis() + 1500;
                } else if (isEnabledMsg(low)) {
                    step = Step.CLICK;
                }
            }
            case COLLECT -> {
                // the server may send the whole lookup as one message with line breaks
                for (String line : text.split("\\R")) {
                    if (!line.isBlank()) {
                        collect(line.trim(), line.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            case DISABLE -> {
                if (isDisabledMsg(low)) {
                    step = Step.REOPEN;
                }
            }
            default -> {
            }
        }
    }

    /** "Inspector now enabled." / "Инспектор теперь enabled." / "Инспектор включён" … */
    private static boolean isEnabledMsg(String low) {
        return (low.contains("inspector") || low.contains("инспектор")) && !isDisabledMsg(low)
                && (low.contains("enabled") || low.contains("включ"));
    }

    private static boolean isDisabledMsg(String low) {
        return (low.contains("inspector") || low.contains("инспектор"))
                && (low.contains("disabled") || low.contains("выключ") || low.contains("отключ"));
    }

    private static void collect(String text, String low) {
        long now = System.currentTimeMillis();
        if (low.contains("-----") || low.contains("container") || low.contains("контейнер")) {
            sawHeader = true;
            lastLine = now;
        }
        Matcher pm = PAGE.matcher(text);
        if (pm.find()) {
            page = Integer.parseInt(pm.group(1));
            pages = Integer.parseInt(pm.group(2));
            askedPage = false;
            lastLine = now;
            return;
        }
        if (low.contains("no data") || low.contains("нет данных") || low.contains("no results")) {
            sawHeader = true;
            lastLine = now - 5000;
            return;
        }
        Matcher m = LINE.matcher(text);
        while (m.find()) {
            addMatch(m, now);
        }
    }

    private static void addMatch(Matcher m, long now) {
        // the verb ("removed" / "забрал") wins; the +/- sign is the fallback
        int dir = m.group("verb") != null ? verb(m.group("verb").toLowerCase(Locale.ROOT)) : 0;
        if (dir == 0) {
            String sign = m.group("sign2") != null ? m.group("sign2") : m.group("sign1");
            dir = sign == null ? 0 : "+".equals(sign) ? 1 : -1;
        }
        if (dir == 0) {
            debug("  (no direction) " + m.group());
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(m.group("amount"));
        } catch (NumberFormatException e) {
            return;
        }
        ChestStore.Entry e = new ChestStore.Entry();
        e.cp = true;
        e.player = m.group("user");
        e.time = now - age(m.group("num"), m.group("unit"));
        e.delta.put(itemId(m.group("item")), dir * amount);
        found.add(e);
        sawHeader = true;
        lastLine = now;
        debug("  + parsed: " + e.player + " " + (dir * amount) + " " + m.group("item"));
    }

    /** Diagnostics: what CoreProtect sent during the last check (config/chestlog/coreprotect-last.txt). */
    private static void debug(String line) {
        StringBuilder esc = new StringBuilder();
        for (char ch : line.toCharArray()) {
            if (ch < 0x20 || (ch >= 0x7F && ch < 0xA0) || ch == 0xA0 || (ch >= 0xE000 && ch <= 0xF8FF)
                    || ch == 0xFFFC || (ch >= 0x2000 && ch <= 0x206F && ch != 0x2013 && ch != 0x2014)) {
                esc.append(String.format("\\u%04X", (int) ch));
            } else {
                esc.append(ch);
            }
        }
        try {
            java.nio.file.Files.createDirectories(DEBUG.getParent());
            java.nio.file.Files.writeString(DEBUG, esc + System.lineSeparator(), java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // diagnostics only
        }
    }

    private static int verb(String v) {
        if (v.startsWith("add") || v.startsWith("deposit") || v.startsWith("put") || v.startsWith("place")
                || v.startsWith("добав") || v.startsWith("полож") || v.startsWith("помест")) {
            return 1;
        }
        if (v.startsWith("remov") || v.startsWith("withdr") || v.startsWith("took") || v.startsWith("take")
                || v.startsWith("забр") || v.startsWith("взял") || v.startsWith("удал") || v.startsWith("убр")
                || v.startsWith("изъя")) {
            return -1;
        }
        return 0;
    }

    private static long age(String num, String unit) {
        double v;
        try {
            v = Double.parseDouble(num.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
        double ms = switch (unit.toLowerCase(Locale.ROOT)) {
            case "s", "с" -> 1000;
            case "m", "м" -> 60_000;
            case "h", "ч" -> 3_600_000;
            case "d", "д" -> 86_400_000;
            case "w", "н" -> 604_800_000;
            default -> 0;
        };
        return (long) (v * ms);
    }

    /** "Iron Ingot" / "#iron_ingot" / "minecraft:iron_ingot" → "minecraft:iron_ingot" (or "raw:<text>"). */
    private static String itemId(String raw) {
        String s = raw.trim().replace("#", "").toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!s.contains(":")) {
            s = "minecraft:" + s;
        }
        Identifier id = Identifier.tryParse(s);
        if (id != null && BuiltInRegistries.ITEM.getValue(id) != Items.AIR) {
            return id.toString();
        }
        return "raw:" + raw.trim();
    }

    private static void click() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) {
            return;
        }
        Vec3 c = Vec3.atCenterOf(pos);
        Direction face = Direction.getApproximateNearest(p.getEyePosition().subtract(c));
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(c.relative(face, 0.5), face, pos, false));
    }

    /** Client tick: timeouts and the steps that do not wait for a message. */
    public static void tick(Minecraft mc) {
        if (step == Step.IDLE) {
            return;
        }
        if (mc.player == null) {
            step = Step.IDLE;
            return;
        }
        long now = System.currentTimeMillis();
        switch (step) {
            case ENABLE -> {
                if (now > deadline) {
                    // no recognisable reply: assume it is on now
                    step = Step.CLICK;
                }
            }
            case CLICK -> {
                click();
                step = Step.COLLECT;
                deadline = now + 3500;
                lastLine = now;
            }
            case COLLECT -> {
                int maxPages = LogConfig.get().cpPages;
                if (!askedPage && page < pages && page < maxPages && now - lastLine > 150) {
                    askedPage = true;
                    command("co page " + (page + 1));
                    deadline = now + 3000;
                    lastLine = now;
                } else if ((sawHeader && now - lastLine > 400 && (page >= pages || page >= maxPages))
                        || now > deadline) {
                    finish();
                }
            }
            case DISABLE -> {
                if (now > deadline) {
                    step = Step.REOPEN;
                }
            }
            case REOPEN -> {
                step = Step.IDLE;
                showHistory = true;
                click();
            }
            default -> {
            }
        }
    }

    private static void finish() {
        long now = System.currentTimeMillis();
        if (!sawHeader && found.isEmpty()) {
            say(Component.translatable("chestlog.cp.no_reply"), ChatFormatting.RED);
        } else {
            record.coreProtect.clear();
            record.coreProtect.addAll(found);
            record.coreProtect.sort((a, b) -> Long.compare(b.time, a.time));
            record.cpChecked = now;
            ChestStore.save();
            say(Component.translatable("chestlog.cp.done", found.size()), ChatFormatting.WHITE);
        }
        if (wasOn) {
            step = Step.REOPEN;
        } else {
            step = Step.DISABLE;
            deadline = now + 1500;
            command("co i");
        }
    }

    public static String key() {
        return key;
    }
}
