import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import Components.Vec3;

/** Presentation only: the original world and assets drawn as a shaded meadow diorama. */
public final class MeadowPainter {
    private static final Color GRASS = new Color(146, 176, 103);
    private static final Color GOLD = new Color(235, 173, 54);
    private static final Color INK = new Color(33, 59, 48);
    private static final List<Vec3> OUTLINE = outline();
    private final SceneAssets assets;
    private final List<Vec3> grasses = new ArrayList<>();
    private BufferedImage terrain;
    private Vec3 terrainCamera;
    private record DrawCommand(double depth, Consumer<Graphics2D> paint) { }

    public MeadowPainter(SceneAssets assets) {
        this.assets = assets;
        // Decorative randomness is fixed and independent of Simulation.random.
        Random decoration = new Random(421);
        for (int i = 0; i < 560; i++) {
            double x = decoration.nextDouble() * 18.8 - 9.4;
            double z = decoration.nextDouble() * 18.8 - 9.4;
            grasses.add(new Vec3(x, 0.025, z));
        }
    }

    public void paint(Graphics2D g, CameraProjection camera, Simulation simulation,
            SceneObject selected, SceneObject hovered, boolean paths) {
        // Terrain is static. Repaint this layer only when the camera or viewport
        // changes, rather than re-projecting hundreds of grass blades each tick.
        if (terrain == null || terrain.getWidth() != camera.width() || terrain.getHeight() != camera.height()
                || !camera.origin().equals(terrainCamera)) {
            terrain = new BufferedImage(camera.width(), camera.height(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D background = terrain.createGraphics();
            try {
                background.setRenderingHints(g.getRenderingHints());
                drawTerrain(background, camera);
                drawGroundRing(background, camera, new Vec3(0, 0.03, 0), 1.4, new Color(98, 126, 68, 70), true);
                drawGroundRing(background, camera, new Vec3(0.7, 0.035, 0.7), 1.1, new Color(48, 75, 46, 35), true);
            } finally { background.dispose(); }
            terrainCamera = camera.origin();
        }
        g.drawImage(terrain, 0, 0, null);
        List<DrawCommand> commands = new ArrayList<>();
        for (SceneAssets.Face face : assets.tree()) {
            if (face.vertices().stream().allMatch(vertex -> Math.abs(vertex.y()) < 1e-9)) continue;
            List<CameraProjection.Point> points = camera.projectFace(face.vertices());
            if (points.size() < 3) continue;
            Polygon shape = polygon(points);
            double depth = points.stream().mapToDouble(CameraProjection.Point::depth).average().orElse(0);
            Color color = shadedFace(face);
            commands.add(new DrawCommand(depth, target -> {
                target.setColor(color); target.fillPolygon(shape);
                // Matching outlines hide anti-alias seams between touching mesh faces.
                target.setStroke(new BasicStroke(0.6f)); target.drawPolygon(shape);
            }));
        }
        for (SceneObject object : simulation.getSceneObjects()) {
            if (!object.isActive() || object instanceof Hive) continue;
            Vec3 ground = new Vec3(object.getPosition().x() + 0.1, 0.035, object.getPosition().z() + 0.16);
            if (object instanceof Bee || object instanceof Enemy) {
                drawGroundRing(g, camera, ground, object instanceof Exterminator ? 0.43 : 0.21,
                        new Color(36, 66, 43, 40), true);
            }
            CameraProjection.Point point = camera.project(object.getPosition());
            if (point.depth() > 0.1) commands.add(new DrawCommand(point.depth(),
                    target -> drawObject(target, object, point, camera, simulation.getElapsed())));
        }
        if (paths) drawPaths(g, camera, simulation);
        commands.sort(Comparator.comparingDouble(DrawCommand::depth).reversed());
        commands.forEach(command -> command.paint().accept(g));
        if (hovered != null && hovered != selected) highlight(g, camera, hovered, false);
        if (selected != null) highlight(g, camera, selected, true);
        g.setStroke(new BasicStroke(1));
    }

    private void drawTerrain(Graphics2D g, CameraProjection camera) {
        for (int i = 3; i >= 1; i--) {
            List<Vec3> shadow = new ArrayList<>();
            for (Vec3 p : OUTLINE) shadow.add(new Vec3(p.x() + 0.25 + i * 0.10, -0.8 - i * 0.03, p.z() + 0.45 + i * 0.10));
            g.setColor(new Color(35, 62, 44, 10 + i * 2));
            g.fillPolygon(polygon(camera.projectFace(shadow)));
        }
        List<DrawCommand> sides = new ArrayList<>();
        for (int i = 0; i < OUTLINE.size(); i++) {
            Vec3 a = OUTLINE.get(i), b = OUTLINE.get((i + 1) % OUTLINE.size());
            List<Vec3> face = List.of(a, b, b.add(0, -0.68, 0), a.add(0, -0.68, 0));
            List<CameraProjection.Point> points = camera.projectFace(face);
            if (points.size() < 3) continue;
            double depth = points.stream().mapToDouble(CameraProjection.Point::depth).average().orElse(0);
            Polygon shape = polygon(points);
            double light = 0.82 + 0.14 * Math.cos(Math.atan2(a.z(), a.x()) + 1.2);
            Color soil = tint(new Color(162, 139, 101), light);
            sides.add(new DrawCommand(depth, target -> { target.setColor(soil); target.fillPolygon(shape); }));
        }
        sides.sort(Comparator.comparingDouble(DrawCommand::depth).reversed());
        sides.forEach(command -> command.paint().accept(g));
        Polygon surface = polygon(camera.projectFace(OUTLINE));
        g.setPaint(new GradientPaint(surface.getBounds().x, surface.getBounds().y,
                new Color(170, 195, 120), surface.getBounds().x + surface.getBounds().width,
                surface.getBounds().y + surface.getBounds().height, GRASS));
        g.fillPolygon(surface);
        g.setColor(new Color(204, 219, 159)); g.setStroke(new BasicStroke(1.7f)); g.drawPolygon(surface);
        g.setStroke(new BasicStroke(1));
        java.awt.Shape clip = g.getClip();
        g.clip(surface);
        for (int i = 0; i < grasses.size(); i++) {
            Vec3 p = grasses.get(i);
            g.setColor(i % 3 == 0 ? new Color(205, 221, 156, 105) : new Color(87, 131, 70, 60));
            line(g, camera, p, p.add(0.06, 0.07, -0.02));
            if (i % 3 == 0) line(g, camera, p, p.add(-0.04, 0.10, 0.02));
        }
        // A path around the hive gives its position a readable visual anchor.
        drawGroundRing(g, camera, new Vec3(0.2, 0.02, -0.1), 1.15, new Color(198, 184, 130, 190), true);
        g.setClip(clip);
    }

    private void drawObject(Graphics2D g, SceneObject object, CameraProjection.Point p,
            CameraProjection camera, double time) {
        double x = p.x(), y = p.y();
        if (object instanceof Flower flower) {
            int size = Math.max(10, (int) (1.5 * p.scale()));
            BufferedImage sprite = assets.flower();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(sprite, (int) x - size / 2, (int) y - size / 2, size, size, null);
            if (flower.isOccupied()) {
                g.setColor(new Color(255, 241, 157, 150));
                g.fill(new Ellipse2D.Double(x + size * 0.22, y - size * 0.28, 3, 3));
            }
        } else if (object instanceof Bee bee) {
            boolean queen = bee instanceof BeeQueen;
            double wingBeat = Math.sin(time * 30 + bee.getName().hashCode()) * 0.6;
            int size = Math.max(13, (int) ((queen ? 1.1 : 0.83) * p.scale()));
            // The pose follows the same target as the model; it does not change movement.
            double angle = 0;
            if (bee instanceof BeeWorker worker) {
                Vec3 destination = worker.getTarget() != null ? worker.getTarget().getPosition()
                        : worker.getState() == BeeWorker.State.RETURNING ? worker.getHive().getPosition() : null;
                if (destination != null) {
                    CameraProjection.Point target = camera.project(destination);
                    angle = Math.atan2(target.y() - y, target.x() - x) + Math.PI;
                }
            }
            AffineTransform transform = g.getTransform();
            g.translate(x, y); g.rotate(angle);
            g.setColor(new Color(252, 255, 248, 130));
            g.fill(new Ellipse2D.Double(-size * 0.53, -size * 0.2, size * 0.5, size * (0.35 + wingBeat * 0.1)));
            g.fill(new Ellipse2D.Double(size * 0.03, -size * 0.2, size * 0.5, size * (0.35 - wingBeat * 0.1)));
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(assets.bee(), -size / 2, -size / 2, size, size, null);
            g.setTransform(transform);
            if (queen) {
                int cy = (int) (y - size / 2.0 - 6);
                Polygon crown = new Polygon(new int[]{(int) x - 5, (int) x - 5, (int) x - 1, (int) x,
                        (int) x + 2, (int) x + 5, (int) x + 5},
                        new int[]{cy + 5, cy, cy + 2, cy - 2, cy + 2, cy, cy + 5}, 7);
                g.setColor(GOLD); g.fillPolygon(crown);
                g.setColor(new Color(127, 82, 27)); g.setStroke(new BasicStroke(0.7f)); g.drawPolygon(crown);
            } else if (bee instanceof BeeWorker worker && worker.getNectar() > 0) {
                g.setColor(new Color(255, 224, 133));
                g.fillOval((int) x + size / 3, (int) y + size / 4, 4, 4);
            }
        } else if (object instanceof Larvae larva) {
            int r = Math.max(4, (int) (p.scale() * 0.22));
            g.setColor(new Color(104, 96, 63, 55)); g.fillOval((int) x - r, (int) y - r + 2, r * 2, r * 2);
            g.setColor(new Color(255, 246, 208)); g.fillOval((int) x - r, (int) y - r, r * 2, r * 2);
            g.setColor(new Color(211, 177, 112)); g.drawArc((int) x - r, (int) y - r, r * 2, r * 2,
                    90, (int) (-360 * larva.getAge() / Larvae.MATURATION_TIME));
        } else if (object instanceof Wasp) {
            int size = Math.max(14, (int) (p.scale() * 0.9));
            g.setColor(new Color(255, 247, 222, 200));
            g.fillOval((int) x - size / 2, (int) y - size / 2, size / 2, size / 2);
            g.fillOval((int) x, (int) y - size / 2, size / 2, size / 2);
            g.setColor(new Color(180, 76, 50)); g.fillOval((int) x - size / 2, (int) y - size / 4, size, size / 2);
            g.setColor(INK); g.setStroke(new BasicStroke(2));
            g.drawLine((int) x - 2, (int) y - size / 4, (int) x - 2, (int) y + size / 4);
            g.fillOval((int) x + size / 3, (int) y - 3, 5, 5);
        } else if (object instanceof Exterminator visitor) {
            int unit = Math.max(7, (int) (p.scale() * 0.6));
            g.setColor(new Color(38, 69, 67));
            g.fillRoundRect((int) x - unit / 2, (int) y + unit / 2, unit / 3, unit, 3, 3);
            g.fillRoundRect((int) x + unit / 6, (int) y + unit / 2, unit / 3, unit, 3, 3);
            g.setPaint(new GradientPaint((int) x - unit / 2, 0, new Color(118, 158, 164),
                    (int) x + unit / 2, 0, new Color(69, 112, 122)));
            g.fillRoundRect((int) x - unit / 2, (int) y - unit, unit, (int) (unit * 1.6), 5, 5);
            g.setColor(new Color(237, 208, 152)); g.fillOval((int) x - unit / 2, (int) y - unit * 2, unit, unit);
            g.setColor(new Color(58, 84, 74)); g.fillRoundRect((int) x - unit / 2 - 2, (int) y - unit * 2 - 1, unit + 4, 4, 4, 4);
            if (visitor.isSpraying()) {
                Vec3 ground = new Vec3(visitor.getPosition().x(), 0.06, visitor.getPosition().z());
                drawGroundRing(g, camera, ground, Exterminator.SPRAY_RADIUS, new Color(194, 83, 53, 35), true);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{5, 5}, 0));
                drawGroundRing(g, camera, ground, Exterminator.SPRAY_RADIUS, new Color(175, 76, 48, 180), false);
            }
        }
        g.setStroke(new BasicStroke(1));
    }

