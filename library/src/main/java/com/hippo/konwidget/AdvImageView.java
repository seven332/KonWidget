/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.konwidget;

/*
 * Created by Hippo on 8/21/2016.
 */

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.content.res.AppCompatResources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

// android-7.0.0_r1

/**
 * A fork of {@link android.widget.ImageView}.
 * Keep with latest ImageView source code.
 * <p>
 * Support {@link android.support.graphics.drawable.VectorDrawableCompat}
 * and {@link android.support.graphics.drawable.AnimatedVectorDrawableCompat}
 * in {@link #setImageResource(int)}.
 * <p>
 * Add {@link #getAspectRatio()} and {@link #setAspectRatio(float)}.
 */
public class AdvImageView extends View {

    private static final String LOG_TAG = AdvImageView.class.getSimpleName();

    @IntDef({SCALE_TYPE_MATRIX, SCALE_TYPE_FIT_XY, SCALE_TYPE_FIT_START,
            SCALE_TYPE_FIT_CENTER, SCALE_TYPE_FIT_END, SCALE_TYPE_CENTER,
            SCALE_TYPE_CENTER_CROP, SCALE_TYPE_CENTER_INSIDE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScaleType {}

    public static final int SCALE_TYPE_MATRIX = 0;
    public static final int SCALE_TYPE_FIT_XY = 1;
    public static final int SCALE_TYPE_FIT_START = 2;
    public static final int SCALE_TYPE_FIT_CENTER = 3;
    public static final int SCALE_TYPE_FIT_END = 4;
    public static final int SCALE_TYPE_CENTER = 5;
    public static final int SCALE_TYPE_CENTER_CROP = 6;
    public static final int SCALE_TYPE_CENTER_INSIDE = 7;

    public static final float INVALID_ASPECT_RATIO = -1.0f;
    public static final float ASPECT_RATIO_OF_DRAWABLE = 0.0f;

    private Context mContext;

    // settable by the client
    private Uri mUri;
    private int mResource = 0;
    private Matrix mMatrix;
    @ScaleType
    private int mScaleType;
    private boolean mHaveFrame = false;
    private float mAspectRatio = INVALID_ASPECT_RATIO;
    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMaxHeight = Integer.MAX_VALUE;
    private int mMinWidth;
    private int mMinHeight;

    // these are applied to the drawable
    private ColorFilter mColorFilter = null;
    private boolean mHasColorFilter = false;
    private int mAlpha = 255;
    private final int mViewAlphaScale = 256;
    private boolean mColorMod = false;

    private Drawable mDrawable = null;
    private ColorStateList mDrawableTintList = null;
    private PorterDuff.Mode mDrawableTintMode = null;
    private boolean mHasDrawableTint = false;
    private boolean mHasDrawableTintMode = false;

    private int[] mState = null;
    private boolean mMergeState = false;
    private int mLevel = 0;
    private int mDrawableWidth;
    private int mDrawableHeight;
    private Matrix mDrawMatrix = null;

    // Avoid allocations...
    private final RectF mTempSrc = new RectF();
    private final RectF mTempDst = new RectF();

    private boolean mCropToPadding;

    private int mBaseline = -1;
    private boolean mBaselineAlignBottom = false;

    private static TypedValue sTempValue;

    public AdvImageView(Context context) {
        super(context);
        mContext = context;
        mMatrix = new Matrix();
        mScaleType = SCALE_TYPE_FIT_CENTER;
    }

    public AdvImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public AdvImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        mMatrix = new Matrix();
        mScaleType = SCALE_TYPE_FIT_CENTER;

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.AdvImageView, defStyleAttr, defStyleRes);

        final Drawable d = a.getDrawable(R.styleable.AdvImageView_kon_src);
        if (d != null) {
            setImageDrawable(d);
        }

        mBaselineAlignBottom = a.getBoolean(R.styleable.AdvImageView_kon_baselineAlignBottom, false);

        mBaseline = a.getDimensionPixelSize(R.styleable.AdvImageView_kon_baseline, -1);

        setAspectRatio(a.getFloat(R.styleable.AdvImageView_kon_aspectRatio, INVALID_ASPECT_RATIO));

        setMaxWidth(a.getDimensionPixelSize(R.styleable.AdvImageView_kon_maxWidth, Integer.MAX_VALUE));

        setMaxHeight(a.getDimensionPixelSize(R.styleable.AdvImageView_kon_maxHeight, Integer.MAX_VALUE));

        //noinspection WrongConstant
        setScaleType(a.getInt(R.styleable.AdvImageView_kon_scaleType, SCALE_TYPE_FIT_CENTER));

        if (a.hasValue(R.styleable.AdvImageView_kon_tint)) {
            mDrawableTintList = a.getColorStateList(R.styleable.AdvImageView_kon_tint);
            mHasDrawableTint = true;

            // Prior to L, this attribute would always set a color filter with
            // blending mode SRC_ATOP. Preserve that default behavior.
            mDrawableTintMode = PorterDuff.Mode.SRC_ATOP;
            mHasDrawableTintMode = true;
        }

        if (a.hasValue(R.styleable.AdvImageView_kon_tintMode)) {
            mDrawableTintMode = Utils.parseTintMode(a.getInt(
                    R.styleable.AdvImageView_kon_tintMode, -1), mDrawableTintMode);
            mHasDrawableTintMode = true;
        }

        applyImageTint();

        final int alpha = a.getInt(R.styleable.AdvImageView_kon_drawableAlpha, 255);
        if (alpha != 255) {
            setImageAlpha(alpha);
        }

        mCropToPadding = a.getBoolean(R.styleable.AdvImageView_kon_cropToPadding, false);

        a.recycle();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable dr) {
        return mDrawable == dr || super.verifyDrawable(dr);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mDrawable != null) DrawableCompat.jumpToCurrentState(mDrawable);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable dr) {
        if (dr == mDrawable) {
            if (dr != null) {
                // update cached drawable dimensions if they've changed
                final int w = dr.getIntrinsicWidth();
                final int h = dr.getIntrinsicHeight();
                if (w != mDrawableWidth || h != mDrawableHeight) {
                    mDrawableWidth = w;
                    mDrawableHeight = h;
                    // updates the matrix, which is dependent on the bounds
                    configureBounds();
                }
            }
            /* we invalidate the whole view in this case because it's very
             * hard to know where the drawable actually is. This is made
             * complicated because of the offsets and transformations that
             * can be applied. In theory we could get the drawable's bounds
             * and run them through the transformation and offsets, but this
             * is probably not worth the effort.
             */
            invalidate();
        } else {
            super.invalidateDrawable(dr);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        final Drawable background = getBackground();
        return (background != null && background.getCurrent() != null);
    }

    /**
     * Return true if aspect ratio was set.
     */
    public boolean isAdjustViewBounds() {
        return mAspectRatio != INVALID_ASPECT_RATIO;
    }

    /**
     * Return aspect ratio.
     *
     * @return {@link #INVALID_ASPECT_RATIO} for no aspect ratio set.
     * {@link #ASPECT_RATIO_OF_DRAWABLE} for fit aspect ratio of drawable.
     */
    public float getAspectRatio() {
        return mAspectRatio;
    }

    /**
     * Set aspect ratio, width/height. {@link #INVALID_ASPECT_RATIO} to
     * cancel aspect ratio. {@link #ASPECT_RATIO_OF_DRAWABLE} to
     * fit aspect ratio of drawable.
     */
    public void setAspectRatio(float aspectRatio) {
        if (!(aspectRatio >= ASPECT_RATIO_OF_DRAWABLE)) {
            aspectRatio = INVALID_ASPECT_RATIO;
        }

        if (mAspectRatio != aspectRatio) {
            mAspectRatio = aspectRatio;
            requestLayout();
            invalidate();
        }
    }

    /**
     * The maximum width of this view.
     *
     * @return The maximum width of this view
     *
     * @see #setMaxWidth(int)
     */
    public int getMaxWidth() {
        return mMaxWidth;
    }

    /**
     * An optional argument to supply a maximum width for this view. Only valid if
     * aspect ratio is set. To set an image to be a maximum
     * of 100 x 100 while preserving the original aspect ratio, do the following: 1) set
     * adjustViewBounds to true 2) set maxWidth and maxHeight to 100 3) set the height and width
     * layout params to WRAP_CONTENT.
     *
     * <p>
     * Note that this view could be still smaller than 100 x 100 using this approach if the original
     * image is small. To set an image to a fixed size, specify that size in the layout params and
     * then use {@link #setScaleType(int)} to determine how to fit
     * the image within the bounds.
     * </p>
     *
     * @param maxWidth maximum width for this view
     *
     * @see #getMaxWidth()
     */
    public void setMaxWidth(int maxWidth) {
        mMaxWidth = maxWidth;
    }

    /**
     * The maximum height of this view.
     *
     * @return The maximum height of this view
     *
     * @see #setMaxHeight(int)
     */
    public int getMaxHeight() {
        return mMaxHeight;
    }

    /**
     * An optional argument to supply a maximum height for this view. Only valid if
     * aspect ratio is set. To set an image to be a
     * maximum of 100 x 100 while preserving the original aspect ratio, do the following: 1) set
     * adjustViewBounds to true 2) set maxWidth and maxHeight to 100 3) set the height and width
     * layout params to WRAP_CONTENT.
     *
     * @param maxHeight maximum height for this view
     *
     * @see #getMaxHeight()
     */
    public void setMaxHeight(int maxHeight) {
        mMaxHeight = maxHeight;
    }

    @Override
    public void setMinimumWidth(int minWidth) {
        super.setMinimumWidth(minWidth);
        mMinWidth = minWidth;
    }

    @Override
    public void setMinimumHeight(int minHeight) {
        super.setMinimumHeight(minHeight);
        mMinHeight = minHeight;
    }

    /** Return the view's drawable, or null if no drawable has been assigned.
     */
    public Drawable getDrawable() {
        return mDrawable;
    }

    /**
     * Sets a drawable as the content of this AdvImageView.
     *
     * <p class="note">This does Bitmap reading and decoding on the UI
     * thread, which can cause a latency hiccup.  If that's a concern,
     * consider using {@link #setImageDrawable(android.graphics.drawable.Drawable)} or
     * {@link #setImageBitmap(android.graphics.Bitmap)} and
     * {@link android.graphics.BitmapFactory} instead.</p>
     *
     * @param resId the resource identifier of the drawable
     */
    public void setImageResource(@DrawableRes int resId) {
        // The resource configuration may have changed, so we should always
        // try to load the resource even if the resId hasn't changed.
        final int oldWidth = mDrawableWidth;
        final int oldHeight = mDrawableHeight;

        updateDrawable(null);
        mResource = resId;
        mUri = null;

        resolveUri();

        if (oldWidth != mDrawableWidth || oldHeight != mDrawableHeight) {
            requestLayout();
        }
        invalidate();
    }

    /**
     * Sets the content of this AdvImageView to the specified Uri.
     *
     * <p class="note">This does Bitmap reading and decoding on the UI
     * thread, which can cause a latency hiccup.  If that's a concern,
     * consider using {@link #setImageDrawable(Drawable)} or
     * {@link #setImageBitmap(android.graphics.Bitmap)} and
     * {@link android.graphics.BitmapFactory} instead.</p>
     *
     * @param uri the Uri of an image, or {@code null} to clear the content
     */
    public void setImageURI(@Nullable Uri uri) {
        if (mResource != 0 || (mUri != uri && (uri == null || mUri == null || !uri.equals(mUri)))) {
            updateDrawable(null);
            mResource = 0;
            mUri = uri;

            final int oldWidth = mDrawableWidth;
            final int oldHeight = mDrawableHeight;

            resolveUri();

            if (oldWidth != mDrawableWidth || oldHeight != mDrawableHeight) {
                requestLayout();
            }
            invalidate();
        }
    }

    /**
     * Sets a drawable as the content of this AdvImageView.
     *
     * @param drawable the Drawable to set, or {@code null} to clear the
     *                 content
     */
    public void setImageDrawable(@Nullable Drawable drawable) {
        if (mDrawable != drawable) {
            mResource = 0;
            mUri = null;

            final int oldWidth = mDrawableWidth;
            final int oldHeight = mDrawableHeight;

            updateDrawable(drawable);

            if (oldWidth != mDrawableWidth || oldHeight != mDrawableHeight) {
                requestLayout();
            }
            invalidate();
        }
    }

    /**
     * Applies a tint to the image drawable. Does not modify the current tint
     * mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
     * <p>
     * Subsequent calls to {@link #setImageDrawable(Drawable)} will automatically
     * mutate the drawable and apply the specified tint and tint mode using
     * {@link Drawable#setTintList(ColorStateList)}.
     *
     * @param tint the tint to apply, may be {@code null} to clear tint
     *
     * @see #getImageTintList()
     * @see Drawable#setTintList(ColorStateList)
     */
    public void setImageTintList(@Nullable ColorStateList tint) {
        mDrawableTintList = tint;
        mHasDrawableTint = true;

        applyImageTint();
    }

    /**
     * @return the tint applied to the image drawable
     *
     * @see #setImageTintList(ColorStateList)
     */
    @Nullable
    public ColorStateList getImageTintList() {
        return mDrawableTintList;
    }

    /**
     * Specifies the blending mode used to apply the tint specified by
     * {@link #setImageTintList(ColorStateList)}} to the image drawable. The default
     * mode is {@link PorterDuff.Mode#SRC_IN}.
     *
     * @param tintMode the blending mode used to apply the tint, may be
     *                 {@code null} to clear tint
     *
     * @see #getImageTintMode()
     * @see Drawable#setTintMode(PorterDuff.Mode)
     */
    public void setImageTintMode(@Nullable PorterDuff.Mode tintMode) {
        mDrawableTintMode = tintMode;
        mHasDrawableTintMode = true;

        applyImageTint();
    }

    /**
     * @return the blending mode used to apply the tint to the image drawable
     *
     * @see #setImageTintMode(PorterDuff.Mode)
     */
    @Nullable
    public PorterDuff.Mode getImageTintMode() {
        return mDrawableTintMode;
    }

    private void applyImageTint() {
        if (mDrawable != null && (mHasDrawableTint || mHasDrawableTintMode)) {
            mDrawable = mDrawable.mutate();

            if (mHasDrawableTint) {
                DrawableCompat.setTintList(mDrawable, mDrawableTintList);
            }

            if (mHasDrawableTintMode) {
                DrawableCompat.setTintMode(mDrawable, mDrawableTintMode);
            }

            // The drawable (or one of its children) may not have been
            // stateful before applying the tint, so let's try again.
            if (mDrawable.isStateful()) {
                mDrawable.setState(getDrawableState());
            }
        }
    }

    /**
     * Sets a Bitmap as the content of this AdvImageView.
     *
     * @param bm The bitmap to set
     */
    public void setImageBitmap(Bitmap bm) {
        // if this is used frequently, may handle bitmaps explicitly
        // to reduce the intermediate drawable object
        setImageDrawable(new BitmapDrawable(mContext.getResources(), bm));
    }

    public void setImageState(int[] state, boolean merge) {
        mState = state;
        mMergeState = merge;
        if (mDrawable != null) {
            refreshDrawableState();
            resizeFromDrawable();
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        resizeFromDrawable();
    }

    /**
     * Sets the image level, when it is constructed from a
     * {@link android.graphics.drawable.LevelListDrawable}.
     *
     * @param level The new level for the image.
     */
    public void setImageLevel(int level) {
        mLevel = level;
        if (mDrawable != null) {
            mDrawable.setLevel(level);
            resizeFromDrawable();
        }
    }

    /**
     * Controls how the image should be resized or moved to match the size
     * of this AdvImageView.
     *
     * @param scaleType The desired scaling mode.
     */
    public void setScaleType(@ScaleType int scaleType) {
        if (mScaleType != scaleType) {
            mScaleType = scaleType;

            setWillNotCacheDrawing(mScaleType == SCALE_TYPE_CENTER);

            requestLayout();
            invalidate();
        }
    }

    /**
     * Return the current scale type in use by this AdvImageView.
     */
    @ScaleType
    public int getScaleType() {
        return mScaleType;
    }

    /** Return the view's optional matrix. This is applied to the
     view's drawable when it is drawn. If there is no matrix,
     this method will return an identity matrix.
     Do not change this matrix in place but make a copy.
     If you want a different matrix applied to the drawable,
     be sure to call setImageMatrix().
     */
    public Matrix getImageMatrix() {
        if (mDrawMatrix == null) {
            return new Matrix(IdentityMatrix.INSTANCE);
        }
        return mDrawMatrix;
    }

    /**
     * Adds a transformation {@link Matrix} that is applied
     * to the view's drawable when it is drawn.  Allows custom scaling,
     * translation, and perspective distortion.
     *
     * @param matrix the transformation parameters in matrix form
     */
    public void setImageMatrix(Matrix matrix) {
        // collapse null and identity to just null
        if (matrix != null && matrix.isIdentity()) {
            matrix = null;
        }

        // don't invalidate unless we're actually changing our matrix
        if (matrix == null && !mMatrix.isIdentity() ||
                matrix != null && !mMatrix.equals(matrix)) {
            mMatrix.set(matrix);
            configureBounds();
            invalidate();
        }
    }

    /**
     * Return whether this AdvImageView crops to padding.
     *
     * @return whether this AdvImageView crops to padding
     *
     * @see #setCropToPadding(boolean)
     */
    public boolean getCropToPadding() {
        return mCropToPadding;
    }

    /**
     * Sets whether this AdvImageView will crop to padding.
     *
     * @param cropToPadding whether this AdvImageView will crop to padding
     *
     * @see #getCropToPadding()
     */
    public void setCropToPadding(boolean cropToPadding) {
        if (mCropToPadding != cropToPadding) {
            mCropToPadding = cropToPadding;
            requestLayout();
            invalidate();
        }
    }

    private void resolveUri() {
        if (mDrawable != null) {
            return;
        }

        if (getResources() == null) {
            return;
        }

        Drawable d = null;

        if (mResource != 0) {
            try {
                d = AppCompatResources.getDrawable(mContext, mResource);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Unable to find resource: " + mResource, e);
                // Don't try again.
                mUri = null;
            }
        } else if (mUri != null) {
            d = getDrawableFromUri(mUri);

            if (d == null) {
                Log.w(LOG_TAG, "resolveUri failed on bad bitmap uri: " + mUri);
                // Don't try again.
                mUri = null;
            }
        } else {
            return;
        }

        updateDrawable(d);
    }

    private Drawable getDrawableFromUri(Uri uri) {
        final String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
            try {
                // Load drawable through Resources, to get the source density information
                final OpenResourceIdResult r = getResourceId(uri);
                return getDrawableFromId(r.r, r.id, mContext.getTheme());
            } catch (Exception e) {
                Log.w(LOG_TAG, "Unable to open content: " + uri, e);
            }
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme)) {
            InputStream stream = null;
            try {
                stream = mContext.getContentResolver().openInputStream(uri);
                return Drawable.createFromResourceStream(getResources(), null, stream, null);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Unable to open content: " + uri, e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        Log.w(LOG_TAG, "Unable to close content: " + uri, e);
                    }
                }
            }
        } else {
            return Drawable.createFromPath(uri.toString());
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Drawable getDrawableFromId(Resources resources, @DrawableRes int id, Resources.Theme theme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return resources.getDrawable(id, theme);
        } else if (Build.VERSION.SDK_INT >= 16) {
            return resources.getDrawable(id);
        } else {
            // Prior to JELLY_BEAN, Resources.getDrawable() would not correctly
            // retrieve the final configuration density when the resource ID
            // is a reference another Drawable resource. As a workaround, try
            // to resolve the drawable reference manually.
            if (sTempValue == null) {
                sTempValue = new TypedValue();
            }
            resources.getValue(id, sTempValue, true);
            final int resolvedId = sTempValue.resourceId;
            return resources.getDrawable(resolvedId);
        }
    }

    /**
     * A resource identified by the {@link Resources} that contains it, and a resource id.
     */
    private class OpenResourceIdResult {
        public Resources r;
        public int id;
    }

    /**
     * Resolves an android.resource URI to a {@link Resources} and a resource id.
     */
    private OpenResourceIdResult getResourceId(Uri uri) throws FileNotFoundException {
        final String authority = uri.getAuthority();
        final Resources r;
        if (TextUtils.isEmpty(authority)) {
            throw new FileNotFoundException("No authority: " + uri);
        } else {
            try {
                r = mContext.getPackageManager().getResourcesForApplication(authority);
            } catch (PackageManager.NameNotFoundException ex) {
                throw new FileNotFoundException("No package found for authority: " + uri);
            }
        }
        final List<String> path = uri.getPathSegments();
        if (path == null) {
            throw new FileNotFoundException("No path: " + uri);
        }
        final int len = path.size();
        final int id;
        if (len == 1) {
            try {
                id = Integer.parseInt(path.get(0));
            } catch (NumberFormatException e) {
                throw new FileNotFoundException("Single path segment is not a resource ID: " + uri);
            }
        } else if (len == 2) {
            id = r.getIdentifier(path.get(1), path.get(0), authority);
        } else {
            throw new FileNotFoundException("More than two path segments: " + uri);
        }
        if (id == 0) {
            throw new FileNotFoundException("No resource found for: " + uri);
        }
        final OpenResourceIdResult res = new OpenResourceIdResult();
        res.r = r;
        res.id = id;
        return res;
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        if (mState == null) {
            return super.onCreateDrawableState(extraSpace);
        } else if (!mMergeState) {
            return mState;
        } else {
            return mergeDrawableStates(
                    super.onCreateDrawableState(extraSpace + mState.length), mState);
        }
    }

    private void updateDrawable(Drawable d) {
        if (mDrawable != null) {
            mDrawable.setCallback(null);
            unscheduleDrawable(mDrawable);
            if (ViewCompat.isAttachedToWindow(this)) {
                mDrawable.setVisible(false, false);
            }
        }

        mDrawable = d;

        if (d != null) {
            d.setCallback(this);
            DrawableCompat.setLayoutDirection(d, ViewCompat.getLayoutDirection(this));
            if (d.isStateful()) {
                d.setState(getDrawableState());
            }
            if (ViewCompat.isAttachedToWindow(this)) {
                d.setVisible(getVisibility() == VISIBLE, true);
            }
            d.setLevel(mLevel);
            mDrawableWidth = d.getIntrinsicWidth();
            mDrawableHeight = d.getIntrinsicHeight();
            applyImageTint();
            applyColorMod();

            configureBounds();
        } else {
            mDrawableWidth = mDrawableHeight = -1;
        }
    }

    private void resizeFromDrawable() {
        final Drawable d = mDrawable;
        if (d != null) {
            int w = d.getIntrinsicWidth();
            if (w < 0) w = mDrawableWidth;
            int h = d.getIntrinsicHeight();
            if (h < 0) h = mDrawableHeight;
            if (w != mDrawableWidth || h != mDrawableHeight) {
                mDrawableWidth = w;
                mDrawableHeight = h;
                requestLayout();
            }
        }
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mDrawable != null) {
            DrawableCompat.setLayoutDirection(mDrawable, layoutDirection);
        }
    }

    private static final Matrix.ScaleToFit[] sS2FArray = {
            Matrix.ScaleToFit.FILL,
            Matrix.ScaleToFit.START,
            Matrix.ScaleToFit.CENTER,
            Matrix.ScaleToFit.END
    };

    private static Matrix.ScaleToFit scaleTypeToScaleToFit(@ScaleType int st)  {
        // ScaleToFit enum to their corresponding Matrix.ScaleToFit values
        return sS2FArray[st - 1];
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        resolveUri();
        int w;
        int h;

        // Desired aspect ratio of the view's contents (not including padding)
        float desiredAspect = INVALID_ASPECT_RATIO;

        // We are allowed to change the view's width
        boolean resizeWidth = false;

        // We are allowed to change the view's height
        boolean resizeHeight = false;

        final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);

        if (mDrawable == null) {
            // If no drawable, its intrinsic size is 0.
            mDrawableWidth = -1;
            mDrawableHeight = -1;
            w = h = 0;

            // We are supposed to adjust view bounds to match the aspect
            // ratio. See if that is possible.
            if (mAspectRatio > ASPECT_RATIO_OF_DRAWABLE) {
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
                desiredAspect = mAspectRatio;
            }
        } else {
            w = mDrawableWidth;
            h = mDrawableHeight;
            if (w <= 0) w = 1;
            if (h <= 0) h = 1;

            // We are supposed to adjust view bounds to match the aspect
            // ratio. See if that is possible.
            if (mAspectRatio >= ASPECT_RATIO_OF_DRAWABLE) {
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
                desiredAspect = mAspectRatio > ASPECT_RATIO_OF_DRAWABLE ? mAspectRatio : (float) w / (float) h;
            }
        }

        final int pLeft = getPaddingLeft();
        final int pRight = getPaddingRight();
        final int pTop = getPaddingTop();
        final int pBottom = getPaddingBottom();

        int widthSize;
        int heightSize;

        if (resizeWidth || resizeHeight) {
            /* If we get here, it means we want to resize to match the
                drawables aspect ratio, and we have the freedom to change at
                least one dimension.
            */

            // Get the max possible width given our constraints
            widthSize = resolveAdjustedSize(w + pLeft + pRight, mMinWidth, mMaxWidth, widthMeasureSpec);

            // Get the max possible height given our constraints
            heightSize = resolveAdjustedSize(h + pTop + pBottom, mMinHeight, mMaxHeight, heightMeasureSpec);

            if (desiredAspect != INVALID_ASPECT_RATIO) {
                // See what our actual aspect ratio is
                final float actualAspect = (float)(widthSize - pLeft - pRight) /
                        (heightSize - pTop - pBottom);

                if (Math.abs(actualAspect - desiredAspect) > 0.0000001) {

                    boolean done = false;

                    // Try adjusting width to be proportional to height
                    if (resizeWidth) {
                        final int newWidth = (int)(desiredAspect * (heightSize - pTop - pBottom)) +
                                pLeft + pRight;

                        if (isSizeAcceptable(newWidth, mMinWidth, mMaxWidth, widthMeasureSpec)) {
                            widthSize = newWidth;
                            done = true;
                        }
                    }

                    // Try adjusting height to be proportional to width
                    if (!done && resizeHeight) {
                        final int newHeight = (int)((widthSize - pLeft - pRight) / desiredAspect) +
                                pTop + pBottom;

                        if (isSizeAcceptable(newHeight, mMinHeight, mMaxHeight, heightMeasureSpec)) {
                            heightSize = newHeight;
                        }
                    }
                }
            }
        } else {
            /* We are either don't want to preserve the drawables aspect ratio,
               or we are not allowed to change view dimensions. Just measure in
               the normal way.
            */
            w += pLeft + pRight;
            h += pTop + pBottom;

            w = Math.max(w, getSuggestedMinimumWidth());
            h = Math.max(h, getSuggestedMinimumHeight());

            widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
            heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    private int resolveAdjustedSize(int desiredSize, int minSize,
            int maxSize, int measureSpec) {
        int result = desiredSize;
        final int specMode = MeasureSpec.getMode(measureSpec);
        final int specSize =  MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                /* Parent says we can be as big as we want. Just don't be larger
                   than max size imposed on ourselves.
                */
                result = Math.max(Math.min(desiredSize, maxSize), minSize);
                break;
            case MeasureSpec.AT_MOST:
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be larger than
                // the max size imposed on ourselves.
                result = Math.max(Math.min(Math.min(desiredSize, specSize), maxSize), minSize);
                break;
            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
    }

    private static boolean isSizeAcceptable(int size, int minSize, int maxSize, int measureSpec) {
        final int specMode = MeasureSpec.getMode(measureSpec);
        final int specSize =  MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                // Parent says we can be as big as we want. Just don't be smaller
                // than min size, and don't be larger than max size.
                return size >= minSize && size <= maxSize;
            case MeasureSpec.AT_MOST:
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be smaller
                // than min size, and don't be larger than max size.
                return size <= specSize && size >= minSize && size <= maxSize;
            case MeasureSpec.EXACTLY:
                // No choice.
                return size == specSize;
            default:
                // WTF? Return true to make you happy. (´・ω・`)
                return true;
        }
    }

    public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        final int specMode = MeasureSpec.getMode(measureSpec);
        final int specSize = MeasureSpec.getSize(measureSpec);
        final int result;
        switch (specMode) {
            case MeasureSpec.AT_MOST:
                if (specSize < size) {
                    result = specSize | MEASURED_STATE_TOO_SMALL;
                } else {
                    result = size;
                }
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                result = size;
        }
        return result | (childMeasuredState & MEASURED_STATE_MASK);
    }

    // setFrame is not public API, so use layout instead of setFrame
    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mHaveFrame = true;
        configureBounds();
    }

    private void configureBounds() {
        if (mDrawable == null || !mHaveFrame) {
            return;
        }

        final int dWidth = mDrawableWidth;
        final int dHeight = mDrawableHeight;

        final int vWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        final int vHeight = getHeight() - getPaddingTop() - getPaddingBottom();

        final boolean fits = (dWidth < 0 || vWidth == dWidth) &&
                (dHeight < 0 || vHeight == dHeight);

        if (dWidth <= 0 || dHeight <= 0 || mScaleType == SCALE_TYPE_FIT_XY) {
            /* If the drawable has no intrinsic size, or we're told to
                scale to fit, then we just fill our entire view.
            */
            mDrawable.setBounds(0, 0, vWidth, vHeight);
            mDrawMatrix = null;
        } else {
            // We need to do the scaling ourselves, so have the drawable
            // use its native size.
            mDrawable.setBounds(0, 0, dWidth, dHeight);

            if (mScaleType == SCALE_TYPE_MATRIX) {
                // Use the specified matrix as-is.
                if (mMatrix.isIdentity()) {
                    mDrawMatrix = null;
                } else {
                    mDrawMatrix = mMatrix;
                }
            } else if (fits) {
                // The bitmap fits exactly, no transform needed.
                mDrawMatrix = null;
            } else if (mScaleType == SCALE_TYPE_CENTER) {
                // Center bitmap in view, no scaling.
                mDrawMatrix = mMatrix;
                mDrawMatrix.setTranslate(Math.round((vWidth - dWidth) * 0.5f),
                        Math.round((vHeight - dHeight) * 0.5f));
            } else if (mScaleType == SCALE_TYPE_CENTER_CROP) {
                mDrawMatrix = mMatrix;

                final float scale;
                float dx = 0, dy = 0;

                if (dWidth * vHeight > vWidth * dHeight) {
                    scale = (float) vHeight / (float) dHeight;
                    dx = (vWidth - dWidth * scale) * 0.5f;
                } else {
                    scale = (float) vWidth / (float) dWidth;
                    dy = (vHeight - dHeight * scale) * 0.5f;
                }

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(Math.round(dx), Math.round(dy));
            } else if (mScaleType == SCALE_TYPE_CENTER_INSIDE) {
                mDrawMatrix = mMatrix;
                final float scale;
                final float dx;
                final float dy;

                if (dWidth <= vWidth && dHeight <= vHeight) {
                    scale = 1.0f;
                } else {
                    scale = Math.min((float) vWidth / (float) dWidth,
                            (float) vHeight / (float) dHeight);
                }

                dx = Math.round((vWidth - dWidth * scale) * 0.5f);
                dy = Math.round((vHeight - dHeight * scale) * 0.5f);

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
            } else {
                // Generate the required transform.
                mTempSrc.set(0, 0, dWidth, dHeight);
                mTempDst.set(0, 0, vWidth, vHeight);

                mDrawMatrix = mMatrix;
                mDrawMatrix.setRectToRect(mTempSrc, mTempDst, scaleTypeToScaleToFit(mScaleType));
            }
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable drawable = mDrawable;
        if (drawable != null && drawable.isStateful()
                && drawable.setState(getDrawableState())) {
            invalidateDrawable(drawable);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (mDrawable != null) {
            DrawableCompat.setHotspot(mDrawable, x, y);
        }
    }

    public void animateTransform(Matrix matrix) {
        if (mDrawable == null) {
            return;
        }
        if (matrix == null) {
            mDrawable.setBounds(0, 0, getWidth(), getHeight());
        } else {
            mDrawable.setBounds(0, 0, mDrawableWidth, mDrawableHeight);
            if (mDrawMatrix == null) {
                mDrawMatrix = new Matrix();
            }
            mDrawMatrix.set(matrix);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mDrawable == null) {
            return; // couldn't resolve the URI
        }

        if (mDrawableWidth == 0 || mDrawableHeight == 0) {
            return;     // nothing to draw (empty bounds)
        }

        final int pLeft = getPaddingLeft();
        final int pTop = getPaddingTop();

        if (mDrawMatrix == null && pTop == 0 && pLeft == 0) {
            mDrawable.draw(canvas);
        } else {
            final int saveCount = canvas.getSaveCount();
            canvas.save();

            if (mCropToPadding) {
                final int scrollX = getScrollX();
                final int scrollY = getScrollY();
                canvas.clipRect(scrollX + pLeft, scrollY + pTop,
                        scrollX + getWidth() - getPaddingRight(),
                        scrollY + getHeight() - getPaddingBottom());
            }

            canvas.translate(pLeft, pTop);

            if (mDrawMatrix != null) {
                canvas.concat(mDrawMatrix);
            }
            mDrawable.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    /**
     * <p>Return the offset of the widget's text baseline from the widget's top
     * boundary. </p>
     *
     * @return the offset of the baseline within the widget's bounds or -1
     *         if baseline alignment is not supported.
     */
    @Override
    public int getBaseline() {
        if (mBaselineAlignBottom) {
            return getMeasuredHeight();
        } else {
            return mBaseline;
        }
    }

    /**
     * <p>Set the offset of the widget's text baseline from the widget's top
     * boundary.  This value is overridden by the {@link #setBaselineAlignBottom(boolean)}
     * property.</p>
     *
     * @param baseline The baseline to use, or -1 if none is to be provided.
     *
     * @see #setBaseline(int)
     */
    public void setBaseline(int baseline) {
        if (mBaseline != baseline) {
            mBaseline = baseline;
            requestLayout();
        }
    }

    /**
     * Set whether to set the baseline of this view to the bottom of the view.
     * Setting this value overrides any calls to setBaseline.
     *
     * @param aligned If true, the image view will be baseline aligned with
     *      based on its bottom edge.
     */
    public void setBaselineAlignBottom(boolean aligned) {
        if (mBaselineAlignBottom != aligned) {
            mBaselineAlignBottom = aligned;
            requestLayout();
        }
    }

    /**
     * Return whether this view's baseline will be considered the bottom of the view.
     *
     * @see #setBaselineAlignBottom(boolean)
     */
    public boolean getBaselineAlignBottom() {
        return mBaselineAlignBottom;
    }

    /**
     * Set a tinting option for the image.
     *
     * @param color Color tint to apply.
     * @param mode How to apply the color.  The standard mode is
     * {@link PorterDuff.Mode#SRC_ATOP}
     */
    public final void setColorFilter(int color, PorterDuff.Mode mode) {
        setColorFilter(new PorterDuffColorFilter(color, mode));
    }

    /**
     * Set a tinting option for the image. Assumes
     * {@link PorterDuff.Mode#SRC_ATOP} blending mode.
     *
     * @param color Color tint to apply.
     */
    public final void setColorFilter(int color) {
        setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    public final void clearColorFilter() {
        setColorFilter(null);
    }

    /**
     * Returns the active color filter for this AdvImageView.
     *
     * @return the active color filter for this AdvImageView
     *
     * @see #setColorFilter(android.graphics.ColorFilter)
     */
    public ColorFilter getColorFilter() {
        return mColorFilter;
    }

    /**
     * Apply an arbitrary colorfilter to the image.
     *
     * @param cf the colorfilter to apply (may be null)
     *
     * @see #getColorFilter()
     */
    public void setColorFilter(ColorFilter cf) {
        if (mColorFilter != cf) {
            mColorFilter = cf;
            mHasColorFilter = true;
            mColorMod = true;
            applyColorMod();
            invalidate();
        }
    }

    /**
     * Returns the alpha that will be applied to the drawable of this AdvImageView.
     *
     * @return the alpha that will be applied to the drawable of this AdvImageView
     *
     * @see #setImageAlpha(int)
     */
    public int getImageAlpha() {
        return mAlpha;
    }

    /**
     * Sets the alpha value that should be applied to the image.
     *
     * @param alpha the alpha value that should be applied to the image
     *
     * @see #getImageAlpha()
     */
    public void setImageAlpha(int alpha) {
        //noinspection deprecation
        setAlpha(alpha);
    }

    /**
     * Sets the alpha value that should be applied to the image.
     *
     * @param alpha the alpha value that should be applied to the image
     *
     * @deprecated use #setImageAlpha(int) instead
     */
    @Deprecated
    public void setAlpha(int alpha) {
        alpha &= 0xFF;          // keep it legal
        if (mAlpha != alpha) {
            mAlpha = alpha;
            mColorMod = true;
            applyColorMod();
            invalidate();
        }
    }

    private void applyColorMod() {
        // Only mutate and apply when modifications have occurred. This should
        // not reset the mColorMod flag, since these filters need to be
        // re-applied if the Drawable is changed.
        if (mDrawable != null && mColorMod) {
            mDrawable = mDrawable.mutate();
            if (mHasColorFilter) {
                mDrawable.setColorFilter(mColorFilter);
            }
            mDrawable.setAlpha(mAlpha * mViewAlphaScale >> 8);
        }
    }

    @Override
    public boolean isOpaque() {
        return super.isOpaque() || mDrawable != null
                && mDrawable.getOpacity() == PixelFormat.OPAQUE
                && mAlpha * mViewAlphaScale >> 8 == 255
                && isFilledByImage();
    }

    private boolean isFilledByImage() {
        if (mDrawable == null) {
            return false;
        }

        final Rect bounds = mDrawable.getBounds();
        final Matrix matrix = mDrawMatrix;
        if (matrix == null) {
            return bounds.left <= 0 && bounds.top <= 0 && bounds.right >= getWidth()
                    && bounds.bottom >= getHeight();
        } else if (matrix.rectStaysRect()) {
            final RectF boundsSrc = mTempSrc;
            final RectF boundsDst = mTempDst;
            boundsSrc.set(bounds);
            matrix.mapRect(boundsDst, boundsSrc);
            return boundsDst.left <= 0 && boundsDst.top <= 0 && boundsDst.right >= getWidth()
                    && boundsDst.bottom >= getHeight();
        } else {
            // If the matrix doesn't map to a rectangle, assume the worst.
            return false;
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mDrawable != null) {
            mDrawable.setVisible(
                    ViewCompat.isAttachedToWindow(this) && visibility == VISIBLE, false);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDrawable != null) {
            mDrawable.setVisible(getVisibility() == VISIBLE, false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDrawable != null) {
            mDrawable.setVisible(false, false);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return AdvImageView.class.getName();
    }
}
