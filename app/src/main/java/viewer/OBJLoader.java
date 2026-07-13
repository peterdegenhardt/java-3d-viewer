package viewer;

import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Lädt Wavefront .OBJ-Dateien inklusive .MTL-Material und Texture-Bildern.
 * Unterstützt: v, vn, vt, f, usemtl, mtllib
 * Annahme: Dreiecke (f v/vt/vn oder f v//vn) — keine Quads oder N-Gone.
 * Y-up → Z-up Konvertierung wie bei STL (Y↔Z tauschen).
 */
public class OBJLoader {

    /**
     * Lädt eine .obj-Datei und gibt eine Liste von Meshes zurück
     * (ein OBJ kann mehrere Materialien enthalten → mehrere Meshes).
     */
    public List<Mesh> load(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists())
            throw new FileNotFoundException("OBJ file not found: " + filePath);

        String baseName = file.getName().replaceAll("(?i)\\.obj$", "");
        Path parentDir = file.toPath().getParent();
        if (parentDir == null) parentDir = Paths.get(".");

        // === Datei einlesen ===
        List<Vector3f> positions = new ArrayList<>();
        List<Vector3f> texCoords = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();

        // Materialien
        Map<String, Material> materials = new HashMap<>();
        String currentMtl = null;

        // Face-Daten pro Material sammeln
        // Key = Materialname, Value = Liste von "posIdx/texIdx/normalIdx"
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }

        // Zuerst mtllib finden und laden
        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts[0].equals("mtllib") && parts.length >= 2) {
                String mtlFile = parts[1];
                Path mtlPath = parentDir.resolve(mtlFile).normalize();
                if (mtlPath.toFile().exists()) {
                    loadMTL(mtlPath.toString(), parentDir, materials);
                    System.out.println("  Loaded MTL: " + mtlPath.getFileName());
                } else {
                    System.out.println("  Warning: MTL not found: " + mtlPath);
                }
            }
        }

        // Dann Geometrie parsen
        // Zwischenspeicher: Material → Liste von Integer-Tripeln (pos, tex, norm)
        Map<String, List<int[]>> materialFaces = new LinkedHashMap<>();

        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");

            switch (parts[0]) {
                case "v": // Vertex position (x y z)
                    if (parts.length >= 4) {
                        float vx = Float.parseFloat(parts[1]);
                        float vy = Float.parseFloat(parts[2]);
                        float vz = Float.parseFloat(parts[3]);
                        // Y-up → Z-up: tausche y und z
                        positions.add(new Vector3f(vx, vz, vy));
                    }
                    break;
                case "vt": // Texture coordinate (u v [w])
                    if (parts.length >= 3) {
                        float u = Float.parseFloat(parts[1]);
                        float v = Float.parseFloat(parts[2]);
                        texCoords.add(new Vector3f(u, 1.0f - v, 0)); // v invertieren für OpenGL
                    }
                    break;
                case "vn": // Vertex normal (nx ny nz)
                    if (parts.length >= 4) {
                        float nx = Float.parseFloat(parts[1]);
                        float ny = Float.parseFloat(parts[2]);
                        float nz = Float.parseFloat(parts[3]);
                        // Y-up → Z-up
                        normals.add(new Vector3f(nx, nz, ny));
                    }
                    break;
                case "usemtl": // Use material
                    if (parts.length >= 2) {
                        currentMtl = parts[1];
                        System.out.println("    usemtl: '" + currentMtl + "'");
                        // Sicherstellen, dass der Eintrag existiert
                        materialFaces.putIfAbsent(currentMtl, new ArrayList<>());
                    }
                    break;
                case "f": // Face
                    if (parts.length < 4) break;
                    if (currentMtl == null) {
                        currentMtl = "__default__";
                        System.out.println("    DEBUG: no usemtl before f, using __default__");
                    }
                    materialFaces.putIfAbsent(currentMtl, new ArrayList<>());

                    List<int[]> faceData = materialFaces.get(currentMtl);

                    // Parse "f v1/vt1/vn1 v2/vt2/vn2 v3/vt3/vn3" oder "f v1//vn1 v2//vn2 v3//vn3" oder "f v1 v2 v3"
                    List<int[]> verts = new ArrayList<>();
                    for (int i = 1; i < parts.length; i++) {
                        String[] indices = parts[i].split("/");
                        int pi = parseIntOrZero(indices[0]) - 1; // OBJ ist 1-indexed
                        int ti = indices.length > 1 && !indices[1].isEmpty() ? parseIntOrZero(indices[1]) - 1 : -1;
                        int ni = indices.length > 2 && !indices[2].isEmpty() ? parseIntOrZero(indices[2]) - 1 : -1;
                        verts.add(new int[]{pi, ti, ni});
                    }

                    // Debug: Face-Format erkennen
                    if (verts.size() > 0 && verts.get(0)[1] < 0) {
                        // keine UVs in diesem Face
                    }
                    if (verts.size() > 0 && verts.get(0)[2] < 0) {
                        // keine Normalen in diesem Face
                    }

                    // Fan-triangulation: (0,1,2), (0,2,3), (0,3,4), ...
                    for (int i = 1; i < verts.size() - 1; i++) {
                        faceData.add(verts.get(0));
                        faceData.add(verts.get(i));
                        faceData.add(verts.get(i + 1));
                    }
                    break;
            }
        }

        // === Meshes bauen ===
        List<Mesh> meshes = new ArrayList<>();

        System.out.println("  DEBUG: " + materialFaces.size() + " material group(s) found");
        for (String mtlKey : materialFaces.keySet()) {
            System.out.println("  DEBUG:   material='" + mtlKey + "', faces=" + materialFaces.get(mtlKey).size() / 3);
            Material m = materials.get(mtlKey);
            if (m != null) {
                System.out.println("  DEBUG:     MTL: name=" + m.name + ", mapKd=" + m.mapKd);
            } else {
                System.out.println("  DEBUG:     No MTL entry found for '" + mtlKey + "'");
            }
        }

        for (Map.Entry<String, List<int[]>> entry : materialFaces.entrySet()) {
            String mtlName = entry.getKey();
            List<int[]> verts = entry.getValue();

            if (verts.isEmpty()) continue;

            Material mat = materials.getOrDefault(mtlName, new Material());
            int textureId = loadTexture(mat.mapKd, parentDir);

            // Vertex-Arrays aufbauen (pos+normal+uv interleaved)
            List<Vector3f> meshPos = new ArrayList<>();
            List<Vector3f> meshNorm = new ArrayList<>();
            List<Vector3f> meshUv = new ArrayList<>();

            for (int[] v : verts) {
                int pi = v[0];
                int ti = v[1];
                int ni = v[2];

                if (pi >= 0 && pi < positions.size()) {
                    meshPos.add(positions.get(pi));
                } else {
                    meshPos.add(new Vector3f(0, 0, 0));
                }

                if (ni >= 0 && ni < normals.size()) {
                    meshNorm.add(normals.get(ni));
                } else {
                    meshNorm.add(new Vector3f(0, 0, 1));
                }

                if (ti >= 0 && ti < texCoords.size()) {
                    meshUv.add(texCoords.get(ti));
                } else {
                    meshUv.add(new Vector3f(0, 0, 0));
                }
            }

            if (meshPos.isEmpty()) continue;

            String meshName = baseName + (mtlName.equals("__default__") ? "" : "_" + mtlName);
            Mesh mesh = Mesh.fromTriangleList(meshPos, meshNorm, meshUv, meshName, textureId);

            // Auf Boden setzen (wie STL)
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (Vector3f p : meshPos) {
                if (p.z < minZ) minZ = p.z;
                if (p.z > maxZ) maxZ = p.z;
            }
            float height = maxZ - minZ;
            mesh.getPosition().z = -minZ;
            mesh.getPosition().y = 3;
            if (height > 3f) {
                float s = 3f / height;
                mesh.getScale().set(s, s, s);
                System.out.println("  Auto-scaled by " + String.format("%.3f", s) + " (original height=" + String.format("%.2f", height) + " -> 3m)");
            }

            System.out.println("  Mesh '" + meshName + "': " + (meshPos.size() / 3) + " triangles"
                + (textureId > 0 ? ", textured" : ", no texture")
                + " | " + mat.toString());

            meshes.add(mesh);
        }

        if (meshes.isEmpty()) {
            System.out.println("  Warning: No faces found in OBJ file!");
        }

        return meshes;
    }

    /** Lädt eine .mtl-Datei und füllt die Material-Map */
    private void loadMTL(String mtlPath, Path baseDir, Map<String, Material> materials) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(mtlPath))) {
            String line;
            Material current = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");

                switch (parts[0]) {
                    case "newmtl":
                        if (parts.length >= 2) {
                            current = new Material();
                            current.name = parts[1];
                            materials.put(current.name, current);
                            System.out.println("    MTL: newmtl '" + current.name + "'");
                        }
                        break;
                    case "map_Kd": // Diffuse texture map
                        if (current != null && parts.length >= 2) {
                            // Der Rest des Strings ist der Pfad (kann Leerzeichen enthalten)
                            String texFile = line.substring(parts[0].length()).trim();
                            current.mapKd = baseDir.resolve(texFile).normalize().toString();
                            System.out.println("    Texture: " + current.mapKd);
                        }
                        break;
                    case "Kd": // Diffuse color (r g b)
                        if (current != null && parts.length >= 4) {
                            current.kd[0] = Float.parseFloat(parts[1]);
                            current.kd[1] = Float.parseFloat(parts[2]);
                            current.kd[2] = Float.parseFloat(parts[3]);
                        }
                        break;
                    case "Ka": // Ambient color
                        if (current != null && parts.length >= 4) {
                            current.ka[0] = Float.parseFloat(parts[1]);
                            current.ka[1] = Float.parseFloat(parts[2]);
                            current.ka[2] = Float.parseFloat(parts[3]);
                        }
                        break;
                    case "Ks": // Specular color
                        if (current != null && parts.length >= 4) {
                            current.ks[0] = Float.parseFloat(parts[1]);
                            current.ks[1] = Float.parseFloat(parts[2]);
                            current.ks[2] = Float.parseFloat(parts[3]);
                        }
                        break;
                    case "Ns": // Specular exponent
                        if (current != null && parts.length >= 2) {
                            current.ns = Float.parseFloat(parts[1]);
                        }
                        break;
                    case "d": // Dissolve (opacity)
                    case "Tr":
                        if (current != null && parts.length >= 2) {
                            current.d = Float.parseFloat(parts[1]);
                        }
                        break;
                }
            }
        }
    }

    /** Lädt ein Texture-Bild via stb_image und gibt eine OpenGL-Texture-ID zurück (0 bei Fehler) */
    private int loadTexture(String filePath, Path baseDir) {
        if (filePath == null || filePath.isEmpty()) return 0;

        File texFile = new File(filePath);
        if (!texFile.exists()) {
            System.out.println("    Warning: Texture file not found: " + filePath);
            return 0;
        }

        IntBuffer w = BufferUtils.createIntBuffer(1);
        IntBuffer h = BufferUtils.createIntBuffer(1);
        IntBuffer comp = BufferUtils.createIntBuffer(1);

        ByteBuffer image = STBImage.stbi_load(filePath, w, h, comp, 4); // RGBA forced
        if (image == null) {
            System.out.println("    Error loading texture: " + STBImage.stbi_failure_reason());
            return 0;
        }

        int width = w.get(0);
        int height = h.get(0);

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);

        // Texture-Parameter
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Bild in OpenGL laden
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        glGenerateMipmap(GL_TEXTURE_2D);

        STBImage.stbi_image_free(image);

        System.out.println("    Loaded texture: " + width + "x" + height + " (" + texFile.getName() + ")");
        return texId;
    }

    private int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Material-Datenstruktur für MTL */
    private static class Material {
        String name;
        String mapKd; // Pfad zur Diffuse-Texture
        float[] kd = {0.8f, 0.8f, 0.8f}; // Diffuse color fallback
        float[] ka = {0.2f, 0.2f, 0.2f};
        float[] ks = {0.0f, 0.0f, 0.0f};
        float ns = 32.0f;
        float d = 1.0f;

        @Override
        public String toString() {
            return (mapKd != null ? "tex=" + new File(mapKd).getName() : "color=(" + kd[0] + "," + kd[1] + "," + kd[2] + ")");
        }
    }
}
