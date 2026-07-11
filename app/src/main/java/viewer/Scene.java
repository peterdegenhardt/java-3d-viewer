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

    // 3D Grid
    private int gridVao, gridVbo, gridCount;
    public boolean gridVisible = true;

    // Einmal Buffer-Objekte erzeugen, dann nur noch Daten updaten
    private boolean gridBuffersInitialized = false;

    // Letzte eingerastete Position
    private float gridOriginX = Float.NaN;
    private float gridOriginZ = Float.NaN;

    public static final float GRID_RADIUS = 10f;      // ±10m = 20×20m
    public static final float GRID_STEP = 1f;          // 1m Raster
    public static final float GRID_HEIGHT = 20f;       // 20m hoch

    public Scene() {
        meshes = new ArrayList<>();
        initShaders();
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

    /**
     * Baut das Grid um die Kameraposition herum auf.
     * Das Grid "rastet" auf 1m-Grenzen ein und springt nur,
     * wenn die Kamera mehr als 1m von der aktuellen Mitte entfernt ist.
     */
    private void updateGrid(Vector3f cameraPos) {
        if (cameraPos == null) return;

        // Auf 1m-Raster einrasten
        float ox = (float) Math.floor(cameraPos.x / GRID_STEP) * GRID_STEP;
        float oz = (float) Math.floor(cameraPos.z / GRID_STEP) * GRID_STEP;

        if (ox == gridOriginX && oz == gridOriginZ && gridBuffersInitialized)
            return;

        gridOriginX = ox;
        gridOriginZ = oz;

        int divs = (int)(GRID_RADIUS / GRID_STEP); // 10
        List<Float> vertices = new ArrayList<>();

        // === Boden: X-Linien (in Z-Richtung) ===
        for (int i = -divs; i <= divs; i++) {
            float z = oz + i * GRID_STEP;
            vertices.add(ox - GRID_RADIUS); vertices.add(0.01f); vertices.add(z);
            vertices.add(ox + GRID_RADIUS); vertices.add(0.01f); vertices.add(z);
        }

        // === Boden: Z-Linien (in X-Richtung) ===
        for (int i = -divs; i <= divs; i++) {
            float x = ox + i * GRID_STEP;
            vertices.add(x); vertices.add(0.01f); vertices.add(oz - GRID_RADIUS);
            vertices.add(x); vertices.add(0.01f); vertices.add(oz + GRID_RADIUS);
        }

        // === Vertikale Linien (Boden bis 20m Höhe) ===
        for (int ix = -divs; ix <= divs; ix++) {
            float x = ox + ix * GRID_STEP;
            for (int iz = -divs; iz <= divs; iz++) {
                float z = oz + iz * GRID_STEP;
                vertices.add(x); vertices.add(0.01f);        vertices.add(z);
                vertices.add(x); vertices.add(GRID_HEIGHT); vertices.add(z);
            }
        }

        gridCount = vertices.size() / 3;
        float[] verts = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) verts[i] = vertices.get(i);

        java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(verts.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(verts)
                .flip();

        if (!gridBuffersInitialized) {
            gridVao = glGenVertexArrays();
            gridVbo = glGenBuffers();
            glBindVertexArray(gridVao);
            glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
            glEnableVertexAttribArray(0);
            glBindVertexArray(0);
            gridBuffersInitialized = true;
        } else {
            glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
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

            if (selectedMesh == i) {
                meshShader.setVec3("uObjectColor", 1.0f, 0.9f, 0.4f);
            } else {
                meshShader.setVec3("uObjectColor", 0.7f, 0.7f, 0.9f);
            }

            mesh.render();
        }

        // --- 3D Grid (folgt der Kamera, ohne Depth-Test = immer sichtbar) ---
        if (gridVisible) {
            updateGrid(camera.getPosition());
            glDisable(GL_DEPTH_TEST);
            gridShader.use();
            gridShader.setMat4("uProjection", projection.get(new float[16]));
            gridShader.setMat4("uView", view.get(new float[16]));
            glBindVertexArray(gridVao);
            glDrawArrays(GL_LINES, 0, gridCount);
            glBindVertexArray(0);
            glEnable(GL_DEPTH_TEST);
        }
    }

    public void cleanup() {
        meshShader.cleanup();
        gridShader.cleanup();
        for (Mesh mesh : meshes) {
            mesh.cleanup();
        }
        if (gridBuffersInitialized) {
            glDeleteVertexArrays(gridVao);
            glDeleteBuffers(gridVbo);
        }
    }
}
