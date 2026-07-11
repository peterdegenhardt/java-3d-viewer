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

    private boolean mouseLocked = true;
    private boolean leftMouseDown = false;
    private double mouseX, mouseY;

    // Welche Mesh-Id wird gerade platziert (0 = keine)
    private int placingMesh = -1;

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

        // Setup callbacks
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (mouseLocked) {
                    // Maus frei
                    mouseLocked = false;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    if (scene.getMeshCount() > 0) placingMesh = scene.getMeshCount() - 1;
                } else {
                    // Maus wieder sperren (ohne zu fixieren — Rechtsklick macht das)
                    mouseLocked = true;
                    placingMesh = -1;
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    camera.resetMouse();
                }
            }
            if (key >= 0 && key < keys.length) {
                keys[key] = action != GLFW_RELEASE;
            }
        });

        // Mouse position (for placing)
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
            if (mouseLocked) {
                camera.handleMouse(xpos, ypos);
            } else if (placingMesh >= 0) {
                // Mauszeiger -> Boden-Position berechnen
                Vector3f hit = mouseToGround(xpos, ypos);
                if (hit != null) {
                    scene.getMesh(placingMesh).getPosition().set(hit);
                }
            }
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                leftMouseDown = action == GLFW_PRESS;
                if (!mouseLocked && action == GLFW_PRESS && placingMesh < 0) {
                    // Linksklick: letztes Mesh auswählen zum Platzieren
                    if (scene.getMeshCount() > 0) {
                        placingMesh = scene.getMeshCount() - 1;
                        System.out.println("Mesh ausgewählt — Maus bewegen zum Platzieren, Rechtsklick fixiert");
                    }
                }
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS && placingMesh >= 0) {
                // Rechtsklick: Mesh fixieren + zurück in Flug-Modus
                System.out.println("Mesh platziert an (" +
                    String.format("%.1f", scene.getMesh(placingMesh).getPosition().x) + ", " +
                    String.format("%.1f", scene.getMesh(placingMesh).getPosition().z) + ")");
                placingMesh = -1;
                mouseLocked = true;
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                camera.resetMouse();
            }
        });

        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            if (mouseLocked) {
                camera.handleScroll((float) yoffset);
            } else if (placingMesh >= 0) {
                // Scrollen = Mesh höher/tiefer
                float dy = (float) yoffset * 0.5f;
                Vector3f pos = scene.getMesh(placingMesh).getPosition();
                pos.y = Math.max(0, pos.y + dy);
            }
        });

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Drag & Drop for STL files
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
                            // Neues Mesh direkt in den Platzier-Modus
                            if (!mouseLocked) {
                                placingMesh = scene.getMeshCount() - 1;
                                System.out.println("Mesh ausgewählt — Maus bewegen zum Platzieren, ESC fixiert");
                            }
                        } catch (Exception e) {
                            System.err.println("Error loading STL: " + e.getMessage());
                        }
                    }
                }
            }
        });

        System.out.println("=== 3D STL Viewer ===");
        System.out.println("Pfeiltasten / WASD = bewegen");
        System.out.println("Maus = Kamera drehen");
        System.out.println("ESC = Maus frei / Mesh platzieren");
        System.out.println("Scroll (Maus frei) = Mesh höher/tiefer");
        System.out.println("Linksklick (Maus frei) = Mesh auswählen");
        System.out.println("STL-Dateien reinziehen = laden");
    }

    /** Berechnet den Schnittpunkt Mausstrahl -> Boden (y=0) */
    private Vector3f mouseToGround(double mx, double my) {
        int[] vp = new int[4];
        glGetIntegerv(GL_VIEWPORT, vp);
        int w = vp[2], h = vp[3];

        // Normalisierte Gerätekoordinaten
        float ndcX = (float) (2.0 * mx / w - 1.0);
        float ndcY = (float) (1.0 - 2.0 * my / h);

        Matrix4f proj = camera.getProjectionMatrix(w, h);
        Matrix4f view = camera.getViewMatrix();
        Matrix4f invProjView = new Matrix4f(proj).mul(view).invert();

        // Near und Far Punkte im World Space
        Vector4f near = new Vector4f(ndcX, ndcY, -1, 1).mul(invProjView);
        Vector4f far  = new Vector4f(ndcX, ndcY,  1, 1).mul(invProjView);
        near.div(near.w);
        far.div(far.w);

        Vector3f origin = new Vector3f(near.x, near.y, near.z);
        Vector3f dir = new Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize();

        // Strahl-Ebene (y=0)
        if (dir.y >= 0) return null; // Strahl zeigt nach oben
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
        if (!mouseLocked) return; // keine Bewegung im Platzier-Modus

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
