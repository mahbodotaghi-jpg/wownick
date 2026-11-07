package dev.nitro.gui;

import dev.nitro.Config;
import dev.nitro.module.Module;
import dev.nitro.module.ModuleManager;
import dev.nitro.settings.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Icon rail (categories) | module list | settings pane. Amber accent. */
public final class ClickGui extends Screen {
    private static final int ACCENT = 0xFFFFB000;
    private static final int BG = 0xF0121214;
    private static final int RAIL = 0xFF0E0E10;
    private static final int PANE = 0xFF18181B;
    private static final int ROW = 0xFF202025;
    private static final int ROW_SEL = 0xFF2B2B32;
    private static final int TEXT = 0xFFEDEDED;
    private static final int DIM = 0xFF8A8A93;
    private static final int OFF = 0xFF3A3A42;

    private static final int RAIL_W = 40, LIST_W = 150, HEAD_H = 28;

    // rail entries: 0 = favourites, then each module category
    private int catIndex = 1;
    private Module selected;
    private int listScroll, setScroll;
    private boolean binding;

    private Setting.Num dragNum;
    private int dragX0, dragW;
    private Setting.Color dragColor;
    private int dragCh;

    private int wx, wy, ww, wh;

    private enum Kind { ENABLE, BIND, FAV, SETTING }

    private record Row(Kind kind, Setting<?> s, int y, int h) {}

    public ClickGui() {
        super(Text.literal("NITRO"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------ layout helpers

    private void layout() {
        ww = Math.min(width - 20, 580);
        wh = Math.min(height - 20, 330);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
    }

    private int listX() { return wx + RAIL_W; }
    private int setX() { return wx + RAIL_W + LIST_W; }
    private int setW() { return ww - RAIL_W - LIST_W; }
    private int bodyTop() { return wy + HEAD_H; }

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<>();
        Module.Category[] cats = Module.Category.values();
        for (Module m : ModuleManager.INSTANCE.modules) {
            if (catIndex == 0 ? m.favorite : m.category == cats[catIndex - 1]) out.add(m);
        }
        return out;
    }

    private int rowHeight(Setting<?> s) {
        if (s instanceof Setting.Num) return 30;
        if (s instanceof Setting.Color) return 38;
        if (s instanceof Setting.Choice<?> c) return 22 + c.options().length * 13;
        return 22;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (selected == null) return rows;
        int y = bodyTop() + 6 - setScroll;
        rows.add(new Row(Kind.ENABLE, null, y, 22)); y += 24;
        rows.add(new Row(Kind.BIND, null, y, 22)); y += 24;
        rows.add(new Row(Kind.FAV, null, y, 22)); y += 28;
        for (Setting<?> s : selected.settings) {
            if (!s.visible.getAsBoolean()) continue;
            int h = rowHeight(s);
            rows.add(new Row(Kind.SETTING, s, y, h));
            y += h + 2;
        }
        return rows;
    }

    private int contentHeight() {
        List<Row> r = rows();
        if (r.isEmpty()) return 0;
        Row last = r.get(r.size() - 1);
        return last.y() + last.h() + setScroll - bodyTop();
    }

    // ------------------------------------------------------------ rendering

    private void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, color, false);
    }

