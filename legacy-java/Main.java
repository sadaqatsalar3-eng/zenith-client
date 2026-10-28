import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Zenith Client - launcher UI (Java Swing, single file).
 * Build:  javac Main.java && java Main
 * Pages: Play, Profiles (mods live inside profiles), Discover (mod browser), Settings.
 */
public class Main {

    // ───────────────────────── Theme ─────────────────────────
    static final Color BG = new Color(0x090911), SIDE = new Color(0x0E0E18), CARD = new Color(0x151522),
            CARD_HI = new Color(0x1D1D30), TEXT = new Color(0xF4F4FB), MUTED = new Color(0x8A8AA5),
            A1 = new Color(0x8B5CFF), A2 = new Color(0x22D3EE), GOOD = new Color(0x34D399),
            LINE = new Color(255, 255, 255, 18);
    static final Color[][] PAL = {
            {new Color(0x8B5CFF), new Color(0x22D3EE)}, {new Color(0xFF5C8A), new Color(0xFFB86B)},
            {new Color(0x34D399), new Color(0x22D3EE)}, {new Color(0xF59E0B), new Color(0xEF4444)},
            {new Color(0x60A5FA), new Color(0x8B5CFF)}};
    static final Cursor HAND = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    static Font F(int style, float size) { return new Font("Segoe UI", style, 12).deriveFont(style, size); }

    static Graphics2D g2(Graphics g) {
        Graphics2D x = (Graphics2D) g.create();
        x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        x.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        x.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        x.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        x.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return x;
    }

    static Color mix(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }

