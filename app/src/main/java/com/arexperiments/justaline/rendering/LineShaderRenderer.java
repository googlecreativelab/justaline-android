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

package com.arexperiments.justaline.rendering;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.arexperiments.justaline.R;
import com.arexperiments.justaline.model.Stroke;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.vecmath.Vector3f;


/**
 * Renders a point cloud.
 */
public class LineShaderRenderer {
    private static final String TAG = LineShaderRenderer.class.getSimpleName();
    private static final int FLOATS_PER_POINT = 3;  // X,Y,Z.
    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;

    public float[] mModelMatrix = new float[16];
    private float[] mModelViewMatrix = new float[16];
    private float[] mModelViewProjectionMatrix = new float[16];

    private int mPositionAttribute = 0;
    private int mPreviousAttribute = 0;
    private int mNextAttribute = 0;
    private int mSideAttribute = 0;
    private int mWidthAttribute = 0;
    private int mLengthsAttribute = 0;
    private int mEndCapsAttribute = 0;

    private final int[] textures = new int[1];

    //private int mTextureUniform = 0;
    private int mEndCapTextureUniform = 0;

    private int mProjectionUniform = 0;
    private int mModelViewUniform = 0;
    private int mResolutionUniform = 0;
    private int mColorUniform = 0;
    private int mNearUniform = 0;
    private int mFarUniform = 0;
    private int mDrawingDistUniform = 0;
    private int mLineDepthScaleUniform = 0;

    private float[] mPositions;
    private float[] mNext;
    private float[] mSide;
    private float[] mWidth;
    private float[] mPrevious;
    private float[] mLengths;
    private float[] mEndCaps;

    private int mPositionAddress;
    private int mPreviousAddress;
    private int mNextAddress;
    private int mSideAddress;
    private int mWidthAddress;
    private int mLengthAddress;
    private int mEndCapsAddress;

    private int mNumBytes = 0;

    private int mVbo = 0;
    private int mVboSize = 0;

    private int mProgramName = 0;
    private float mLineWidth = 0;

    private Vector3f mColor;

    public AtomicBoolean bNeedsUpdate = new AtomicBoolean();

    private float mLineDepthScale = 1.0f;

    public float mDrawDistance;
    public int mNumPoints;


    public LineShaderRenderer() {
    }

    /**
     * Allocates and initializes OpenGL resources needed by the Line renderer.  Must be
     * called on the OpenGL thread, typically in
     * {@link GLSurfaceView.Renderer#onSurfaceCreated(GL10, EGLConfig)}.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context) throws IOException {
        ShaderUtil.checkGLError(TAG, "before create");

        int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mVbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mVboSize = 0;
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);


        ShaderUtil.checkGLError(TAG, "buffer alloc");


        /*
         *
         * The LineShaderRenderer uses an ES2 pipeline.  It uses the line_vert.glsl and
         * line_frag.glsl shader to render a volumetric line.  It uses several techniques detailed in
         * the following resources:
         *
         *      Drawing Lines is Hard by Matt DesLauriers
         *          https://mattdesl.svbtle.com/drawing-lines-is-hard
         *
         *      InkSpace an Android Experiment by Zach Lieberman
         *          https://experiments.withgoogle.com/android/ink-space
         *          https://github.com/ofZach/inkSpace
         *
         *      THREEJS.MeshLine by Jaume Sanchez
         *          https://github.com/spite/THREE.MeshLine/blob/master/src/THREE.MeshLine.js
         *
         *
         * The Renderer batches all of the geometry into a single VBO.  This allows us to have a single
         * draw call to render the geometry.  We also optimize the application to only re-upload the
         * geometry data when a new stroke or new points are added to the drawing. The renderer uses
         * a technique detailed in the following link to create degenerate faces between the strokes
         * to disconnect them from one another.
         *      https://developer.apple.com/library/content/documentation/3DDrawing/Conceptual/OpenGLES_ProgrammingGuide/TechniquesforWorkingwithVertexData/TechniquesforWorkingwithVertexData.html
         *
         */

        int vertexShader = ShaderUtil.loadGLShader(TAG, context,
                GLES20.GL_VERTEX_SHADER, R.raw.line_vert);
        int fragmentShader = ShaderUtil.loadGLShader(TAG, context,
                GLES20.GL_FRAGMENT_SHADER, R.raw.line_frag);


