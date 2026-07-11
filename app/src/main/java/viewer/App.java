package viewer;

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

    private boolean mouseLocked = true;

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

        window = glfwCreateWindow(1280, 720, "3D STL Viewer", NULL, NULL);
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
                mouseLocked = !mouseLocked;
                if (mouseLocked) {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                } else {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    camera.resetMouse(); // reset mouse smoothing
                }
            }
            if (key >= 0 && key < keys.length) {
                keys[key] = action != GLFW_RELEASE;
            }
        });

        // ... callback setup ...
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (mouseLocked) {
                camera.handleMouse(xpos, ypos);
            }
        });

        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            camera.handleScroll((float) yoffset);
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
        System.out.println("ESC = Maus frei/sperren (für Drag & Drop)");
        System.out.println("Scroll = Zoom");
        System.out.println("STL-Dateien reinziehen = laden");
        System.out.println("ESC im Maus-Frei-Modus = beenden");
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
