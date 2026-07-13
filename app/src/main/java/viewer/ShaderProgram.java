package viewer;

import java.io.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {

    private int programId;

    public ShaderProgram(String vertexCode, String fragmentCode) {
        programId = glCreateProgram();
        int vertexId = compileShader(vertexCode, GL_VERTEX_SHADER);
        int fragmentId = compileShader(fragmentCode, GL_FRAGMENT_SHADER);

        glAttachShader(programId, vertexId);
        glAttachShader(programId, fragmentId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link error: " + glGetProgramInfoLog(programId));
        }

        glDeleteShader(vertexId);
        glDeleteShader(fragmentId);
    }

    private int compileShader(String code, int type) {
        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, code);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String typeName = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new RuntimeException("Shader compile error (" + typeName + "): " + glGetShaderInfoLog(shaderId));
        }
        return shaderId;
    }

    public void use() {
        glUseProgram(programId);
    }

    public int getUniformLocation(String name) {
        return glGetUniformLocation(programId, name);
    }

    public void setMat4(String name, float[] matrix) {
        glUniformMatrix4fv(getUniformLocation(name), false, matrix);
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(getUniformLocation(name), x, y, z);
    }

    public void setFloat(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    public void setInt(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }

    public void cleanup() {
        glDeleteProgram(programId);
    }

    // === Default shaders ===

    public static String defaultVertexSource() {
        return """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec3 aNormal;
            layout (location = 2) in vec2 aUv;

            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;

            out vec3 FragPos;
            out vec3 Normal;
            out vec2 Uv;

            void main() {
                FragPos = vec3(uModel * vec4(aPos, 1.0));
                Normal = mat3(transpose(inverse(uModel))) * aNormal;
                Uv = aUv;
                gl_Position = uProjection * uView * vec4(FragPos, 1.0);
            }
            """;
    }

    public static String defaultFragmentSource() {
        return """
            #version 330 core
            in vec3 FragPos;
            in vec3 Normal;
            in vec2 Uv;

            uniform vec3 uLightPos;
            uniform vec3 uLightColor;
            uniform vec3 uViewPos;
            uniform vec3 uObjectColor;
            uniform float uAmbientStrength;
            uniform int uUseTexture;
            uniform sampler2D uTexture;

            out vec4 FragColor;

            void main() {
                // Ambient
                vec3 ambient = uAmbientStrength * uLightColor;

                // Diffuse
                vec3 norm = normalize(Normal);
                vec3 lightDir = normalize(uLightPos - FragPos);
                float diff = max(dot(norm, lightDir), 0.0);
                vec3 diffuse = diff * uLightColor;

                // Specular
                float specularStrength = 0.5;
                vec3 viewDir = normalize(uViewPos - FragPos);
                vec3 reflectDir = reflect(-lightDir, norm);
                float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32);
                vec3 specular = specularStrength * spec * uLightColor;

                // Farbe: entweder Textur oder uObjectColor
                vec3 baseColor;
                if (uUseTexture > 0) {
                    baseColor = texture(uTexture, Uv).rgb;
                } else {
                    baseColor = uObjectColor;
                }

                vec3 result = (ambient + diffuse + specular) * baseColor;
                FragColor = vec4(result, 1.0);
            }
            """;
    }

    public static String gridVertexSource() {
        return """
            #version 330 core
            layout (location = 0) in vec3 aPos;

            uniform mat4 uView;
            uniform mat4 uProjection;

            void main() {
                gl_Position = uProjection * uView * vec4(aPos, 1.0);
            }
            """;
    }

    public static String gridFragmentSource() {
        return """
            #version 330 core
            out vec4 FragColor;

            uniform vec3 uObjectColor;

            void main() {
                FragColor = vec4(uObjectColor, 1.0);
            }
            """;
    }
}
