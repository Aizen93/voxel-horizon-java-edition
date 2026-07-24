package org.aouessar.renderer.ui;

import org.lwjgl.stb.STBEasyFont;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Mouse-driven pause menu, Minecraft style: a centered panel with draggable
 * sliders (plus -/+ fine-step buttons), toggle buttons and action buttons,
 * all with hover highlights. Immediate-mode: widgets are laid out, hit-tested
 * and drawn every frame through the {@link UiOverlay} quad batch; labels are
 * centered with real text metrics (stb_easy_font).
 */
public final class PauseMenu {

    // ---- widget model ----
    private sealed interface Item permits SliderItem, ToggleItem, ActionItem {}

    private record SliderItem(String label, Supplier<Float> get, Consumer<Float> set,
                              float min, float max, float step, String fmt) implements Item {}

    private record ToggleItem(String label, BooleanSupplier get, Consumer<Boolean> set) implements Item {}

    private record ActionItem(String label, Runnable run) implements Item {}

    public boolean open = false;

    private final List<Item> items = new ArrayList<>();
    private final List<ActionItem> footer = new ArrayList<>();

    private int activeSlider = -1;
    private boolean mouseWasDown = false;
    private final double[] mxBuf = new double[1];
    private final double[] myBuf = new double[1];

    // Labels drawn after the quad flush (text goes on top of the widgets)
    private final List<float[]> labelPos = new ArrayList<>();
    private final List<String> labelText = new ArrayList<>();

    public void slider(String label, Supplier<Float> get, Consumer<Float> set,
                       float min, float max, float step, String fmt) {
        items.add(new SliderItem(label, get, set, min, max, step, fmt));
    }

