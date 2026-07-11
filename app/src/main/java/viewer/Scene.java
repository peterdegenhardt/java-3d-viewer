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
    public int selectedMesh = -1;

    // 3D Grid (statisch, zentriert)
    private int gridVao, gridVbo, gridCount;
    public boolean gridVisible = true;

    public static final float GRID_SIZE = 10f;       // ±10m = 20×20m Gesamtfläche
    public static final float GRID_STEP = 1f;         // 1m Raster
    public static final float GRID_HEIGHT = 20f;      // Vertikale Linien bis 20m

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
        List<Float> vertices = new ArrayList<>();
        int divs = (int)(GRID_SIZE / GRID_STEP);  // 10

        // Boden: X-Linien (alle 1m in Z)
        for (int i = -divs; i <= divs; i++) {
            float z = i * GRID_STEP;
            vertices.add(-GRID_SIZE); vertices.add(0f); vertices.add(z);
            vertices.add( GRID_SIZE); vertices.add(0f); vertices.add(z);
        }

        // Boden: Z-Linien (alle 1m in X)
        for (int i = -divs; i <= divs; i++) {
            float x = i * GRID_STEP;
            vertices.add(x); vertices.add(0f); vertices.add(-GRID_SIZE);
            vertices.add(x); vertices.add(0f); vertices.add( GRID_SIZE);
        }

        // Vertikale Linien (alle 1m)
        for (int ix = -divs; ix <= divs; ix++) {
            float x = ix * GRID_STEP;
            for (int iz = -divs; iz <= divs; iz++) {
                float z = iz * GRID_STEP;
                vertices.add(x); vertices.add(0f);            vertices.add(z);
                vertices.add(x); vertices.add(GRID_HEIGHT);  vertices.add(z);
            }
        }

        gridCount = vertices.size() / 3;
        float[] verts = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) verts[i] = vertices.get(i);

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

        System.out.println("Grid: " + gridCount + " Linien, " 
            + (int)(GRID_SIZE*2) + "x" + (int)(GRID_SIZE*2) + "m, " + (int)GRID_STEP + "m Raster");
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

        // --- 3D Grid (nur wenn sichtbar) ---
        if (gridVisible) {
            gridShader.use();
            gridShader.setMat4("uProjection", projection.get(new float[16]));
            gridShader.setMat4("uView", view.get(new float[16]));
            glBindVertexArray(gridVao);
            glDrawArrays(GL_LINES, 0, gridCount);
            glBindVertexArray(0);
        }

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

            if (selectedMesh == i) {
                meshShader.setVec3("uObjectColor", 1.0f, 0.9f, 0.4f);
            } else {
                meshShader.setVec3("uObjectColor", 0.7f, 0.7f, 0.9f);
            }

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
