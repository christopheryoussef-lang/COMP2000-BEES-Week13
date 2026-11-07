import java.util.Locale;

/** Fixture world positions for the GUI driver's projected mouse clicks. */
public final class PickingFixture {
    public static void main(String[] args) {
        for (Flower flower : new Simulation(2000).getField().getFlowers()) {
            System.out.printf(Locale.ROOT, "%s\t%.9f\t%.9f\t%.9f%n", flower.getName(),
                    flower.getPosition().x(), flower.getPosition().y(), flower.getPosition().z());
        }
    }
}
