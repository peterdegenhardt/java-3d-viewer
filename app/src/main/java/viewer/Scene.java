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

    // Bodengrid: horizontale Linien (auf y=0.05)
    private int floorVao = 0;
    private int floorVbo = 0;
    private int floorVertCount = 0;

    // Vertikales Grid: Pfeiler, die nach oben gehen
    private int vertVao = 0;
    private int vertVbo = 0;
    private int vertVertCount = 0;

    public boolean gridVisible = true;

    public static final float GRID_RADIUS = 10f;
    public static final float GRID_STEP = 1f;
    public static final float GRID_HEIGHT = 10f;

    private float lastGridX = Float.NaN;
    private float lastGridZ = Float.NaN;
    private boolean gridBufferDirty = true;

    public Scene() {
        meshes = new ArrayList<>();
        initShaders();

        // === Boden-VAO ===
        floorVao = glGenVertexArrays();
        floorVbo = glGenBuffers();
        glBindVertexArray(floorVao);
        glBindBuffer(GL_ARRAY_BUFFER, floorVbo);
        initFloorData(0, 0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        // === Vertikal-VAO ===
        vertVao = glGenVertexArrays();
        vertVbo = glGenBuffers();
        glBindVertexArray(vertVao);
        glBindBuffer(GL_ARRAY_BUFFER, vertVbo);
        initVertData(0, 0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        System.out.println("Scene init. Floor: VAO=" + floorVao + " VBO=" + floorVbo + " verts=" + floorVertCount);
        System.out.println("Scene init. Vert:  VAO=" + vertVao + " VBO=" + vertVbo + " verts=" + vertVertCount);
    }

    /** Bodengrid: horizontale Linien auf y=0.05 */
    private void initFloorData(float ox, float oz) {
        int divs = (int)(GRID_RADIUS / GRID_STEP);
        float y = 0.05f;

        int vertCount = (divs * 2 + 1) * 4; // 84 Vertices: 42 Linien × 2
        float[] arr = new float[vertCount * 3];

        int idx = 0;
        // Linien parallel zur Z-Achse (X konstant)
        for (int i = -divs; i <= divs; i++) {
            float x = ox + i * GRID_STEP;
            arr[idx++] = x; arr[idx++] = y; arr[idx++] = oz - GRID_RADIUS;
            arr[idx++] = x; arr[idx++] = y; arr[idx++] = oz + GRID_RADIUS;
        }
        // Linien parallel zur X-Achse (Z konstant)
        for (int i = -divs; i <= divs; i++) {
            float z = oz + i * GRID_STEP;
            arr[idx++] = ox - GRID_RADIUS; arr[idx++] = y; arr[idx++] = z;
            arr[idx++] = ox + GRID_RADIUS; arr[idx++] = y; arr[idx++] = z;
        }

        FloatBuffer fb = ByteBuffer.allocateDirect(arr.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(arr).flip();
        glBindBuffer(GL_ARRAY_BUFFER, floorVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        floorVertCount = vertCount;
    }

    /** Vertikalgrid: Pfeiler von y=0.05 bis y=GRID_HEIGHT an jedem Kreuzungspunkt */
    private void initVertData(float ox, float oz) {
        int divs = (int)(GRID_RADIUS / GRID_STEP);
        float y0 = 0.05f;
        float y1 = GRID_HEIGHT;

        // An jedem Kreuzungspunkt (21×21 Punkte) ein vertikaler Strich
        int vertCount = (divs * 2 + 1) * (divs * 2 + 1) * 2; // 21×21×2 = 882 Vertices
        float[] arr = new float[vertCount * 3];

        int idx = 0;
        for (int ix = -divs; ix <= divs; ix++) {
            float x = ox + ix * GRID_STEP;
            for (int iz = -divs; iz <= divs; iz++) {
                float z = oz + iz * GRID_STEP;
                arr[idx++] = x; arr[idx++] = y0; arr[idx++] = z;
                arr[idx++] = x; arr[idx++] = y1; arr[idx++] = z;
            }
        }

        FloatBuffer fb = ByteBuffer.allocateDirect(arr.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(arr).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vertVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        vertVertCount = vertCount;
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

        // --- Grid: Boden + Vertikalen ---
        if (gridVisible) {
            Vector3f pos = camera.getPosition();
            float gx = (float) Math.floor(pos.x / GRID_STEP) * GRID_STEP;
            float gz = (float) Math.floor(pos.z / GRID_STEP) * GRID_STEP;

            // Neu laden wenn Kamera sich > 1m bewegt hat
            if (gridBufferDirty || gx != lastGridX || gz != lastGridZ) {
                initFloorData(gx, gz);
                initVertData(gx, gz);
                lastGridX = gx;
                lastGridZ = gz;
                gridBufferDirty = false;
            }

            glDisable(GL_DEPTH_TEST);
            glLineWidth(2.0f);

            gridShader.use();
            gridShader.setMat4("uProjection", projection.get(new float[16]));
            gridShader.setMat4("uView", view.get(new float[16]));
            gridShader.setVec3("uObjectColor", 0.3f, 0.3f, 0.42f);

            // Boden zeichnen
            glBindVertexArray(floorVao);
            glDrawArrays(GL_LINES, 0, floorVertCount);
            glBindVertexArray(0);

            // Vertikalen zeichnen (dünner, heller)
            gridShader.setVec3("uObjectColor", 0.25f, 0.25f, 0.35f);
            glLineWidth(1.5f);
            glBindVertexArray(vertVao);
            glDrawArrays(GL_LINES, 0, vertVertCount);
            glBindVertexArray(0);

            glEnable(GL_DEPTH_TEST);
            glLineWidth(1.0f);
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