    static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (float) Math.pow(1 - t, 3); }

    static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }

    static void text(Graphics2D g, String s, Font f, Color c, double x, double y) {
        g.setFont(f); g.setColor(c); g.drawString(s, (float) x, (float) y);
    }

    static void ctext(Graphics2D g, String s, Font f, Color c, double cx, double cy) {
        g.setFont(f); g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    static void para(Graphics2D g, String s, Font f, Color c, int x, int y, int maxW, int lh, int maxLines) {
        g.setFont(f); g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int n = 0;
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (fm.stringWidth(test) > maxW && line.length() > 0) {
                g.drawString(line.toString(), x, y); y += lh;
                line = new StringBuilder(w);
                if (++n >= maxLines) return;
            } else line = new StringBuilder(test);
        }
        if (line.length() > 0 && n < maxLines) g.drawString(line.toString(), x, y);
    }

    static void header(Graphics2D g, String title, String sub) {
        text(g, title, F(Font.BOLD, 30), TEXT, 32, 52);
        text(g, sub, F(Font.PLAIN, 14), MUTED, 33, 76);
    }

    // ───────────────────────── Vector icons ─────────────────────────
    enum Ic { PLAY, USERS, GRID, GEAR, SEARCH, CLOSE, MIN, DOWNLOAD, CHECK, PLUS, TRASH, CHEVRON }

    static void ln(Graphics2D g, double x1, double y1, double x2, double y2) { g.draw(new Line2D.Double(x1, y1, x2, y2)); }

    static void icon(Graphics2D g0, Ic ic, double cx, double cy, double s, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(c);
        g.setStroke(new BasicStroke((float) (s * 0.1), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double r = s / 2;
        switch (ic) {
            case PLAY: {
                Path2D p = new Path2D.Double();
                p.moveTo(cx - r * 0.35, cy - r * 0.62); p.lineTo(cx + r * 0.68, cy); p.lineTo(cx - r * 0.35, cy + r * 0.62); p.closePath();
                g.fill(p); g.draw(p); break;
            }
            case USERS:
                g.draw(new Ellipse2D.Double(cx - r * 0.3, cy - r * 0.72, r * 0.6, r * 0.6));
                g.draw(new Arc2D.Double(cx - r * 0.65, cy + r * 0.05, r * 1.3, r * 1.1, 0, 180, Arc2D.OPEN)); break;
            case GRID:
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++)
                    g.draw(rr(cx - r * 0.75 + i * r * 0.85, cy - r * 0.75 + j * r * 0.85, r * 0.65, r * 0.65, 4));
                break;
            case GEAR:
                g.draw(new Ellipse2D.Double(cx - r * 0.3, cy - r * 0.3, r * 0.6, r * 0.6));
                g.draw(new Ellipse2D.Double(cx - r * 0.58, cy - r * 0.58, r * 1.16, r * 1.16));
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    ln(g, cx + Math.cos(a) * r * 0.58, cy + Math.sin(a) * r * 0.58, cx + Math.cos(a) * r * 0.86, cy + Math.sin(a) * r * 0.86);
                }
                break;
            case SEARCH:
                g.draw(new Ellipse2D.Double(cx - r * 0.7, cy - r * 0.7, r * 1.1, r * 1.1));
                ln(g, cx + r * 0.15, cy + r * 0.15, cx + r * 0.7, cy + r * 0.7); break;
            case CLOSE:
                ln(g, cx - r * 0.5, cy - r * 0.5, cx + r * 0.5, cy + r * 0.5);
                ln(g, cx + r * 0.5, cy - r * 0.5, cx - r * 0.5, cy + r * 0.5); break;
            case MIN: ln(g, cx - r * 0.55, cy, cx + r * 0.55, cy); break;
            case DOWNLOAD:
                ln(g, cx, cy - r * 0.7, cx, cy + r * 0.3);
                ln(g, cx - r * 0.4, cy - r * 0.1, cx, cy + r * 0.3); ln(g, cx + r * 0.4, cy - r * 0.1, cx, cy + r * 0.3);
                ln(g, cx - r * 0.7, cy + r * 0.75, cx + r * 0.7, cy + r * 0.75); break;
            case CHECK:
                ln(g, cx - r * 0.6, cy, cx - r * 0.15, cy + r * 0.45); ln(g, cx - r * 0.15, cy + r * 0.45, cx + r * 0.6, cy - r * 0.4); break;
            case PLUS: ln(g, cx - r * 0.6, cy, cx + r * 0.6, cy); ln(g, cx, cy - r * 0.6, cx, cy + r * 0.6); break;
            case CHEVRON: ln(g, cx - r * 0.25, cy - r * 0.5, cx + r * 0.3, cy); ln(g, cx + r * 0.3, cy, cx - r * 0.25, cy + r * 0.5); break;
            case TRASH:
                ln(g, cx - r * 0.6, cy - r * 0.45, cx + r * 0.6, cy - r * 0.45);
                ln(g, cx - r * 0.2, cy - r * 0.75, cx + r * 0.2, cy - r * 0.75);
                g.draw(rr(cx - r * 0.45, cy - r * 0.3, r * 0.9, r * 1.05, 4)); break;
        }
        g.dispose();
    }

    // ───────────────────────── Data ─────────────────────────
    static class Profile {
        String name, version, loader; int ram, hue;
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        Profile(String n, String v, String l, int r, int h) { name = n; version = v; loader = l; ram = r; hue = h; }
    }

    static final List<Profile> profiles = new ArrayList<>();
    static int selected = 0;
    static final String[] VERSIONS = {"1.8.9", "1.12.2", "1.16.5", "1.20.1", "1.21.4", "1.21.11"};
    static final String[] LOADERS = {"Vanilla", "Fabric", "Forge", "NeoForge"};
    static final int[] RAMS = {2, 4, 6, 8, 12};
    static final boolean[] OPT = {true, false, true, true};

    static Profile cur() { return profiles.get(Math.max(0, Math.min(selected, profiles.size() - 1))); }

    static File dataFile() {
        File d = new File(System.getProperty("user.home"), ".zenithclient");
        d.mkdirs();
        return new File(d, "profiles.txt");
    }

    static void save() {
        try (PrintWriter w = new PrintWriter(dataFile(), "UTF-8")) {
            w.println("selected=" + selected);
            for (Profile p : profiles)
                w.println(p.name.replace("|", "") + "|" + p.version + "|" + p.loader + "|" + p.ram + "|" + p.hue + "|" + String.join(",", p.mods));
        } catch (IOException ignored) { }
    }

    static void load() {
        try {
            for (String l : Files.readAllLines(dataFile().toPath(), StandardCharsets.UTF_8)) {
                if (l.startsWith("selected=")) { selected = Integer.parseInt(l.substring(9).trim()); continue; }
                String[] p = l.split("\\|", -1);
                if (p.length < 5) continue;
                Profile pr = new Profile(p[0], p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                if (p.length > 5 && !p[5].isEmpty()) pr.mods.addAll(Arrays.asList(p[5].split(",")));
                profiles.add(pr);
            }
        } catch (Exception ignored) { }
        if (profiles.isEmpty()) {
            profiles.add(new Profile("Zenith PvP", "1.21.11", "Fabric", 4, 0));
            profiles.add(new Profile("Classic 1.8.9", "1.8.9", "Vanilla", 2, 1));
            profiles.add(new Profile("Survival", "1.21.4", "Fabric", 6, 2));
            profiles.get(0).mods.addAll(Arrays.asList("Sodium", "Lithium"));
        }
        if (selected >= profiles.size() || selected < 0) selected = 0;
    }

    static class Mod {
        String name, author, cat, desc; int hue;
        Mod(String n, String a, String c, String d, int h) { name = n; author = a; cat = c; desc = d; hue = h; }
    }

    static final Mod[] MODS = {
            new Mod("Sodium", "jellysquid3", "Performance", "Modern rendering engine that boosts FPS and removes stutter.", 0),
            new Mod("Lithium", "jellysquid3", "Performance", "Optimizes game logic, physics and AI without changing behaviour.", 2),
            new Mod("FerriteCore", "malte0811", "Performance", "Cuts memory usage with smarter data structures.", 3),
            new Mod("Entity Culling", "tr7zw", "Performance", "Skips rendering of entities you cannot see.", 0),
            new Mod("Iris Shaders", "coderbot", "Visual", "Shader pack support designed to work with Sodium.", 4),
            new Mod("Sodium Extra", "FlashyReese", "Visual", "Extra video options and tweaks on top of Sodium.", 1),
            new Mod("Better Clouds", "Sodium Team", "Visual", "Smooth, volumetric clouds with minimal cost.", 4),
            new Mod("Mod Menu", "Prospector", "Utility", "In-game menu to view and configure installed mods.", 1),
            new Mod("Xaero's Minimap", "xaero96", "Utility", "Clean, fast minimap with waypoints and radar.", 2),
            new Mod("Zoomify", "Isxander", "Utility", "Configurable zoom with smooth scroll and cinematic camera.", 4),
            new Mod("AppleSkin", "squeek", "Utility", "Shows hunger and saturation values in the HUD.", 3),
            new Mod("ReplayMod", "CrushedPixel", "PvP", "Record and replay gameplay from any camera angle.", 1),
            new Mod("Freelook", "Zenith", "PvP", "Look around your player without turning your aim.", 0),
            new Mod("Keystrokes", "Zenith", "PvP", "Show WASD, mouse buttons and CPS on screen.", 3),
    };

    // ───────────────────────── Base interactive component ─────────────────────────
    static abstract class Fx extends JComponent {
        float hover, press; boolean over, down; Timer t;
        Fx() {
            setOpaque(false); setCursor(HAND);
            t = new Timer(14, e -> {
                float th = over ? 1f : 0f, tp = down ? 1f : 0f;
                hover += (th - hover) * 0.22f; press += (tp - press) * 0.4f;
                if (Math.abs(th - hover) < 0.004f && Math.abs(tp - press) < 0.004f) { hover = th; press = tp; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { over = true; t.start(); }
                public void mouseExited(MouseEvent e) { over = false; down = false; t.start(); }
                public void mousePressed(MouseEvent e) { down = true; t.start(); }
                public void mouseReleased(MouseEvent e) {
                    boolean was = down; down = false; t.start();
                    if (was && contains(e.getPoint()) && isEnabled()) click(e);
                }
            });
        }
        void click(MouseEvent e) { }
    }

    static class IconBtn extends Fx {
        Ic ic; Runnable r; boolean danger;
        IconBtn(Ic ic, boolean danger, Runnable r) { this.ic = ic; this.r = r; this.danger = danger; }
        void click(MouseEvent e) { r.run(); }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            Color hc = danger ? new Color(0xEF4444) : Color.WHITE;
            g.setColor(alpha(hc, (int) ((danger ? 200 : 30) * hover)));
            g.fill(rr(2, 2, getWidth() - 4, getHeight() - 4, 12));
            icon(g, ic, getWidth() / 2.0, getHeight() / 2.0, 16, mix(MUTED, TEXT, hover));
            g.dispose();
        }
    }

    // ───────────────────────── Scroll helpers ─────────────────────────
    static class VPanel extends JPanel implements Scrollable {
        VPanel(LayoutManager l) { super(l); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 28; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 160; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static class SB extends BasicScrollBarUI {
        protected void configureScrollBarColors() { }
        JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        protected JButton createDecreaseButton(int o) { return zero(); }
        protected JButton createIncreaseButton(int o) { return zero(); }
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D x = g2(g);
            x.setColor(new Color(255, 255, 255, isThumbRollover() ? 70 : 38));
            x.fill(rr(r.x + 3, r.y + 2, r.width - 6, r.height - 4, 8));
            x.dispose();
        }
    }

    static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.setBorder(null);
        sp.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        sp.getVerticalScrollBar().setUI(new SB());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        return sp;
    }

    // ───────────────────────── Shell ─────────────────────────
    static Sidebar side;
    static PageHost host;

    static void go(int i) {
        side.select(i);
        JComponent p;
        switch (i) {
            case 0: p = new PlayPage(); break;
            case 1: p = new ProfilesPage(); break;
            case 2: p = new ModsPage(); break;
            default: p = new SettingsPage();
        }
        host.show(p);
    }

    static class PageHost extends JComponent {
        float p = 1; Timer t;
        PageHost() {
            setOpaque(false); setLayout(new BorderLayout());
            t = new Timer(14, e -> { p += 0.07f; if (p >= 1) { p = 1; t.stop(); } repaint(); });
        }
        void show(JComponent pg) {
            removeAll(); add(pg, BorderLayout.CENTER);
            p = OPT[2] ? 0 : 1; revalidate(); t.restart(); repaint();
        }
        @Override protected void paintChildren(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            float e = ease(p);
            g.translate(0, (1 - e) * 18);
            if (e < 1) g.setComposite(AlphaComposite.SrcOver.derive(e));
            super.paintChildren(g);
            g.dispose();
        }
    }

    static final int NAVH = 46, NAVY = 150;

    static class NavBtn extends Fx {
        Ic ic; String label; int idx;
        NavBtn(Ic ic, String label, int idx) { this.ic = ic; this.label = label; this.idx = idx; }
        void click(MouseEvent e) { go(idx); }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            float act = Math.max(0, 1 - Math.abs(side.indY - getY()) / NAVH);
            g.setColor(alpha(Color.WHITE, (int) (14 * hover * (1 - act))));
            g.fill(rr(0, 0, getWidth(), getHeight(), 14));
            Color c = mix(MUTED, TEXT, Math.max(act, hover * 0.75f));
            icon(g, ic, 30, getHeight() / 2.0, 20, act > 0.5 ? mix(c, A2, act) : c);
            text(g, label, F(Font.BOLD, 14), c, 54, getHeight() / 2.0 + 5);
            g.dispose();
        }
    }

    static class Sidebar extends JComponent {
        float indY = NAVY; int sel; Timer t; NavBtn[] btns = new NavBtn[4];
        Sidebar() {
            setOpaque(false); setLayout(null);
            String[] names = {"Play", "Profiles", "Discover", "Settings"};
            Ic[] ics = {Ic.PLAY, Ic.USERS, Ic.GRID, Ic.GEAR};
            for (int i = 0; i < 4; i++) {
                btns[i] = new NavBtn(ics[i], names[i], i);
                btns[i].setBounds(18, NAVY + i * (NAVH + 6), 194, NAVH);
                add(btns[i]);
            }
            t = new Timer(14, e -> {
                float target = NAVY + sel * (NAVH + 6);
                indY += (target - indY) * 0.2f;
                if (Math.abs(target - indY) < 0.3f) { indY = target; t.stop(); }
                repaint();
            });
        }
        void select(int i) { sel = i; t.start(); }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            g.setColor(SIDE); g.fill(rr(0, 0, getWidth() + 40, getHeight(), 28));
            g.setColor(LINE); g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            // logo
            g.setPaint(new GradientPaint(26, 30, A1, 66, 70, A2));
            g.fill(rr(26, 30, 42, 42, 14));
            ctext(g, "Z", F(Font.BOLD, 24), Color.WHITE, 47, 51);
            text(g, "ZENITH", F(Font.BOLD, 19), TEXT, 80, 50);
            text(g, "C L I E N T", F(Font.BOLD, 10), MUTED, 81, 66);
            // indicator
            g.setPaint(new GradientPaint(18, indY, alpha(A1, 70), 212, indY, alpha(A2, 30)));
            g.fill(rr(18, indY, 194, NAVH, 14));
            g.setPaint(new GradientPaint(0, indY, A1, 0, indY + NAVH, A2));
            g.fill(rr(6, indY + 10, 4, NAVH - 20, 4));
            // user chip
            int y = getHeight() - 84;
            g.setColor(CARD); g.fill(rr(18, y, 194, 60, 16));
            g.setPaint(new GradientPaint(30, y + 12, A2, 66, y + 48, A1));
            g.fill(new Ellipse2D.Double(30, y + 12, 36, 36));
            ctext(g, "P", F(Font.BOLD, 16), Color.WHITE, 48, y + 30);
            text(g, "Player", F(Font.BOLD, 14), TEXT, 76, y + 28);
            g.setColor(GOOD); g.fill(new Ellipse2D.Double(76, y + 36, 7, 7));
            text(g, "Offline account", F(Font.PLAIN, 12), MUTED, 89, y + 44);
            g.dispose();
        }
    }

    static class Root extends JPanel {
        final JFrame f; final JComponent top = new JComponent() { };
        Point off;
        Root(JFrame f) {
            this.f = f; setOpaque(false); setLayout(null);
            side = new Sidebar(); host = new PageHost();
            top.setLayout(null);
            IconBtn min = new IconBtn(Ic.MIN, false, () -> f.setState(Frame.ICONIFIED));
            IconBtn close = new IconBtn(Ic.CLOSE, true, () -> { save(); System.exit(0); });
            top.add(min); top.add(close);
            top.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    close.setBounds(top.getWidth() - 56, 14, 36, 36); min.setBounds(top.getWidth() - 96, 14, 36, 36);
                }
            });
            MouseAdapter drag = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { Point s = e.getLocationOnScreen(); off = new Point(s.x - f.getX(), s.y - f.getY()); }
                public void mouseDragged(MouseEvent e) { Point s = e.getLocationOnScreen(); f.setLocation(s.x - off.x, s.y - off.y); }
            };
            top.addMouseListener(drag); top.addMouseMotionListener(drag);
            add(side); add(top); add(host);
        }
        public void doLayout() {
            int w = getWidth(), h = getHeight();
            side.setBounds(0, 0, 230, h); top.setBounds(230, 0, w - 230, 60); host.setBounds(230, 60, w - 230, h - 60);
        }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            g.setColor(BG); g.fill(rr(0, 0, getWidth(), getHeight(), 28));
            g.setColor(LINE); g.setStroke(new BasicStroke(1f)); g.draw(rr(.5, .5, getWidth() - 1, getHeight() - 1, 28));
            g.dispose();
        }
    }

    static abstract class Page extends JComponent {
        Page() { setOpaque(false); setLayout(null); }
    }

    // ───────────────────────── Play page ─────────────────────────
    static class PlayPage extends Page {
        final Picker pick = new Picker(); final PlayBtn btn = new PlayBtn();
        float prog; String status = ""; boolean launching, shown;
        Timer anim, launch; final long t0 = System.nanoTime();

        PlayPage() { add(pick); add(btn); anim = new Timer(16, e -> repaint()); }
        public void addNotify() { super.addNotify(); anim.start(); }
        public void removeNotify() { super.removeNotify(); anim.stop(); if (launch != null) launch.stop(); }

        Rectangle hero() { return new Rectangle(32, 4, getWidth() - 64, getHeight() - 4 - 110 - 20 - 24); }

        public void doLayout() {
            Rectangle h = hero();
            pick.setBounds(h.x + 36, h.y + h.height - 128, 330, 76);
            btn.setBounds(h.x + 36 + 330 + 16 - 14, h.y + h.height - 128 - 14, 238, 104);
        }

        void startLaunch() {
            if (launching) return;
            launching = true; shown = true; prog = 0;
            Profile p = cur();
            launch = new Timer(30, e -> {
                prog += 0.0065f;
                status = prog < .25f ? "Checking game files…" : prog < .55f ? "Downloading libraries…"
                        : prog < .8f ? "Applying " + p.mods.size() + " mod" + (p.mods.size() == 1 ? "" : "s") + "…"
                        : "Starting Minecraft " + p.version + "…";
                if (prog >= 1) {
                    prog = 1; launching = false;
                    status = "Demo finished. Hook your game runner in startLaunch()";
                    ((Timer) e.getSource()).stop();
                }
                btn.repaint();
            });
            launch.start();
        }

        void blob(Graphics2D g, double cx, double cy, float r, Color c) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), r, new float[]{0, 1}, new Color[]{alpha(c, 105), alpha(c, 0)}));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        }

        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            double t = (System.nanoTime() - t0) / 1e9;
            Rectangle h = hero();
            Shape old = g.getClip();
            g.setClip(rr(h.x, h.y, h.width, h.height, 28));
            g.setPaint(new GradientPaint(h.x, h.y, new Color(0x171736), h.x + h.width, h.y + h.height, new Color(0x0B0B19)));
            g.fill(h);
            blob(g, h.x + h.width * 0.25 + Math.sin(t * 0.4) * 120, h.y + h.height * 0.35 + Math.cos(t * 0.33) * 60, 360, A1);
            blob(g, h.x + h.width * 0.78 + Math.cos(t * 0.3) * 100, h.y + h.height * 0.3 + Math.sin(t * 0.45) * 70, 320, A2);
            blob(g, h.x + h.width * 0.55 + Math.sin(t * 0.22) * 140, h.y + h.height * 0.85 + Math.cos(t * 0.5) * 40, 300, new Color(0xFF5C8A));
            for (int i = 0; i < 40; i++) {
                double x = h.x + ((i * 137) % 100) / 100.0 * h.width + Math.sin(t * 0.6 + i) * 14;
                double sp = 10 + (i * 7) % 22;
                double y = h.y + h.height - ((t * sp + i * 61) % h.height);
                double sz = 2 + i % 3;
                g.setColor(new Color(255, 255, 255, 25 + (i * 11) % 60));
                g.fill(new Ellipse2D.Double(x, y, sz, sz));
            }
            g.setClip(old);
            g.setColor(LINE); g.setStroke(new BasicStroke(1f)); g.draw(rr(h.x + .5, h.y + .5, h.width - 1, h.height - 1, 28));

            Profile p = cur();
            text(g, "WELCOME BACK, PLAYER", F(Font.BOLD, 13), A2, h.x + 38, h.y + 82);
            text(g, "Jump back in.", F(Font.BOLD, 56), TEXT, h.x + 36, h.y + 150);
            text(g, p.name + "  ·  Minecraft " + p.version + "  ·  " + p.loader, F(Font.PLAIN, 17), new Color(0xB9B9D6), h.x + 38, h.y + 186);

            if (shown) {
                int bx = h.x + 36, by = h.y + h.height - 40, bw = 330 + 16 + 210;
                g.setColor(new Color(255, 255, 255, 24)); g.fill(rr(bx, by, bw, 6, 6));
                g.setPaint(new GradientPaint(bx, 0, A1, bx + bw, 0, A2)); g.fill(rr(bx, by, Math.max(6, bw * prog), 6, 6));
                text(g, status, F(Font.PLAIN, 13), MUTED, bx, by + 26);
            }

            int cw = (getWidth() - 64 - 32) / 3, y = h.y + h.height + 20;
            String[] lab = {"PROFILES", "MODS IN " + p.name.toUpperCase(), "MEMORY"};
            String[] val = {String.valueOf(profiles.size()), String.valueOf(p.mods.size()), p.ram + " GB"};
            Ic[] ics = {Ic.USERS, Ic.GRID, Ic.GEAR};
            for (int i = 0; i < 3; i++) {
                int x = 32 + i * (cw + 16);
                g.setColor(CARD); g.fill(rr(x, y, cw, 110, 22));
                g.setColor(LINE); g.draw(rr(x + .5, y + .5, cw - 1, 109, 22));
                g.setPaint(new GradientPaint(x + 22, y + 22, alpha(PAL[i][0], 70), x + 70, y + 70, alpha(PAL[i][1], 40)));
                g.fill(rr(x + 22, y + 28, 52, 52, 16));
                icon(g, ics[i], x + 48, y + 54, 22, TEXT);
                text(g, val[i], F(Font.BOLD, 30), TEXT, x + 92, y + 62);
                String l = lab[i];
                FontMetrics fm = g.getFontMetrics(F(Font.BOLD, 11));
                while (fm.stringWidth(l) > cw - 110 && l.length() > 4) l = l.substring(0, l.length() - 2);
                text(g, l, F(Font.BOLD, 11), MUTED, x + 93, y + 84);
            }
            g.dispose();
        }

        class Picker extends Fx {
            void click(MouseEvent e) { selected = (selected + 1) % profiles.size(); save(); PlayPage.this.repaint(); }
            protected void paintComponent(Graphics gr) {
                Graphics2D g = g2(gr); Profile p = cur();
                g.setColor(mix(new Color(255, 255, 255, 18), new Color(255, 255, 255, 34), hover));
                g.fill(rr(0, 0, getWidth(), getHeight(), 22));
                g.setColor(LINE); g.draw(rr(.5, .5, getWidth() - 1, getHeight() - 1, 22));
                Color[] pc = PAL[p.hue % PAL.length];
                g.setPaint(new GradientPaint(14, 14, pc[0], 62, 62, pc[1])); g.fill(rr(14, 14, 48, 48, 15));
                ctext(g, p.name.substring(0, 1).toUpperCase(), F(Font.BOLD, 22), Color.WHITE, 38, 38);
                text(g, p.name, F(Font.BOLD, 18), TEXT, 76, 36);
                text(g, p.version + " · " + p.loader + " · " + p.ram + " GB", F(Font.PLAIN, 13), MUTED, 76, 56);
                icon(g, Ic.CHEVRON, getWidth() - 26 + hover * 3, getHeight() / 2.0, 16, mix(MUTED, TEXT, hover));
                g.dispose();
            }
        }

        class PlayBtn extends Fx {
            void click(MouseEvent e) { startLaunch(); }
            protected void paintComponent(Graphics gr) {
                Graphics2D g = g2(gr);
                double sc = 1 - press * 0.04 + hover * 0.015;
                g.translate(getWidth() / 2.0, getHeight() / 2.0); g.scale(sc, sc); g.translate(-getWidth() / 2.0, -getHeight() / 2.0);
                for (int i = 7; i >= 1; i--) {
                    g.setColor(alpha(A1, (int) ((10 + 14 * hover) * (8 - i) / 7f)));
                    g.fill(rr(14 - i * 1.8, 14 - i * 1.8, 210 + i * 3.6, 76 + i * 3.6, 38 + i * 2));
                }
                g.setPaint(new GradientPaint(14, 14, mix(A1, A2, hover * 0.35f), 224, 90, mix(A2, A1, 0.25f + hover * 0.25f)));
                g.fill(rr(14, 14, 210, 76, 38));
                g.setColor(new Color(255, 255, 255, 40)); g.fill(rr(14, 14, 210, 30, 38));
                String s = launching ? "LAUNCHING" : "PLAY";
                g.setFont(F(Font.BOLD, 20));
                int tw = g.getFontMetrics().stringWidth(s);
                double sx = 119 - (tw + 34) / 2.0;
                if (!launching) icon(g, Ic.PLAY, sx + 10, 52, 22, Color.WHITE);
                text(g, s, F(Font.BOLD, 20), Color.WHITE, sx + (launching ? 0 : 34), 59);
                g.dispose();
            }
        }
    }

    // ───────────────────────── Profiles page ─────────────────────────
    static class ProfilesPage extends Page {
        final VPanel grid = new VPanel(new GridLayout(0, 3, 16, 16));
        final JScrollPane sp; final Fx nb;

        ProfilesPage() {
            VPanel wrap = new VPanel(new BorderLayout());
            wrap.setBorder(new EmptyBorder(4, 0, 28, 8));
            wrap.add(grid, BorderLayout.NORTH);
            sp = scroll(wrap); add(sp);
            nb = new Fx() {
                void click(MouseEvent e) {
                    String n = JOptionPane.showInputDialog(ProfilesPage.this, "Profile name:", "New profile", JOptionPane.PLAIN_MESSAGE);
                    if (n != null && !n.trim().isEmpty()) {
                        profiles.add(new Profile(n.trim(), "1.21.11", "Fabric", 4, profiles.size() % PAL.length));
                        selected = profiles.size() - 1; save(); rebuild();
                    }
                }
                protected void paintComponent(Graphics gr) {
                    Graphics2D g = g2(gr);
                    g.setPaint(new GradientPaint(0, 0, mix(A1, A2, hover * 0.4f), getWidth(), getHeight(), mix(A2, A1, 0.3f + hover * 0.3f)));
                    g.fill(rr(0, 0, getWidth(), getHeight(), 21));
                    icon(g, Ic.PLUS, 30, getHeight() / 2.0, 16, Color.WHITE);
                    text(g, "New profile", F(Font.BOLD, 14), Color.WHITE, 48, getHeight() / 2.0 + 5);
                    g.dispose();
                }
            };
            add(nb); rebuild();
        }

        void rebuild() {
            grid.removeAll();
            for (int i = 0; i < profiles.size(); i++) grid.add(new ProfileCard(i, this));
            grid.revalidate(); grid.repaint(); repaint();
        }

        public void doLayout() {
            sp.setBounds(32, 96, getWidth() - 52, getHeight() - 96);
            nb.setBounds(getWidth() - 32 - 160, 24, 160, 42);
        }

        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            header(g, "Profiles", "Each profile has its own version, loader, memory and mods.");
            g.dispose();
        }
    }

    static class ProfileCard extends Fx {
        final int index; final ProfilesPage page;
        ProfileCard(int i, ProfilesPage p) { index = i; page = p; setPreferredSize(new Dimension(300, 190)); }

        Rectangle[] chips() {
            Profile p = profiles.get(index);
            FontMetrics fm = getFontMetrics(F(Font.BOLD, 12));
            String[] s = {p.version, p.loader, p.ram + " GB"};
            Rectangle[] r = new Rectangle[3]; int x = 20;
            for (int i = 0; i < 3; i++) { int cw = fm.stringWidth(s[i]) + 24; r[i] = new Rectangle(x, getHeight() - 56, cw, 32); x += cw + 8; }
            return r;
        }

        void click(MouseEvent e) {
            Point pt = e.getPoint(); Profile p = profiles.get(index);
            if (profiles.size() > 1 && new Rectangle(getWidth() - 48, 14, 32, 32).contains(pt)) {
                profiles.remove(index);
                if (selected >= profiles.size()) selected = profiles.size() - 1;
                else if (selected > index) selected--;
                save(); page.rebuild(); return;
            }
            Rectangle[] c = chips();
            if (c[0].contains(pt)) { p.version = VERSIONS[(Arrays.asList(VERSIONS).indexOf(p.version) + 1) % VERSIONS.length]; }
            else if (c[1].contains(pt)) { p.loader = LOADERS[(Arrays.asList(LOADERS).indexOf(p.loader) + 1) % LOADERS.length]; }
            else if (c[2].contains(pt)) {
                int k = 0; for (int i = 0; i < RAMS.length; i++) if (RAMS[i] == p.ram) k = i;
                p.ram = RAMS[(k + 1) % RAMS.length];
            } else selected = index;
            save(); page.rebuild();
        }

        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr); Profile p = profiles.get(index);
            boolean sel = index == selected; int w = getWidth(), h = getHeight();
            g.translate(0, -hover * 2);
            g.setColor(mix(CARD, CARD_HI, hover)); g.fill(rr(0, 2, w, h - 4, 22));
            if (sel) { g.setPaint(new GradientPaint(0, 0, A1, w, h, A2)); g.setStroke(new BasicStroke(1.8f)); g.draw(rr(1, 3, w - 2.5, h - 6.5, 21)); }
            else { g.setColor(LINE); g.setStroke(new BasicStroke(1f)); g.draw(rr(.5, 2.5, w - 1, h - 5, 22)); }
            Color[] pc = PAL[p.hue % PAL.length];
            g.setPaint(new GradientPaint(20, 22, pc[0], 72, 74, pc[1])); g.fill(rr(20, 22, 52, 52, 16));
            ctext(g, p.name.substring(0, 1).toUpperCase(), F(Font.BOLD, 24), Color.WHITE, 46, 48);
            text(g, p.name, F(Font.BOLD, 18), TEXT, 86, 46);
            text(g, p.mods.size() + (p.mods.size() == 1 ? " mod" : " mods") + " installed", F(Font.PLAIN, 13), MUTED, 86, 66);
            if (sel) {
                g.setColor(alpha(GOOD, 40)); g.fill(rr(20, 90, 84, 22, 11));
                ctext(g, "SELECTED", F(Font.BOLD, 10), GOOD, 62, 101);
            }
            if (profiles.size() > 1) icon(g, Ic.TRASH, w - 32, 30, 18, mix(MUTED, new Color(0xEF4444), hover));
            Rectangle[] c = chips(); String[] s = {p.version, p.loader, p.ram + " GB"};
            for (int i = 0; i < 3; i++) {
                g.setColor(new Color(255, 255, 255, 14)); g.fill(rr(c[i].x, c[i].y, c[i].width, c[i].height, 16));
                ctext(g, s[i], F(Font.BOLD, 12), new Color(0xC9C9E2), c[i].getCenterX(), c[i].getCenterY());
            }
            g.dispose();
        }
    }

    // ───────────────────────── Discover (mod browser) ─────────────────────────
    static class Search extends JComponent {
        final JTextField tf = new JTextField();
        Search(Runnable onChange) {
            setLayout(null);
            tf.setOpaque(false); tf.setBorder(null); tf.setForeground(TEXT); tf.setCaretColor(A2); tf.setFont(F(Font.PLAIN, 15));
            add(tf);
            tf.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { onChange.run(); repaint(); }
                public void removeUpdate(DocumentEvent e) { onChange.run(); repaint(); }
                public void changedUpdate(DocumentEvent e) { onChange.run(); }
            });
            tf.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }
        public void doLayout() { tf.setBounds(46, 0, getWidth() - 62, getHeight()); }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            g.setColor(CARD); g.fill(rr(0, 0, getWidth(), getHeight(), getHeight()));
            if (tf.hasFocus()) { g.setPaint(new GradientPaint(0, 0, A1, getWidth(), 0, A2)); g.setStroke(new BasicStroke(1.6f)); }
            else { g.setColor(LINE); g.setStroke(new BasicStroke(1f)); }
            g.draw(rr(.8, .8, getWidth() - 1.6, getHeight() - 1.6, getHeight()));
            icon(g, Ic.SEARCH, 25, getHeight() / 2.0, 18, tf.hasFocus() ? A2 : MUTED);
            if (tf.getText().isEmpty()) text(g, "Search mods…", F(Font.PLAIN, 15), MUTED, 46, getHeight() / 2.0 + 5);
            g.dispose();
        }
    }

    static class Chip extends Fx {
        final String label; boolean active; final Runnable r;
        Chip(String l, Runnable r) { label = l; this.r = r; }
        void click(MouseEvent e) { r.run(); }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            if (active) { g.setPaint(new GradientPaint(0, 0, A1, getWidth(), getHeight(), A2)); g.fill(rr(0, 0, getWidth(), getHeight(), getHeight())); }
            else { g.setColor(mix(CARD, CARD_HI, hover)); g.fill(rr(0, 0, getWidth(), getHeight(), getHeight())); g.setColor(LINE); g.draw(rr(.5, .5, getWidth() - 1, getHeight() - 1, getHeight())); }
            ctext(g, label, F(Font.BOLD, 13), active ? Color.WHITE : mix(MUTED, TEXT, hover), getWidth() / 2.0, getHeight() / 2.0);
            g.dispose();
        }
    }

    static class ModsPage extends Page {
        final String[] cats = {"All", "Performance", "Visual", "Utility", "PvP"};
        final Chip[] chips = new Chip[cats.length];
        final Search search; final VPanel grid = new VPanel(new GridLayout(0, 3, 14, 14)); final JScrollPane sp;
        String cat = "All";

        ModsPage() {
            search = new Search(this::rebuild); add(search);
            for (int i = 0; i < cats.length; i++) {
                final String c = cats[i];
                chips[i] = new Chip(c, () -> { cat = c; for (Chip x : chips) x.active = x.label.equals(cat); for (Chip x : chips) x.repaint(); rebuild(); });
                chips[i].active = i == 0; add(chips[i]);
            }
            VPanel wrap = new VPanel(new BorderLayout());
            wrap.setBorder(new EmptyBorder(4, 0, 28, 8)); wrap.add(grid, BorderLayout.NORTH);
            sp = scroll(wrap); add(sp); rebuild();
        }

        void rebuild() {
            grid.removeAll();
            String q = search == null ? "" : search.tf.getText().trim().toLowerCase();
            for (Mod m : MODS) {
                if (!cat.equals("All") && !m.cat.equals(cat)) continue;
                if (!q.isEmpty() && !(m.name + " " + m.author + " " + m.desc).toLowerCase().contains(q)) continue;
                grid.add(new ModCard(m));
            }
            grid.revalidate(); grid.repaint();
        }

        public void doLayout() {
            search.setBounds(32, 92, 340, 44);
            int x = 392; FontMetrics fm = getFontMetrics(F(Font.BOLD, 13));
            for (Chip c : chips) { int w = fm.stringWidth(c.label) + 34; c.setBounds(x, 98, w, 32); x += w + 8; }
            sp.setBounds(32, 156, getWidth() - 52, getHeight() - 156);
        }

        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            header(g, "Discover", "Installing into  " + cur().name + "  ·  " + cur().version + " " + cur().loader + "   (demo catalogue, wire to Modrinth next)");
            g.dispose();
        }
    }

    static class ModCard extends Fx {
        final Mod m; boolean installing; float ip; Timer it;
        ModCard(Mod m) { this.m = m; setPreferredSize(new Dimension(300, 186)); }

        boolean installed() { return cur().mods.contains(m.name); }
        Rectangle btnRect() { return new Rectangle(getWidth() - 20 - 112, getHeight() - 58, 112, 36); }

        void click(MouseEvent e) {
            if (!btnRect().contains(e.getPoint()) || installing) return;
            if (installed()) { cur().mods.remove(m.name); save(); repaint(); return; }
            installing = true; ip = 0;
            it = new Timer(20, ev -> {
                ip += 0.035f;
                if (ip >= 1) { ip = 1; installing = false; cur().mods.add(m.name); save(); ((Timer) ev.getSource()).stop(); }
                repaint();
            });
            it.start();
        }

        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr); int w = getWidth(), h = getHeight();
            g.translate(0, -hover * 3);
            for (int i = 4; i >= 1; i--) { g.setColor(new Color(0, 0, 0, (int) (14 * hover * (5 - i) / 4f))); g.fill(rr(-i, 6 - i + i, w + i * 2, h - 8 + i, 24)); }
            g.setColor(mix(CARD, CARD_HI, hover)); g.fill(rr(0, 4, w, h - 8, 22));
            g.setColor(mix(LINE, alpha(A2, 90), hover)); g.setStroke(new BasicStroke(1f)); g.draw(rr(.5, 4.5, w - 1, h - 9, 22));
            Color[] pc = PAL[m.hue % PAL.length];
            g.setPaint(new GradientPaint(20, 22, pc[0], 72, 74, pc[1])); g.fill(rr(20, 22, 50, 50, 15));
            ctext(g, m.name.substring(0, 1), F(Font.BOLD, 22), Color.WHITE, 45, 47);
            text(g, m.name, F(Font.BOLD, 17), TEXT, 84, 44);
            text(g, "by " + m.author, F(Font.PLAIN, 12), MUTED, 84, 63);
            para(g, m.desc, F(Font.PLAIN, 13), new Color(0xB0B0CA), 20, 96, w - 40, 18, 2);
            // category pill
            FontMetrics fm = g.getFontMetrics(F(Font.BOLD, 11));
            int pw = fm.stringWidth(m.cat) + 20;
            g.setColor(alpha(pc[0], 40)); g.fill(rr(20, h - 54, pw, 28, 14));
            ctext(g, m.cat, F(Font.BOLD, 11), mix(pc[0], Color.WHITE, 0.5f), 20 + pw / 2.0, h - 40);
            // install button
            Rectangle b = btnRect(); boolean inst = installed();
            if (installing) {
                g.setColor(new Color(255, 255, 255, 22)); g.fill(rr(b.x, b.y, b.width, b.height, 18));
                Shape old = g.getClip(); g.clip(rr(b.x, b.y, b.width, b.height, 18));
                g.setPaint(new GradientPaint(b.x, 0, A1, b.x + b.width, 0, A2)); g.fillRect(b.x, b.y, (int) (b.width * ip), b.height);
                g.setClip(old);
                ctext(g, "Installing", F(Font.BOLD, 13), Color.WHITE, b.getCenterX(), b.getCenterY());
            } else if (inst) {
                g.setColor(alpha(GOOD, 38 + (int) (30 * hover))); g.fill(rr(b.x, b.y, b.width, b.height, 18));
                icon(g, Ic.CHECK, b.x + 30, b.getCenterY(), 16, GOOD);
                text(g, "Installed", F(Font.BOLD, 13), GOOD, b.x + 46, b.getCenterY() + 5);
            } else {
                g.setPaint(new GradientPaint(b.x, b.y, mix(A1, A2, hover * 0.5f), b.x + b.width, b.y + b.height, mix(A2, A1, 0.3f)));
                g.fill(rr(b.x, b.y, b.width, b.height, 18));
                icon(g, Ic.DOWNLOAD, b.x + 29, b.getCenterY(), 15, Color.WHITE);
                text(g, "Install", F(Font.BOLD, 13), Color.WHITE, b.x + 46, b.getCenterY() + 5);
            }
            g.dispose();
        }
    }

    // ───────────────────────── Settings page ─────────────────────────
    static class SettingRow extends Fx {
        final String title, desc; final int idx; float k; Timer kt;
        SettingRow(String t, String d, int i) {
            title = t; desc = d; idx = i; k = OPT[i] ? 1 : 0;
            kt = new Timer(14, e -> {
                float target = OPT[idx] ? 1 : 0; k += (target - k) * 0.25f;
                if (Math.abs(target - k) < 0.01f) { k = target; kt.stop(); }
                repaint();
            });
        }
        void click(MouseEvent e) { OPT[idx] = !OPT[idx]; kt.start(); }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr); int w = getWidth(), h = getHeight();
            g.setColor(mix(CARD, CARD_HI, hover)); g.fill(rr(0, 0, w, h, 20));
            g.setColor(LINE); g.draw(rr(.5, .5, w - 1, h - 1, 20));
            text(g, title, F(Font.BOLD, 16), TEXT, 24, 32);
            text(g, desc, F(Font.PLAIN, 13), MUTED, 24, 54);
            int tx = w - 24 - 52, ty = h / 2 - 15;
            g.setPaint(new GradientPaint(tx, 0, mix(new Color(0x2A2A40), A1, k), tx + 52, 0, mix(new Color(0x2A2A40), A2, k)));
            g.fill(rr(tx, ty, 52, 30, 30));
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(tx + 4 + k * 22, ty + 4, 22, 22));
            g.dispose();
        }
    }

    static class SettingsPage extends Page {
        final SettingRow[] rows = {
                new SettingRow("Close launcher on game start", "Hide Zenith once Minecraft is running.", 0),
                new SettingRow("Show snapshots", "Include snapshot versions when creating profiles.", 1),
                new SettingRow("Smooth animations", "Page transitions, hover effects and the animated hero.", 2),
                new SettingRow("Discord rich presence", "Show what you are playing on your Discord profile.", 3)};
        SettingsPage() { for (SettingRow r : rows) add(r); }
        public void doLayout() {
            int w = Math.min(getWidth() - 64, 780);
            for (int i = 0; i < rows.length; i++) rows[i].setBounds(32, 100 + i * 86, w, 74);
        }
        protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            header(g, "Settings", "Launcher preferences.");
            text(g, "Zenith Client 1.0.0   ·   Java " + System.getProperty("java.version"), F(Font.PLAIN, 12), MUTED, 33, 100 + rows.length * 86 + 20);
            g.dispose();
        }
    }

    // ───────────────────────── Entry ─────────────────────────
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(() -> {
            load();
            JFrame f = new JFrame("Zenith Client");
            f.setUndecorated(true);
            f.setBackground(new Color(0, 0, 0, 0));
            f.setSize(1200, 760);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(new Root(f));
            f.setVisible(true);
            go(0);
        });
    }
}
