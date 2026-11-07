import Components.Vec3;

public abstract class Location extends SceneObject {
    protected Location(String name, Vec3 position, Vec3 halfSize) {
        super(name, position, halfSize);
    }
}
