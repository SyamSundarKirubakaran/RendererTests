/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bugscript.renderertest.Rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.bugscript.renderertest.R;
import com.google.ar.core.PointCloud;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PointCloudRenderer {
  private static final String TAG = PointCloud.class.getSimpleName();

  private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
  private static final int FLOATS_PER_POINT = 4; // X,Y,Z,confidence.
  private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;
  private static final int INITIAL_BUFFER_POINTS = 1000;

  private int vbo;
  private int vboSize;

  private int programName;
  private int positionAttribute;
  private int modelViewProjectionUniform;
  private int colorUniform;
  private int pointSizeUniform;

  private int numPoints = 0;


  private PointCloud lastPointCloud = null;

  public PointCloudRenderer() {}

  public void createOnGlThread(Context context) {
    ShaderUtil.checkGLError(TAG, "before create");

    int[] buffers = new int[1];
    GLES20.glGenBuffers(1, buffers, 0);
    vbo = buffers[0];
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);

    vboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "buffer alloc");

    int vertexShader =
        ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, R.raw.point_cloud_vertex);
    int passthroughShader =
        ShaderUtil.loadGLShader(
            TAG, context, GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

    programName = GLES20.glCreateProgram();
    GLES20.glAttachShader(programName, vertexShader);
    GLES20.glAttachShader(programName, passthroughShader);
    GLES20.glLinkProgram(programName);
    GLES20.glUseProgram(programName);

    ShaderUtil.checkGLError(TAG, "program");

    positionAttribute = GLES20.glGetAttribLocation(programName, "a_Position");
    colorUniform = GLES20.glGetUniformLocation(programName, "u_Color");
    modelViewProjectionUniform = GLES20.glGetUniformLocation(programName, "u_ModelViewProjection");
    pointSizeUniform = GLES20.glGetUniformLocation(programName, "u_PointSize");

    ShaderUtil.checkGLError(TAG, "program  params");
  }

  public void update(PointCloud cloud) {
    if (lastPointCloud == cloud) {
      // Redundant call.
      return;
    }

    ShaderUtil.checkGLError(TAG, "before update");

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
    lastPointCloud = cloud;

    numPoints = lastPointCloud.getPoints().remaining() / FLOATS_PER_POINT;
    if (numPoints * BYTES_PER_POINT > vboSize) {
      while (numPoints * BYTES_PER_POINT > vboSize) {
        vboSize *= 2;
      }
      GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
    }
    GLES20.glBufferSubData(
        GLES20.GL_ARRAY_BUFFER, 0, numPoints * BYTES_PER_POINT, lastPointCloud.getPoints());
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "after update");
  }

  public void draw(float[] cameraView, float[] cameraPerspective) {
    float[] modelViewProjection = new float[16];
    Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

    ShaderUtil.checkGLError(TAG, "Before draw");

    GLES20.glUseProgram(programName);
    GLES20.glEnableVertexAttribArray(positionAttribute);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
    GLES20.glVertexAttribPointer(positionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
    GLES20.glUniform4f(colorUniform, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0);
    GLES20.glUniform1f(pointSizeUniform, 5.0f);

    GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints);
    GLES20.glDisableVertexAttribArray(positionAttribute);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "Draw");
  }
}
