package com.dctimer.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

import com.dctimer.model.SmartCubeOrientation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SmartCube3DView extends GLSurfaceView {
    private static final String SOLVED_FACELET = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
    private static final long DEFAULT_ANIMATION_DURATION_MS = 110L;
    private static final float TOUCH_DEGREES_PER_PX = 0.28f;
    private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    private final CubeRenderer cubeRenderer;
    private ValueAnimator animator;
    private float lastTouchX;
    private float lastTouchY;
    private float touchStartX;
    private float touchStartY;
    private boolean draggingView;
    private int touchSlop;
    private long lastTapTime;
    private OnDoubleTapListener onDoubleTapListener;
    private final Runnable singleTapRunnable = new Runnable() {
        @Override
        public void run() {
            lastTapTime = 0L;
            performClick();
        }
    };

    public SmartCube3DView(Context context) {
        this(context, null);
    }

    public SmartCube3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setPreserveEGLContextOnPause(true);
        cubeRenderer = new CubeRenderer();
        setRenderer(cubeRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void showCubeState(String facelets) {
        final String validState = sanitizeFacelets(facelets);
        if (validState == null) {
            return;
        }
        stopAnimator();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.setState(validState);
            }
        });
        requestRender();
    }

    public void animateMove(String fromState, String toState, int move) {
        animateMove(fromState, toState, move, DEFAULT_ANIMATION_DURATION_MS);
    }

    public void setDeviceOrientation(final SmartCubeOrientation orientation) {
        if (orientation == null) {
            return;
        }
        final float[] orientationMatrix = orientation.toMatrix();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.setDeviceOrientation(orientationMatrix);
            }
        });
        requestRender();
    }

    public void resetOrientationToWhiteTopGreenFront() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.resetOrientationToWhiteTopGreenFront();
            }
        });
        requestRender();
    }

    public void setOnDoubleTapListener(OnDoubleTapListener listener) {
        onDoubleTapListener = listener;
    }

    public void animateMove(String fromState, String toState, final int move, long durationMs) {
        final String validFromState = sanitizeFacelets(fromState);
        final String validToState = sanitizeFacelets(toState);
        if (validFromState == null || validToState == null || !canAnimateMove(move) || !isShown()) {
            showCubeState(validToState);
            return;
        }
        stopAnimator();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                cubeRenderer.startMove(validFromState, validToState, move);
            }
        });
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs > 0 ? durationMs : DEFAULT_ANIMATION_DURATION_MS);
        animator.setInterpolator(LINEAR_INTERPOLATOR);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float progress = (float) animation.getAnimatedValue();
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        cubeRenderer.setMoveProgress(progress);
                    }
                });
                requestRender();
                if (progress >= 1f) {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            cubeRenderer.setState(validToState);
                        }
                    });
                    requestRender();
                }
            }
        });
        animator.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                touchStartX = lastTouchX;
                touchStartY = lastTouchY;
                draggingView = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                if (!draggingView
                        && (Math.abs(x - touchStartX) > touchSlop || Math.abs(y - touchStartY) > touchSlop)) {
                    draggingView = true;
                }
                final float deltaYaw = (x - lastTouchX) * TOUCH_DEGREES_PER_PX;
                final float deltaPitch = (y - lastTouchY) * TOUCH_DEGREES_PER_PX;
                lastTouchX = x;
                lastTouchY = y;
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        cubeRenderer.rotateView(deltaYaw, deltaPitch);
                    }
                });
                requestRender();
                return true;
            case MotionEvent.ACTION_UP:
                if (!draggingView) {
                    handleTap();
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        removeCallbacks(singleTapRunnable);
        super.onDetachedFromWindow();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator.removeAllListeners();
            animator = null;
        }
    }

    private boolean canAnimateMove(int move) {
        return move >= 0 && move < 18;
    }

    private void handleTap() {
        long now = SystemClock.uptimeMillis();
        if (now - lastTapTime <= ViewConfiguration.getDoubleTapTimeout()) {
            lastTapTime = 0L;
            removeCallbacks(singleTapRunnable);
            if (onDoubleTapListener != null) {
                onDoubleTapListener.onDoubleTap(this);
            } else {
                performClick();
            }
            return;
        }
        lastTapTime = now;
        removeCallbacks(singleTapRunnable);
        postDelayed(singleTapRunnable, ViewConfiguration.getDoubleTapTimeout());
    }

    private String sanitizeFacelets(String facelets) {
        if (TextUtils.isEmpty(facelets) || facelets.length() < 54) {
            return null;
        }
        return facelets.substring(0, 54);
    }

    public interface OnDoubleTapListener {
        void onDoubleTap(SmartCube3DView view);
    }

    private static class CubeRenderer implements Renderer {
        private static final float CUBIE_SPACING = 0.990f;
        private static final float CUBIE_HALF = 0.49f;
        private static final float CUBIE_FRONT_HALF = 0.474f;
        private static final float CUBIE_CENTER_FRONT_HALF = 0.478f;
        private static final float CUBIE_FRONT_OFFSET = 0.006f;
        private static final float CUBIE_CORNER_FACE_RADIUS = 0.07f;
        private static final float CUBIE_EDGE_OUTER_RADIUS = 0.07f;
        private static final float CUBIE_EDGE_CENTER_RADIUS = 0.30f;
        private static final float CUBIE_CENTER_FACE_RADIUS = 0.40f;
        private static final int ROUNDED_CORNER_SEGMENTS = 5;
        private static final int ROUNDED_RECT_POINT_COUNT = (ROUNDED_CORNER_SEGMENTS + 1) * 4;
        private static final int ROUNDED_RECT_FLOAT_COUNT = ROUNDED_RECT_POINT_COUNT * 9;
        private static final float MAX_VIEW_PITCH = 115f;
        private static final String VERTEX_SHADER =
                "uniform mat4 uMvpMatrix;" +
                "attribute vec3 aPosition;" +
                "void main() {" +
                "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);" +
                "}";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;" +
                "uniform vec4 uColor;" +
                "void main() {" +
                "  gl_FragColor = uColor;" +
                "}";
        private static final Vec3 LIGHT = new Vec3(-0.35f, 0.65f, 0.68f).normalize();
        private static final Vec3[] FACE_NORMALS = {
                new Vec3(0f, 1f, 0f),
                new Vec3(1f, 0f, 0f),
                new Vec3(0f, 0f, 1f),
                new Vec3(0f, -1f, 0f),
                new Vec3(-1f, 0f, 0f),
                new Vec3(0f, 0f, -1f)
        };
        private static final Vec3[] FACE_U = {
                new Vec3(1f, 0f, 0f),
                new Vec3(0f, 0f, -1f),
                new Vec3(1f, 0f, 0f),
                new Vec3(1f, 0f, 0f),
                new Vec3(0f, 0f, 1f),
                new Vec3(-1f, 0f, 0f)
        };
        private static final Vec3[] FACE_V = {
                new Vec3(0f, 0f, 1f),
                new Vec3(0f, -1f, 0f),
                new Vec3(0f, -1f, 0f),
                new Vec3(0f, 0f, -1f),
                new Vec3(0f, -1f, 0f),
                new Vec3(0f, -1f, 0f)
        };

        private final float[] projectionMatrix = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private final float[] dragMatrix = new float[16];
        private final float[] deviceOrientationMatrix = new float[16];
        private final float[] calibrationMatrix = new float[16];
        private final float[] calibratedDeviceMatrix = new float[16];
        private final float[] vpMatrix = new float[16];
        private final float[] mvpMatrix = new float[16];
        private final float[] quad = new float[18];
        private final float[] roundedRectVertices = new float[ROUNDED_RECT_FLOAT_COUNT];
        private final Vec3[] roundedRectPoints = new Vec3[ROUNDED_RECT_POINT_COUNT];
        private final FloatBuffer quadBuffer = ByteBuffer.allocateDirect(quad.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        private final FloatBuffer roundedRectBuffer = ByteBuffer.allocateDirect(roundedRectVertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        private final Cubie[] cubies = createCubies();
        private String cubeState = SOLVED_FACELET;
        private String animationStartState;
        private String animationEndState;
        private MoveSpec animationMoveSpec;
        private float animationProgress;
        private float viewYaw;
        private float viewPitch;
        private boolean hasDeviceOrientation;
        private int program;
        private int positionHandle;
        private int colorHandle;
        private int mvpHandle;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Matrix.setIdentityM(calibrationMatrix, 0);
            Matrix.setIdentityM(deviceOrientationMatrix, 0);
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            colorHandle = GLES20.glGetUniformLocation(program, "uColor");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix");
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = width > 0 && height > 0 ? (float) width / (float) height : 1f;
            Matrix.frustumM(projectionMatrix, 0, -aspect * 1.18f, aspect * 1.18f, -1.18f, 1.18f, 3f, 20f);
            Matrix.setLookAtM(viewMatrix, 0, 0f, 4.5f, 8.2f, 0f, 0f, 0f, 0f, 1f, 0f);
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            updateMvpMatrix();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
            if (animationStartState != null && animationMoveSpec != null) {
                drawCube(animationStartState, animationMoveSpec, animationProgress);
            } else {
                drawCube(cubeState, null, 0f);
            }
        }

        void setState(String state) {
            cubeState = state;
            animationStartState = null;
            animationEndState = null;
            animationMoveSpec = null;
            animationProgress = 0f;
        }

        void startMove(String fromState, String toState, int move) {
            cubeState = toState;
            animationStartState = fromState;
            animationEndState = toState;
            animationMoveSpec = MoveSpec.fromMove(move);
            animationProgress = 0f;
        }

        void setMoveProgress(float progress) {
            animationProgress = Math.max(0f, Math.min(1f, progress));
            if (animationProgress >= 1f && animationEndState != null) {
                setState(animationEndState);
            }
        }

        void rotateView(float deltaYaw, float deltaPitch) {
            viewYaw = normalizeDegrees(viewYaw + deltaYaw);
            viewPitch = clamp(viewPitch + deltaPitch, -MAX_VIEW_PITCH, MAX_VIEW_PITCH);
            updateMvpMatrix();
        }

        void setDeviceOrientation(float[] orientationMatrix) {
            if (orientationMatrix == null || orientationMatrix.length < 16) {
                return;
            }
            System.arraycopy(orientationMatrix, 0, deviceOrientationMatrix, 0, 16);
            hasDeviceOrientation = true;
            updateMvpMatrix();
        }

        void resetOrientationToWhiteTopGreenFront() {
            viewYaw = 0f;
            viewPitch = 0f;
            if (hasDeviceOrientation) {
                Matrix.transposeM(calibrationMatrix, 0, deviceOrientationMatrix, 0);
            } else {
                Matrix.setIdentityM(calibrationMatrix, 0);
            }
            updateMvpMatrix();
        }

        private void updateMvpMatrix() {
            Matrix.setIdentityM(dragMatrix, 0);
            Matrix.rotateM(dragMatrix, 0, viewPitch, 1f, 0f, 0f);
            Matrix.rotateM(dragMatrix, 0, viewYaw, 0f, 1f, 0f);
            if (hasDeviceOrientation) {
                Matrix.multiplyMM(calibratedDeviceMatrix, 0, calibrationMatrix, 0, deviceOrientationMatrix, 0);
                Matrix.multiplyMM(modelMatrix, 0, dragMatrix, 0, calibratedDeviceMatrix, 0);
            } else {
                System.arraycopy(dragMatrix, 0, modelMatrix, 0, 16);
            }
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);
        }

        private float normalizeDegrees(float degrees) {
            if (degrees > 180f) {
                return degrees - 360f;
            }
            if (degrees < -180f) {
                return degrees + 360f;
            }
            return degrees;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private void drawCube(String state, MoveSpec moveSpec, float moveProgress) {
            populateCubies(state);
            for (Cubie cubie : cubies) {
                drawCubie(cubie, moveSpec, moveProgress);
            }
        }

        private void drawCubie(Cubie cubie, MoveSpec moveSpec, float moveProgress) {
            Vec3 center = new Vec3(cubie.x * CUBIE_SPACING, cubie.y * CUBIE_SPACING, cubie.z * CUBIE_SPACING);
            float angle = moveSpec != null && moveSpec.belongsToLayer(cubie)
                    ? moveSpec.angleDegrees * moveProgress : 0f;
            if (angle != 0f) {
                center = rotateAroundAxis(center, moveSpec.axis, angle);
            }
            for (int face = 0; face < 6; face++) {
                Vec3 normal = FACE_NORMALS[face];
                Vec3 u = FACE_U[face];
                Vec3 v = FACE_V[face];
                if (angle != 0f) {
                    normal = rotateAroundAxis(normal, moveSpec.axis, angle);
                    u = rotateAroundAxis(u, moveSpec.axis, angle);
                    v = rotateAroundAxis(v, moveSpec.axis, angle);
                }
                drawCubieFace(cubie, face, center, normal, u, v, cubie.colors[face]);
            }
        }

        private void drawCubieFace(Cubie cubie, int face, Vec3 cubieCenter, Vec3 normal, Vec3 u, Vec3 v, int color) {
            Vec3 faceCenter = cubieCenter.add(normal.scale(CUBIE_HALF));
            if (color == 0) {
                int inferredColor = inferHiddenFaceColor(cubie, face);
                int hiddenSideColor = shadeColor(mixColor(inferredColor, 0xff111111, 0.44f), normal, 0.72f);
                drawQuad(faceCenter, u, v, normal, CUBIE_HALF, hiddenSideColor);
                return;
            }
            int bevelColor = shadeColor(mixColor(color, 0xff111111, 0.30f), normal, 0.78f);
            int frontColor = shadeColor(color, normal, 0.94f);
            boolean centerFace = isCenterFace(cubie, face);
            float frontHalf = centerFace ? CUBIE_CENTER_FRONT_HALF : CUBIE_FRONT_HALF;
            CornerRadii cornerRadii = faceCornerRadii(cubie, face);
            drawQuad(faceCenter, u, v, normal, CUBIE_HALF, bevelColor);
            drawRoundedRect(faceCenter.add(normal.scale(CUBIE_FRONT_OFFSET)), u, v, normal,
                    frontHalf, frontHalf, cornerRadii, frontColor);
        }

        private boolean isCenterFace(Cubie cubie, int face) {
            switch (face) {
                case 0:
                case 3:
                    return cubie.x == 0 && cubie.z == 0;
                case 1:
                case 4:
                    return cubie.y == 0 && cubie.z == 0;
                default:
                    return cubie.x == 0 && cubie.y == 0;
            }
        }

        private CornerRadii faceCornerRadii(Cubie cubie, int face) {
            int outerAxes = 0;
            if (cubie.x != 0) outerAxes++;
            if (cubie.y != 0) outerAxes++;
            if (cubie.z != 0) outerAxes++;
            if (outerAxes == 1) {
                return CornerRadii.all(CUBIE_CENTER_FACE_RADIUS);
            }
            if (outerAxes == 2) {
                return edgeFaceCornerRadii(cubie, face);
            }
            return CornerRadii.all(CUBIE_CORNER_FACE_RADIUS);
        }

        private CornerRadii edgeFaceCornerRadii(Cubie cubie, int face) {
            float topRight = CUBIE_EDGE_OUTER_RADIUS;
            float topLeft = CUBIE_EDGE_OUTER_RADIUS;
            float bottomLeft = CUBIE_EDGE_OUTER_RADIUS;
            float bottomRight = CUBIE_EDGE_OUTER_RADIUS;
            for (int corner = 0; corner < 4; corner++) {
                float localU = (corner == 0 || corner == 3) ? 1f : -1f;
                float localV = (corner == 0 || corner == 1) ? 1f : -1f;
                Vec3 towardCorner = FACE_U[face].scale(localU).add(FACE_V[face].scale(localV));
                if (pointsTowardCubeCenter(cubie, towardCorner)) {
                    switch (corner) {
                        case 0:
                            topRight = CUBIE_EDGE_CENTER_RADIUS;
                            break;
                        case 1:
                            topLeft = CUBIE_EDGE_CENTER_RADIUS;
                            break;
                        case 2:
                            bottomLeft = CUBIE_EDGE_CENTER_RADIUS;
                            break;
                        default:
                            bottomRight = CUBIE_EDGE_CENTER_RADIUS;
                            break;
                    }
                }
            }
            return new CornerRadii(topRight, topLeft, bottomLeft, bottomRight);
        }

        private boolean pointsTowardCubeCenter(Cubie cubie, Vec3 direction) {
            Vec3 toCenter = new Vec3(-cubie.x, -cubie.y, -cubie.z);
            return direction.dot(toCenter) > 0.5f;
        }

        private int inferHiddenFaceColor(Cubie cubie, int face) {
            if (face == 0 || face == 3) {
                if (cubie.x == 1) return cubie.colors[1] != 0 ? cubie.colors[1] : faceColor('R');
                if (cubie.x == -1) return cubie.colors[4] != 0 ? cubie.colors[4] : faceColor('L');
                if (cubie.z == 1) return cubie.colors[2] != 0 ? cubie.colors[2] : faceColor('F');
                if (cubie.z == -1) return cubie.colors[5] != 0 ? cubie.colors[5] : faceColor('B');
            } else if (face == 1 || face == 4) {
                if (cubie.y == 1) return cubie.colors[0] != 0 ? cubie.colors[0] : faceColor('U');
                if (cubie.y == -1) return cubie.colors[3] != 0 ? cubie.colors[3] : faceColor('D');
                if (cubie.z == 1) return cubie.colors[2] != 0 ? cubie.colors[2] : faceColor('F');
                if (cubie.z == -1) return cubie.colors[5] != 0 ? cubie.colors[5] : faceColor('B');
            } else {
                if (cubie.x == 1) return cubie.colors[1] != 0 ? cubie.colors[1] : faceColor('R');
                if (cubie.x == -1) return cubie.colors[4] != 0 ? cubie.colors[4] : faceColor('L');
                if (cubie.y == 1) return cubie.colors[0] != 0 ? cubie.colors[0] : faceColor('U');
                if (cubie.y == -1) return cubie.colors[3] != 0 ? cubie.colors[3] : faceColor('D');
            }
            return 0xff5d5d5d;
        }

        private void drawRoundedRect(Vec3 center, Vec3 u, Vec3 v, Vec3 normal,
                                     float halfWidth, float halfHeight, CornerRadii radii, int color) {
            int pointCount = buildRoundedRectPoints(center, u, v, halfWidth, halfHeight, radii);
            if (pointCount == 0) {
                return;
            }
            int floatCount = 0;
            for (int i = 0; i < pointCount; i++) {
                Vec3 p1 = roundedRectPoints[i];
                Vec3 p2 = roundedRectPoints[(i + 1) % pointCount];
                putTriangle(center, p1, p2, roundedRectVertices, floatCount);
                floatCount += 9;
            }
            drawVertices(roundedRectBuffer, roundedRectVertices, floatCount, pointCount * 3, color, normal);
        }

        private int buildRoundedRectPoints(Vec3 center, Vec3 u, Vec3 v,
                                           float halfWidth, float halfHeight, CornerRadii radii) {
            float maxRadius = Math.min(halfWidth, halfHeight) * 0.86f;
            float topRightRadius = Math.min(radii.topRight, maxRadius);
            float topLeftRadius = Math.min(radii.topLeft, maxRadius);
            float bottomLeftRadius = Math.min(radii.bottomLeft, maxRadius);
            float bottomRightRadius = Math.min(radii.bottomRight, maxRadius);
            int pointCount = 0;
            pointCount = addCornerPoints(pointCount, center, u, v,
                    halfWidth - topRightRadius, halfHeight - topRightRadius,
                    topRightRadius, 0f, 90f);
            pointCount = addCornerPoints(pointCount, center, u, v,
                    -halfWidth + topLeftRadius, halfHeight - topLeftRadius,
                    topLeftRadius, 90f, 180f);
            pointCount = addCornerPoints(pointCount, center, u, v,
                    -halfWidth + bottomLeftRadius, -halfHeight + bottomLeftRadius,
                    bottomLeftRadius, 180f, 270f);
            pointCount = addCornerPoints(pointCount, center, u, v,
                    halfWidth - bottomRightRadius, -halfHeight + bottomRightRadius,
                    bottomRightRadius, 270f, 360f);
            return pointCount;
        }

        private int addCornerPoints(int pointCount, Vec3 center, Vec3 u, Vec3 v,
                                    float cornerU, float cornerV, float radius,
                                    float startDegrees, float endDegrees) {
            for (int i = 0; i <= ROUNDED_CORNER_SEGMENTS; i++) {
                float t = (float) i / ROUNDED_CORNER_SEGMENTS;
                float radians = (float) Math.toRadians(startDegrees + (endDegrees - startDegrees) * t);
                float localU = cornerU + (float) Math.cos(radians) * radius;
                float localV = cornerV + (float) Math.sin(radians) * radius;
                roundedRectPoints[pointCount++] = center.add(u.scale(localU)).add(v.scale(localV));
            }
            return pointCount;
        }

        private void drawQuad(Vec3 center, Vec3 u, Vec3 v, Vec3 normal, float halfSize, int color) {
            Vec3 p0 = center.add(u.scale(-halfSize)).add(v.scale(-halfSize));
            Vec3 p1 = center.add(u.scale(halfSize)).add(v.scale(-halfSize));
            Vec3 p2 = center.add(u.scale(halfSize)).add(v.scale(halfSize));
            Vec3 p3 = center.add(u.scale(-halfSize)).add(v.scale(halfSize));
            putTriangle(p0, p1, p2, 0);
            putTriangle(p0, p2, p3, 9);
            drawVertices(quadBuffer, quad, quad.length, 6, color, normal);
        }

        private void putTriangle(Vec3 p0, Vec3 p1, Vec3 p2, int offset) {
            putTriangle(p0, p1, p2, quad, offset);
        }

        private void putTriangle(Vec3 p0, Vec3 p1, Vec3 p2, float[] target, int offset) {
            putPoint(p0, target, offset);
            putPoint(p1, target, offset + 3);
            putPoint(p2, target, offset + 6);
        }

        private void putPoint(Vec3 point, int offset) {
            putPoint(point, quad, offset);
        }

        private void putPoint(Vec3 point, float[] target, int offset) {
            target[offset] = point.x;
            target[offset + 1] = point.y;
            target[offset + 2] = point.z;
        }

        private void drawVertices(FloatBuffer buffer, float[] vertices, int floatCount,
                                  int vertexCount, int color, Vec3 normal) {
            buffer.clear();
            buffer.put(vertices, 0, floatCount);
            buffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            setColorUniform(color, normal);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private void setColorUniform(int color, Vec3 normal) {
            float alpha = Color.alpha(color) / 255f;
            float red = Color.red(color) / 255f;
            float green = Color.green(color) / 255f;
            float blue = Color.blue(color) / 255f;
            GLES20.glUniform4f(colorHandle, red, green, blue, alpha);
        }

        private int shadeColor(int color, Vec3 normal, float ambient) {
            float diffuse = Math.max(0f, normal.normalize().dot(LIGHT));
            float factor = Math.min(1.12f, ambient + diffuse * 0.28f);
            return Color.argb(Color.alpha(color),
                    clampColor(Color.red(color) * factor),
                    clampColor(Color.green(color) * factor),
                    clampColor(Color.blue(color) * factor));
        }

        private int mixColor(int color, int target, float amount) {
            float keep = 1f - amount;
            return Color.argb(Color.alpha(color),
                    clampColor(Color.red(color) * keep + Color.red(target) * amount),
                    clampColor(Color.green(color) * keep + Color.green(target) * amount),
                    clampColor(Color.blue(color) * keep + Color.blue(target) * amount));
        }

        private int clampColor(float value) {
            return Math.max(0, Math.min(255, Math.round(value)));
        }

        private int faceColor(char face) {
            switch (face) {
                case 'U':
                    return 0xfff2f0e8;
                case 'R':
                    return 0xffe01a14;
                case 'F':
                    return 0xff00b84a;
                case 'D':
                    return 0xffffea45;
                case 'L':
                    return 0xffff5c00;
                case 'B':
                    return 0xff0080ff;
                default:
                    return 0xff8c8c8c;
            }
        }

        private Cubie[] createCubies() {
            Cubie[] result = new Cubie[26];
            int index = 0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        result[index++] = new Cubie(x, y, z);
                    }
                }
            }
            return result;
        }

        private void populateCubies(String state) {
            for (Cubie cubie : cubies) {
                Arrays.fill(cubie.colors, 0);
                if (cubie.y == 1) cubie.colors[0] = faceColor(state.charAt(faceletIndex(0, cubie.x, cubie.y, cubie.z)));
                if (cubie.x == 1) cubie.colors[1] = faceColor(state.charAt(faceletIndex(1, cubie.x, cubie.y, cubie.z)));
                if (cubie.z == 1) cubie.colors[2] = faceColor(state.charAt(faceletIndex(2, cubie.x, cubie.y, cubie.z)));
                if (cubie.y == -1) cubie.colors[3] = faceColor(state.charAt(faceletIndex(3, cubie.x, cubie.y, cubie.z)));
                if (cubie.x == -1) cubie.colors[4] = faceColor(state.charAt(faceletIndex(4, cubie.x, cubie.y, cubie.z)));
                if (cubie.z == -1) cubie.colors[5] = faceColor(state.charAt(faceletIndex(5, cubie.x, cubie.y, cubie.z)));
            }
        }

        private int faceletIndex(int face, int x, int y, int z) {
            int row;
            int col;
            switch (face) {
                case 0:
                    row = z + 1;
                    col = x + 1;
                    break;
                case 1:
                    row = 1 - y;
                    col = 1 - z;
                    break;
                case 2:
                    row = 1 - y;
                    col = x + 1;
                    break;
                case 3:
                    row = 1 - z;
                    col = x + 1;
                    break;
                case 4:
                    row = 1 - y;
                    col = z + 1;
                    break;
                default:
                    row = 1 - y;
                    col = 1 - x;
                    break;
            }
            return face * 9 + row * 3 + col;
        }

        private Vec3 rotateAroundAxis(Vec3 point, int axis, float angleDegrees) {
            float radians = (float) Math.toRadians(angleDegrees);
            float sin = (float) Math.sin(radians);
            float cos = (float) Math.cos(radians);
            switch (axis) {
                case 0:
                    return new Vec3(point.x, point.y * cos - point.z * sin, point.y * sin + point.z * cos);
                case 1:
                    return new Vec3(point.x * cos + point.z * sin, point.y, -point.x * sin + point.z * cos);
                default:
                    return new Vec3(point.x * cos - point.y * sin, point.x * sin + point.y * cos, point.z);
            }
        }

        private int createProgram(String vertexShaderSource, String fragmentShaderSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource);
            int shaderProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(shaderProgram, vertexShader);
            GLES20.glAttachShader(shaderProgram, fragmentShader);
            GLES20.glLinkProgram(shaderProgram);
            return shaderProgram;
        }

        private int loadShader(int type, String shaderSource) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderSource);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }

    private static class Cubie {
        final int x;
        final int y;
        final int z;
        final int[] colors = new int[6];

        Cubie(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class CornerRadii {
        final float topRight;
        final float topLeft;
        final float bottomLeft;
        final float bottomRight;

        CornerRadii(float topRight, float topLeft, float bottomLeft, float bottomRight) {
            this.topRight = topRight;
            this.topLeft = topLeft;
            this.bottomLeft = bottomLeft;
            this.bottomRight = bottomRight;
        }

        static CornerRadii all(float radius) {
            return new CornerRadii(radius, radius, radius, radius);
        }
    }

    private static class MoveSpec {
        private static final int[] FACE_AXIS = {1, 0, 2, 1, 0, 2};
        private static final int[] FACE_LAYER = {1, 1, 1, -1, -1, -1};
        private static final int[] FACE_SIGN = {-1, -1, -1, 1, 1, 1};

        final int axis;
        final int layer;
        final float angleDegrees;

        MoveSpec(int axis, int layer, float angleDegrees) {
            this.axis = axis;
            this.layer = layer;
            this.angleDegrees = angleDegrees;
        }

        static MoveSpec fromMove(int move) {
            int face = move / 3;
            int turns = move % 3 == 1 ? 2 : 1;
            int sign = FACE_SIGN[face];
            if (move % 3 == 2) {
                sign = -sign;
            }
            return new MoveSpec(FACE_AXIS[face], FACE_LAYER[face], sign * turns * 90f);
        }

        boolean belongsToLayer(Cubie cubie) {
            float coordinate;
            switch (axis) {
                case 0:
                    coordinate = cubie.x;
                    break;
                case 1:
                    coordinate = cubie.y;
                    break;
                default:
                    coordinate = cubie.z;
                    break;
            }
            return layer > 0 ? coordinate > 0 : coordinate < 0;
        }
    }

    private static class Vec3 {
        final float x;
        final float y;
        final float z;

        Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        Vec3 scale(float scale) {
            return new Vec3(x * scale, y * scale, z * scale);
        }

        Vec3 normalize() {
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length <= 0f) {
                return this;
            }
            return new Vec3(x / length, y / length, z / length);
        }

        float dot(Vec3 other) {
            return x * other.x + y * other.y + z * other.z;
        }
    }
}
