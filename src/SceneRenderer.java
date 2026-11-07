import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** Swing view/controller. The live model is confined to the event dispatch thread. */
public final class SceneRenderer {
    private static final int TOP = 92, BOTTOM = 110, SIDEBAR = 310;
    private static final Color INK = new Color(29, 53, 45);
    private static final Color PAPER = new Color(246, 247, 240);
    private static final Color SKY = new Color(230, 236, 218);
    private static final Color MUTED = new Color(104, 119, 104);
    private static final Color GOLD = new Color(240, 186, 73);
    private static final Color RED = new Color(185, 63, 53);
    private static final Color BLUE = new Color(62, 107, 160);
    private enum Action { WORKER, FLOWERS, WASP, VISITOR, PAUSE, SPEED, THREATS, RESET }

    public void run(Simulation simulation, int frameLimit, Path screenshot) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A desktop display is required. Use --headless or --compare without a display.");
        }
        SceneAssets assets = new SceneAssets();
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame window = new JFrame("Bee Simulator");
                MeadowPanel panel = new MeadowPanel(window, simulation, assets, frameLimit, screenshot, closed, failure);
                window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                window.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosing(WindowEvent event) { panel.close(); }
                });
                window.setContentPane(panel);
                window.pack();
                fitToScreen(window);
                window.setVisible(true);
                panel.requestFocusInWindow();
                panel.start();
            });
            closed.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Desktop interrupted.", error);
        } catch (InvocationTargetException error) {
            throw new IOException("Cannot open the desktop: " + error.getCause(), error);
        }
        if (failure.get() != null) throw new IOException(failure.get().getMessage(), failure.get());
    }

    /** Respect the usable monitor area, including the OS taskbar and window decorations. */
    private static void fitToScreen(JFrame window) {
        Rectangle available = new Rectangle(window.getGraphicsConfiguration().getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(window.getGraphicsConfiguration());
        available.x += insets.left;
        available.y += insets.top;
        available.width -= insets.left + insets.right;
        available.height -= insets.top + insets.bottom;
        window.setMinimumSize(new Dimension(Math.min(1100, available.width), Math.min(720, available.height)));
        window.setSize(Math.min(window.getWidth(), available.width), Math.min(window.getHeight(), available.height));
        window.setLocation(available.x + (available.width - window.getWidth()) / 2,
                available.y + (available.height - window.getHeight()) / 2);
    }

    private static final class MeadowPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JFrame window;
        private final Simulation simulation;
        private final MeadowPainter meadow;
        private final int frameLimit;
        private final Path finalScreenshot;
        private final CountDownLatch closed;
        private final AtomicReference<Exception> failure;
        private final ObjectPicker<SceneObject> picker = new ObjectPicker<>();
        private final Timer timer;
        private SceneObject selected;
        private SceneObject hovered;
        private int mouseX = -1, mouseY = -1;
        private final Deque<HoneySample> honeyHistory = new ArrayDeque<>();
        private record HoneySample(double time, double honey) { }
        private long previousTick;
        private int frames, dragX, dragY;
        private double yaw = 0.45, pitch = 0.66, distance = 33;
        private boolean paths, closing, comparing;
        private String message = "Click a bee, flower, or hive to inspect it.";
        private double messageTimer = 8;
        private String comparisonTitle = "4 paired seeds / 180 seconds";
        private String comparisonDetail = "New worlds; same seeds for both policies.";
        private List<String> comparisonLines = List.of("Compare nectar delivered by each policy.");
        private double[] comparisonMeans = new double[0];

        MeadowPanel(JFrame window, Simulation simulation, SceneAssets assets, int frameLimit,
                Path screenshot, CountDownLatch closed, AtomicReference<Exception> failure) {
            this.window = window;
            this.simulation = simulation;
            meadow = new MeadowPainter(assets);
            this.frameLimit = frameLimit;
            finalScreenshot = screenshot;
            this.closed = closed;
            this.failure = failure;
            selected = simulation.getHive();
            setPreferredSize(new Dimension(1280, 800));
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            setBackground(SKY);
            bindControls();
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    mouseX = event.getX(); mouseY = event.getY();
                    hovered = null;
                    boolean button = policyBounds().contains(mouseX, mouseY) || compareBounds().contains(mouseX, mouseY);
                    for (int i = 0; i < Action.values().length; i++) button |= buttonBounds(i).contains(mouseX, mouseY);
                    if (overScene(mouseX, mouseY)) {
                        CameraProjection camera = camera();
                        hovered = picker.pick(simulation.getSceneObjects(), camera.origin(),
                                camera.rayDirection(mouseX, mouseY - TOP)).orElse(null);
                    }
                    setCursor(Cursor.getPredefinedCursor(button || hovered != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
                @Override public void mouseExited(MouseEvent event) {
                    mouseX = mouseY = -1; hovered = null; repaint();
                }
                @Override public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    dragX = event.getX(); dragY = event.getY();
                    if (SwingUtilities.isLeftMouseButton(event)) click(event.getX(), event.getY());
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if ((event.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0 && overScene(event.getX(), event.getY())) {
                        hovered = null;
                        yaw -= (event.getX() - dragX) * 0.005;
                        pitch += (event.getY() - dragY) * 0.003;
                        clampCamera(); repaint();
                    }
                    dragX = event.getX(); dragY = event.getY();
                }
                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    if (overScene(event.getX(), event.getY())) {
                        distance += event.getPreciseWheelRotation() * 1.5;
                        clampCamera(); repaint();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
            timer = new Timer(16, event -> tick());
            recordHoney();
        }
        void start() { previousTick = System.nanoTime(); timer.start(); }
        private void tick() {
            try {
                long now = System.nanoTime();
                double delta = Math.min(0.1, (now - previousTick) / 1_000_000_000.0);
                previousTick = now;
                simulation.advance(delta);
                recordHoney();
                messageTimer -= delta;
                if (selected != null && !selected.isActive()) selected = null;
                if (hovered != null && !hovered.isActive()) hovered = null;
                repaint();
                if (frameLimit > 0 && ++frames >= frameLimit) close();
            } catch (RuntimeException error) {
                failure.compareAndSet(null, error); close();
            }
        }
        private void recordHoney() {
            double time = simulation.getElapsed();
            if (!honeyHistory.isEmpty() && time < honeyHistory.getLast().time()) honeyHistory.clear();
            if (honeyHistory.isEmpty() || time - honeyHistory.getLast().time() >= 1) {
                honeyHistory.addLast(new HoneySample(time, simulation.getHive().getHoney()));
                while (honeyHistory.size() > 120) honeyHistory.removeFirst();
            }
        }
        void close() {
            if (closing) return;
            closing = true;
            timer.stop();
            try {
                if (finalScreenshot != null) saveScreenshot(finalScreenshot);
            } catch (IOException error) {
                failure.compareAndSet(null, error);
            } finally {
                System.out.println("FINAL " + simulation.summary());
                window.dispose(); closed.countDown();
            }
        }
        private void bind(String key, Runnable callback) {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
            getActionMap().put(key, new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent event) { callback.run(); repaint(); }
            });
        }
        private void bindControls() {
            String[] keys = {"B", "F", "W", "E", "SPACE", "X", "T", "R"};
            Action[] actions = Action.values();
            for (int i = 0; i < keys.length; i++) {
                Action action = actions[i];
                bind(keys[i], () -> perform(action));
            }
            bind("P", this::changePolicy);
            bind("C", this::comparePolicies);
            bind("G", () -> paths = !paths);
            bind("H", () -> select(simulation.getHive()));
            bind("Q", () -> select(simulation.getHive().getBees().stream()
                    .filter(bee -> bee instanceof BeeQueen && bee.isActive()).findFirst().orElse(null)));
            bind("TAB", () -> {
                List<SceneObject> objects = simulation.getSceneObjects().stream().filter(SceneObject::isActive).toList();
                if (!objects.isEmpty()) select(objects.get((objects.indexOf(selected) + 1) % objects.size()));
            });
            bind("HOME", () -> { yaw = 0.45; pitch = 0.66; distance = 33; });
            bind("LEFT", () -> yaw -= 0.08);
            bind("RIGHT", () -> yaw += 0.08);
            bind("UP", () -> { distance -= 1; clampCamera(); });
            bind("DOWN", () -> { distance += 1; clampCamera(); });
            bind("PAGE_UP", () -> { pitch += 0.05; clampCamera(); });
            bind("PAGE_DOWN", () -> { pitch -= 0.05; clampCamera(); });
            bind("F12", () -> {
                try {
                    saveScreenshot(Path.of("screenshots", "bee-simulator.png"));
                    notifyUser("Screenshot saved to screenshots/bee-simulator.png");
                } catch (IOException error) { notifyUser("Screenshot failed: " + error.getMessage()); }
            });
            bind("ESCAPE", this::close);
        }
        private void clampCamera() {
            distance = Math.max(8, Math.min(45, distance));
            pitch = Math.max(0.22, Math.min(1.25, pitch));
        }
        private int sceneWidth() { return getWidth() - SIDEBAR; }
        private int sceneHeight() { return getHeight() - TOP - BOTTOM; }
        private CameraProjection camera() { return new CameraProjection(sceneWidth(), sceneHeight(), yaw, pitch, distance); }
        private boolean overScene(int x, int y) { return x < sceneWidth() && y >= TOP && y < TOP + sceneHeight(); }
        private void click(int x, int y) {
            for (int i = 0; i < Action.values().length; i++) {
                if (buttonBounds(i).contains(x, y)) { perform(Action.values()[i]); return; }
            }
            if (policyBounds().contains(x, y)) { changePolicy(); return; }
            if (compareBounds().contains(x, y)) { comparePolicies(); return; }
            if (overScene(x, y)) {
                CameraProjection camera = camera();
                select(picker.pick(simulation.getSceneObjects(), camera.origin(), camera.rayDirection(x, y - TOP)).orElse(null));
            }
        }
        private void select(SceneObject object) {
            selected = object;
            System.out.println("SELECT " + (object == null ? "none" : object.getType() + " / " + object.getName()));
        }
        private void notifyUser(String text) { message = text; messageTimer = 6; }
        private void perform(Action action) {
            switch (action) {
                case WORKER -> {
                    Bee bee = simulation.getHive().addBee(BeeFactory.Kind.WORKER);
                    if (bee == null) notifyUser("The hive has reached its population limit.");
                    else { select(bee); notifyUser("Added " + bee.getName() + "."); }
                }
                case FLOWERS -> notifyUser("Added " + simulation.getField().addFlowers(10) + " flowers.");
                case WASP, VISITOR -> {
                    Enemy enemy = action == Action.WASP ? simulation.spawnWasp() : simulation.spawnExterminator();
                    if (enemy == null) notifyUser("There are already 12 visitors in the meadow.");
                    else { select(enemy); notifyUser(enemy.getName() + " entered the meadow."); }
                }
                case PAUSE -> { simulation.togglePause(); notifyUser(simulation.isPaused() ? "Simulation paused." : "Simulation resumed."); }
                case SPEED -> { simulation.cycleSpeed(); notifyUser("Simulation speed: " + simulation.getSpeed() + "x."); }
                case THREATS -> { simulation.toggleAutoThreats(); notifyUser(simulation.hasAutoThreats() ? "Automatic threats enabled." : "Automatic threats disabled."); }
                case RESET -> { simulation.reset(); hovered = null; honeyHistory.clear(); recordHoney(); selected = simulation.getHive(); notifyUser("Meadow reset; foraging policy kept."); }
            }
            System.out.println("ACTION " + action + " " + simulation.summary());
        }
        private void changePolicy() {
            simulation.setForagingPolicy(simulation.getForagingPolicy().next());
            notifyUser("Foraging: " + simulation.getForagingPolicy().label() + ". Applies to each worker's next trip.");
            System.out.println("POLICY " + simulation.getForagingPolicy().key());
        }
        private void comparePolicies() {
            if (comparing) return;
            comparing = true;
            // Capture only values on the EDT. The background task never touches simulation.
            long seed = simulation.getSeed();
            boolean threats = simulation.hasAutoThreats();
            comparisonTitle = "4 paired seeds / 180 seconds";
            comparisonDetail = "Seeds " + seed + ".." + (seed + 3) + "; threats " + (threats ? "on" : "off");
            comparisonLines = List.of("Running 8 independent worlds...");
            comparisonMeans = new double[0];
            new SwingWorker<List<SimulationExperiment.Result>, Void>() {
                @Override protected List<SimulationExperiment.Result> doInBackground() {
                    return SimulationExperiment.compare(seed, 4, 180, threats, true);
                }
                @Override protected void done() {
                    // SwingWorker delivers completion on the EDT, including failures.
                    comparing = false;
                    if (closing) return;
                    try {
                        List<SimulationExperiment.Result> results = get();
                        Map<ForagingPolicy, DoubleSummaryStatistics> stats = SimulationExperiment.deliveredByPolicy(results);
                        comparisonMeans = new double[]{stats.get(ForagingPolicy.NEAREST).getAverage(),
                                stats.get(ForagingPolicy.RICHEST).getAverage()};
                        comparisonLines = List.of(
                                String.format(Locale.ROOT, "Nearest: %.1f nectar / run", stats.get(ForagingPolicy.NEAREST).getAverage()),
                                String.format(Locale.ROOT, "Richest: %.1f nectar / run", stats.get(ForagingPolicy.RICHEST).getAverage()));
                        notifyUser("Comparison finished. These are separate seeded trials, not the live colony.");
                        System.out.println("COMPARE complete\n" + SimulationExperiment.csv(results));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        comparisonLines = List.of("Comparison interrupted; press C to retry.");
                    } catch (ExecutionException error) {
                        comparisonLines = List.of("Comparison failed; press C to retry.");
                        System.err.println("Comparison failed: " + error.getCause());
                    }
                    repaint();
                }
            }.execute();
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (sceneWidth() <= 0 || sceneHeight() <= 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                Graphics2D scene = (Graphics2D) g.create(0, TOP, sceneWidth(), sceneHeight());
                try { drawScene(scene); } finally { scene.dispose(); }
                drawInterface(g);
            } finally { g.dispose(); }
        }
        private void drawScene(Graphics2D g) {
            g.setPaint(new GradientPaint(0, 0, new Color(242, 244, 232),
                    0, sceneHeight(), new Color(222, 232, 211)));
            g.fillRect(0, 0, sceneWidth(), sceneHeight());
            meadow.paint(g, camera(), simulation, selected, hovered, paths);
            text(g, "THE MEADOW", 26, 35, 12, MUTED, true);
            text(g, "A small world, always in motion.", 26, 61, sceneWidth() < 800 ? 17 : 20, INK, true);
            text(g, "Drag to orbit  /  Scroll to zoom  /  Click to inspect", 27, 83, 12, MUTED, false);
            boolean paused = simulation.isPaused();
            g.setColor(paused ? new Color(251, 231, 179) : new Color(215, 230, 203));
            g.fillRoundRect(26, 100, paused ? 98 : 114, 27, 20, 20);
            g.setColor(paused ? new Color(183, 128, 43) : new Color(78, 132, 85));
            g.fillOval(38, 110, 6, 6);
            text(g, paused ? "PAUSED" : "LIVE COLONY", 51, 118, 10, INK, true);
            drawHoneyChart(g);
            if (simulation.getHive().livingCount() == 0 && simulation.getHive().getLarvae().isEmpty()) {
                card(g, new Rectangle(26, 145, Math.min(420, sceneWidth() - 52), 53), Color.WHITE);
                text(g, "The colony is empty. Reset [R] to start again.", 40, 176, 13, RED, true);
            }
            int legendY = sceneHeight() - 39;
            g.setColor(new Color(247, 248, 240, 230));
            g.fillRoundRect(22, legendY, Math.min(510, sceneWidth() - 44), 28, 16, 16);
            String[] names = {"Worker", "Queen", "Larva", "Wasp", "Visitor"};
            Color[] colors = {GOLD, new Color(188, 147, 70), new Color(221, 204, 155), RED, BLUE};
            int x = 36;
            for (int i = 0; i < names.length; i++) {
                g.setColor(colors[i]); g.fillOval(x, legendY + 10, 7, 7);
                text(g, names[i], x + 14, legendY + 18, 11, INK, false);
                x += g.getFontMetrics().stringWidth(names[i]) + 38;
            }
            if (selected != null) {
                CameraProjection.Point position = camera().project(selected.getPosition());
                if (position.depth() > 0.1) {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                    int width = g.getFontMetrics().stringWidth(selected.getName()) + 24;
                    int labelX = Math.max(12, Math.min(sceneWidth() - width - 12, (int) position.x() - width / 2));
                    int labelY = Math.max(150, Math.min(sceneHeight() - 85, (int) position.y() - 58));
                    card(g, new Rectangle(labelX, labelY, width, 24), new Color(255, 251, 230));
                    text(g, selected.getName(), labelX + 12, labelY + 16, 11, INK, true);
                }
            }
        }
        private void drawHoneyChart(Graphics2D g) {
            int width = Math.min(224, sceneWidth() / 3);
            int x = sceneWidth() - width - 23, y = 22;
            card(g, new Rectangle(x, y, width, 113), new Color(250, 251, 244, 235));
            text(g, "HONEY IN STORAGE", x + 15, y + 22, 10, MUTED, true);
            text(g, String.format(Locale.ROOT, "%,.0f", simulation.getHive().getHoney()), x + 15, y + 49, 23, INK, true);
            text(g, "last 120s", x + width - 70, y + 23, 10, MUTED, false);
            double maximum = Math.max(30, honeyHistory.stream().mapToDouble(HoneySample::honey).max().orElse(30));
            int left = x + 15, right = x + width - 15, top = y + 60, bottom = y + 96;
            g.setColor(new Color(224, 229, 212)); g.drawLine(left, bottom, right, bottom);
            Path2D.Double line = new Path2D.Double();
            double firstTime = honeyHistory.getFirst().time();
            double span = Math.max(10, honeyHistory.getLast().time() - firstTime);
            boolean first = true;
            double lastX = left, lastY = bottom;
            for (HoneySample sample : honeyHistory) {
                double px = left + (sample.time() - firstTime) / span * (right - left);
                double py = bottom - sample.honey() / maximum * (bottom - top);
                if (first) line.moveTo(px, py); else line.lineTo(px, py);
                first = false; lastX = px; lastY = py;
            }
            Path2D.Double area = (Path2D.Double) line.clone();
            area.lineTo(lastX, bottom); area.lineTo(left, bottom); area.closePath();
            g.setPaint(new GradientPaint(left, top, new Color(225, 185, 81, 100), left, bottom, new Color(225, 185, 81, 8)));
            g.fill(area);
            g.setColor(new Color(191, 139, 40)); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line); g.fillOval((int) lastX - 3, (int) lastY - 3, 6, 6); g.setStroke(new BasicStroke(1));
        }
        private void drawInterface(Graphics2D g) {
            int width = getWidth(), height = getHeight(), panelX = sceneWidth();
            Hive hive = simulation.getHive();
            g.setPaint(new GradientPaint(0, 0, INK, width, TOP, new Color(39, 69, 53)));
            g.fillRect(0, 0, width, TOP);
            Polygon honeycomb = new Polygon();
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI / 3 * i;
                honeycomb.addPoint(40 + (int) (Math.cos(angle) * 20), 44 + (int) (Math.sin(angle) * 20));
            }
            g.setColor(GOLD); g.fillPolygon(honeycomb);
            g.setColor(INK); g.setStroke(new BasicStroke(2));
            g.drawOval(32, 37, 16, 14); g.drawLine(38, 38, 38, 50); g.drawLine(43, 38, 43, 50);
            g.setStroke(new BasicStroke(1));
            text(g, "BEE MEADOW", 72, 43, 23, PAPER, true);
            text(g, "COLONY SIMULATOR", 73, 65, 10, new Color(178, 197, 173), false);
            int metricsStart = 322;
            int spacing = (width - metricsStart - 157) / 4;
            stat(g, metricsStart, "HONEY", String.format(Locale.ROOT, "%,.0f", hive.getHoney()));
            stat(g, metricsStart + spacing, "WORKERS", Long.toString(hive.getWorkerCount()));
            stat(g, metricsStart + spacing * 2, "BROOD", Integer.toString(hive.getLarvae().size()));
            stat(g, metricsStart + spacing * 3, "THREATS", Integer.toString(simulation.getEnemies().size()));
            g.setColor(new Color(255, 255, 255, 9)); g.fillRoundRect(width - 144, 16, 123, 60, 16, 16);
            text(g, String.format(Locale.ROOT, "%02d:%02d", (int) simulation.getElapsed() / 60,
                    (int) simulation.getElapsed() % 60), width - 128, 43, 25, GOLD, true);
            text(g, simulation.getSpeed() + "x speed  /  " + (simulation.isPaused() ? "paused" : "running"),
                    width - 128, 62, 10, SKY, false);
            g.setColor(new Color(229, 233, 221)); g.fillRect(panelX, TOP, SIDEBAR, sceneHeight());
            Rectangle inspector = new Rectangle(panelX + 14, TOP + 14, SIDEBAR - 28, sceneHeight() - 238);
            card(g, inspector, new Color(253, 253, 247));
            int contentX = panelX + 30;
            text(g, "INSPECTOR", contentX, inspector.y + 27, 10, MUTED, true);
            g.setColor(new Color(120, 154, 104)); g.fillOval(panelX + SIDEBAR - 38, inspector.y + 19, 7, 7);
            if (selected == null) {
                text(g, "Explore the colony", contentX, inspector.y + 61, 20, INK, true);
                text(g, "Every bee has a job.", contentX, inspector.y + 88, 13, MUTED, false);
                text(g, "Select one to follow its journey.", contentX, inspector.y + 108, 12, MUTED, false);
                g.setColor(new Color(235, 239, 224)); g.fillRoundRect(contentX, inspector.y + 130, SIDEBAR - 60, 50, 10, 10);
                text(g, "H  Hive     Q  Queen     Tab  Next", contentX + 12, inspector.y + 160, 11, INK, true);
            } else {
                text(g, fit(g, selected.getName(), SIDEBAR - 60, 21), contentX, inspector.y + 60, 21, INK, true);
                text(g, selected.getType(), contentX, inspector.y + 81, 12, MUTED, false);
                g.setColor(new Color(228, 232, 219)); g.drawLine(contentX, inspector.y + 94, panelX + SIDEBAR - 30, inspector.y + 94);
                Map<String, String> details = selected.getStats();
                List<Map.Entry<String, String>> stats = details.entrySet().stream()
                        .filter(entry -> !entry.getKey().equals("Type") && !entry.getKey().equals("Position")).toList();
                int rowHeight = Math.min(30, (inspector.height - 145) / Math.max(1, stats.size() - 1));
                int y = inspector.y + 116;
                for (Map.Entry<String, String> entry : stats) {
                    text(g, entry.getKey(), contentX, y, 11, MUTED, false);
                    String value = fit(g, entry.getValue(), 128, 12);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                    int valueX = panelX + SIDEBAR - 30 - g.getFontMetrics().stringWidth(value);
                    text(g, value, valueX, y, 12, INK, true);
                    y += rowHeight;
                }
                String position = details.get("Position");
                text(g, "POSITION   " + position, contentX, inspector.y + inspector.height - 14, 9, MUTED, false);
            }
            int y = height - BOTTOM - 203;
            card(g, new Rectangle(panelX + 14, y - 8, SIDEBAR - 28, 204), new Color(253, 253, 247));
            text(g, "FORAGING", panelX + 30, y + 18, 10, MUTED, true);
            text(g, "STRATEGY", panelX + SIDEBAR - 88, y + 18, 9, MUTED, false);
            button(g, policyBounds(), simulation.getForagingPolicy().label() + " [P]", true);
            text(g, "Changes on the next foraging trip", panelX + 30, y + 80, 11, MUTED, false);
            text(g, comparisonTitle, panelX + 30, y + 104, 11, INK, true);
            text(g, comparisonDetail, panelX + 30, y + 121, 10, MUTED, false);
            for (int i = 0; i < comparisonLines.size(); i++) {
                if (comparisonMeans.length == 2) {
                    double maximum = Math.max(1, Math.max(comparisonMeans[0], comparisonMeans[1]));
                    g.setColor(i == 0 ? new Color(226, 236, 211) : new Color(249, 237, 200));
                    g.fillRoundRect(panelX + 26, y + 128 + i * 17,
                            (int) ((SIDEBAR - 56) * comparisonMeans[i] / maximum), 15, 5, 5);
                }
                text(g, comparisonLines.get(i), panelX + 30, y + 140 + i * 17, 11, INK, false);
            }
            button(g, compareBounds(), comparing ? "Comparing..." : "Compare policies [C]", false);
            g.setColor(PAPER); g.fillRect(0, height - BOTTOM, width, BOTTOM);
            g.setColor(new Color(217, 223, 205)); g.drawLine(0, height - BOTTOM, width, height - BOTTOM);
            for (int i = 0; i < Action.values().length; i++) {
                Action action = Action.values()[i];
                boolean active = action == Action.PAUSE && simulation.isPaused() || action == Action.THREATS && simulation.hasAutoThreats();
                actionButton(g, buttonBounds(i), action, active);
            }
            String footer = messageTimer > 0 ? message
                    : "Arrows: orbit / zoom   PgUp/PgDn: tilt   Home: camera   G: paths   P: policy   C: compare   F12: screenshot   Esc: exit";
            g.setColor(new Color(113, 145, 96)); g.fillOval(25, height - 29, 5, 5);
            text(g, fit(g, footer, width - 165, 11), 39, height - 23, 11, MUTED, false);
            text(g, "SEED " + simulation.getSeed(), width - 101, height - 23, 10, MUTED, true);
        }
        private Rectangle buttonBounds(int index) {
            int cell = (getWidth() - 40) / 8;
            return new Rectangle(20 + index * cell, getHeight() - 88, cell - 8, 42);
        }
        private Rectangle policyBounds() { return new Rectangle(sceneWidth() + 30, getHeight() - BOTTOM - 171, SIDEBAR - 60, 34); }
        private Rectangle compareBounds() { return new Rectangle(sceneWidth() + 30, getHeight() - BOTTOM - 36, SIDEBAR - 60, 29); }
        private String label(Action action) {
            return switch (action) {
                case WORKER -> "+ Worker [B]";
                case FLOWERS -> "+ Flowers [F]";
                case WASP -> "+ Wasp [W]";
                case VISITOR -> "+ Visitor [E]";
                case PAUSE -> simulation.isPaused() ? "Resume [Space]" : "Pause [Space]";
                case SPEED -> "Speed " + simulation.getSpeed() + "x [X]";
                case THREATS -> "Threats " + (simulation.hasAutoThreats() ? "on" : "off") + " [T]";
                case RESET -> "Reset [R]";
            };
        }
        private void button(Graphics2D g, Rectangle bounds, String label, boolean active) {
            boolean hover = bounds.contains(mouseX, mouseY);
            g.setColor(active ? (hover ? new Color(247, 203, 104) : GOLD)
                    : hover ? new Color(56, 85, 65) : INK);
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
            g.setColor(active ? new Color(210, 161, 56) : new Color(32, 57, 43));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
            int size = bounds.width < 140 ? 11 : 12;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            int x = bounds.x + (bounds.width - g.getFontMetrics().stringWidth(label)) / 2;
            text(g, label, x, bounds.y + bounds.height / 2 + 4, size, active ? INK : PAPER, true);
        }
        private void actionButton(Graphics2D g, Rectangle bounds, Action action, boolean active) {
            String label = label(action);
            int bracket = label.lastIndexOf(" [");
            String title = label.substring(0, bracket);
            String key = label.substring(bracket + 2, label.length() - 1);
            boolean danger = action == Action.WASP || action == Action.VISITOR;
            boolean hover = bounds.contains(mouseX, mouseY);
            Color background = active ? new Color(247, 220, 147) : danger ? new Color(247, 236, 223) : new Color(234, 239, 224);
            if (hover) background = active ? new Color(246, 205, 106) : danger ? new Color(242, 222, 203) : new Color(220, 231, 203);
            g.setColor(background); g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 11, 11);
            g.setColor(active ? new Color(218, 175, 75) : danger ? new Color(231, 211, 188) : new Color(211, 222, 199));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 11, 11);
            Color ink = danger ? new Color(143, 83, 52) : INK;
            int size = bounds.width < 130 ? 10 : 11;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            int titleWidth = g.getFontMetrics().stringWidth(title);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            int keyWidth = g.getFontMetrics().stringWidth(key) + 10;
            int left = bounds.x + (bounds.width - titleWidth - keyWidth - 8) / 2;
            text(g, title, left, bounds.y + 26, size, ink, true);
            int keyX = left + titleWidth + 8;
            g.setColor(new Color(255, 255, 255, 165)); g.fillRoundRect(keyX, bounds.y + 12, keyWidth, 19, 5, 5);
            text(g, key, keyX + 5, bounds.y + 25, 9, ink, false);
        }
        private static void card(Graphics2D g, Rectangle bounds, Color color) {
            g.setColor(new Color(44, 68, 42, 8)); g.fillRoundRect(bounds.x, bounds.y + 3, bounds.width, bounds.height, 16, 16);
            g.setColor(color); g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
            g.setColor(new Color(210, 220, 200, 145)); g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
        }
        private static void stat(Graphics2D g, int x, String label, String value) {
            g.setColor(new Color(192, 209, 183, 35)); g.drawLine(x - 17, 25, x - 17, 66);
            text(g, label, x, 31, 10, new Color(179, 200, 171), true);
            text(g, value, x, 62, 24, PAPER, true);
        }
        private static String fit(Graphics2D g, String text, int width, int size) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
            if (g.getFontMetrics().stringWidth(text) <= width) return text;
            while (!text.isEmpty() && g.getFontMetrics().stringWidth(text + "...") > width) text = text.substring(0, text.length() - 1);
            return text + "...";
        }
        private static void text(Graphics2D g, String text, int x, int y, int size, Color color, boolean bold) {
            g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
            g.setColor(color); g.drawString(text, x, y);
        }
        private void saveScreenshot(Path path) throws IOException {
            Path absolute = path.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try { printAll(graphics); } finally { graphics.dispose(); }
            if (!ImageIO.write(image, "png", absolute.toFile())) throw new IOException("No PNG writer available.");
            System.out.println("SCREENSHOT " + absolute);
        }
    }
}
