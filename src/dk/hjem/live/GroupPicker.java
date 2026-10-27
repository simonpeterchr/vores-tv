package dk.hjem.live;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Vælg hvilke grupper der vises på tv'et: lande til venstre, grupper til højre, OK slår til/fra. */
final class GroupPicker extends FrameLayout {
    static final int ROWS = 11;
    private final Px px;
    private final Runnable onClose;
    private final Source src;

    /** Hvor grupperne kommer fra (live, film eller serier). */
    interface Source {
        List<String> prefixes();
        List<String[]> groups(String prefix); // {id, navn, antal-tekst}
        boolean shown(String id);
        void toggle(String id);
    }

    static Source live(final Context c) {
        return new Source() {
            public List<String> prefixes() { return Catalog.prefixes(); }
            public List<String[]> groups(String p) {
                List<String[]> o = new ArrayList<>();
                for (Catalog.Cat k : Catalog.groupsWithPrefix(p)) o.add(new String[]{k.id, k.name, String.valueOf(k.count)});
                return o;
            }
            public boolean shown(String id) { return Catalog.isShown(c, id); }
            public void toggle(String id) { Catalog.toggleGroup(c, id); }
        };
    }

    static Source vod(final Context c, final String kind) {
        return new Source() {
            public List<String> prefixes() {
                java.util.Map<String, Integer> n = new java.util.LinkedHashMap<>();
                for (Vod.Cat k : Vod.categories(kind).values()) { String p = k.prefix.isEmpty() ? "Andet" : k.prefix; n.put(p, n.containsKey(p) ? n.get(p) + 1 : 1); }
                List<String> out = new ArrayList<>(n.keySet());
                java.util.Collections.sort(out, (a, b) -> rank(a) != rank(b) ? rank(a) - rank(b) : n.get(b) - n.get(a));
                return out;
            }
            int rank(String p) { return p.equals("DK") || p.equals("Danske") || p.equals("Dansk") ? 0 : p.equals("Nordic") || p.equals("Scandinavian") || p.equals("Nordisk") ? 1 : 2; }
            public List<String[]> groups(String p) {
                List<String[]> o = new ArrayList<>();
                for (Vod.Cat k : Vod.categories(kind).values())
                    if (p.equals("Andet") ? k.prefix.isEmpty() : p.equals(k.prefix)) o.add(new String[]{k.id, k.name, null});
                return o;
            }
            public boolean shown(String id) { return Vod.shownGroups(c, kind).contains(id); }
            public void toggle(String id) { Vod.toggleGroup(c, kind, id); }
        };
    }
    private final LinearLayout left, right;
    private final TextView title;
    private List<String> prefixes = new ArrayList<>();
    private List<String[]> groups = new ArrayList<>();
    private int pi, gi, col = 1;

    GroupPicker(Context c, Px px, Source src, Runnable onClose) {
        super(c);
        this.px = px;
        this.src = src;
        this.onClose = onClose;
        setBackgroundColor(0xCC000000);
        setFocusable(true);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.round(0xF21E1E22, px.p(33)));
        box.setPadding(px.p(42), px.p(36), px.p(42), px.p(36));
        addView(box, new LayoutParams(px.p(1320), px.p(900), Gravity.CENTER));
        title = px.t(36, 700, 0xFFFFFFFF);
        title.setText("Grupper på tv'et");
        box.addView(title);
        TextView sub = px.t(21, 400, Theme.white(0.55f));
        sub.setText("OK slår en gruppe til eller fra  ·  ◂▸ skift mellem lande og grupper  ·  Tilbage for at lukke");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = px.p(10); sl.bottomMargin = px.p(24);
        box.addView(sub, sl);
        LinearLayout cols = new LinearLayout(c);
        box.addView(cols, new LinearLayout.LayoutParams(-1, 0, 1));
        left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        cols.addView(left, new LinearLayout.LayoutParams(px.p(260), -1));
        right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, -1, 1);
        rl.leftMargin = px.p(30);
        cols.addView(right, rl);
        prefixes = src.prefixes();
        load();
    }

    private void load() {
        groups = prefixes.isEmpty() ? new ArrayList<>() : src.groups(prefixes.get(pi));
        gi = Math.min(gi, Math.max(0, groups.size() - 1));
        render();
    }

    private void render() {
        left.removeAllViews();
        right.removeAllViews();
        int f = Math.max(0, Math.min(pi - ROWS / 2, prefixes.size() - ROWS));
        for (int i = f; i < Math.min(prefixes.size(), f + ROWS); i++) {
            String p = prefixes.get(i);
            int on = 0;
            for (String[] k : src.groups(p)) if (src.shown(k[0])) on++;
            left.addView(row(p + (on > 0 ? "   ✓" + on : ""), i == pi, col == 0, false));
        }
        int g = Math.max(0, Math.min(gi - ROWS / 2, groups.size() - ROWS));
        for (int i = g; i < Math.min(groups.size(), g + ROWS); i++) {
            String[] k = groups.get(i);
            boolean shown = src.shown(k[0]);
            right.addView(row((shown ? "✓  " : "     ") + k[1] + (k[2] != null ? "   (" + k[2] + ")" : ""), i == gi, col == 1, shown));
        }
    }

    private TextView row(String text, boolean sel, boolean activeCol, boolean on) {
        TextView t = px.t(24, on ? 600 : 500, 0xFFFFFFFF);
        t.setText(text);
        t.setPadding(px.p(21), px.p(14), px.p(21), px.p(14));
        if (sel && activeCol) { t.setBackground(Ui.round(0xFFFFFFFF, px.p(15))); t.setTextColor(0xFF000000); }
        else if (sel) t.setBackground(Ui.round(Theme.white(0.14f), px.p(15)));
        else t.setTextColor(on ? 0xFFFFFFFF : Theme.white(0.75f));
        return t;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (col == 0 && pi > 0) { pi--; gi = 0; load(); } else if (col == 1 && gi > 0) { gi--; render(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (col == 0 && pi < prefixes.size() - 1) { pi++; gi = 0; load(); } else if (col == 1 && gi < groups.size() - 1) { gi++; render(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: col = 0; render(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: col = 1; render(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                if (e.getRepeatCount() > 0) return true;
                if (col == 0) { col = 1; render(); }
                else if (!groups.isEmpty()) { src.toggle(groups.get(gi)[0]); render(); }
                return true;
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                onClose.run();
                return true;
        }
        return true;
    }
}
