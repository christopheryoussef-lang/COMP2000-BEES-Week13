/** Simulation time is measured in seconds, not rendered frames. */
public interface Updatable {
    void update(double deltaTime);
}
