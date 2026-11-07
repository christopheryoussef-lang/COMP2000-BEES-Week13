package Components;

/** Each object owns a box; there are no shared mutable interface fields. */
public final class CollisionBox {
    private Vec3 center;
    private final Vec3 halfSize;

    public CollisionBox(Vec3 center, Vec3 halfSize) {
        if (halfSize.x() <= 0 || halfSize.y() <= 0 || halfSize.z() <= 0) {
            throw new IllegalArgumentException("Collision box dimensions must be positive.");
        }
        this.center = center;
        this.halfSize = halfSize;
    }

    public Vec3 center() { return center; }
    public Vec3 halfSize() { return halfSize; }
    public void moveTo(Vec3 position) { center = java.util.Objects.requireNonNull(position); }

    /** Slab intersection: nearest forward hit, or infinity on a miss. */
    public double rayDistance(Vec3 origin, Vec3 direction) {
        double[] o = {origin.x(), origin.y(), origin.z()};
        double[] d = {direction.x(), direction.y(), direction.z()};
        double[] c = {center.x(), center.y(), center.z()};
        double[] h = {halfSize.x(), halfSize.y(), halfSize.z()};
        if (direction.distanceTo(Vec3.ZERO) < 1e-12) return Double.POSITIVE_INFINITY;
        double near = 0;
        double far = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-12) {
                if (o[axis] < c[axis] - h[axis] || o[axis] > c[axis] + h[axis]) {
                    return Double.POSITIVE_INFINITY;
                }
            } else {
                double a = (c[axis] - h[axis] - o[axis]) / d[axis];
                double b = (c[axis] + h[axis] - o[axis]) / d[axis];
                near = Math.max(near, Math.min(a, b));
                far = Math.min(far, Math.max(a, b));
                if (near > far) return Double.POSITIVE_INFINITY;
            }
        }
        return near;
    }
}
