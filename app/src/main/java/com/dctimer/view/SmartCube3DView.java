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
import java.util.ArrayList;
import java.util.List;

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
            if (onDoubleTapListener != null) {
                onDoubleTapListener.onDoubleTap(this);
            } else {
                performClick();
            }
            return;
        }
        lastTapTime = now;
        performClick();
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
        private static final float FACE_DISTANCE = 1.5f;
        private static final float CELL_HALF = 0.505f;
        private static final float STICKER_HALF = 0.415f;
        private static final float STICKER_Z_OFFSET = 0.032f;
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
        private static final Facelet[] FACELETS = buildFacelets();
        private static final Vec3 LIGHT = new Vec3(-0.35f, 0.65f, 0.68f).normalize();

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
            for (int i = 0; i < FACELETS.length; i++) {
                Facelet facelet = FACELETS[i];
                Transform transform = buildTransform(facelet, moveSpec, moveProgress);
                int baseColor = shadeColor(0xff111111, transform.normal, 0.42f);
                int stickerColor = shadeColor(faceColor(state.charAt(i)), transform.normal, 0.86f);
                drawQuad(transform.center, transform.u, transform.v, transform.normal, CELL_HALF, baseColor);
                GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
                GLES20.glPolygonOffset(-1f, -1f);
                drawQuad(transform.center.add(transform.normal.scale(STICKER_Z_OFFSET)), transform.u, transform.v,
                        transform.normal, STICKER_HALF, stickerColor);
                GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);
            }
        }

        private Transform buildTransform(Facelet facelet, MoveSpec moveSpec, float moveProgress) {
            Vec3 center = facelet.center;
            Vec3 normal = facelet.normal;
            Vec3 u = facelet.u;
            Vec3 v = facelet.v;
            if (moveSpec != null && moveSpec.belongsToLayer(center)) {
                float angle = moveSpec.angleDegrees * moveProgress;
                center = rotateAroundAxis(center, moveSpec.axis, angle);
                normal = rotateAroundAxis(normal, moveSpec.axis, angle);
                u = rotateAroundAxis(u, moveSpec.axis, angle);
                v = rotateAroundAxis(v, moveSpec.axis, angle);
            }
            return new Transform(center, normal.normalize(), u.normalize(), v.normalize());
        }

        private void drawQuad(Vec3 center, Vec3 u, Vec3 v, Vec3 normal, float halfSize, int color) {
            Vec3 p0 = center.add(u.scale(-halfSize)).add(v.scale(-halfSize));
            Vec3 p1 = center.add(u.scale(halfSize)).add(v.scale(-halfSize));
            Vec3 p2 = center.add(u.scale(halfSize)).add(v.scale(halfSize));
            Vec3 p3 = center.add(u.scale(-halfSize)).add(v.scale(halfSize));
            putTriangle(p0, p1, p2, 0);
            putTriangle(p0, p2, p3, 9);
            FloatBuffer buffer = ByteBuffer.allocateDirect(quad.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            buffer.put(quad);
            buffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            setColorUniform(color, normal);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private void putTriangle(Vec3 p0, Vec3 p1, Vec3 p2, int offset) {
            putPoint(p0, offset);
            putPoint(p1, offset + 3);
            putPoint(p2, offset + 6);
        }

        private void putPoint(Vec3 point, int offset) {
            quad[offset] = point.x;
            quad[offset + 1] = point.y;
            quad[offset + 2] = point.z;
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

        private int clampColor(float value) {
            return Math.max(0, Math.min(255, Math.round(value)));
        }

        private int faceColor(char face) {
            switch (face) {
                case 'U':
                    return 0xfffbfbfb;
                case 'R':
                    return 0xffef4444;
                case 'F':
                    return 0xff3f9b46;
                case 'D':
                    return 0xfff5d142;
                case 'L':
                    return 0xfff28b24;
                case 'B':
                    return 0xff2d67cf;
                default:
                    return 0xff8c8c8c;
            }
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

        private static Facelet[] buildFacelets() {
            List<Facelet> facelets = new ArrayList<>(54);
            addFace(facelets, new Vec3(0f, FACE_DISTANCE, 0f), new Vec3(0f, 1f, 0f), new Vec3(1f, 0f, 0f), new Vec3(0f, 0f, 1f));
            addFace(facelets, new Vec3(FACE_DISTANCE, 0f, 0f), new Vec3(1f, 0f, 0f), new Vec3(0f, 0f, -1f), new Vec3(0f, -1f, 0f));
            addFace(facelets, new Vec3(0f, 0f, FACE_DISTANCE), new Vec3(0f, 0f, 1f), new Vec3(1f, 0f, 0f), new Vec3(0f, -1f, 0f));
            addFace(facelets, new Vec3(0f, -FACE_DISTANCE, 0f), new Vec3(0f, -1f, 0f), new Vec3(1f, 0f, 0f), new Vec3(0f, 0f, -1f));
            addFace(facelets, new Vec3(-FACE_DISTANCE, 0f, 0f), new Vec3(-1f, 0f, 0f), new Vec3(0f, 0f, 1f), new Vec3(0f, -1f, 0f));
            addFace(facelets, new Vec3(0f, 0f, -FACE_DISTANCE), new Vec3(0f, 0f, -1f), new Vec3(-1f, 0f, 0f), new Vec3(0f, -1f, 0f));
            return facelets.toArray(new Facelet[0]);
        }

        private static void addFace(List<Facelet> facelets, Vec3 faceCenter, Vec3 normal, Vec3 u, Vec3 v) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float uOffset = col - 1f;
                    float vOffset = row - 1f;
                    Vec3 center = faceCenter.add(u.scale(uOffset)).add(v.scale(vOffset));
                    facelets.add(new Facelet(center, normal, u, v));
                }
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

    private static class Facelet {
        final Vec3 center;
        final Vec3 normal;
        final Vec3 u;
        final Vec3 v;

        Facelet(Vec3 center, Vec3 normal, Vec3 u, Vec3 v) {
            this.center = center;
            this.normal = normal;
            this.u = u;
            this.v = v;
        }
    }

    private static class Transform {
        final Vec3 center;
        final Vec3 normal;
        final Vec3 u;
        final Vec3 v;

        Transform(Vec3 center, Vec3 normal, Vec3 u, Vec3 v) {
            this.center = center;
            this.normal = normal;
            this.u = u;
            this.v = v;
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

        boolean belongsToLayer(Vec3 center) {
            float coordinate;
            switch (axis) {
                case 0:
                    coordinate = center.x;
                    break;
                case 1:
                    coordinate = center.y;
                    break;
                default:
                    coordinate = center.z;
                    break;
            }
            return layer > 0 ? coordinate > 0.5f : coordinate < -0.5f;
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