        mProgramName = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgramName, vertexShader);
        GLES20.glAttachShader(mProgramName, fragmentShader);
        GLES20.glLinkProgram(mProgramName);
        GLES20.glUseProgram(mProgramName);

        ShaderUtil.checkGLError(TAG, "program");

        // mTextureUniform = GLES20.glGetUniformLocation(mProgramName, "u_Texture");
        mEndCapTextureUniform = GLES20.glGetUniformLocation(mProgramName, "u_EndCapTexture");

        mPositionAttribute = GLES20.glGetAttribLocation(mProgramName, "position");
        mPreviousAttribute = GLES20.glGetAttribLocation(mProgramName, "previous");
        mNextAttribute = GLES20.glGetAttribLocation(mProgramName, "next");
        mSideAttribute = GLES20.glGetAttribLocation(mProgramName, "side");
        mWidthAttribute = GLES20.glGetAttribLocation(mProgramName, "width");
        mLengthsAttribute = GLES20.glGetAttribLocation(mProgramName, "length");
        mEndCapsAttribute = GLES20.glGetAttribLocation(mProgramName, "endCaps");


        mProjectionUniform = GLES20.glGetUniformLocation(mProgramName, "projectionMatrix");
        mModelViewUniform = GLES20.glGetUniformLocation(mProgramName, "modelViewMatrix");
        mResolutionUniform = GLES20.glGetUniformLocation(mProgramName, "resolution");
        mColorUniform = GLES20.glGetUniformLocation(mProgramName, "color");
        mNearUniform = GLES20.glGetUniformLocation(mProgramName, "near");
        mFarUniform = GLES20.glGetUniformLocation(mProgramName, "far");
        mLineDepthScaleUniform = GLES20.glGetUniformLocation(mProgramName, "lineDepthScale");
        mDrawingDistUniform = GLES20.glGetUniformLocation(mProgramName, "drawingDist");

        ShaderUtil.checkGLError(TAG, "program  params");


        // Read the texture.
//        Bitmap textureBitmap =
//                BitmapFactory.decodeStream(context.getAssets().open("texture.png"));
//
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//        GLES20.glGenTextures(textures.length, textures, 0);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
//
//        GLES20.glTexParameteri(
//                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
//        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
////        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//
//        ShaderUtil.checkGLError(TAG, "Texture loading");

        // Read the line texture.
        Bitmap endCapTextureBitmap =
                BitmapFactory.decodeStream(context.getAssets().open("linecap.png"));