    private void sw(DrawContext c, int x, int y, boolean on) {
        c.fill(x, y, x + 22, y + 10, on ? ACCENT : OFF);
        int kx = on ? x + 13 : x + 1;
        c.fill(kx, y + 1, kx + 8, y + 9, 0xFFF4F4F4);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        layout();
        ctx.fill(0, 0, width, height, 0x88000000);
        ctx.fill(wx, wy, wx + ww, wy + wh, BG);
        ctx.fill(wx, wy, wx + ww, wy + 1, ACCENT);

        // header
        text(ctx, "NITRO", wx + 12, wy + 10, ACCENT);
        text(ctx, "Right Shift closes", wx + ww - 12 - textRenderer.getWidth("Right Shift closes"), wy + 10, DIM);

        // rail
        ctx.fill(wx, bodyTop(), wx + RAIL_W, wy + wh, RAIL);
        String[] glyphs = new String[Module.Category.values().length + 1];
        glyphs[0] = "*";
        for (int i = 0; i < Module.Category.values().length; i++) glyphs[i + 1] = Module.Category.values()[i].glyph;
        for (int i = 0; i < glyphs.length; i++) {
            int ry = bodyTop() + 8 + i * 30;
            boolean sel = i == catIndex;
            if (sel) {
                ctx.fill(wx, ry, wx + 3, ry + 24, ACCENT);
                ctx.fill(wx + 3, ry, wx + RAIL_W, ry + 24, ROW);
            }
            text(ctx, glyphs[i], wx + RAIL_W / 2 - textRenderer.getWidth(glyphs[i]) / 2, ry + 8, sel ? ACCENT : DIM);
        }

        // module list
        ctx.fill(listX(), bodyTop(), listX() + LIST_W, wy + wh, PANE);
        ctx.enableScissor(listX(), bodyTop(), listX() + LIST_W, wy + wh);
        List<Module> mods = visibleModules();
        int y = bodyTop() + 6 - listScroll;
        if (mods.isEmpty()) text(ctx, catIndex == 0 ? "No favourites yet" : "No modules", listX() + 10, y + 4, DIM);
        for (Module m : mods) {
            boolean sel = m == selected;
            ctx.fill(listX() + 4, y, listX() + LIST_W - 4, y + 24, sel ? ROW_SEL : ROW);
            if (sel) ctx.fill(listX() + 4, y, listX() + 6, y + 24, ACCENT);
            text(ctx, m.favorite ? "*" : "+", listX() + 12, y + 8, m.favorite ? ACCENT : DIM);
            text(ctx, m.name, listX() + 24, y + 8, TEXT);
            sw(ctx, listX() + LIST_W - 32, y + 7, m.enabled);
            y += 28;
        }
        ctx.disableScissor();

        // settings
        ctx.fill(setX(), bodyTop(), wx + ww, wy + wh, 0xFF141417);
        if (selected == null) {
            text(ctx, "Select a module", setX() + 14, bodyTop() + 14, DIM);
        } else {
            ctx.enableScissor(setX(), bodyTop(), wx + ww, wy + wh);
            int ix = setX() + 10, iw = setW() - 20;
            for (Row r : rows()) {
                if (r.y() + r.h() < bodyTop() || r.y() > wy + wh) continue;
                ctx.fill(ix, r.y(), ix + iw, r.y() + r.h(), ROW);
                switch (r.kind()) {
                    case ENABLE -> {
                        text(ctx, "Enabled", ix + 8, r.y() + 7, TEXT);
                        sw(ctx, ix + iw - 30, r.y() + 6, selected.enabled);
                    }
                    case BIND -> {
                        text(ctx, "Shortcut", ix + 8, r.y() + 7, TEXT);
                        String k = binding ? "press a key..." : keyName(selected.key);
                        text(ctx, k, ix + iw - 8 - textRenderer.getWidth(k), r.y() + 7, binding ? ACCENT : DIM);
                    }
                    case FAV -> {
                        text(ctx, "Favourite", ix + 8, r.y() + 7, TEXT);
                        sw(ctx, ix + iw - 30, r.y() + 6, selected.favorite);
                    }
                    case SETTING -> drawSetting(ctx, r, ix, iw);
                }
            }
            ctx.disableScissor();
        }
    }

    private void drawSetting(DrawContext ctx, Row r, int ix, int iw) {
        Setting<?> s = r.s();
        int y = r.y();
        text(ctx, s.name, ix + 8, y + 7, TEXT);
        if (s instanceof Setting.Bool b) {
            sw(ctx, ix + iw - 30, y + 6, b.on());
        } else if (s instanceof Setting.Choice<?> c) {
            Object[] opts = c.options();
            for (int i = 0; i < opts.length; i++) {
                boolean on = opts[i] == c.value;
                int oy = y + 20 + i * 13;
                if (on) ctx.fill(ix + 8, oy - 1, ix + iw - 8, oy + 11, 0x33FFB000);
                text(ctx, (on ? "> " : "  ") + opts[i], ix + 12, oy + 1, on ? ACCENT : DIM);
            }
        } else if (s instanceof Setting.Num n) {
            String v = n.step >= 1 ? String.valueOf(n.i()) : String.format("%.1f", n.d());
            text(ctx, v, ix + iw - 8 - textRenderer.getWidth(v), y + 7, DIM);
            int x0 = ix + 8, w = iw - 16;
            ctx.fill(x0, y + 21, x0 + w, y + 25, OFF);
            ctx.fill(x0, y + 21, x0 + (int) (w * n.fraction()), y + 25, ACCENT);
        } else if (s instanceof Setting.Color c) {
            ctx.fill(ix + iw - 24, y + 5, ix + iw - 8, y + 15, 0xFF000000 | c.rgb());
            int x0 = ix + 8, w = (iw - 16) / 3;
            int[] cols = {0xFFE05555, 0xFF55C46A, 0xFF5587E0};
            for (int i = 0; i < 3; i++) {
                int sx = x0 + i * w;
                ctx.fill(sx, y + 24, sx + w - 4, y + 28, OFF);
                ctx.fill(sx, y + 24, sx + (int) ((w - 4) * (c.ch(i) / 255.0)), y + 28, cols[i]);
            }
        }
    }

