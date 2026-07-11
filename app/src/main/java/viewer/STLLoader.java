package viewer;

import org.joml.Vector3f;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class STLLoader {

    /**
     * Load an STL file (ASCII or binary) and return a Mesh.
     * The mesh's position.z is set so its lowest point sits at z=0.
     */
    public Mesh load(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists())
            throw new FileNotFoundException("STL file not found: " + filePath);

        String name = file.getName().replaceAll("(?i)\\.stl$", "");

        // Quick check: if file starts with "solid", it's ASCII
        boolean isAscii = isAsciiSTL(file);

        if (isAscii) {
            return loadAscii(file, name);
        } else {
            return loadBinary(file, name);
        }
    }

    private boolean isAsciiSTL(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[80];
            int bytesRead = raf.read(header);
            if (bytesRead < 5) return false;
            String sig = new String(header, 0, Math.min(80, bytesRead), StandardCharsets.US_ASCII).trim().toLowerCase();
            return sig.startsWith("solid");
        }
    }

    private Mesh loadAscii(File file, String name) throws IOException {
        List<Vector3f> positions = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Vector3f currentNormal = new Vector3f(0, 0, 1);

            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");

                if (parts[0].equals("facet")) {
                    // facet normal nx ny nz (original, wird nach dem Einlesen konvertiert)
                    if (parts.length >= 5) {
                        currentNormal = new Vector3f(
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3]),
                            Float.parseFloat(parts[4])
                        );
                    }
                } else if (parts[0].equals("vertex")) {
                    if (parts.length >= 4) {
                        // STL-Format: x,y,z mit y=height → konvertieren zu Z-up: y↔z
                        float vx = Float.parseFloat(parts[1]);
                        float vy = Float.parseFloat(parts[3]); // alte Z → neue Y
                        float vz = Float.parseFloat(parts[2]); // alte Y → neue Z (Höhe)
                        positions.add(new Vector3f(vx, vy, vz));
                        // Normale auch konvertieren
                        Vector3f convertedNormal = new Vector3f(
                            currentNormal.x,
                            currentNormal.z, // alte Z → neue Y
                            currentNormal.y  // alte Y → neue Z
                        );
                        normals.add(convertedNormal);
                    }
                }
            }
        }

        System.out.println("  Loaded " + (positions.size() / 3) + " triangles (" + name + ")");

        Mesh mesh = Mesh.fromTriangleList(positions, normals, name);
        // Auf Boden: Zielposition.z = -minZ
        float minZ = getMinZ(positions);
        mesh.getPosition().z = -minZ;
        System.out.println("  Positioned at z=" + String.format("%.2f", -minZ) + " (minZ=" + String.format("%.2f", minZ) + ")");
        return mesh;
    }

    private Mesh loadBinary(File file, String name) throws IOException {
        List<Vector3f> positions = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileChannel channel = raf.getChannel();
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Skip 80-byte header
            buffer.position(80);

            // Read triangle count (4 bytes, unsigned int)
            int triangleCount = buffer.getInt() & 0xFFFFFFFF;

            // Ensure buffer has enough data
            long expectedSize = 84L + triangleCount * 50L; // 50 bytes per triangle
            if (channel.size() < expectedSize) {
                throw new IOException("File truncated: expected " + expectedSize + " bytes, got " + channel.size());
            }

            for (int i = 0; i < triangleCount; i++) {
                // Normal (original: nx,ny,nz → konvertieren: y↔z)
                float nx = buffer.getFloat();
                float ny = buffer.getFloat();
                float nz = buffer.getFloat();
                Vector3f normal = new Vector3f(nx, nz, ny); // alte Z→Y, alte Y→Z

                // 3 vertices (STL-Format: x,y,z mit y=height, z=depth)
                for (int v = 0; v < 3; v++) {
                    float vx = buffer.getFloat();
                    float vy = buffer.getFloat(); // alte Y (Höhe) → neue Z
                    float vz = buffer.getFloat(); // alte Z (Tiefe) → neue Y
                    positions.add(new Vector3f(vx, vz, vy));
                    normals.add(normal);
                }

                // Skip 2-byte attribute byte count
                buffer.position(buffer.position() + 2);
            }

            System.out.println("  Loaded " + triangleCount + " triangles (" + name + ")");
        }

        Mesh mesh = Mesh.fromTriangleList(positions, normals, name);
        float minZ = getMinZ(positions);
        mesh.getPosition().z = -minZ;
        System.out.println("  Positioned at z=" + String.format("%.2f", -minZ) + " (minZ=" + String.format("%.2f", minZ) + ")");
        return mesh;
    }

    /** 
     * Ermittelt den tiefsten Z-Wert aller Vertices und gibt ihn zurück.
     * positive Werte: Objekt schwebt, negative: Objekt ragt in den Boden.
     */
    private float getMinZ(List<Vector3f> positions) {
        float minZ = Float.MAX_VALUE;
        for (Vector3f p : positions) {
            if (p.z < minZ) minZ = p.z;
        }
        return minZ;
    }
}
