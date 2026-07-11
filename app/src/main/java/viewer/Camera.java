package viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Camera {

    private Vector3f position;
    // Z-up System:
    // yaw: Rotation um Z-Achse (links/rechts)
    // pitch: Kippen aus der XY-Ebene (oben/unten)
    private float yaw = -90.0f;
    private float pitch = 0.0f;
    private float lastX = 640.0f;
    private float lastY = 360.0f;
    private boolean firstMouse = true;

    private float fov = 60.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 1000.0f;

    public Camera() {
        this.position = new Vector3f(0, 5, 3);
    }

    public void resetMouse() {
        firstMouse = true;
    }

    public void handleMouse(double xpos, double ypos) {
        if (firstMouse) {
            lastX = (float) xpos;
            lastY = (float) ypos;
            firstMouse = false;
            return;
        }

        float dx = (float) (xpos - lastX);
        float dy = (float) (ypos - lastY);
        lastX = (float) xpos;
        lastY = (float) ypos;

        float sensitivity = 0.15f;
        dx *= sensitivity;
        dy *= sensitivity;

        yaw += dx;
        pitch -= dy;
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
    }

    public void handleScroll(float yoffset) {
        fov -= yoffset;
        if (fov < 1.0f) fov = 1.0f;
        if (fov > 120.0f) fov = 120.0f;
    }

    public void move(float dx, float dy) {
        // Horizontale Bewegung in der XY-Ebene (Z-up)
        Vector3f forward = getForward();
        // Nur XY-Komponente für horizontale Bewegung
        Vector3f flatForward = new Vector3f(forward.x, forward.y, 0);
        if (flatForward.length() > 0.001f) {
            flatForward.normalize();
        } else {
            flatForward.set(0, 1, 0); // Fallback wenn genau nach oben/unten
        }

        // Right = flatForward × (0,0,1)
        Vector3f right = new Vector3f();
        flatForward.cross(new Vector3f(0, 0, 1), right).normalize();

        position.add(right.mul(dx));
        position.add(flatForward.mul(dy));
    }

    public void moveUp(float speed) {
        position.z += speed;
    }

    public void moveDown(float speed) {
        position.z -= speed;
    }

    public Vector3f getForward() {
        // Im Z-up: yaw rotiert um Z, pitch kippt aus XY
        Vector3f forward = new Vector3f();
        float cosPitch = (float) Math.cos(Math.toRadians(pitch));
        forward.x = (float) (Math.cos(Math.toRadians(yaw)) * cosPitch);
        forward.y = (float) (Math.sin(Math.toRadians(yaw)) * cosPitch);
        forward.z = (float) Math.sin(Math.toRadians(pitch));
        forward.normalize();
        return forward;
    }

    public Matrix4f getViewMatrix() {
        Vector3f forward = getForward();
        Vector3f center = new Vector3f(position).add(forward);

        // Up-Vektor dynamisch berechnen: wenn pitch extrem ist, nicht kippen
        Vector3f worldUp = new Vector3f(0, 0, 1);
        // Wenn die Kamera fast senkrecht nach oben/unten schaut, right als Referenz
        Vector3f right = new Vector3f();
        forward.cross(worldUp, right);
        if (right.length() < 0.001f) {
            // Blickrichtung fast parallel zu Z → fallback
            worldUp.set(0, 1, 0);
            forward.cross(worldUp, right);
        }
        right.normalize();
        Vector3f up = new Vector3f();
        right.cross(forward, up).normalize();

        return new Matrix4f().lookAt(position, center, up);
    }

    public Matrix4f getProjectionMatrix(int width, int height) {
        float aspect = (float) width / (float) height;
        return new Matrix4f().perspective((float) Math.toRadians(fov), aspect, nearPlane, farPlane);
    }

    public Vector3f getPosition() {
        return position;
    }
}