    private String keyName(int key) {
        if (key < 0) return "None";
        try {
            return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
        } catch (Exception e) {
            return "Key " + key;
        }
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        // rail
        if (mx >= wx && mx < wx + RAIL_W && my >= bodyTop()) {
            int n = Module.Category.values().length + 1;
            for (int i = 0; i < n; i++) {
                int ry = bodyTop() + 8 + i * 30;
                if (my >= ry && my < ry + 24) {
                    catIndex = i;
                    listScroll = 0;
                    return true;
                }
            }
        }
        // list
        if (mx >= listX() && mx < listX() + LIST_W && my >= bodyTop()) {
            int y = bodyTop() + 6 - listScroll;
            for (Module m : visibleModules()) {
                if (my >= y && my < y + 24) {
                    if (mx < listX() + 22) m.favorite = !m.favorite;
                    else if (mx >= listX() + LIST_W - 36) m.toggle();
                    else {
                        selected = m;
                        setScroll = 0;
                        binding = false;
                    }
                    return true;
                }
                y += 28;
            }
            return true;
        }
        // settings
        if (selected != null && mx >= setX() && my >= bodyTop()) {
            int ix = setX() + 10, iw = setW() - 20;
            for (Row r : rows()) {
                if (my < r.y() || my >= r.y() + r.h()) continue;
                if (mx < ix || mx > ix + iw) break;
                switch (r.kind()) {
                    case ENABLE -> selected.toggle();
                    case BIND -> binding = !binding;
                    case FAV -> selected.favorite = !selected.favorite;
                    case SETTING -> clickSetting(r, mx, my, button, ix, iw);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void clickSetting(Row r, double mx, double my, int button, int ix, int iw) {
        Setting<?> s = r.s();
        if (s instanceof Setting.Bool b) {
            b.value = !b.value;
        } else if (s instanceof Setting.Choice<?> c) {
            int idx = (int) ((my - (r.y() + 20)) / 13);
            if (idx >= 0 && idx < c.options().length) c.set(idx);
            else c.next(button == 1 ? -1 : 1);
        } else if (s instanceof Setting.Num n) {
            dragNum = n;
            dragX0 = ix + 8;
            dragW = iw - 16;
            n.setFraction((mx - dragX0) / dragW);
        } else if (s instanceof Setting.Color c) {
            int w = (iw - 16) / 3;
            int ch = (int) ((mx - (ix + 8)) / w);
            if (ch < 0 || ch > 2) return;
            dragColor = c;
            dragCh = ch;
            dragX0 = ix + 8 + ch * w;
            dragW = w - 4;
            c.setCh(ch, (int) Math.round(255.0 * (mx - dragX0) / dragW));
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragNum != null) {
            dragNum.setFraction((mx - dragX0) / dragW);
            return true;
        }
        if (dragColor != null) {
            dragColor.setCh(dragCh, (int) Math.round(255.0 * (mx - dragX0) / dragW));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragNum = null;
        dragColor = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        layout();
        if (mx >= listX() && mx < listX() + LIST_W) {
            int max = Math.max(0, visibleModules().size() * 28 + 12 - (wh - HEAD_H));
            listScroll = Math.max(0, Math.min(max, listScroll - (int) (vAmount * 16)));
        } else if (mx >= setX()) {
            int max = Math.max(0, contentHeight() + 12 - (wh - HEAD_H));
            setScroll = Math.max(0, Math.min(max, setScroll - (int) (vAmount * 16)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding && selected != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                binding = false;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                selected.key = -1;
                binding = false;
            } else {
                selected.key = keyCode;
                binding = false;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }
}
