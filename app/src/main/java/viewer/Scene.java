package viewer;

import org.joml.Matrix4f;
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

public class Scene {

    private ShaderProgram meshShader;
    private ShaderProgram gridShader;
    private List<Mesh> meshes;
    public int selectedMesh = -1;

    // Einfach: ein einziger VBO+VAO fürs gesamte Grid
    private int gridVao = 0;
    private int gridVbo = 0;
    private int gridCount = 0;

    // Zusätzliches Backup: direkter Draw mit immediatem Modus (Core Profile via glDrawArrays)
    // als harter Test: ein großes rotes X
    private int testVao = 0;
    private int testVbo = 0;
    private static final int TEST_COUNT = 4; // 2 Linien = 4 Vertices

    public boolean gridVisible = true;

    public Scene() {
        meshes = new ArrayList<>();
        initShaders();
        initGridDebug();
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

    /** Einfaches statisches Test-Grid: 2 große sich kreuzende Linien */
    private void initGridDebug() {
        // Grid VAO+VBO
        gridVao = glGenVertexArrays();
        gridVbo = glGenBuffers();
        glBindVertexArray(gridVao);
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo);

        // Grid = 1 horizontal + 1 vertikal: 2 Linien = 4 Vertices
        float[] gridData = {
            -10f, 0.05f, 0f,   10f, 0.05f, 0f,   // horizontale Linie (X-Achse)
             0f,  0.05f,-10f,   0f, 0.05f, 10f    // vertikale Linie (Z-Achse)
        };
        FloatBuffer fb = ByteBuffer.allocateDirect(gridData.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(gridData).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        gridCount = 2; // 2 Linien = 4 Vertices insgesamt, also 2 Segmente

        // Test-Grid: großes rotes X zwischen -8 und +8
        testVao = glGenVertexArrays();
        testVbo = glGenBuffers();
        glBindVertexArray(testVao);
        glBindBuffer(GL_ARRAY_BUFFER, testVbo);

        float[] testData = {
            -8f, 0.1f, -8f,    8f, 0.1f,  8f,   // Diagonale 1
            -8f, 0.1f,  8f,    8f, 0.1f, -8f    // Diagonale 2
        };
        FloatBuffer tb = ByteBuffer.allocateDirect(testData.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(testData).flip();
        glBufferData(GL_ARRAY_BUFFER, tb, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        System.out.println("Grid VAO=" + gridVao + " VBO=" + gridVbo + " count=" + gridCount);
        System.out.println("Test VAO=" + testVao + " VBO=" + testVbo);
    }

    public void toggleGrid() {
        gridVisible = !gridVisible;
        System.out.println("Grid " + (gridVisible ? "EIN" : "AUS"));
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

        // --- Render meshes ---
        meshShader.use();
        meshShader.setMat4("uProjection", projection.get(new float[16]));
        meshShader.setMat4("uView", view.get(new float[16]));

        meshShader.setVec3("uLightPos", 100, 150, 100);
        meshShader.setVec3("uLightColor", 1, 1, 1);
        meshShader.setVec3("uViewPos", camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        meshShader.setFloat("uAmbientStrength", 0.3f);

        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            Matrix4f model = new Matrix4f()
                .translate(mesh.getPosition())
                .rotateX((float) Math.toRadians(mesh.getRotation().x))
                .rotateY((float) Math.toRadians(mesh.getRotation().y))
                .rotateZ((float) Math.toRadians(mesh.getRotation().z))
                .scale(mesh.getScale());

            meshShader.setMat4("uModel", model.get(new float[16]));
            if (selectedMesh == i) meshShader.setVec3("uObjectColor", 1.0f, 0.9f, 0.4f);
            else meshShader.setVec3("uObjectColor", 0.7f, 0.7f, 0.9f);
            mesh.render();
        }

        // --- Grid ---
        if (gridVisible) {
            glDisable(GL_DEPTH_TEST);
            glLineWidth(4.0f); // extra dick

            gridShader.use();
            gridShader.setMat4("uProjection", projection.get(new float[16]));
            gridShader.setMat4("uView", view.get(new float[16]));

            // Normal-Grid (hellgrau): zwei sich kreuzende Linien am Ursprung
            gridShader.setVec3("uObjectColor", 0.25f, 0.25f, 0.35f);
            glBindVertexArray(gridVao);
            glDrawArrays(GL_LINES, 0, gridCount * 2);
            glBindVertexArray(0);

            // Test-Grid (rot): großes X
            gridShader.setVec3("uObjectColor", 1.0f, 0.2f, 0.2f);
            glBindVertexArray(testVao);
            glDrawArrays(GL_LINES, 0, 4);
            glBindVertexArray(0);

            glEnable(GL_DEPTH_TEST);
            glLineWidth(1.0f);
        }
    }

    public void cleanup() {
        meshShader.cleanup();
        gridShader.cleanup();
        for (Mesh mesh : meshes) mesh.cleanup();
        glDeleteVertexArrays(gridVao);
        glDeleteBuffers(gridVbo);
        glDeleteVertexArrays(testVao);
        glDeleteBuffers(testVbo);
    }
}
