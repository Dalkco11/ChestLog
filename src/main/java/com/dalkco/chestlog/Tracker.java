package com.dalkco.chestlog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ties together: the block you right-clicked → the contents the server sends when the screen opens →
 * what is in it when you close it.
 */
public final class Tracker {
    public static final String ENDER = "ender";

    private record Pending(String key, String dim, BlockPos pos, String block, long time) {
    }

    /** An open container we are watching. */
    public static final class Session {
        public final String key;
        public final int containerId;
        public final int size;
        public final Map<String, Integer> openItems;
        /** what changed without you since you last saw it (empty the first time) */
        public final Map<String, Integer> changes;
        public final long previousSeen;
        public final boolean first;
        public final ChestStore.Record record;

        Session(String key, int containerId, int size, Map<String, Integer> openItems, Map<String, Integer> changes,
                long previousSeen, boolean first, ChestStore.Record record) {
            this.key = key;
            this.containerId = containerId;
            this.size = size;
            this.openItems = openItems;
            this.changes = changes;
            this.previousSeen = previousSeen;
            this.first = first;
            this.record = record;
        }
    }

    private static Pending pending;
    private static Session session;

    /** What the panel showed before the "Who took it?" check closed the chest; restored when it reopens. */
    private record Carry(String key, Map<String, Integer> changes, long previousSeen, boolean first, long until) {
    }

    private static Carry carry;

    /** Keep the current "since last time" changes across the close / reopen of a CoreProtect check. */
    public static void carryOver(String key) {
        Session s = session;
        if (s != null && s.key.equals(key)) {
            carry = new Carry(key, s.changes, s.previousSeen, s.first, System.currentTimeMillis() + 30_000);
        }
    }

    private Tracker() {
    }

    /** Right-click on a block (client side): remember it if it is a container we track. */
    public static void onUse(Level level, BlockPos pos) {
        LogConfig.Data c = LogConfig.get();
        if (!c.enabled) {
            return;
        }
        BlockState st = level.getBlockState(pos);
        Block b = st.getBlock();
        String dim = level.dimension().identifier().toString();
        String blockId = BuiltInRegistries.BLOCK.getKey(b).toString();
        BlockPos keyPos = pos;
        String key;
        if (b instanceof EnderChestBlock) {
            if (!c.trackEnder) {
                return;
            }
            key = ENDER;
            dim = "";
        } else {
            boolean tracked = b instanceof ChestBlock ? c.trackChests
                    : b instanceof BarrelBlock ? c.trackBarrels
                    : b instanceof ShulkerBoxBlock ? c.trackShulkers
                    : (b instanceof HopperBlock || b instanceof DispenserBlock) && c.trackOther;
            if (!tracked) {
                return;
            }
            if (b instanceof ChestBlock && st.hasProperty(ChestBlock.TYPE) && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                // a double chest is one container: key it by the lower of its two halves
                BlockPos other = ChestBlock.getConnectedBlockPos(pos, st);
                if (other.asLong() < pos.asLong()) {
                    keyPos = other;
                }
            }
            key = dim + "|" + keyPos.getX() + "," + keyPos.getY() + "," + keyPos.getZ();
        }
        pending = new Pending(key, dim, keyPos, blockId, System.currentTimeMillis());
    }

    /** Full contents of the open container arrived. */
    public static void onContent(ClientboundContainerSetContentPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || packet.containerId() == 0) {
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != packet.containerId()) {
            return;
        }
        if (session != null && session.containerId == packet.containerId()) {
            return;
        }
        Pending p = pending;
        if (p == null || System.currentTimeMillis() - p.time() > 3000) {
            return;
        }
        pending = null;
        int size = menu.slots.size() - 36;
        if (size <= 0) {
            return;
        }
        ChestStore.ensureLoaded(mc);
        long now = System.currentTimeMillis();
        Map<String, Integer> items = ItemUtil.count(packet.items(), size);
        ChestStore.Record r = ChestStore.get(p.key());
        boolean first = r == null;
        Map<String, Integer> changes = first ? Map.of() : ItemUtil.diff(r.items, items);
        long previous = first ? 0 : r.lastSeen;
        Carry k = carry;
        if (k != null && k.key().equals(p.key()) && now < k.until() && r != null) {
            // reopened by the CoreProtect check: show what the panel showed before it closed the chest
            carry = null;
            Map<String, Integer> fresh = changes;
            changes = k.changes();
            previous = k.previousSeen();
            first = k.first();
            if (!fresh.isEmpty()) {
                // something really changed during the check: log it as usual
                addHistory(r, false, fresh, r.lastSeen, now);
            }
            r.items = items;
            r.lastSeen = now;
            session = new Session(p.key(), packet.containerId(), size, items, changes, previous, first, r);
            ChestStore.save();
            return;
        }
        if (first) {
            r = new ChestStore.Record();
            r.dim = p.dim();
            r.x = p.pos().getX();
            r.y = p.pos().getY();
            r.z = p.pos().getZ();
            r.block = p.block();
            ChestStore.put(p.key(), r);
        }
        if (!changes.isEmpty()) {
            addHistory(r, false, changes, previous, now);
        }
        r.items = items;
        r.lastSeen = now;
        session = new Session(p.key(), packet.containerId(), size, items, changes, previous, first, r);
        ChestStore.save();
    }

    /** The container screen closed: log what you changed and remember the final contents. */
    public static void onClose(AbstractContainerScreen<?> screen) {
        Session s = session(screen);
        if (s == null) {
            return;
        }
        session = null;
        Map<String, Integer> now = current(screen, s);
        Map<String, Integer> yours = ItemUtil.diff(s.openItems, now);
        long t = System.currentTimeMillis();
        if (!yours.isEmpty() && LogConfig.get().logOwn) {
            addHistory(s.record, true, yours, s.record.lastSeen, t);
        }
        s.record.items = now;
        s.record.lastSeen = t;
        s.record.title = screen.getTitle().getString();
        ChestStore.save();
    }

    public static Session session(AbstractContainerScreen<?> screen) {
        Session s = session;
        return s != null && screen.getMenu().containerId == s.containerId ? s : null;
    }

    /** Contents of the container right now (while it is open). */
    public static Map<String, Integer> current(AbstractContainerScreen<?> screen, Session s) {
        List<ItemStack> stacks = new ArrayList<>();
        AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < Math.min(s.size, menu.slots.size()); i++) {
            stacks.add(menu.slots.get(i).getItem());
        }
        return ItemUtil.count(stacks, s.size);
    }

    private static void addHistory(ChestStore.Record r, boolean you, Map<String, Integer> delta, long since, long time) {
        ChestStore.Entry e = new ChestStore.Entry();
        e.time = time;
        e.since = since;
        e.you = you;
        e.delta.putAll(delta);
        r.history.add(0, e);
        int max = LogConfig.get().historySize;
        while (r.history.size() > max) {
            r.history.remove(r.history.size() - 1);
        }
    }

    public static void reset() {
        pending = null;
        session = null;
    }
}
