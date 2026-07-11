package viewer;

import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
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

    public Mesh(float[] vertices, int[] indices, String name) {
        this.name = name;
        this.position = new Vector3f(0, 0, 0);
        this.rotation = new Vector3f(0, 0, 0);
        this.scale = new Vector3f(1, 1, 1);

        vertexCount = indices.length;
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // Vertex buffer (interleaved: position 3f + normal 3f)
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer fb = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
                .flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

        // Position attribute (location = 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * 4, 0);
        glEnableVertexAttribArray(0);

        // Normal attribute (location = 1)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * 4, 3 * 4);
        glEnableVertexAttribArray(1);

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
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
    }

    public Vector3f getPosition() { return position; }
    public Vector3f getRotation() { return rotation; }
    public Vector3f getScale() { return scale; }
    public String getName() { return name; }

    /**
     * Compute face normals for flat-shaded STL rendering.
     * Returns interleaved float array: [x,y,z, nx,ny,nz, ...]
     */
    public static float[] computeNormals(float[] vertices) {
        float[] normals = new float[vertices.length];
        // For flat shading: each triangle's normals = face normal
        for (int i = 0; i < vertices.length; i += 18) { // 6 floats per vertex, 3 vertices per triangle
            float x1 = vertices[i], y1 = vertices[i+1], z1 = vertices[i+2];
            float x2 = vertices[i+6], y2 = vertices[i+7], z2 = vertices[i+8];
            float x3 = vertices[i+12], y3 = vertices[i+13], z3 = vertices[i+14];

            float ux = x2 - x1, uy = y2 - y1, uz = z2 - z1;
            float vx = x3 - x1, vy = y3 - y1, vz = z3 - z1;

            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;

            float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len != 0) { nx /= len; ny /= len; nz /= len; }

            for (int j = 0; j < 3; j++) {
                normals[i + j*6 + 3] = nx;
                normals[i + j*6 + 4] = ny;
                normals[i + j*6 + 5] = nz;
            }
        }
        return normals;
    }

    /**
     * Create a mesh from raw vertex data (position+normal interleaved).
     */
    public static Mesh fromTriangleList(List<Vector3f> positions, List<Vector3f> normals, String name) {
        float[] vertexData = new float[positions.size() * 6];
        for (int i = 0; i < positions.size(); i++) {
            vertexData[i * 6]     = positions.get(i).x;
            vertexData[i * 6 + 1] = positions.get(i).y;
            vertexData[i * 6 + 2] = positions.get(i).z;
            vertexData[i * 6 + 3] = normals.get(i).x;
            vertexData[i * 6 + 4] = normals.get(i).y;
            vertexData[i * 6 + 5] = normals.get(i).z;
        }

        int[] indices = new int[positions.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        return new Mesh(vertexData, indices, name);
    }
}