//        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
//        GLES20.glGenTextures(textures.length, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, endCapTextureBitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        ShaderUtil.checkGLError(TAG, "Line texture loading");


        Matrix.setIdentityM(mModelMatrix, 0);

        mColor = new Vector3f(1f, 1f, 1f);


    }

    public void clearGL() {
        GLES20.glDeleteShader(mProgramName);
        GLES20.glDeleteBuffers(1, new int[]{mVbo}, 0);
    }


    /**
     * Sets the LineWidth of the Line.
     * Requires bNeedsUpdate.set(true) to take effect
     *
     * @param width of line in decimal format
     */
    public void setLineWidth(float width) {
        mLineWidth = width;
    }

    /**
     * This sets the color of the line by setting the color uniform in the shader.
     *
     * @param color a Vector3f representing R, G, B for the X, Y, Z values
     */
    public void setColor(Vector3f color) {
        mColor = new Vector3f(color);
    }


    /**
     * This sets a feature in the vertex shader to scale the line width based on the distance away
     * from the current view.
     *
     * @param distanceScale a float for scale of line width
     */
    public void setDistanceScale(float distanceScale) {
        this.mLineDepthScale = distanceScale;
    }

    /**
     * This updates the geometry data to be rendered. It ensures the capacity of the float arrays
     * and then calls updateLine to generate the geometry.
     *
     * @param strokes a ArrayList of ArrayLists of Vector3fs in world space.  The outer ArrayList
     *                contains the strokes, while the inner ArrayList contains the Vertex of each Line
     */
    public void updateStrokes(List<Stroke> strokes, Map<String, Stroke> sharedStrokes) {
        mNumPoints = 0;

        for (Stroke l : strokes) {
            mNumPoints += l.size() * 2 + 2;
        }

        for (Stroke l : sharedStrokes.values()) {
            mNumPoints += l.size() * 2 + 2;
        }

        ensureCapacity(mNumPoints);

        int offset = 0;

        for (Stroke l : strokes) {
            offset = addLine(l, offset);
        }

        for (Stroke l : sharedStrokes.values()) {
            offset = addLine(l, offset);
        }
        mNumBytes = offset;
    }

    /**
     * This ensures the capacity of the float arrays that hold the information bound to the Vertex
     * Attributes needed to render the line with the Vertex and Fragment shader.
     *
     * @param numPoints int denoting number of points
     */
    private void ensureCapacity(int numPoints) {
        int count = 1024;
        if (mSide != null) {
            count = mSide.length;
        }

        while (count < numPoints) {
            count += 1024;
        }

        if (mSide == null || mSide.length < count) {
            Log.i(TAG, "alloc " + count);
            mPositions = new float[count * 3];
            mNext = new float[count * 3];
            mPrevious = new float[count * 3];

            mSide = new float[count];
            mWidth = new float[count];
            mLengths = new float[count];
            mEndCaps = new float[count];
        }
    }


    /**
     * AddLine takes in the 3D positions adds to the buffers to create the stroke and the degenerate
     * faces needed so the lines render properly.
     */
    private int addLine(Stroke line, int offset) {
        if (line == null || line.size() < 2)
            return offset;


        int lineSize = line.size();

        float mLineWidthMax = mLineWidth = line.getLineWidth();

        float length = 0;
        float totalLength;
        int ii = offset;

        if (line.localLine) {
            totalLength = line.totalLength;
        } else {
            totalLength = line.animatedLength;
        }

        for (int i = 0; i < lineSize; i++) {

            int iGood = i;
            if (iGood >= lineSize) iGood = lineSize - 1;

            int i_m_1 = (iGood - 1) < 0 ? iGood : iGood - 1;
            int i_p_1 = (iGood + 1) > (lineSize - 1) ? iGood : iGood + 1;

            Vector3f current = line.get(iGood);
            Vector3f previous = line.get(i_m_1);
            Vector3f next = line.get(i_p_1);

            Vector3f dist = new Vector3f(current);
            dist.sub(previous);
            length += dist.length();


//            if (i < line.mTapperPoints) {
//                mLineWidth = mLineWidthMax * line.mTaperLookup[i];
//            } else if (i > lineSize - line.mTapperPoints) {
//                mLineWidth = mLineWidthMax * line.mTaperLookup[lineSize - i];
//            } else {
            mLineWidth = line.getLineWidth();
//            }


            mLineWidth = Math.max(0, Math.min(mLineWidthMax, mLineWidth));


            if (i == 0) {
                setMemory(ii++, current, previous, next, mLineWidth, 1f, length, totalLength);
            }

            setMemory(ii++, current, previous, next, mLineWidth, 1f, length, totalLength);
            setMemory(ii++, current, previous, next, mLineWidth, -1f, length, totalLength);

            if (i == lineSize - 1) {
                setMemory(ii++, current, previous, next, mLineWidth, -1f, length, totalLength);
            }


        }
        return ii;
    }

    /**
     * setMemory is a helper method used to add the stroke data to the float[] buffers
     */
    private void setMemory(int index, Vector3f pos, Vector3f prev, Vector3f next, float width, float side, float length, float endCapPosition) {
        mPositions[index * 3] = pos.x;
        mPositions[index * 3 + 1] = pos.y;
        mPositions[index * 3 + 2] = pos.z;

        mNext[index * 3] = next.x;
        mNext[index * 3 + 1] = next.y;
        mNext[index * 3 + 2] = next.z;

        mPrevious[index * 3] = prev.x;
        mPrevious[index * 3 + 1] = prev.y;
        mPrevious[index * 3 + 2] = prev.z;

        mSide[index] = side;
        mWidth[index] = width;
        mLengths[index] = length;
        mEndCaps[index] = endCapPosition;
    }

    /**
     * Sets the bNeedsUpdate to true.
     */
    public void clear() {
        bNeedsUpdate.set(true);
    }


    /**
     * This takes the float[] and creates FloatBuffers, Binds the VBO, and upload the Attributes to
     * correct locations with the correct offsets so the Vertex and Fragment shader can render the lines
     */
    public void upload() {
        bNeedsUpdate.set(false);

        FloatBuffer current = toFloatBuffer(mPositions);
        FloatBuffer next = toFloatBuffer(mNext);
        FloatBuffer previous = toFloatBuffer(mPrevious);

        FloatBuffer side = toFloatBuffer(mSide);
        FloatBuffer width = toFloatBuffer(mWidth);
        FloatBuffer lengths = toFloatBuffer(mLengths);
        FloatBuffer endCaps = toFloatBuffer(mEndCaps);


//        mNumPoints = mPositions.length;

        mPositionAddress = 0;
        mNextAddress = mPositionAddress + mNumBytes * 3 * BYTES_PER_FLOAT;
        mPreviousAddress = mNextAddress + mNumBytes * 3 * BYTES_PER_FLOAT;
        mSideAddress = mPreviousAddress + mNumBytes * 3 * BYTES_PER_FLOAT;
        mWidthAddress = mSideAddress + mNumBytes * BYTES_PER_FLOAT;
        mLengthAddress = mWidthAddress + mNumBytes * BYTES_PER_FLOAT;
        mEndCapsAddress = mLengthAddress + mNumBytes * BYTES_PER_FLOAT;
        mVboSize = mEndCapsAddress + mNumBytes * BYTES_PER_FLOAT;

        ShaderUtil.checkGLError(TAG, "before update");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mPositionAddress, mNumBytes * 3 * BYTES_PER_FLOAT,
                current);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mNextAddress, mNumBytes * 3 * BYTES_PER_FLOAT,
                next);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mPreviousAddress, mNumBytes * 3 * BYTES_PER_FLOAT,
                previous);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mSideAddress, mNumBytes * BYTES_PER_FLOAT,
                side);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mWidthAddress, mNumBytes * BYTES_PER_FLOAT,
                width);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mLengthAddress, mNumBytes * BYTES_PER_FLOAT,
                lengths);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mEndCapsAddress, mNumBytes * BYTES_PER_FLOAT,
                endCaps);


        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "after update");
    }


    /**
     * This method takes in the current CameraView Matrix and the Camera's Projection Matrix, the
     * current position and pose of the device, uses those to calculate the ModelViewMatrix and
     * ModelViewProjectionMatrix.  It binds the VBO, enables the custom attribute locations,
     * binds and uploads the shader uniforms, calls our single DrawArray call, and finally disables
     * and unbinds the shader attributes and VBO.
     */
    public void draw(float[] cameraView, float[] cameraPerspective, float screenWidth, float screenHeight, float nearClip, float farClip) {


        Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0);

        ShaderUtil.checkGLError(TAG, "Before draw");

        GLES20.glUseProgram(mProgramName);


        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // Blending setup
        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFuncSeparate(
//                GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA, // RGB (src, dest)
//                GLES20.GL_ZERO, GLES20.GL_ONE); // ALPHA (src, dest)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Attach the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

//        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);

//        GLES20.glUniform1i(mTextureUniform, 0);
        GLES20.glUniform1i(mEndCapTextureUniform, 0);
//
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glVertexAttribPointer(
                mPositionAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mPositionAddress);
        GLES20.glVertexAttribPointer(
                mPreviousAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mPreviousAddress);
        GLES20.glVertexAttribPointer(
                mNextAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, mNextAddress);
        GLES20.glVertexAttribPointer(
                mSideAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mSideAddress);
        GLES20.glVertexAttribPointer(
                mWidthAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mWidthAddress);
        GLES20.glVertexAttribPointer(
                mLengthsAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mLengthAddress);
        GLES20.glVertexAttribPointer(
                mEndCapsAttribute, 1, GLES20.GL_FLOAT, false, BYTES_PER_FLOAT, mEndCapsAddress);
//

        GLES20.glUniformMatrix4fv(
                mModelViewUniform, 1, false, mModelViewMatrix, 0);
        GLES20.glUniformMatrix4fv(
                mProjectionUniform, 1, false, cameraPerspective, 0);

        GLES20.glUniform2f(mResolutionUniform, screenWidth, screenHeight);
        GLES20.glUniform3f(mColorUniform, mColor.x, mColor.y, mColor.z);
        GLES20.glUniform1f(mNearUniform, nearClip);
        GLES20.glUniform1f(mFarUniform, farClip);
        GLES20.glUniform1f(mLineDepthScaleUniform, mLineDepthScale);
        GLES20.glUniform1f(mDrawingDistUniform, mDrawDistance);

        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glEnableVertexAttribArray(mPreviousAttribute);
        GLES20.glEnableVertexAttribArray(mNextAttribute);
        GLES20.glEnableVertexAttribArray(mSideAttribute);
        GLES20.glEnableVertexAttribArray(mWidthAttribute);
        GLES20.glEnableVertexAttribArray(mLengthsAttribute);
        GLES20.glEnableVertexAttribArray(mEndCapsAttribute);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mNumBytes);

        GLES20.glDisableVertexAttribArray(mEndCapsAttribute);
        GLES20.glDisableVertexAttribArray(mLengthsAttribute);
        GLES20.glDisableVertexAttribArray(mWidthAttribute);
        GLES20.glDisableVertexAttribArray(mSideAttribute);
        GLES20.glDisableVertexAttribArray(mNextAttribute);
        GLES20.glDisableVertexAttribArray(mPreviousAttribute);
        GLES20.glDisableVertexAttribArray(mPositionAttribute);


        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

    }

    /**
     * A helper function to allocate a FloatBuffer the size of our float[] and copy the float[] into
     * the newly created FloatBuffer.
     */
    private FloatBuffer toFloatBuffer(float[] data) {
        FloatBuffer buff;
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        buff = bb.asFloatBuffer();
        buff.put(data);
        buff.position(0);
        return buff;
    }

}
