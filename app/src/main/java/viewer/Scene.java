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

    // Grid: ein einziger VAO+VBO, dynamisch aktualisiert
    private int gridVao = 0;
    private int gridVbo = 0;
    private int gridVertCount = 0;

    public boolean gridVisible = true;

    public static final float GRID_RADIUS = 10f;
    public static final float GRID_STEP = 1f;

    public Scene() {
        meshes = new ArrayList<>();
        initShaders();
        gridVao = glGenVertexArrays();
        gridVbo = glGenBuffers();
        glBindVertexArray(gridVao);
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glEnableVertexAttribArray(0);
        initStaticGrid();
        glBindVertexArray(0);
        System.out.println("Scene init complete. Grid VAO=" + gridVao + " VBO=" + gridVbo + " count=" + gridVertCount);
    }

    /** Einmaliges, statisches Grid um den Ursprung */
    private void initStaticGrid() {
        int divs = (int)(GRID_RADIUS / GRID_STEP); // 10
        float y = 0.05f;

        // Gesamtanzahl Vertices vorher berechnen: (21 Linien X + 21 Linien Z) * 2 Vertices/Linie
        float[] arr = new float[(divs * 2 + 1) * 2 * 3 * 2]; // 21*2*3*2 = 252 floats

        int idx = 0;
        // Linien parallel zur Z-Achse (X-Richtung)
        for (int i = -divs; i <= divs; i++) {
            float x = i * GRID_STEP;
            arr[idx++] = x; arr[idx++] = y; arr[idx++] = -GRID_RADIUS;
            arr[idx++] = x; arr[idx++] = y; arr[idx++] =  GRID_RADIUS;
        }
        // Linien parallel zur X-Achse (Z-Richtung)
        for (int i = -divs; i <= divs; i++) {
            float z = i * GRID_STEP;
            arr[idx++] = -GRID_RADIUS; arr[idx++] = y; arr[idx++] = z;
            arr[idx++] =  GRID_RADIUS; arr[idx++] = y; arr[idx++] = z;
        }

        FloatBuffer fb = ByteBuffer.allocateDirect(arr.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(arr).flip();

        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        gridVertCount = arr.length / 3;
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

        // --- Grid ---
        if (gridVisible) {
            glDisable(GL_DEPTH_TEST);
            glLineWidth(2.0f);

            gridShader.use();
            gridShader.setMat4("uProjection", projection.get(new float[16]));
            gridShader.setMat4("uView", view.get(new float[16]));
            gridShader.setVec3("uObjectColor", 0.3f, 0.3f, 0.42f);

            glBindVertexArray(gridVao);
            glDrawArrays(GL_LINES, 0, gridVertCount);
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
    }
}