    private static void drawPaths(Graphics2D g, CameraProjection camera, Simulation simulation) {
        g.setColor(new Color(255, 242, 175, 180));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{4, 5}, 0));
        for (Bee bee : simulation.getHive().getBees()) {
            if (bee instanceof BeeWorker worker && worker.getTarget() != null) {
                line(g, camera, worker.getPosition(), worker.getTarget().getPosition());
            }
        }
        g.setStroke(new BasicStroke(1));
    }

    private static void highlight(Graphics2D g, CameraProjection camera, SceneObject object, boolean selected) {
        Vec3 p = object.getPosition();
        double radius = object instanceof Hive ? 0.86 : object instanceof Flower ? 0.52 : 0.45;
        Vec3 ring = object instanceof Flower ? new Vec3(p.x(), 0.06, p.z()) : p.add(0, -0.28, 0);
        drawGroundRing(g, camera, ring, radius, new Color(255, 240, 172, selected ? 70 : 35), true);
        g.setStroke(new BasicStroke(selected ? 2 : 1));
        drawGroundRing(g, camera, ring, radius, selected ? new Color(255, 224, 122) : new Color(255, 250, 220), false);
        CameraProjection.Point point = camera.project(p);
        if (point.depth() <= 0.1) return;
        int x = (int) point.x(), y = (int) point.y();
        g.setColor(new Color(255, 239, 189));
        g.drawLine(x, y - 16, x, y - 24);
        g.fillOval(x - 2, y - 27, 4, 4);
        g.setStroke(new BasicStroke(1));
    }

    private static Color shadedFace(SceneAssets.Face face) {
        Vec3 a = face.vertices().get(0), b = face.vertices().get(1), c = face.vertices().get(2);
        double ax = b.x() - a.x(), ay = b.y() - a.y(), az = b.z() - a.z();
        double bx = c.x() - a.x(), by = c.y() - a.y(), bz = c.z() - a.z();
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double light = length < 1e-9 ? 1 : 0.83 + 0.28 * Math.max(0, (-0.45 * nx + 0.8 * ny + 0.4 * nz) / length);
        Color base = face.color();
        if (base.getGreen() > base.getRed()) base = new Color(69, 120, 73);
        else if (base.getRed() < 100) base = new Color(128, 88, 54);
        else base = new Color(230, 171, 63);
        return tint(base, light);
    }

    private static Color tint(Color color, double scale) {
        return new Color(Math.min(255, (int) (color.getRed() * scale)),
                Math.min(255, (int) (color.getGreen() * scale)), Math.min(255, (int) (color.getBlue() * scale)));
    }

    private static List<Vec3> outline() {
        List<Vec3> points = new ArrayList<>();
        double half = 10.2, radius = 2.7;
        for (int corner = 0; corner < 4; corner++) {
            double centerX = corner == 0 || corner == 3 ? half - radius : -half + radius;
            double centerZ = corner < 2 ? half - radius : -half + radius;
            for (int i = 0; i <= 10; i++) {
                double angle = corner * Math.PI / 2 + i * Math.PI / 20;
                points.add(new Vec3(centerX + radius * Math.cos(angle), 0, centerZ + radius * Math.sin(angle)));
            }
        }
        return List.copyOf(points);
    }

    private static Polygon polygon(List<CameraProjection.Point> points) {
        Polygon polygon = new Polygon();
        for (CameraProjection.Point point : points) polygon.addPoint((int) Math.round(point.x()), (int) Math.round(point.y()));
        return polygon;
    }

    private static void drawGroundRing(Graphics2D g, CameraProjection camera, Vec3 center,
            double radius, Color color, boolean fill) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double angle = i * Math.PI / 20;
            points.add(center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        Polygon polygon = polygon(camera.projectFace(points));
        g.setColor(color);
        if (fill) g.fillPolygon(polygon); else g.drawPolygon(polygon);
    }

    private static void line(Graphics2D g, CameraProjection camera, Vec3 start, Vec3 end) {
        CameraProjection.Point a = camera.project(start), b = camera.project(end);
        if (a.depth() > 0.1 && b.depth() > 0.1) g.drawLine((int) a.x(), (int) a.y(), (int) b.x(), (int) b.y());
    }
}
