package viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Camera {

    private Vector3f position;
    private float yaw = -90.0f;
    private float pitch = 0.0f;
    private float lastX = 640.0f;
    private float lastY = 360.0f;
    private boolean firstMouse = true;

    private float fov = 60.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 1000.0f;

    public Camera() {
        this.position = new Vector3f(0, 3, 8);
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

    public void move(float dx, float dz) {
        // Move relative to camera facing direction
        Vector3f forward = getForward();
        Vector3f right = new Vector3f();
        Vector3f up = new Vector3f(0, 1, 0);
        forward.cross(up, right).normalize();

        position.add(right.mul(dx));
        position.add(forward.mul(dz));
    }

    public void moveUp(float speed) {
        position.y += speed;
    }

    public void moveDown(float speed) {
        position.y -= speed;
    }

    public Vector3f getForward() {
        Vector3f forward = new Vector3f();
        forward.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        forward.y = (float) Math.sin(Math.toRadians(pitch));
        forward.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        forward.normalize();
        return forward;
    }

    public Matrix4f getViewMatrix() {
        Vector3f forward = getForward();
        Vector3f center = new Vector3f(position).add(forward);
        return new Matrix4f().lookAt(position, center, new Vector3f(0, 1, 0));
    }

    public Matrix4f getProjectionMatrix(int width, int height) {
        float aspect = (float) width / (float) height;
        return new Matrix4f().perspective((float) Math.toRadians(fov), aspect, nearPlane, farPlane);
    }

    public Vector3f getPosition() {
        return position;
    }
}
