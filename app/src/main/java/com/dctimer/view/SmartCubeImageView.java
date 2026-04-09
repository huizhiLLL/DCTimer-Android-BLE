package com.dctimer.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SmartCubeImageView extends AppCompatImageView {
    private static final float DEFAULT_WRAP_WIDTH_DP = 164f;
    private static final float DEFAULT_WRAP_HEIGHT_DP = 180f;
    private static final float FACE_DISTANCE = 1.5f;
    private static final float CELL_HALF = 0.54f;
    private static final float STICKER_HALF = 0.42f;
    private static final float YAW_DEGREES = -38f;
    private static final float PITCH_DEGREES = 27f;
    private static final float CAMERA_DISTANCE = 11.5f;
    private static final float MIN_VISIBLE_NORMAL_Z = 0.05f;
    private static final long DEFAULT_ANIMATION_DURATION_MS = 90L;
    private static final Facelet[] FACELETS = buildFacelets();
    private static final Comparator<RenderTile> DEPTH_COMPARATOR = new Comparator<RenderTile>() {
        @Override
        public int compare(RenderTile left, RenderTile right) {
            return Float.compare(left.depth, right.depth);
        }
    };
    private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF bounds = new RectF();
    private final List<RenderTile> renderTiles = new ArrayList<>(54);

    private boolean smartCubeMode;
    private String cubeState;
    private String animationStartState;
    private String animationEndState;
    private int animationMove = -1;
    private float animationProgress;
    private ValueAnimator animator;

    public SmartCubeImageView(Context context) {
        super(context);
        init();
    }

    public SmartCubeImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SmartCubeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        basePaint.setStyle(Paint.Style.FILL);
        stickerPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x22000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void showCubeState(String facelets) {
        String validState = sanitizeFacelets(facelets);
        if (validState == null) {
            stopAnimator(false);
            smartCubeMode = false;
            invalidate();
            return;
        }
        stopAnimator(false);
        smartCubeMode = true;
        cubeState = validState;
        invalidate();
    }

    public void animateMove(String fromState, String toState, int move) {
        animateMove(fromState, toState, move, DEFAULT_ANIMATION_DURATION_MS);
    }

    public void animateMove(String fromState, String toState, int move, long durationMs) {
        String validFromState = sanitizeFacelets(fromState);
        String validToState = sanitizeFacelets(toState);
        if (validFromState == null || validToState == null || !canAnimateMove(move) || !isShown()) {
            showCubeState(validToState);
            return;
        }
        stopAnimator(true);
        smartCubeMode = true;
        cubeState = validToState;
        animationStartState = validFromState;
        animationEndState = validToState;
        animationMove = move;
        animationProgress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DEFAULT_ANIMATION_DURATION_MS);
        animator.setInterpolator(LINEAR_INTERPOLATOR);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                animationProgress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
        invalidate();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        stopAnimator(false);
        smartCubeMode = false;
        super.setImageBitmap(bm);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(getResources().getDisplayMetrics().density * DEFAULT_WRAP_WIDTH_DP)
                + getPaddingLeft() + getPaddingRight();
        int desiredHeight = Math.round(getResources().getDisplayMetrics().density * DEFAULT_WRAP_HEIGHT_DP)
                + getPaddingTop() + getPaddingBottom();
        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!smartCubeMode) {
            super.onDraw(canvas);
            return;
        }
        String renderState = cubeState;
        if (TextUtils.isEmpty(renderState)) {
            return;
        }
        RenderBatch renderBatch = buildRenderBatch(renderState);
        if (renderBatch.tiles.isEmpty()) {
            return;
        }
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0f || height <= 0f) {
            return;
        }
        float cubeWidth = Math.max(1f, renderBatch.bounds.width());
        float cubeHeight = Math.max(1f, renderBatch.bounds.height());
        float shadowExtra = cubeHeight * 0.18f;
        float scale = Math.min(width * 0.92f / cubeWidth, height * 0.88f / (cubeHeight + shadowExtra));
        float offsetX = getPaddingLeft() + (width - cubeWidth * scale) / 2f - renderBatch.bounds.left * scale;
        float offsetY = getPaddingTop() + (height - (cubeHeight + shadowExtra) * scale) / 2f - renderBatch.bounds.top * scale;
        float strokeWidth = Math.max(getResources().getDisplayMetrics().density * 1.2f, scale * 0.045f);
        strokePaint.setStrokeWidth(strokeWidth);

        drawShadow(canvas, scale, offsetX, offsetY, cubeWidth, cubeHeight);
        if (animationStartState != null && animationEndState != null) {
            int startAlpha = Math.max(0, Math.min(255, Math.round((1f - animationProgress) * 255f)));
            int endAlpha = Math.max(0, Math.min(255, Math.round(animationProgress * 255f)));
            drawRenderBatch(canvas, buildRenderBatch(animationStartState), scale, offsetX, offsetY, startAlpha);
            drawRenderBatch(canvas, buildRenderBatch(animationEndState), scale, offsetX, offsetY, endAlpha);
        } else {
            drawRenderBatch(canvas, renderBatch, scale, offsetX, offsetY, 255);
        }
        if (animationStartState != null && animationProgress >= 1f) {
            stopAnimator(true);
        }
    }

    private RenderBatch buildRenderBatch(String renderState) {
        RenderBatch batch = new RenderBatch();
        renderTiles.clear();
        bounds.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (int i = 0; i < FACELETS.length; i++) {
            Facelet facelet = FACELETS[i];
            Transform transform = buildTransform(facelet, null, 0f);
            if (transform.normal.z <= MIN_VISIBLE_NORMAL_Z) {
                continue;
            }
            PointF[] base = projectQuad(transform.center, transform.u, transform.v, CELL_HALF);
            PointF[] sticker = projectQuad(transform.center, transform.u, transform.v, STICKER_HALF);
            updateBounds(base);
            updateBounds(sticker);
            int stickerColor = shadeSticker(faceColor(renderState.charAt(i)), transform.normal);
            int baseColor = shadeBase(transform.normal);
            renderTiles.add(new RenderTile(base, sticker, transform.center.z, baseColor, stickerColor));
        }
        Collections.sort(renderTiles, DEPTH_COMPARATOR);
        batch.tiles.addAll(renderTiles);
        batch.bounds.set(bounds);
        return batch;
    }

    private void drawRenderBatch(Canvas canvas, RenderBatch batch, float scale, float offsetX, float offsetY, int alpha) {
        for (int i = 0; i < batch.tiles.size(); i++) {
            RenderTile tile = batch.tiles.get(i);
            basePaint.setColor(applyAlpha(tile.baseColor, alpha));
            drawPolygon(canvas, tile.base, scale, offsetX, offsetY, basePaint, false);
            stickerPaint.setColor(applyAlpha(tile.stickerColor, alpha));
            drawPolygon(canvas, tile.sticker, scale, offsetX, offsetY, stickerPaint, false);
            strokePaint.setColor(applyAlpha(0xee080808, alpha));
            drawPolygon(canvas, tile.sticker, scale, offsetX, offsetY, strokePaint, true);
        }
    }

    private void drawShadow(Canvas canvas, float scale, float offsetX, float offsetY, float cubeWidth, float cubeHeight) {
        float centerX = (bounds.centerX() * scale) + offsetX;
        float bottom = (bounds.bottom * scale) + offsetY;
        float shadowWidth = cubeWidth * scale * 0.54f;
        float shadowHeight = cubeHeight * scale * 0.12f;
        canvas.drawOval(centerX - shadowWidth / 2f, bottom - shadowHeight * 0.45f,
                centerX + shadowWidth / 2f, bottom + shadowHeight * 0.55f, shadowPaint);
    }

    private void drawPolygon(Canvas canvas, PointF[] points, float scale, float offsetX, float offsetY, Paint paint, boolean strokeOnly) {
        if (points.length == 0) {
            return;
        }
        path.reset();
        path.moveTo(points[0].x * scale + offsetX, points[0].y * scale + offsetY);
        for (int i = 1; i < points.length; i++) {
            path.lineTo(points[i].x * scale + offsetX, points[i].y * scale + offsetY);
        }
        path.close();
        canvas.drawPath(path, paint);
        if (strokeOnly) {
            return;
        }
    }

    private void updateBounds(PointF[] points) {
        for (PointF point : points) {
            if (point.x < bounds.left) bounds.left = point.x;
            if (point.x > bounds.right) bounds.right = point.x;
            if (point.y < bounds.top) bounds.top = point.y;
            if (point.y > bounds.bottom) bounds.bottom = point.y;
        }
    }

    private Transform buildTransform(Facelet facelet, MoveSpec moveSpec, float moveAngleDegrees) {
        Vec3 center = facelet.center;
        Vec3 normal = facelet.normal;
        Vec3 u = facelet.u;
        Vec3 v = facelet.v;
        if (moveSpec != null && moveSpec.belongsToLayer(center)) {
            center = rotateAroundAxis(center, moveSpec.axis, moveAngleDegrees);
            normal = rotateAroundAxis(normal, moveSpec.axis, moveAngleDegrees);
            u = rotateAroundAxis(u, moveSpec.axis, moveAngleDegrees);
            v = rotateAroundAxis(v, moveSpec.axis, moveAngleDegrees);
        }
        center = applyViewRotation(center);
        normal = applyViewRotation(normal).normalize();
        u = applyViewRotation(u).normalize();
        v = applyViewRotation(v).normalize();
        return new Transform(center, normal, u, v);
    }

    private PointF[] projectQuad(Vec3 center, Vec3 u, Vec3 v, float halfSize) {
        PointF[] points = new PointF[4];
        points[0] = project(center.add(u.scale(-halfSize)).add(v.scale(-halfSize)));
        points[1] = project(center.add(u.scale(halfSize)).add(v.scale(-halfSize)));
        points[2] = project(center.add(u.scale(halfSize)).add(v.scale(halfSize)));
        points[3] = project(center.add(u.scale(-halfSize)).add(v.scale(halfSize)));
        return points;
    }

    private PointF project(Vec3 point) {
        float perspective = CAMERA_DISTANCE / (CAMERA_DISTANCE - point.z);
        return new PointF(point.x * perspective, -point.y * perspective);
    }

    private Vec3 applyViewRotation(Vec3 point) {
        Vec3 rotated = rotateAroundAxis(point, 1, YAW_DEGREES);
        return rotateAroundAxis(rotated, 0, PITCH_DEGREES);
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

    private int shadeSticker(int color, Vec3 normal) {
        float light = clamp(0.76f + normal.z * 0.16f + Math.max(0f, normal.y) * 0.08f, 0.62f, 1.08f);
        int shaded = multiplyColor(color, light);
        if (normal.y > 0.5f) {
            shaded = blend(shaded, Color.WHITE, 0.06f);
        }
        return shaded;
    }

    private int shadeBase(Vec3 normal) {
        float light = clamp(0.35f + normal.z * 0.08f + Math.max(0f, normal.y) * 0.04f, 0.24f, 0.52f);
        return multiplyColor(0xff101010, light);
    }

    private int multiplyColor(int color, float factor) {
        int alpha = Color.alpha(color);
        int red = Math.min(255, Math.max(0, Math.round(Color.red(color) * factor)));
        int green = Math.min(255, Math.max(0, Math.round(Color.green(color) * factor)));
        int blue = Math.min(255, Math.max(0, Math.round(Color.blue(color) * factor)));
        return Color.argb(alpha, red, green, blue);
    }

    private int blend(int color, int overlay, float amount) {
        float inverse = 1f - amount;
        int alpha = Math.round(Color.alpha(color) * inverse + Color.alpha(overlay) * amount);
        int red = Math.round(Color.red(color) * inverse + Color.red(overlay) * amount);
        int green = Math.round(Color.green(color) * inverse + Color.green(overlay) * amount);
        int blue = Math.round(Color.blue(color) * inverse + Color.blue(overlay) * amount);
        return Color.argb(alpha, red, green, blue);
    }

    private int applyAlpha(int color, int alpha) {
        return Color.argb(Math.round(Color.alpha(color) * (alpha / 255f)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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

    private boolean canAnimateMove(int move) {
        return move >= 0 && move < 18;
    }

    private String sanitizeFacelets(String facelets) {
        if (TextUtils.isEmpty(facelets) || facelets.length() < 54) {
            return null;
        }
        return facelets.substring(0, 54);
    }

    private void stopAnimator(boolean applyEndState) {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator.removeAllListeners();
            animator = null;
        }
        if (applyEndState && animationEndState != null) {
            cubeState = animationEndState;
        }
        animationStartState = null;
        animationEndState = null;
        animationMove = -1;
        animationProgress = 0f;
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

    private static class RenderTile {
        final PointF[] base;
        final PointF[] sticker;
        final float depth;
        final int baseColor;
        final int stickerColor;

        RenderTile(PointF[] base, PointF[] sticker, float depth, int baseColor, int stickerColor) {
            this.base = base;
            this.sticker = sticker;
            this.depth = depth;
            this.baseColor = baseColor;
            this.stickerColor = stickerColor;
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

    private static class RenderBatch {
        final List<RenderTile> tiles = new ArrayList<>(54);
        final RectF bounds = new RectF();
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
    }

    private static class MoveSpec {
        private static final int[] FACE_AXIS = {1, 0, 2, 1, 0, 2};
        private static final int[] FACE_LAYER = {1, 1, 1, -1, -1, -1};
        private static final int[] FACE_SIGN = {1, -1, -1, -1, 1, 1};

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
}
