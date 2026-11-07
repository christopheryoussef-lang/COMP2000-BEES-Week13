import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import Components.Vec3;

/** Loads the original PNGs and small, untextured OBJ using standard Java. */
public final class SceneAssets {
    public record Face(List<Vec3> vertices, Color color) {
        public Face { vertices = List.copyOf(vertices); }
    }
    private final BufferedImage bee;
    private final BufferedImage flower;
    private final List<Face> tree;

    public SceneAssets() throws IOException {
        for (String asset : new String[]{"Assets/Bee.png", "Assets/flower.png",
                "Assets/beetree.obj", "Assets/beetree.mtl"}) {
            if (!Files.isRegularFile(Path.of(asset))) {
                throw new IOException("Missing " + asset + ". Start with run.sh or run.bat from the project.");
            }
        }
        bee = ImageIO.read(Path.of("Assets/Bee.png").toFile());
        flower = ImageIO.read(Path.of("Assets/flower.png").toFile());
        if (bee == null || flower == null) throw new IOException("Cannot decode the meadow PNG assets.");
        tree = readTree();
    }
    public BufferedImage bee() { return bee; }
    public BufferedImage flower() { return flower; }
    public List<Face> tree() { return tree; }

    private static List<Face> readTree() throws IOException {
        Map<String, Color> materials = new HashMap<>();
        List<Vec3> vertices = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        String material = "";
        try {
            for (String line : Files.readAllLines(Path.of("Assets/beetree.mtl"))) {
                String[] parts = line.strip().split("\\s+");
                if (parts[0].equals("newmtl")) material = parts[1];
                else if (parts[0].equals("Kd")) materials.put(material, new Color(
                        Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
            }
            Color color = Color.GRAY;
            for (String line : Files.readAllLines(Path.of("Assets/beetree.obj"))) {
                String[] parts = line.strip().split("\\s+");
                switch (parts[0]) {
                    case "v" -> vertices.add(new Vec3(Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
                    case "usemtl" -> color = materials.getOrDefault(parts[1], Color.GRAY);
                    case "f" -> {
                        List<Vec3> polygon = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            int index = Integer.parseInt(parts[i].split("/", 2)[0]);
                            polygon.add(vertices.get(index > 0 ? index - 1 : vertices.size() + index));
                        }
                        if (polygon.size() < 3) throw new IllegalArgumentException("Face needs three vertices.");
                        faces.add(new Face(polygon, color));
                    }
                    default -> { /* Normals/UVs are unnecessary for flat Java2D faces. */ }
                }
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
            throw new IOException("Invalid meadow OBJ/MTL asset: " + error.getMessage(), error);
        }
        if (faces.isEmpty()) throw new IOException("The meadow OBJ contains no faces.");
        return List.copyOf(faces);
    }
}
