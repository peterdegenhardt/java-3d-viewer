package viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class App {

    private long window;
    private Camera camera;
    private Scene scene;
    private STLLoader stlLoader;
    private boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private int windowWidth = 1280, windowHeight = 720;

    private enum Mode { FLIEGEN, EDIT }
    private Mode mode = Mode.FLIEGEN;

    // Welche Mesh-Id wird gerade editiert
    private int editMesh = -1;
    private double mouseX, mouseY;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(windowWidth, windowHeight, "3D STL Viewer", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.15f, 0.15f, 0.2f, 1.0f);

        camera = new Camera();
        scene = new Scene();
        stlLoader = new STLLoader();

        // === Key callback ===
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_E && action == GLFW_RELEASE) {
                // E = Edit-Modus umschalten
                if (mode == Mode.FLIEGEN) {
                    mode = Mode.EDIT;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    camera.resetMouse();
                    if (scene.getMeshCount() > 0)
                        editMesh = scene.getMeshCount() - 1;
                    System.out.println(">>> EDIT-Modus (Maus = Mesh verschieben, E = zurück)");
                } else {
                    mode = Mode.FLIEGEN;
                    editMesh = -1;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    camera.resetMouse();
                    System.out.println(">>> FLIEG-Modus");
                }
            }

            if (key == GLFW_KEY_R && action == GLFW_RELEASE && mode == Mode.EDIT && editMesh >= 0) {
                // R = Rotation um 45° um Y-Achse
                Mesh m = scene.getMesh(editMesh);
                m.getRotation().y = (m.getRotation().y + 45) % 360;
                System.out.println("Rotation: " + m.getRotation().y + "°");
            }

            if (key >= 0 && key < keys.length) {
                keys[key] = action != GLFW_RELEASE;
            }
        });

        // === Mouse position ===
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
            switch (mode) {
                case FLIEGEN:
                    camera.handleMouse(xpos, ypos);
                    break;
                case EDIT:
                    if (editMesh >= 0) {
                        Vector3f hit = mouseToGround(xpos, ypos);
                        if (hit != null) {
                            scene.getMesh(editMesh).getPosition().set(hit);
                        }
                    }
                    break;
            }
        });

        // === Mouse buttons ===
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (mode == Mode.EDIT && button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                // Rechtsklick = Edit-Modus beenden
                mode = Mode.FLIEGEN;
                editMesh = -1;
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                camera.resetMouse();
                System.out.println(">>> FLIEG-Modus");
            }
            if (mode == Mode.EDIT && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                // Linksklick = nächstes Mesh auswählen (oder wieder erstes)
                if (scene.getMeshCount() > 0) {
                    editMesh = (editMesh + 1) % scene.getMeshCount();
                    System.out.println("Mesh #" + (editMesh + 1) + " von " + scene.getMeshCount() + " ausgewählt");
                }
            }
        });

        // === Scroll ===
        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            switch (mode) {
                case FLIEGEN:
                    camera.handleScroll((float) yoffset);
                    break;
                case EDIT:
                    if (editMesh >= 0) {
                        float dy = (float) yoffset * 0.5f;
                        Vector3f pos = scene.getMesh(editMesh).getPosition();
                        pos.y = Math.max(0, pos.y + dy);
                    }
                    break;
            }
        });

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // === Drag & Drop for STL files ===
        glfwSetDropCallback(window, new GLFWDropCallback() {
            @Override
            public void invoke(long window, int count, long names) {
                org.lwjgl.PointerBuffer pb = MemoryUtil.memPointerBuffer(names, count);
                for (int i = 0; i < count; i++) {
                    String path = MemoryUtil.memUTF8(pb.get(i));
                    if (path.toLowerCase().endsWith(".stl")) {
                        System.out.println("Loading STL: " + path);
                        try {
                            scene.addMesh(stlLoader.load(path));
                            if (mode == Mode.EDIT) {
                                editMesh = scene.getMeshCount() - 1;
                            }
                        } catch (Exception e) {
                            System.err.println("Error loading STL: " + e.getMessage());
                        }
                    }
                }
            }
        });

        System.out.println("=== 3D STL Viewer ===");
        System.out.println("FLIEG-Modus: Pfeiltasten/WASD = bewegen, Maus = drehen");
        System.out.println("E = Edit-Modus (Mesh verschieben)");
        System.out.println("EDIT: Maus = Mesh platzieren, Scroll = Höhe, R = rotieren");
        System.out.println("EDIT: Linksklick = nächstes Mesh, Rechtsklick = zurück fliegen");
        System.out.println("STL-Dateien reinziehen = laden");
    }

    /** Berechnet den Schnittpunkt Mausstrahl -> Boden (y=0) */
    private Vector3f mouseToGround(double mx, double my) {
        int[] vp = new int[4];
        glGetIntegerv(GL_VIEWPORT, vp);
        int w = vp[2], h = vp[3];

        float ndcX = (float) (2.0 * mx / w - 1.0);
        float ndcY = (float) (1.0 - 2.0 * my / h);

        Matrix4f proj = camera.getProjectionMatrix(w, h);
        Matrix4f view = camera.getViewMatrix();
        Matrix4f invProjView = new Matrix4f(proj).mul(view).invert();

        Vector4f near = new Vector4f(ndcX, ndcY, -1, 1).mul(invProjView);
        Vector4f far  = new Vector4f(ndcX, ndcY,  1, 1).mul(invProjView);
        near.div(near.w);
        far.div(far.w);

        Vector3f origin = new Vector3f(near.x, near.y, near.z);
        Vector3f dir = new Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize();

        if (dir.y >= 0) return null;
        float t = -origin.y / dir.y;
        if (t < 0) return null;
        return new Vector3f(origin.x + dir.x * t, 0, origin.z + dir.z * t);
    }

    private void loop() {
        double lastTime = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float delta = (float) (currentTime - lastTime);
            lastTime = currentTime;

            handleInput(delta);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            scene.render(camera);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void handleInput(float delta) {
        if (mode == Mode.FLIEGEN) {
            float speed = 5.0f * delta;
            if (keys[GLFW_KEY_LEFT_SHIFT] || keys[GLFW_KEY_RIGHT_SHIFT])
                speed *= 3.0f;

            float dx = 0, dz = 0;
            if (keys[GLFW_KEY_W] || keys[GLFW_KEY_UP]) dz += speed;
            if (keys[GLFW_KEY_S] || keys[GLFW_KEY_DOWN]) dz -= speed;
            if (keys[GLFW_KEY_A] || keys[GLFW_KEY_LEFT]) dx -= speed;
            if (keys[GLFW_KEY_D] || keys[GLFW_KEY_RIGHT]) dx += speed;

            camera.move(dx, dz);
            if (keys[GLFW_KEY_SPACE]) camera.moveUp(speed);
            if (keys[GLFW_KEY_LEFT_CONTROL]) camera.moveDown(speed);
        }
    }

    private void cleanup() {
        scene.cleanup();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public static void main(String[] args) {
        new App().run();
    }
}
