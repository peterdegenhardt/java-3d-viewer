package viewer;

import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {

    private int vaoId;
    private int vboId;
    private int vertexCount;
    private Vector3f position;
    private Vector3f rotation;
    private Vector3f scale;
    private String name;
    private int textureId; // 0 = keine Textur

    // Vertex-Layout: position 3f + normal 3f + uv 2f = 8 floats
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4; // 32 bytes
    private static final int POS_OFFSET = 0;       // 0 bytes
    private static final int NORMAL_OFFSET = 3;     // 12 bytes
    private static final int UV_OFFSET = 6;         // 24 bytes

    public Mesh(float[] vertices, int[] indices, String name) {
        this(vertices, indices, name, 0);
    }

    public Mesh(float[] vertices, int[] indices, String name, int textureId) {
        this.name = name;
        this.textureId = textureId;
        this.position = new Vector3f(0, 0, 0);
        this.rotation = new Vector3f(0, 0, 0);
        this.scale = new Vector3f(1, 1, 1);

        vertexCount = indices.length;
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // Vertex buffer (interleaved: position 3f + normal 3f + uv 2f)
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer fb = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
                .flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

        // Position attribute (location = 0) — 3 floats, offset 0
        glVertexAttribPointer(0, 3, GL_FLOAT, false, BYTES_PER_VERTEX, POS_OFFSET * 4);
        glEnableVertexAttribArray(0);

        // Normal attribute (location = 1) — 3 floats, offset 3 floats
        glVertexAttribPointer(1, 3, GL_FLOAT, false, BYTES_PER_VERTEX, NORMAL_OFFSET * 4);
        glEnableVertexAttribArray(1);

        // UV attribute (location = 2) — 2 floats, offset 6 floats
        glVertexAttribPointer(2, 2, GL_FLOAT, false, BYTES_PER_VERTEX, UV_OFFSET * 4);
        glEnableVertexAttribArray(2);

        // Index buffer
        int iboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
        java.nio.IntBuffer ib = java.nio.ByteBuffer.allocateDirect(indices.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asIntBuffer()
                .put(indices)
                .flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    public void render() {
        glBindVertexArray(vaoId);

        if (textureId > 0) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureId);
        }

        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        if (textureId > 0) {
            glDeleteTextures(textureId);
        }
    }

    public boolean hasTexture() { return textureId > 0; }
    public int getTextureId() { return textureId; }
    public void setTextureId(int id) { this.textureId = id; }
    public Vector3f getPosition() { return position; }
    public Vector3f getRotation() { return rotation; }
    public Vector3f getScale() { return scale; }
    public String getName() { return name; }

    /**
     * Erstellt eine neue Mesh aus Triangle-Listen (pos + normal + uv).
     * UVs werden auf (0,0) gesetzt, wenn die Liste leer ist.
     */
    public static Mesh fromTriangleList(java.util.List<Vector3f> positions,
                                         java.util.List<Vector3f> normals,
                                         java.util.List<Vector3f> uvs,
                                         String name, int textureId) {
        boolean hasUvs = uvs != null && uvs.size() == positions.size();

        float[] vertexData = new float[positions.size() * FLOATS_PER_VERTEX];
        for (int i = 0; i < positions.size(); i++) {
            Vector3f p = positions.get(i);
            Vector3f n = normals.get(i);
            float u = 0, v = 0;
            if (hasUvs) {
                u = uvs.get(i).x;
                v = uvs.get(i).y;
            }

            int base = i * FLOATS_PER_VERTEX;
            vertexData[base]     = p.x;
            vertexData[base + 1] = p.y;
            vertexData[base + 2] = p.z;
            vertexData[base + 3] = n.x;
            vertexData[base + 4] = n.y;
            vertexData[base + 5] = n.z;
            vertexData[base + 6] = u;
            vertexData[base + 7] = v;
        }

        int[] indices = new int[positions.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        return new Mesh(vertexData, indices, name, textureId);
    }

    /**
     * Compute face normals for flat-shaded STL rendering.
     * Returns interleaved float array: [x,y,z, nx,ny,nz, u,v, ...]
     */
    public static float[] computeNormals(float[] vertices) {
        float[] result = new float[vertices.length];
        // Normals nur für Positionen berechnen, UVs überspringen
        for (int i = 0; i < vertices.length; i += FLOATS_PER_VERTEX * 3) { // 3 vertices per triangle
            // Positionen auslesen (offset 0)
            float x1 = vertices[i], y1 = vertices[i+1], z1 = vertices[i+2];
            float x2 = vertices[i+FLOATS_PER_VERTEX], y2 = vertices[i+FLOATS_PER_VERTEX+1], z2 = vertices[i+FLOATS_PER_VERTEX+2];
            float x3 = vertices[i+FLOATS_PER_VERTEX*2], y3 = vertices[i+FLOATS_PER_VERTEX*2+1], z3 = vertices[i+FLOATS_PER_VERTEX*2+2];

            float ux = x2 - x1, uy = y2 - y1, uz = z2 - z1;
            float vx = x3 - x1, vy = y3 - y1, vz = z3 - z1;

            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;

            float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len != 0) { nx /= len; ny /= len; nz /= len; }

            for (int j = 0; j < 3; j++) {
                int base = i + j * FLOATS_PER_VERTEX;
                result[base + 3] = nx;
                result[base + 4] = ny;
                result[base + 5] = nz;
                // UVs kopieren
                result[base + 6] = vertices[base + 6];
                result[base + 7] = vertices[base + 7];
            }
        }
        return result;
    }

    /**
     * Create a mesh from raw vertex data (position+normal+uv interleaved).
     * Normals are computed automatically if not provided.
     */
    public static Mesh fromTriangleList(java.util.List<Vector3f> positions,
                                         java.util.List<Vector3f> normals,
                                         String name) {
        return fromTriangleList(positions, normals, null, name, 0);
    }
}
