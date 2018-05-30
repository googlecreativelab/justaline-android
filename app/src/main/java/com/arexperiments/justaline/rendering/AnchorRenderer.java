// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arexperiments.justaline.rendering;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class AnchorRenderer {
    private static final String TAG = "Cube";

    private static final String VERTEX_SHADER_CODE =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vColor;" +
                    "varying vec4 aColor;" +
                    "void main() {" +
                    "aColor = vColor;" +
                    // The matrix must be included as a modifier of gl_Position.
                    // Note that the uMVPMatrix factor *must be first* in order
                    // for the matrix multiplication product to be correct.
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
                    "varying vec4 aColor;" +
                    "void main() {" +
                    "  gl_FragColor = aColor;" +
                    "}";

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;

    // Number of coordinates per vertex in this array
    private static final int COORDS_PER_VERTEX = 3;
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private static final int COORDS_PER_COLORS = 4;
    private static final int COLORS_STRIDE = COORDS_PER_COLORS * 4; // 4 bytes per vertex

    private static final float VERTICES[] = {
            -1.0f, -1.0f, -1.0f,
            1.0f, -1.0f, -1.0f,
            1.0f, 1.0f, -1.0f,
            -1.0f, 1.0f, -1.0f,
            -1.0f, -1.0f, 1.0f,
            1.0f, -1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 1.0f,
    };

    private static final float COLORS1[] = {
            1.0f, 0, 0, 1.0f,
            1.0f, 0, 0, 1.0f,
            1.0f, 0, 0, 1.0f,
            1.0f, 0, 0, 1.0f,
            1.0f, 0, 0, 1.0f,
            1.0f, 0, 0, 1.0f,
            1.0f, 0, 0, 1.0f,
            1.0f, 0, 0, 1.0f,
    };

    private static final float COLORS2[] = {
            0, 1.0f, 0.0f, 1.0f,
            0, 1.0f, 0.0f, 1.0f,
            0, 1.0f, 0.0f, 1.0f,
            0, 1.0f, 0.0f, 1.0f,
            0, 1.0f, 0.0f, 1.0f,
            0, 1.0f, 0.0f, 1.0f,
            0, 1.0f, 0.0f, 1.0f,
            0, 1.0f, 0.0f, 1.0f,
    };

    private static final short INDICES[] = {
            0, 4, 5, 0, 5, 1,
            1, 5, 6, 1, 6, 2,
            2, 6, 7, 2, 7, 3,
            3, 7, 4, 3, 4, 0,
            4, 7, 6, 4, 6, 5,
            3, 0, 1, 3, 1, 2
    };

    private int mProgram;

    private FloatBuffer mVertexBuffer;
    private FloatBuffer mColor1Buffer;
    private FloatBuffer mColor2Buffer;
    private ShortBuffer mIndexBuffer;

    public float[] mModelMatrix = new float[16];
    private float[] mModelViewMatrix = new float[16];
    private float[] mModelViewProjectionMatrix = new float[16];

    public AnchorRenderer() {
        // Prepare shaders and OpenGL program
        int vertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER,
                VERTEX_SHADER_CODE);
        if (vertexShader == 0) {
            Log.e(TAG, "Vertex shader failed");
            return;
        }
        int fragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                FRAGMENT_SHADER_CODE);
        if (fragmentShader == 0) {
            Log.e(TAG, "Fragment shader failed");
            return;
        }

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);                  // create OpenGL program executables

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(mProgram));
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
            return;
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        // Initialize vertex byte buffer for shape coordinates
        mVertexBuffer = allocateFloatBuffer(VERTICES);

        // Initialize byte buffer for the colors
        mColor1Buffer = allocateFloatBuffer(COLORS1);
        mColor2Buffer = allocateFloatBuffer(COLORS2);

        // Initialize byte buffer for the draw list
        mIndexBuffer = allocateShortBuffer(INDICES);

        Matrix.setIdentityM(mModelMatrix, 0);
    }

    /**
     * Encapsulates the OpenGL ES instructions for drawing this shape.
     */
    public void draw(float[] cameraView, float[] cameraPerspective, boolean changeColor) {

        Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0);
        Matrix.scaleM(mModelViewProjectionMatrix, 0, 0.005f, 0.005f, 0.005f);

        if (mProgram != 0) {
            // Add program to OpenGL environment
            GLES20.glUseProgram(mProgram);

            // Get handle to vertex shader's vPosition member
            int mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

            // Enable a handle to the triangle vertices
            GLES20.glEnableVertexAttribArray(mPositionHandle);

            // Get handle to fragment shader's vColor member
            int mColor = GLES20.glGetAttribLocation(mProgram, "vColor");

            // Enable a handle to the color vertices
            GLES20.glEnableVertexAttribArray(mColor);

            // Get handle to shape's transformation matrix
            int mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkGlError("glGetUniformLocation");

            // Apply the projection and view transformation
            GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mModelViewProjectionMatrix, 0);
            checkGlError("glUniformMatrix4fv");

            // Prepare the coordinate data
            GLES20.glVertexAttribPointer(
                    mPositionHandle, COORDS_PER_VERTEX,
                    GLES20.GL_FLOAT, false,
                    VERTEX_STRIDE, mVertexBuffer);

            // Prepare the color data
            if (changeColor) {
                GLES20.glVertexAttribPointer(
                        mColor, COORDS_PER_COLORS,
                        GLES20.GL_FLOAT, false,
                        COLORS_STRIDE, mColor2Buffer);
            } else {
                GLES20.glVertexAttribPointer(
                        mColor, COORDS_PER_COLORS,
                        GLES20.GL_FLOAT, false,
                        COLORS_STRIDE, mColor1Buffer);
            }

            // Draw the shape
            GLES20.glDrawElements(
                    GLES20.GL_TRIANGLES, INDICES.length,
                    GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);

            // Disable vertex array
            GLES20.glDisableVertexAttribArray(mPositionHandle);
            // Disable color array
            GLES20.glDisableVertexAttribArray(mColor);
        } else {
            Log.i(TAG, "Bummer");
        }
    }

    /**
     * Utility method for compiling a OpenGL shader.
     * <p>
     * <p><strong>Note:</strong> When developing shaders, use the checkGlError()
     * method to debug shader coding errors.</p>
     *
     * @param type       - Vertex or fragment shader type.
     * @param shaderCode - String containing the shader code.
     * @return - Returns an mId for the shader.
     */
    public static int loadShader(int type, String shaderCode) {

        // Create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);
        checkGlError("glCreateShader type=" + type);

        // Add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + type + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        return shader;
    }

    /**
     * Utility method for debugging OpenGL calls. Provide the name of the call
     * just after making it:
     * <p>
     * <pre>
     * mColor = GLES20.glGetUniformLocation(mProgram, "vColor");
     * MyGLRenderer.checkGlError("glGetUniformLocation");</pre>
     * <p>
     * If the operation is not successful, the check throws an error.
     *
     * @param glOperation - Name of the OpenGL call to check.
     */
    public static void checkGlError(String glOperation) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + ": glError " + error);
        }
    }

    /**
     * Creates a direct float buffer, and copy coords into it.
     *
     * @param coords - data to be copied.
     */
    public static FloatBuffer allocateFloatBuffer(float[] coords) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(coords.length * BYTES_PER_FLOAT);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(coords);
        floatBuffer.position(0);
        return floatBuffer;
    }

    /**
     * Creates a direct short buffer, and copy coords into it.
     *
     * @param coords - data to be copied.
     */
    public static ShortBuffer allocateShortBuffer(short[] coords) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(coords.length * BYTES_PER_SHORT);
        byteBuffer.order(ByteOrder.nativeOrder());
        ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
        shortBuffer.put(coords);
        shortBuffer.position(0);
        return shortBuffer;
    }

}