    public void toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        items.add(new ToggleItem(label, get, set));
    }

    /** Footer button (Resume / Quit). */
    public void action(String label, Runnable run) {
        footer.add(new ActionItem(label, run));
    }

    /** Layout + input + draw. Call once per frame while open. */
    public void renderAndHandle(long window, UiOverlay ui, DebugOverlay hud, int w, int h) {
        glfwGetCursorPos(window, mxBuf, myBuf);
        float mx = (float) mxBuf[0];
        float my = (float) myBuf[0];
        boolean down = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean clicked = down && !mouseWasDown;
        if (!down) activeSlider = -1;

        labelPos.clear();
        labelText.clear();

        // ---- panel ----
        int cols = 2;
        int rows = (items.size() + cols - 1) / cols;
        float rowH = 40, colW = 350, colGap = 30;
        float pw = cols * colW + (cols - 1) * colGap + 60;
        float ph = 96 + rows * rowH + 70;
        float px = (w - pw) / 2f;
        float py = (h - ph) / 2f;

        ui.queueRect(px - 2, py - 2, pw + 4, ph + 4, 0.75f, 0.72f, 0.62f, 0.9f); // frame
        ui.queueRect(px, py, pw, ph, 0.12f, 0.12f, 0.14f, 0.94f);

        label(px + pw / 2f, py + 22, "=== GAME PAUSED - SETTINGS ===");

        // ---- widgets ----
        for (int i = 0; i < items.size(); i++) {
            int col = i / rows;
            int row = i % rows;
            float x = px + 30 + col * (colW + colGap);
            float y = py + 56 + row * rowH;

            switch (items.get(i)) {
                case SliderItem s -> drawSlider(ui, i, s, x, y, colW, mx, my, down, clicked);
                case ToggleItem t -> drawToggle(ui, t, x, y, colW, mx, my, clicked);
                case ActionItem a -> { /* footer only */ }
            }
        }

        // ---- footer buttons ----
        float bw = 170, bh = 32;
        float total = footer.size() * bw + (footer.size() - 1) * 20;
        float bx = px + (pw - total) / 2f;
        float by = py + ph - 50;
        for (ActionItem a : footer) {
            boolean hover = hit(mx, my, bx, by, bw, bh);
            ui.queueRect(bx - 1, by - 1, bw + 2, bh + 2, 0.75f, 0.72f, 0.62f, 0.9f);
            ui.queueRect(bx, by, bw, bh,
                    hover ? 0.35f : 0.22f, hover ? 0.35f : 0.22f, hover ? 0.40f : 0.26f, 1f);
            label(bx + bw / 2f, by + bh / 2f - 4, a.label());
            if (hover && clicked) a.run().run();
            bx += bw + 20;
        }

        ui.flushQueued(w, h, 0);

        // ---- labels on top ----
        boolean bg = hud.isShowBackground();
        hud.setShowBackground(false);
        for (int i = 0; i < labelText.size(); i++) {
            hud.render(w, h, labelPos.get(i)[0], labelPos.get(i)[1], labelText.get(i));
        }
        hud.setShowBackground(bg);

        mouseWasDown = down;
    }

    // ---- widgets ----

    private void drawSlider(UiOverlay ui, int id, SliderItem s, float x, float y, float wCol,
                            float mx, float my, boolean down, boolean clicked) {
        float btn = 26, gap = 6;
        float sw = wCol - 2 * (btn + gap);
        float sh = 26;

        // Track + fill + knob
        float v = s.get().get();
        float t = (v - s.min()) / (s.max() - s.min());
        t = t < 0 ? 0 : Math.min(t, 1);

        boolean hoverTrack = hit(mx, my, x, y, sw, sh);
        ui.queueRect(x - 1, y - 1, sw + 2, sh + 2, 0.75f, 0.72f, 0.62f, 0.9f);
        ui.queueRect(x, y, sw, sh, 0.20f, 0.20f, 0.23f, 1f);
        ui.queueRect(x, y, sw * t, sh, 0.24f, 0.42f, 0.28f, 1f);
        float knobX = x + sw * t - 4;
        boolean drag = (activeSlider == id);
        ui.queueRect(knobX, y - 2, 8, sh + 4,
                (drag || hoverTrack) ? 1f : 0.82f, (drag || hoverTrack) ? 1f : 0.82f,
                (drag || hoverTrack) ? 1f : 0.80f, 1f);

        label(x + sw / 2f, y + sh / 2f - 4,
                s.label() + ": " + String.format(s.fmt(), v));

        if (clicked && hoverTrack) activeSlider = id;
        if (down && activeSlider == id) {
            float nv = s.min() + (mx - x) / sw * (s.max() - s.min());
            applySlider(s, nv);
        }

        // -/+ fine-step buttons
        float bxm = x + sw + gap, bxp = bxm + btn + gap;
        boolean hm = hit(mx, my, bxm, y, btn, sh);
        boolean hp = hit(mx, my, bxp, y, btn, sh);
        ui.queueRect(bxm, y, btn, sh, hm ? 0.4f : 0.25f, hm ? 0.4f : 0.25f, hm ? 0.44f : 0.29f, 1f);
        ui.queueRect(bxp, y, btn, sh, hp ? 0.4f : 0.25f, hp ? 0.4f : 0.25f, hp ? 0.44f : 0.29f, 1f);
        label(bxm + btn / 2f, y + sh / 2f - 4, "-");
        label(bxp + btn / 2f, y + sh / 2f - 4, "+");
        if (clicked && hm) applySlider(s, s.get().get() - s.step());
        if (clicked && hp) applySlider(s, s.get().get() + s.step());
    }

    private void applySlider(SliderItem s, float nv) {
        nv = Math.round(nv / s.step()) * s.step();
        nv = nv < s.min() ? s.min() : Math.min(nv, s.max());
        s.set().accept(nv);
    }

    private void drawToggle(UiOverlay ui, ToggleItem t, float x, float y, float wCol,
                            float mx, float my, boolean clicked) {
        float sh = 26;
        boolean on = t.get().getAsBoolean();
        boolean hover = hit(mx, my, x, y, wCol, sh);
        ui.queueRect(x - 1, y - 1, wCol + 2, sh + 2, 0.75f, 0.72f, 0.62f, 0.9f);
        ui.queueRect(x, y, wCol, sh,
                on ? 0.22f : 0.25f, on ? 0.42f : 0.25f, on ? 0.26f : 0.28f,
                1f);
        if (hover) ui.queueRect(x, y, wCol, sh, 1f, 1f, 1f, 0.08f);
        label(x + wCol / 2f, y + sh / 2f - 4, t.label() + ": " + (on ? "ON" : "OFF"));
        if (hover && clicked) t.set().accept(!on);
    }

    private void label(float centerX, float y, String text) {
        float tw = STBEasyFont.stb_easy_font_width(text);
        labelPos.add(new float[]{centerX - tw / 2f, y});
        labelText.add(text);
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
