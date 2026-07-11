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

    // 3D Grid (dynamisch)
    private int gridVao, gridVbo;
    private int gridCapacity = 0;
    public boolean gridVisible = true;

    // Grid-Einstellungen
    public static final float GRID_RADIUS = 5f;      // 5m um die Kamera
    public static final float GRID_STEP = 1f;         // 1m Raster
    public static final float WORLD_HEIGHT = 20f;     // Vertikale Linien bis 20m

    // Letzte Kameraposition für Grid-Update
    private float lastGridX = Float.MAX_VALUE;
    private float lastGridZ = Float.MAX_VALUE;

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
        gridVao = glGenVertexArrays();
        gridVbo = glGenBuffers();
        glBindVertexArray(gridVao);
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /**
     * Erzeugt ein lokales 3D-Raster um einen Mittelpunkt herum.
     * Nur neu berechnen wenn die Kamera sich um mehr als 1m bewegt hat.
     */
    private void updateGrid(Vector3f cameraPos) {
        if (cameraPos == null) return;

        // Nur updaten wenn Kamera sich weit genug bewegt hat
        float cx = cameraPos.x;
        float cz = cameraPos.z;
        if (Math.abs(cx - lastGridX) < GRID_STEP && Math.abs(cz - lastGridZ) < GRID_STEP)
            return;

        lastGridX = cx;
        lastGridZ = cz;

        // Raster zum nächsten 1m-Punkt eingerastet
        float originX = (float) Math.floor(cx / GRID_STEP) * GRID_STEP;
        float originZ = (float) Math.floor(cz / GRID_STEP) * GRID_STEP;
        int divs = (int)(GRID_RADIUS / GRID_STEP);

        List<Float> vertices = new ArrayList<>();

        // === Boden: X-Linien (in Z-Richtung) ===
        for (int i = -divs; i <= divs; i++) {
            float z = originZ + i * GRID_STEP;
            vertices.add(originX - GRID_RADIUS); vertices.add(0f); vertices.add(z);
            vertices.add(originX + GRID_RADIUS); vertices.add(0f); vertices.add(z);
        }

        // === Boden: Z-Linien (in X-Richtung) ===
        for (int i = -divs; i <= divs; i++) {
            float x = originX + i * GRID_STEP;
            vertices.add(x); vertices.add(0f); vertices.add(originZ - GRID_RADIUS);
            vertices.add(x); vertices.add(0f); vertices.add(originZ + GRID_RADIUS);
        }

        // === Vertikale Linien (alle 1m, für bessere räumliche Orientierung) ===
        for (int ix = -divs; ix <= divs; ix++) {
            float x = originX + ix * GRID_STEP;
            for (int iz = -divs; iz <= divs; iz++) {
                float z = originZ + iz * GRID_STEP;
                vertices.add(x); vertices.add(0f);              vertices.add(z);
                vertices.add(x); vertices.add(WORLD_HEIGHT);   vertices.add(z);
            }
        }

        int count = vertices.size() / 3;
        float[] verts = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            verts[i] = vertices.get(i);
        }

        glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) verts.length * 4, GL_STREAM_DRAW);
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(verts.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(verts)
                .flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, fb);

        gridCapacity = count;
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

        // --- Dynamisches 3D Grid (nur wenn sichtbar) ---
        if (gridVisible) {
            updateGrid(camera.getPosition());
            if (gridCapacity > 0) {
                gridShader.use();
                gridShader.setMat4("uProjection", projection.get(new float[16]));
                gridShader.setMat4("uView", view.get(new float[16]));
                glBindVertexArray(gridVao);
                glDrawArrays(GL_LINES, 0, gridCapacity);
                glBindVertexArray(0);
            }
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
