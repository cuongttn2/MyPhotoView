package com.example.myphotoview

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

class CustomZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), 
    ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        updateBaseMatrix(drawable)
        if (sizingMode != SizingMode.NONE) requestLayout()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateBaseMatrix(drawable)
        if (sizingMode != SizingMode.NONE) requestLayout()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        updateBaseMatrix(drawable)
        if (sizingMode != SizingMode.NONE) requestLayout()
    }

    override fun setImageURI(uri: android.net.Uri?) {
        super.setImageURI(uri)
        updateBaseMatrix(drawable)
        if (sizingMode != SizingMode.NONE) requestLayout()
    }

    private val baseMatrix = Matrix()
    private val suppMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val displayRect = RectF()
    private val matrixValues = FloatArray(9)

    private var scaleDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector

    private var allowParentInterceptOnEdge = true
    
    // Zoom limits
    var minScale = 1.0f
    var midScale = 1.75f
    var maxScale = 3.0f

    private var currentFlingRunnable: FlingRunnable? = null

    init {
        super.setScaleType(ScaleType.MATRIX)
        scaleDetector = ScaleGestureDetector(context, this)
        gestureDetector = GestureDetector(context, this)
        gestureDetector.setOnDoubleTapListener(this)
    }

    override fun setScaleType(scaleType: ScaleType) {
        if (scaleType != ScaleType.MATRIX) {
            throw IllegalArgumentException("CustomZoomImageView only supports Matrix ScaleType")
        }
    }

    // --- Sizing Modes State ---
    private var sizingMode = SizingMode.NONE
    
    // Storage for dimensions/ratios
    private var targetWidthFixed = 0
    private var targetHeightFixed = 0
    private var targetWidthPercent = 0f
    private var targetHeightPercent = 0f
    private var targetAspectRatio = 0f // Height / Width

    enum class SizingMode {
        NONE,                       // Standard behavior
        FIXED_SIZE,                 // Explicit W & H in pixels
        FIXED_WIDTH_AUTO_HEIGHT,    // Fixed W, H based on Drawable Ratio
        FIXED_HEIGHT_AUTO_WIDTH,    // Fixed H, W based on Drawable Ratio
        
        PERCENT_WIDTH_AUTO_HEIGHT,  // W = % Screen, H based on Drawable Ratio
        PERCENT_HEIGHT_AUTO_WIDTH,  // H = % Screen, W based on Drawable Ratio
        
        PERCENT_WIDTH_FIXED_HEIGHT, // W = % Screen, H = Fixed Pixels
        PERCENT_HEIGHT_FIXED_WIDTH, // H = % Screen, W = Fixed Pixels
        
        PERCENT_BOTH,               // W = % Screen, H = % Screen
        
        PERCENT_WIDTH_ASPECT_RATIO, // W = % Screen, H = W * Ratio
        FIXED_WIDTH_ASPECT_RATIO    // W = Fixed,     H = W * Ratio
    }
    
    // --- Public API for Sizing ---

    /** 1. Independent Fixed Size */
    fun setFixedSize(widthPx: Int, heightPx: Int) {
        sizingMode = SizingMode.FIXED_SIZE
        targetWidthFixed = widthPx
        targetHeightFixed = heightPx
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 2. Fixed Width, Image Aspect Ratio determines Height */
    fun setFixedWidth(widthPx: Int) {
        sizingMode = SizingMode.FIXED_WIDTH_AUTO_HEIGHT
        targetWidthFixed = widthPx
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 3. Fixed Height, Image Aspect Ratio determines Width */
    fun setFixedHeight(heightPx: Int) {
        sizingMode = SizingMode.FIXED_HEIGHT_AUTO_WIDTH
        targetHeightFixed = heightPx
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 4. Fixed Width, Explicit Aspect Ratio determines Height */
    fun setFixedWidthAndAspectRatio(widthPx: Int, aspectRatio: Float) {
        sizingMode = SizingMode.FIXED_WIDTH_ASPECT_RATIO
        targetWidthFixed = widthPx
        targetAspectRatio = aspectRatio
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 5. Width = % of Screen, Image Aspect Ratio determines Height */
    fun setWidthPercent(percent: Float) {
        sizingMode = SizingMode.PERCENT_WIDTH_AUTO_HEIGHT
        targetWidthPercent = percent
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 6. Height = % of Screen, Image Aspect Ratio determines Width */
    fun setHeightPercent(percent: Float) {
        sizingMode = SizingMode.PERCENT_HEIGHT_AUTO_WIDTH
        targetHeightPercent = percent
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 7. Width = % of Screen, Height = Fixed Pixels */
    fun setWidthPercentageAndHeight(widthPercent: Float, heightPx: Int) {
        sizingMode = SizingMode.PERCENT_WIDTH_FIXED_HEIGHT
        targetWidthPercent = widthPercent
        targetHeightFixed = heightPx
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 8. Height = % of Screen, Width = Fixed Pixels */
    fun setHeightPercentageAndWidth(heightPercent: Float, widthPx: Int) {
        sizingMode = SizingMode.PERCENT_HEIGHT_FIXED_WIDTH
        targetHeightPercent = heightPercent
        targetWidthFixed = widthPx
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 9. Width = % of Screen, Height = % of Screen */
    fun setPercentDimensions(widthPercent: Float, heightPercent: Float) {
        sizingMode = SizingMode.PERCENT_BOTH
        targetWidthPercent = widthPercent
        targetHeightPercent = heightPercent
        updateBaseMatrix(drawable)
        invalidate()
    }

    /** 10. Width = % of Screen, Height = Aspect Ratio * Width */
    fun setWidthPercentageAndAspectRatio(widthPercent: Float, aspectRatio: Float) {
        sizingMode = SizingMode.PERCENT_WIDTH_ASPECT_RATIO
        targetWidthPercent = widthPercent
        targetAspectRatio = aspectRatio
        updateBaseMatrix(drawable)
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Always update matrix to ensure centering
        updateBaseMatrix(drawable)
    }

    private fun updateBaseMatrix(d: Drawable?) {
        if (d == null) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth <= 0 || viewHeight <= 0) return 

        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()

        baseMatrix.reset()
        
        var scale = 1f
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        
        when (sizingMode) {
             SizingMode.NONE -> {
                 val widthScale = viewWidth / drawableWidth
                 val heightScale = viewHeight / drawableHeight
                 scale = widthScale.coerceAtMost(heightScale)
             }
             SizingMode.FIXED_SIZE -> {
                 val wScale = targetWidthFixed / drawableWidth
                 val hScale = targetHeightFixed / drawableHeight
                 scale = wScale.coerceAtMost(hScale)
             }
             SizingMode.FIXED_WIDTH_AUTO_HEIGHT -> {
                 scale = targetWidthFixed / drawableWidth
             }
             SizingMode.FIXED_HEIGHT_AUTO_WIDTH -> {
                 scale = targetHeightFixed / drawableHeight
             }
             SizingMode.FIXED_WIDTH_ASPECT_RATIO -> {
                 scale = targetWidthFixed / drawableWidth
             }
             SizingMode.PERCENT_WIDTH_AUTO_HEIGHT -> {
                 val targetPixels = screenWidth * targetWidthPercent
                 scale = targetPixels / drawableWidth
             }
             SizingMode.PERCENT_HEIGHT_AUTO_WIDTH -> {
                 val targetPixels = screenHeight * targetHeightPercent
                 scale = targetPixels / drawableHeight
             }
             SizingMode.PERCENT_WIDTH_FIXED_HEIGHT -> {
                 val tW = screenWidth * targetWidthPercent
                 val tH = targetHeightFixed.toFloat()
                 val sW = tW / drawableWidth
                 val sH = tH / drawableHeight
                 scale = sW.coerceAtMost(sH)
             }
             SizingMode.PERCENT_HEIGHT_FIXED_WIDTH -> {
                 val tH = screenHeight * targetHeightPercent
                 val tW = targetWidthFixed.toFloat()
                 val sW = tW / drawableWidth
                 val sH = tH / drawableHeight
                 scale = sW.coerceAtMost(sH)
             }
             SizingMode.PERCENT_BOTH -> {
                 val tW = screenWidth * targetWidthPercent
                 val tH = screenHeight * targetHeightPercent
                 val sW = tW / drawableWidth
                 val sH = tH / drawableHeight
                 scale = sW.coerceAtMost(sH)
             }
             SizingMode.PERCENT_WIDTH_ASPECT_RATIO -> {
                 // Width = % Screen. Height = Width * Ratio.
                 val tW = screenWidth * targetWidthPercent
                 val tH = tW * targetAspectRatio
                 val sW = tW / drawableWidth
                 val sH = tH / drawableHeight
                 scale = sW.coerceAtMost(sH)
             }
        }

        baseMatrix.postScale(scale, scale)
        
        // Center the scaled image in the view
        val scaledW = drawableWidth * scale
        val scaledH = drawableHeight * scale
        
        baseMatrix.postTranslate(
            (viewWidth - scaledW) / 2f,
            (viewHeight - scaledH) / 2f
        )
        
        suppMatrix.reset()
        updateImageMatrix()
    }

    private fun updateImageMatrix() {
        if (width <= 0 || height <= 0) return
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(suppMatrix)
        checkMatrixBounds()
        imageMatrix = drawMatrix
    }

    private fun checkMatrixBounds(): Boolean {
        val rect = getDisplayRect(drawMatrix) ?: return false
        val height = rect.height()
        val width = rect.width()
        var deltaX = 0f
        var deltaY = 0f
        
        val vHeight = getHeight().toFloat()
        
        if (height <= vHeight) {
            // Center vertically
            deltaY = (vHeight - height) / 2 - rect.top
        } else if (rect.top > 0) {
            deltaY = -rect.top
        } else if (rect.bottom < vHeight) {
            deltaY = vHeight - rect.bottom
        }

        val vWidth = getWidth().toFloat()
        if (width <= vWidth) {
            // Center horizontally
            deltaX = (vWidth - width) / 2 - rect.left
        } else if (rect.left > 0) {
            deltaX = -rect.left
        } else if (rect.right < vWidth) {
            deltaX = vWidth - rect.right
        }

        suppMatrix.postTranslate(deltaX, deltaY)
        return true
    }

    private fun getDisplayRect(matrix: Matrix): RectF? {
        val d = drawable ?: return null
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matrix.mapRect(displayRect)
        return displayRect
    }

    private fun getScale(): Float {
        suppMatrix.getValues(matrixValues)
        return kotlin.math.sqrt(
            (matrixValues[Matrix.MSCALE_X].toDouble().pow(2) + 
             matrixValues[Matrix.MSKEW_Y].toDouble().pow(2))
        ).toFloat()
    }
    
    private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            currentFlingRunnable?.cancelFling()
            currentFlingRunnable = null
        }

        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled

        // If not handled by detectors, try upstream (e.g. click listeners)
        return handled || super.onTouchEvent(event)
    }

    // --- OnScaleGestureListener ---

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        val focusX = detector.focusX
        val focusY = detector.focusY

        val currentScale = getScale()
        
        // Allow scaling if we are within range OR if we are zooming back into range
        if ((currentScale < maxScale || scaleFactor < 1f) && 
            (currentScale > minScale || scaleFactor > 1f)) {
            
            suppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
            updateImageMatrix()
        }
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    // --- OnGestureListener ---

    override fun onDown(e: MotionEvent): Boolean = true 

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (scaleDetector.isInProgress) return false

        suppMatrix.postTranslate(-distanceX, -distanceY)
        updateImageMatrix()
        
        // Handle parent intercept logic
        val parent = parent
        if (allowParentInterceptOnEdge && !scaleDetector.isInProgress) {
             // If we are zoomed in, generally disallow parent intercept (like ViewPager)
             // unless we are at the edge. For simplicity in this demo, strict disallow if zoomed.
             if (getScale() > minScale + 0.01f) { 
                 parent?.requestDisallowInterceptTouchEvent(true)
             }
        }
        
        return true
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        currentFlingRunnable?.cancelFling()
        currentFlingRunnable = FlingRunnable(context)
        currentFlingRunnable?.fling(
            width,
            height,
            velocityX.toInt(),
            velocityY.toInt()
        )
        postOnAnimation(this, currentFlingRunnable!!)
        return true
    }

    // --- OnDoubleTapListener ---

    // Callback for outside tap
    var onOutsidePhotoTapListener: (() -> Unit)? = null

    // Helper to reset scale
    fun resetScale(animate: Boolean = true) {
         if (animate) {
             // For now, reuse similar logic to double tap, maybe refactor later for full animation support
             // Just snap for this iteration or simple invalidation if we don't have a full animator
             suppMatrix.reset()
             updateImageMatrix()
         } else {
             suppMatrix.reset()
             updateImageMatrix()
         }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val rect = getDisplayRect(drawMatrix)
        if (rect != null) {
            // Check if tap is outside the bounds
            if (!rect.contains(e.x, e.y)) {
                // It is an outside tap
                resetScale()
                onOutsidePhotoTapListener?.invoke()
                return true
            }
        }
        return false
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        try {
            val currentScale = getScale()
            val targetScale = if (currentScale >= midScale) minScale else midScale
            
            suppMatrix.setScale(targetScale, targetScale, e.x, e.y)
            updateImageMatrix()
        } catch (e: Exception) {
            // handle bounds errors
        }
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    // --- Fling Support ---
    
    private fun postOnAnimation(view: android.view.View, runnable: Runnable) {
        view.postOnAnimation(runnable)
    }

    private inner class FlingRunnable(context: Context) : Runnable {
        private val scroller: OverScroller = OverScroller(context)
        private var currentX = 0
        private var currentY = 0

        fun cancelFling() {
            scroller.forceFinished(true)
        }

        fun fling(viewWidth: Int, viewHeight: Int, velocityX: Int, velocityY: Int) {
            val rect = getDisplayRect(drawMatrix) ?: return
            
            val startX = (-rect.left).toInt()
            val startY = (-rect.top).toInt()
            
            val minX: Int
            val maxX: Int
            val minY: Int
            val maxY: Int

            if (rect.width() > viewWidth) {
                minX = 0
                maxX = (rect.width() - viewWidth).toInt()
            } else {
                minX = startX
                maxX = startX
            }

            if (rect.height() > viewHeight) {
                minY = 0
                maxY = (rect.height() - viewHeight).toInt()
            } else {
                minY = startY
                maxY = startY
            }

            currentX = startX
            currentY = startY

            // Velocity inversion is needed because we translate matrix opposite to scroll direction
            // but Scroller calculates "scroll position" from 0 to Max.
            scroller.fling(
                startX, startY,
                -velocityX, -velocityY,
                minX, maxX,
                minY, maxY
            )
        }

        override fun run() {
            if (scroller.isFinished) return

            if (scroller.computeScrollOffset()) {
                val newX = scroller.currX
                val newY = scroller.currY
                
                val tx = (currentX - newX).toFloat()
                val ty = (currentY - newY).toFloat()
                
                suppMatrix.postTranslate(tx, ty)
                updateImageMatrix()
                
                currentX = newX
                currentY = newY
                
                postOnAnimation(this@CustomZoomImageView, this)
            }
        }
    }
}
