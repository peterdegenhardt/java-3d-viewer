package viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Camera {

    private Vector3f position;
    // Z-up System:
    // yaw = Rotation um Z-Achse. 0° = Blick in +Y, 90° = Blick in +X
    // pitch = Kippen aus XY. 0° = horizontal, +90° = senkrecht hoch (+Z), -90° = runter (-Z)
    private float yaw = 0.0f;
    private float pitch = -30.0f; // leicht nach unten schauen
    private float lastX = 640.0f;
    private float lastY = 360.0f;
    private boolean firstMouse = true;

    private float fov = 60.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 1000.0f;

    public Camera() {
        this.position = new Vector3f(0, -5, 5); // über der Mitte, in -Y Richtung schauend (also zur Mitte)
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

        // Maus rechts = yaw +
        yaw += dx;
        // Maus runter = pitch - (nach unten gucken)
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
        // Horizontale Bewegung in der XY-Ebene
        // dy>0 = vorwärts (in Blickrichtung, aber ohne Z-Komponente)
        // dx>0 = rechts

        Vector3f forward = getForward();
        // Flach machen (Z=0) für horizontale Bewegung
        Vector3f flatForward = new Vector3f(forward.x, forward.y, 0);
        if (flatForward.length() > 0.001f) {
            flatForward.normalize();
        } else {
            flatForward.set(0, 1, 0);
        }

        // Right = flatForward × Z_up
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
        // yaw=0 → Blick in +Y:  (0, 1, 0)
        // yaw=90 → Blick in +X: (1, 0, 0)
        // pitch kippt aus XY in Z
        float cosPitch = (float) Math.cos(Math.toRadians(pitch));
        Vector3f forward = new Vector3f();
        forward.x = (float) (Math.sin(Math.toRadians(yaw)) * cosPitch);
        forward.y = (float) (Math.cos(Math.toRadians(yaw)) * cosPitch);
        forward.z = (float) Math.sin(Math.toRadians(pitch));
        forward.normalize();
        return forward;
    }

    public Matrix4f getViewMatrix() {
        Vector3f forward = getForward();
        Vector3f center = new Vector3f(position).add(forward);

        // Dynamischen Up-Vektor berechnen für stabile Rotation
        // Bei pitch=0 ist up = (0,0,1) — Z ist oben
        // Bei pitch=90° (senkrecht nach oben) rotieren wir sauber
        Vector3f worldUp = new Vector3f(0, 0, 1);
        Vector3f right = new Vector3f();
        forward.cross(worldUp, right);
        if (right.length() < 0.001f) {
            // Blickrichtung parallel zu Z → Right über Y-Up
            worldUp.set(0, 1, 0); // temporär Y als up für die Kreuzprodukt-Berechnung
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
