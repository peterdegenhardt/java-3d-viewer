package viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Scene {

    private ShaderProgram meshShader;
    private ShaderProgram gridShader;
    private List<Mesh> meshes;

    // Grid
    private int gridVao, gridVbo, gridCount;

    public Scene() {
        meshes = new ArrayList<>();
        initShaders();
        initGrid();
    }

    private void initShaders() {
        meshShader = new ShaderProgram(
            ShaderProgram.defaultVertexSource(),
            ShaderProgram.defaultFragmentSource()
        );
        gridShader = new ShaderProgram(
            ShaderProgram.gridVertexSource(),
            ShaderProgram.gridFragmentSource()
        );
    }

    private void initGrid() {
        int size = 20; // half-size in each direction
        int divisions = 20;
        float step = (float) size / divisions;

        List<Float> vertices = new ArrayList<>();

        // X lines
        for (int i = -divisions; i <= divisions; i++) {
            float z = i * step;
            vertices.add((float) -size); vertices.add(0.0f); vertices.add(z);
            vertices.add((float) size);  vertices.add(0.0f); vertices.add(z);
        }

        // Z lines
        for (int i = -divisions; i <= divisions; i++) {
            float x = i * step;
            vertices.add(x); vertices.add(0.0f); vertices.add((float) -size);
            vertices.add(x); vertices.add(0.0f); vertices.add((float) size);
        }

        gridCount = vertices.size() / 3;
        float[] verts = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            verts[i] = vertices.get(i);
        }

        gridVao = glGenVertexArrays();
        glBindVertexArray(gridVao);

        gridVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(verts.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(verts)
                .flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    public void addMesh(Mesh mesh) {
        meshes.add(mesh);
    }

    public Mesh getMesh(int index) {
        return meshes.get(index);
    }

    public int getMeshCount() {
        return meshes.size();
    }

    public void render(Camera camera) {
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        int width = viewport[2];
        int height = viewport[3];

        Matrix4f projection = camera.getProjectionMatrix(width, height);
        Matrix4f view = camera.getViewMatrix();

        // --- Render grid ---
        glDisable(GL_DEPTH_TEST);
        gridShader.use();
        gridShader.setMat4("uProjection", projection.get(new float[16]));
        gridShader.setMat4("uView", view.get(new float[16]));
        glBindVertexArray(gridVao);
        glDrawArrays(GL_LINES, 0, gridCount);
        glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);

        // --- Render meshes ---
        meshShader.use();
        meshShader.setMat4("uProjection", projection.get(new float[16]));
        meshShader.setMat4("uView", view.get(new float[16]));

        // Light
        meshShader.setVec3("uLightPos", 10, 20, 10);
        meshShader.setVec3("uLightColor", 1, 1, 1);
        meshShader.setVec3("uViewPos", camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        meshShader.setFloat("uAmbientStrength", 0.3f);

        for (Mesh mesh : meshes) {
            Matrix4f model = new Matrix4f()
                .translate(mesh.getPosition())
                .rotateX((float) Math.toRadians(mesh.getRotation().x))
                .rotateY((float) Math.toRadians(mesh.getRotation().y))
                .rotateZ((float) Math.toRadians(mesh.getRotation().z))
                .scale(mesh.getScale());

            meshShader.setMat4("uModel", model.get(new float[16]));
            meshShader.setVec3("uObjectColor", 0.7f, 0.7f, 0.9f);

            mesh.render();
        }
    }

    public void cleanup() {
        meshShader.cleanup();
        gridShader.cleanup();
        for (Mesh mesh : meshes) {
            mesh.cleanup();
        }
        glDeleteVertexArrays(gridVao);
        glDeleteBuffers(gridVbo);
    }
}
