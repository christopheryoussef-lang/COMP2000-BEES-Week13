package Components;

/** Immutable world coordinates, independent of the graphics library. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Coordinates must be finite.");
        }
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public double distanceTo(Vec3 other) {
        return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2)
                + Math.pow(z - other.z, 2));
    }

    public Vec3 moveTowards(Vec3 target, double distance) {
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Movement must be finite and non-negative.");
        }
        double remaining = distanceTo(target);
        if (remaining <= distance || remaining < 1e-9) return target;
        double fraction = distance / remaining;
        return add((target.x - x) * fraction, (target.y - y) * fraction,
                (target.z - z) * fraction);
    }
}
