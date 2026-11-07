package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.Anchor;
import dev.nitro.util.Gfx;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Info HUD: a small panel with FPS, ping, coordinates, facing direction, speed and the real-world clock. */
public final class InfoHud extends Module {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final Setting.Bool fps = new Setting.Bool("FPS", true);
    private final Setting.Bool ping = new Setting.Bool("Ping", true);
    private final Setting.Bool coords = new Setting.Bool("Coordinates", true);
    private final Setting.Bool direction = new Setting.Bool("Direction", true);
    private final Setting.Bool speed = new Setting.Bool("Speed (blocks/s)", true);
    private final Setting.Bool clock = new Setting.Bool("Clock", false);
    private final Setting.Choice<Anchor> anchor = new Setting.Choice<>("Position", Anchor.TOP_LEFT, Anchor.class);
    private final Setting.Choice<Gfx.PanelStyle> style = new Setting.Choice<>("Style", Gfx.PanelStyle.GLASS, Gfx.PanelStyle.class);
    private final Setting.Num scale = new Setting.Num("Scale", 1.0, 0.6, 1.8, 0.05);
    private final Setting.Num offX = new Setting.Num("X offset", 6, 0, 300, 1);
    private final Setting.Num offY = new Setting.Num("Y offset", 6, 0, 300, 1);
    private final Setting.Color tint = new Setting.Color("Panel tint", 0x20242C);
    private final Setting.Num opacity = new Setting.Num("Panel opacity", 65, 20, 95, 5);
    private final Setting.Color accent = new Setting.Color("Accent", 0x6EA8FF);

    private double lastX, lastZ, bps;
    private boolean hasLast;

    public InfoHud() {
        super("Info HUD", "FPS, ping, coordinates, direction, speed and clock in a small panel.", Category.HUD);
        add(fps, ping, coords, direction, speed, clock, anchor, style, scale, offX, offY, tint, opacity, accent);
    }

    @Override
    public void onWorldChanged() {
        hasLast = false;
        bps = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        double x = mc.player.getX(), z = mc.player.getZ();
        if (hasLast) {
            double inst = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20.0;
            bps = bps * 0.7 + inst * 0.3;
        }
        lastX = x;
        lastZ = z;
        hasLast = true;
    }

    private static String pretty(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.options.hudHidden) return;

        List<String[]> lines = new ArrayList<>();
        if (fps.on()) lines.add(new String[]{"FPS", String.valueOf(mc.getCurrentFps())});
        if (ping.on()) {
            int ms = 0;
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) ms = e.getLatency();
            }
            lines.add(new String[]{"Ping", ms + " ms"});
        }
        if (coords.on()) {
            lines.add(new String[]{"XYZ", String.format("%.1f  %.1f  %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ())});
        }
        if (direction.on()) lines.add(new String[]{"Facing", pretty(mc.player.getHorizontalFacing().name())});
        if (speed.on()) lines.add(new String[]{"Speed", String.format("%.1f", bps)});
        if (clock.on()) lines.add(new String[]{"Time", LocalTime.now().format(CLOCK)});
        if (lines.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int pad = 6, lineH = 11, gap = 12;
        int lw = 0, vw = 0;
        for (String[] l : lines) {
            lw = Math.max(lw, tr.getWidth(l[0]));
            vw = Math.max(vw, tr.getWidth(l[1]));
        }
        int w = pad * 2 + lw + gap + vw;
        int h = pad * 2 + lines.size() * lineH - 2;

        float sc = scale.f();
        int x = anchor.value.x(ctx.getScaledWindowWidth(), (int) (w * sc), offX.i());
        int y = anchor.value.y(ctx.getScaledWindowHeight(), (int) (h * sc), offY.i());
        float time = (System.currentTimeMillis() % 1_000_000L) / 1000f;

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        ms.scale(sc, sc, 1f);
        Gfx.card(ctx, 0, 0, w, h, 4, tint.rgb(), opacity.f() / 100f, accent.rgb(), style.value, 1f, time);
        for (int i = 0; i < lines.size(); i++) {
            int ly = pad + i * lineH;
            ctx.drawText(tr, lines.get(i)[0], pad, ly, 0xFF9AA0AA, false);
            ctx.drawText(tr, lines.get(i)[1], w - pad - tr.getWidth(lines.get(i)[1]), ly, 0xFFF2F4F8, false);
        }
        ms.pop();
    }
}
