package com.embroidermodder.embroideryviewer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.view.View;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ThumbnailView extends View {
    public static final int MIN_THUMBNAIL_SIZE = 250;

    private static final Rect rect = new Rect();

    public static Drawable fileDefault, directoryDefault;
    public Thread thread;
    public File file;
    EmbPattern pattern;
    Matrix matrix;
    Bitmap cache;

    final Paint _paint = new Paint();

    public ThumbnailView(Context context) {
        super(context);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void init() {
        int scaledSize = getResources().getDimensionPixelSize(R.dimen.thumbnailText);
        _paint.setTextSize(scaledSize);
        if (fileDefault == null) {
            fileDefault = ResourcesCompat.getDrawable(getResources(), R.mipmap.ic_launcher, null);
            if (fileDefault != null) {
                fileDefault.setBounds(0, 0, MIN_THUMBNAIL_SIZE, MIN_THUMBNAIL_SIZE);
            }
        }
        if (directoryDefault == null) {
            directoryDefault = ResourcesCompat.getDrawable(getResources(), R.mipmap.ic_launcher, null);
            if (directoryDefault != null) {
                directoryDefault.setBounds(0, 0, MIN_THUMBNAIL_SIZE, MIN_THUMBNAIL_SIZE);
            }
        }
    }

    public void createFromPattern(EmbPattern pattern) {
        if (pattern == null) return;
        this.pattern = pattern;
        if (pattern.getStitchBlocks().isEmpty()) {
            return;
        }
        float _width = getWidth();
        float _height = getHeight();
        if (_width == 0) _width = MIN_THUMBNAIL_SIZE;
        if (_height == 0) _height = MIN_THUMBNAIL_SIZE;

        RectF viewPort = pattern.calculateBoundingBox();
        float scale = Math.min(_height / viewPort.height(), _width / viewPort.width());
        matrix = new Matrix();
        if (scale != 0) {
            matrix.postTranslate(-viewPort.left, -viewPort.top);
            matrix.postScale(scale, scale);
        }

        cache = Bitmap.createBitmap((int) _width, (int) _height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cache);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        if (matrix != null) canvas.concat(matrix);
        for (StitchBlock stitchBlock : pattern.getStitchBlocks()) {
            stitchBlock.draw(canvas, _paint);
        }
        _paint.setColor(Color.BLACK);
        processInvalidation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cache != null) {
            canvas.drawBitmap(cache, 0, 0, _paint);
        } else {
            canvas.save();
            canvas.getClipBounds(rect);
            canvas.translate(rect.left,rect.top);
            if (file == null || file.isDirectory()) {
                canvas.drawColor(Color.BLUE);
                directoryDefault.draw(canvas);
            } else {
                fileDefault.draw(canvas);
            }
            canvas.restore();
        }
        if (file != null) {
            canvas.drawText(file.getName(), 0, getHeight(), _paint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);

        int size = Math.min(w, h);
        size = Math.max(size, MIN_THUMBNAIL_SIZE);
        setMeasuredDimension(size, size);
    }


    public void processInvalidation() {
        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            invalidate();
        } else {
            Activity activity = (Activity) (getContext());
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    processInvalidation();
                }
            });
        }
    }

    public void clear() {
        this.pattern = null;
        this.matrix = null;
        if (thread != null) {
            thread.interrupt();
        }
        if (cache != null) {
            cache.recycle();
            cache = null;
        }
        thread = null;
        file = null;
    }

    public void load(final File file) {
        this.file = file;
        if (file.isDirectory()) return;

        final IFormat.Reader reader = IFormat.getReaderByFilename(file.getPath());
        if (reader == null) return;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(file);
                    DataInputStream in = new DataInputStream(fis);
                    pattern = reader.read(in);
                    createFromPattern(pattern);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (fis != null) fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
    }
}