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

    // Bodengrid: horizontale Linien auf z=0.05 (Grundebene = X/Y)
    private int floorVao = 0;
    private int floorVbo = 0;
    private int floorVertCount = 0;

    // Etagen: horizontale Raster auf z=1,2,3
    private int levelsVao = 0;
    private int levelsVbo = 0;
    private int levelsVertCount = 0;

    // Senkrechte: Pfeiler von z=0.05 bis z=3 an jedem Gitterpunkt
    private int uprightsVao = 0;
    private int uprightsVbo = 0;
    private int uprightsVertCount = 0;

    public boolean gridVisible = true;

    public static final float GRID_RADIUS = 5f;
    public static final float GRID_STEP = 1f;
    public static final float GRID_HEIGHT = 3f;

    private float lastGridX = Float.NaN;
    private float lastGridY = Float.NaN;
    private boolean gridBufferDirty = true;

    public Scene() {
        meshes = new ArrayList<>();
        initShaders();

        // === Boden-VAO (X/Y-Ebene bei z=0.05) ===
        floorVao = glGenVertexArrays();
        floorVbo = glGenBuffers();
        glBindVertexArray(floorVao);
        glBindBuffer(GL_ARRAY_BUFFER, floorVbo);
        initFloorData(0, 0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        // === Etagen-VAO (X/Y-Ebenen bei z=1,2,3) ===
        levelsVao = glGenVertexArrays();
        levelsVbo = glGenBuffers();
        glBindVertexArray(levelsVao);
        glBindBuffer(GL_ARRAY_BUFFER, levelsVbo);
        initLevelsData(0, 0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        // === Senkrechte-VAO (Pfeiler in Z-Richtung) ===
        uprightsVao = glGenVertexArrays();
        uprightsVbo = glGenBuffers();
        glBindVertexArray(uprightsVao);
        glBindBuffer(GL_ARRAY_BUFFER, uprightsVbo);
        initUprightsData(0, 0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        System.out.println("Scene init. Floor: " + floorVertCount + " verts, Levels: " + levelsVertCount + " verts, Uprights: " + uprightsVertCount + " verts");
    }

    /** Bodengitter: X/Y-Ebene bei z=0.05 */
    private void initFloorData(float ox, float oy) {
        int divs = (int)(GRID_RADIUS / GRID_STEP);
        float z = 0.05f;

        int vertCount = (divs * 2 + 1) * 4;
        float[] arr = new float[vertCount * 3];

        int idx = 0;
        // Linien parallel zur Y-Achse (X konstant)
        for (int i = -divs; i <= divs; i++) {
            float x = ox + i * GRID_STEP;
            arr[idx++] = x; arr[idx++] = oy - GRID_RADIUS; arr[idx++] = z;
            arr[idx++] = x; arr[idx++] = oy + GRID_RADIUS; arr[idx++] = z;
        }
        // Linien parallel zur X-Achse (Y konstant)
        for (int i = -divs; i <= divs; i++) {
            float y = oy + i * GRID_STEP;
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

    /** Etagen: X/Y-Ebenen bei z=1,2,3 */
    private void initLevelsData(float ox, float oy) {
        int divs = (int)(GRID_RADIUS / GRID_STEP);
        int levels = (int)(GRID_HEIGHT / GRID_STEP);

        int vertCount = levels * (divs * 2 + 1) * 4;
        float[] arr = new float[vertCount * 3];

        int idx = 0;
        for (int level = 1; level <= levels; level++) {
            float z = level * GRID_STEP;
            for (int i = -divs; i <= divs; i++) {
                float x = ox + i * GRID_STEP;
                arr[idx++] = x; arr[idx++] = oy - GRID_RADIUS; arr[idx++] = z;
                arr[idx++] = x; arr[idx++] = oy + GRID_RADIUS; arr[idx++] = z;
            }
            for (int i = -divs; i <= divs; i++) {
                float y = oy + i * GRID_STEP;
                arr[idx++] = ox - GRID_RADIUS; arr[idx++] = y; arr[idx++] = z;
                arr[idx++] = ox + GRID_RADIUS; arr[idx++] = y; arr[idx++] = z;
            }
        }

        FloatBuffer fb = ByteBuffer.allocateDirect(arr.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(arr).flip();
        glBindBuffer(GL_ARRAY_BUFFER, levelsVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        levelsVertCount = vertCount;
    }

    /** Senkrechte: Pfeiler von z=0.05 bis z=3 an jedem Gitterpunkt */
    private void initUprightsData(float ox, float oy) {
        int divs = (int)(GRID_RADIUS / GRID_STEP);
        float z0 = 0.05f;
        float z1 = GRID_HEIGHT;

        int vertCount = (divs * 2 + 1) * (divs * 2 + 1) * 2;
        float[] arr = new float[vertCount * 3];

        int idx = 0;
        for (int ix = -divs; ix <= divs; ix++) {
            float x = ox + ix * GRID_STEP;
            for (int iy = -divs; iy <= divs; iy++) {
                float y = oy + iy * GRID_STEP;
                arr[idx++] = x; arr[idx++] = y; arr[idx++] = z0;
                arr[idx++] = x; arr[idx++] = y; arr[idx++] = z1;
            }
        }

        FloatBuffer fb = ByteBuffer.allocateDirect(arr.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(arr).flip();
        glBindBuffer(GL_ARRAY_BUFFER, uprightsVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        uprightsVertCount = vertCount;
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

        // --- Grid: Boden + Etagen + Senkrechte ---
        if (gridVisible) {
            Vector3f pos = camera.getPosition();
            float gx = (float) Math.floor(pos.x / GRID_STEP) * GRID_STEP;
            float gy = (float) Math.floor(pos.y / GRID_STEP) * GRID_STEP;

            if (gridBufferDirty || gx != lastGridX || gy != lastGridY) {
                initFloorData(gx, gy);
                initLevelsData(gx, gy);
                initUprightsData(gx, gy);
                lastGridX = gx;
                lastGridY = gy;
                gridBufferDirty = false;
            }

            glDisable(GL_DEPTH_TEST);
            glLineWidth(2.0f);

            gridShader.use();
            gridShader.setMat4("uProjection", projection.get(new float[16]));
            gridShader.setMat4("uView", view.get(new float[16]));

            // Boden (hellgrau)
            gridShader.setVec3("uObjectColor", 0.35f, 0.35f, 0.5f);
            glBindVertexArray(floorVao);
            glDrawArrays(GL_LINES, 0, floorVertCount);
            glBindVertexArray(0);

            // Etagen (mittelgrau)
            gridShader.setVec3("uObjectColor", 0.25f, 0.25f, 0.4f);
            glBindVertexArray(levelsVao);
            glDrawArrays(GL_LINES, 0, levelsVertCount);
            glBindVertexArray(0);

            // Senkrechte (dunkler, dünner)
            gridShader.setVec3("uObjectColor", 0.2f, 0.2f, 0.35f);
            glLineWidth(1.5f);
            glBindVertexArray(uprightsVao);
            glDrawArrays(GL_LINES, 0, uprightsVertCount);
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
        glDeleteVertexArrays(levelsVao);
        glDeleteBuffers(levelsVbo);
        glDeleteVertexArrays(uprightsVao);
        glDeleteBuffers(uprightsVbo);
    }
}
