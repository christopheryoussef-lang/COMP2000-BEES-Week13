import Components.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Perspective projection and its inverse picking ray, using only Java maths. */
public final class CameraProjection {
    public record Point(double x, double y, double depth, double scale) { }

    private final int width;
    private final int height;
    private final double focal;
    private final Vec3 origin;
    private final Vec3 forward;
    private final Vec3 right;
    private final Vec3 up;

    public CameraProjection(int width, int height, double yaw, double pitch, double distance) {
        if (width <= 0 || height <= 0 || !Double.isFinite(yaw) || !Double.isFinite(pitch)
                || !Double.isFinite(distance) || distance <= 0) {
            throw new IllegalArgumentException("Invalid camera geometry.");
        }
        this.width = width;
        this.height = height;
        focal = height / (2.0 * Math.tan(Math.toRadians(22.5)));
        origin = new Vec3(distance * Math.cos(pitch) * Math.sin(yaw),
                0.5 + distance * Math.sin(pitch), distance * Math.cos(pitch) * Math.cos(yaw));
        forward = new Vec3(-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch),
                -Math.cos(pitch) * Math.cos(yaw));
        right = new Vec3(Math.cos(yaw), 0, -Math.sin(yaw));
        up = new Vec3(-Math.sin(pitch) * Math.sin(yaw), Math.cos(pitch),
                -Math.sin(pitch) * Math.cos(yaw));
    }

    public Vec3 origin() { return origin; }
    public int width() { return width; }
    public int height() { return height; }

    public Point project(Vec3 position) {
        Vec3 relative = position.add(-origin.x(), -origin.y(), -origin.z());
        double depth = dot(relative, forward);
        double scale = focal / Math.max(0.01, depth);
        return new Point(width / 2.0 + dot(relative, right) * scale,
                height / 2.0 - dot(relative, up) * scale, depth, scale);
    }

    public Vec3 rayDirection(double x, double y) {
        double horizontal = (x - width / 2.0) / focal;
        double vertical = (height / 2.0 - y) / focal;
        // Ray/box intersection does not require a normalized direction.
        return forward.add(right.x() * horizontal + up.x() * vertical,
                right.y() * horizontal + up.y() * vertical,
                right.z() * horizontal + up.z() * vertical);
    }

    /** Clip polygons at the near plane so close camera positions do not invert the ground. */
    public List<Point> projectFace(List<Vec3> vertices) {
        if (vertices.isEmpty()) return List.of();
        List<Point> projected = new ArrayList<>();
        Vec3 previous = vertices.get(vertices.size() - 1);
        double previousDepth = project(previous).depth();
        for (Vec3 current : vertices) {
            double depth = project(current).depth();
            if ((depth >= 0.1) != (previousDepth >= 0.1)) {
                double t = (0.1 - previousDepth) / (depth - previousDepth);
                projected.add(project(previous.add((current.x() - previous.x()) * t,
                        (current.y() - previous.y()) * t, (current.z() - previous.z()) * t)));
            }
            if (depth >= 0.1) projected.add(project(current));
            previous = current;
            previousDepth = depth;
        }
        return List.copyOf(projected);
    }

    private static double dot(Vec3 a, Vec3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }
}
