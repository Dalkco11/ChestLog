package com.dalkco.chestlog;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Containers found by the search, outlined through walls for a while. */
public final class Highlights {
    private record Mark(ChestStore.Record record, String label, long until) {
    }

    private static final List<Mark> marks = new ArrayList<>();

    private Highlights() {
    }

    public static void add(ChestStore.Record r, String label) {
        marks.removeIf(m -> m.record() == r);
        marks.add(new Mark(r, label, System.currentTimeMillis() + LogConfig.get().highlightSeconds * 1000L));
    }

    public static void clear() {
        marks.clear();
    }

    /** Called every client tick (inside the per-tick gizmo collection). */
    public static void tick(Minecraft mc) {
        if (marks.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String dim = mc.level.dimension().identifier().toString();
        Iterator<Mark> it = marks.iterator();
        while (it.hasNext()) {
            Mark m = it.next();
            if (now > m.until()) {
                it.remove();
                continue;
            }
            ChestStore.Record r = m.record();
            if (!dim.equals(r.dim)) {
                continue;
            }
            BlockPos pos = new BlockPos(r.x, r.y, r.z);
            AABB box = new AABB(pos);
            BlockState st = mc.level.getBlockState(pos);
            if (st.getBlock() instanceof ChestBlock && st.hasProperty(ChestBlock.TYPE)
                    && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                box = box.minmax(new AABB(ChestBlock.getConnectedBlockPos(pos, st)));
            }
            float pulse = 0.6f + 0.4f * (float) Math.sin(now / 220.0);
            int color = ((int) (0xFF * pulse) << 24) | (LogPanel.ACCENT & 0xFFFFFF);
            Gizmos.cuboid(box.inflate(0.02), GizmoStyle.strokeAndFill(color, 2.5f,
                    (0x30 << 24) | (LogPanel.ACCENT & 0xFFFFFF))).setAlwaysOnTop();
            Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY, (box.minZ + box.maxZ) / 2);
            Gizmos.line(top, top.add(0, 6, 0), color, 2.0f).setAlwaysOnTop();
            Gizmos.billboardText(m.label(), top.add(0, 1.2, 0),
                    TextGizmo.Style.forColorAndCentered(LogPanel.ACCENT)).setAlwaysOnTop();
        }
    }
}
