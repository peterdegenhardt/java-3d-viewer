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

    // Boden-Grid (horizontal)
    private int floorVao, floorVbo, floorCount;

    // Vertikal-Grid
    private int vertVao, vertVbo, vertCount;

    public boolean gridVisible = true;

    // Kamera-Raster-Position (damit wir nur bei Bewegung neu berechnen)
    private float gridOx = Float.NaN;
    private float gridOz = Float.NaN;

    public static final float GRID_RADIUS = 10f;
    public static final float GRID_STEP = 1f;
    public static final float GRID_HEIGHT = 20f;

    public Scene() {
        meshes = new ArrayList<>();
        initShaders();
        initGridBuffers();
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

    private void initGridBuffers() {
        floorVao = glGenVertexArrays();
        floorVbo = glGenBuffers();
        glBindVertexArray(floorVao);
        glBindBuffer(GL_ARRAY_BUFFER, floorVbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        vertVao = glGenVertexArrays();
        vertVbo = glGenBuffers();
        glBindVertexArray(vertVao);
        glBindBuffer(GL_ARRAY_BUFFER, vertVbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    private void updateGrid(Vector3f cameraPos) {
        if (cameraPos == null) return;

        float ox = (float) Math.floor(cameraPos.x / GRID_STEP) * GRID_STEP;
        float oz = (float) Math.floor(cameraPos.z / GRID_STEP) * GRID_STEP;

        if (ox == gridOx && oz == gridOz && !Float.isNaN(gridOx))
            return;

        gridOx = ox;
        gridOz = oz;

        int divs = (int)(GRID_RADIUS / GRID_STEP);

        // === BODEN (horizontale Linien) ===
        List<Float> floorVerts = new ArrayList<>();
        for (int i = -divs; i <= divs; i++) {
            float z = oz + i * GRID_STEP;
            floorVerts.add(ox - GRID_RADIUS); floorVerts.add(0.01f); floorVerts.add(z);
            floorVerts.add(ox + GRID_RADIUS); floorVerts.add(0.01f); floorVerts.add(z);
        }
        for (int i = -divs; i <= divs; i++) {
            float x = ox + i * GRID_STEP;
            floorVerts.add(x); floorVerts.add(0.01f); floorVerts.add(oz - GRID_RADIUS);
            floorVerts.add(x); floorVerts.add(0.01f); floorVerts.add(oz + GRID_RADIUS);
        }

        floorCount = floorVerts.size() / 3;
        uploadBuffer(floorVbo, floorVerts);

        // === VERTIKAL ===
        List<Float> vertVerts = new ArrayList<>();
        for (int ix = -divs; ix <= divs; ix++) {
            float x = ox + ix * GRID_STEP;
            for (int iz = -divs; iz <= divs; iz++) {
                float z = oz + iz * GRID_STEP;
                vertVerts.add(x); vertVerts.add(0.01f);       vertVerts.add(z);
                vertVerts.add(x); vertVerts.add(GRID_HEIGHT); vertVerts.add(z);
            }
        }

        vertCount = vertVerts.size() / 3;
        uploadBuffer(vertVbo, vertVerts);
    }

    private void uploadBuffer(int vbo, List<Float> verts) {
        float[] arr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) arr[i] = verts.get(i);
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(arr.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(arr).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
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

        // --- Grid (ohne Depth-Test, immer sichtbar) ---
        if (gridVisible) {
            updateGrid(camera.getPosition());
            glDisable(GL_DEPTH_TEST);

            gridShader.use();
            gridShader.setMat4("uProjection", projection.get(new float[16]));
            gridShader.setMat4("uView", view.get(new float[16]));

            // Boden
            glBindVertexArray(floorVao);
            glDrawArrays(GL_LINES, 0, floorCount);
            glBindVertexArray(0);

            // Vertikalen
            glBindVertexArray(vertVao);
            glDrawArrays(GL_LINES, 0, vertCount);
            glBindVertexArray(0);

            glEnable(GL_DEPTH_TEST);
        }
    }

    public void cleanup() {
        meshShader.cleanup();
        gridShader.cleanup();
        for (Mesh mesh : meshes) mesh.cleanup();
        glDeleteVertexArrays(floorVao);
        glDeleteBuffers(floorVbo);
        glDeleteVertexArrays(vertVao);
        glDeleteBuffers(vertVbo);
    }
}
