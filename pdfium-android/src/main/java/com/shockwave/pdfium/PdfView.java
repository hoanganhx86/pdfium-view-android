package com.shockwave.pdfium;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.shockwave.pdfium.listener.OnErrorOccurredListener;
import com.shockwave.pdfium.listener.OnLoadCompleteListener;
import com.shockwave.pdfium.listener.OnPageChangedListener;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class PdfView extends SurfaceView {
    private static final String TAG = PdfView.class.getName();

    private PdfiumCore mPdfCore;

    private PdfDocument mPdfDoc = null;
    private FileInputStream mDocFileStream = null;

    /** Animation manager manage all offset and zoom animation */
    private AnimationManager animationManager;

    /** Drag manager manage all touch events */
    private DragPinchManager dragPinchManager;

    private GestureDetector mSlidingDetector;
    private ScaleGestureDetector mZoomingDetector;

    private int mCurrentPageIndex = 0;
    private int mPageCount = 0;

    protected float mAccumulateScale = 1f;

    private SurfaceHolder mPdfSurfaceHolder;
    private boolean isSurfaceCreated = false;

    private final Rect mPageRect = new Rect();
    private final RectF mPageRectF = new RectF();
    private final Rect mScreenRect = new Rect();
    private final Matrix mTransformMatrix = new Matrix();
    protected boolean isScaling = false;
    private boolean isReset = true;


    private final ExecutorService mPreLoadPageWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService mRenderPageWorker = Executors.newSingleThreadExecutor();

    private Runnable mRenderRunnable;

    private OnPageChangedListener onPageChangedListener;
    private OnErrorOccurredListener onErrorOccurredListener;
    private OnLoadCompleteListener onLoadCompleteListener;


    public PdfView(Context c,AttributeSet set) {
        super(c,set);
        mPdfCore = new PdfiumCore(c);

        mSlidingDetector = new GestureDetector(c, new SlidingDetector());
        mZoomingDetector = new ScaleGestureDetector(c, new ZoomingDetector());

        mRenderRunnable = new Runnable() {
            @Override
            public void run() {
                loadPageIfNeed(mCurrentPageIndex);

                resetPageFit(mCurrentPageIndex);
                mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                        mPageRect.left, mPageRect.top,
                        mPageRect.width(), mPageRect.height());
                mPreLoadPageWorker.submit(new Runnable() {
                    @Override
                    public void run() {
                        loadPageIfNeed(mCurrentPageIndex + 1);
                        loadPageIfNeed(mCurrentPageIndex - 1);
                        loadPageIfNeed(mCurrentPageIndex + 2);
                        loadPageIfNeed(mCurrentPageIndex - 2);
                    }
                });
                if (onPageChangedListener !=null) {
                    onPageChangedListener.pageChanged(getCurrentPage(), mPageCount);
                }
            }
        };

        this.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                isSurfaceCreated = true;
                updateSurface(holder);
                if (mPdfDoc != null) {
                    mRenderPageWorker.submit(mRenderRunnable);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.w(TAG, "Surface Changed");
                updateSurface(holder);
                if(mPdfDoc != null){
                    mRenderPageWorker.submit(mRenderRunnable);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                isSurfaceCreated = false;
                Log.w(TAG, "Surface Destroy");
            }
        });
    }

    private void loadDocument(Uri fileUri) {
        try{
            mDocFileStream = new FileInputStream(fileUri.getPath());

            mPdfDoc = mPdfCore.newDocument(mDocFileStream.getFD());
            Log.d("Main", "Open Document");

            mPageCount = mPdfCore.getPageCount(mPdfDoc);
            Log.d(TAG, "Page Count: " + mPageCount);
            if (onLoadCompleteListener!=null) {
                onLoadCompleteListener.loadComplete(mPageCount);
            }
        }catch(IOException e) {
            e.printStackTrace();
            Log.e("Main", "Data uri: " + fileUri.toString());
            if (onErrorOccurredListener != null) {
                onErrorOccurredListener.errorOccured();
            }
        }
    }

    private void loadPageIfNeed(final int pageIndex){
        if( pageIndex >= 0 && pageIndex < mPageCount && !mPdfDoc.hasPage(pageIndex) ){
            Log.d(TAG, "Load page: " + pageIndex);
            mPdfCore.openPage(mPdfDoc, pageIndex);
        }
    }

    private void updateSurface(SurfaceHolder holder){
        mPdfSurfaceHolder = holder;
        mScreenRect.set(holder.getSurfaceFrame());
    }

    private void resetPageFit(int pageIndex){
        float pageWidth = mPdfCore.getPageWidth(mPdfDoc, pageIndex);
        float pageHeight = mPdfCore.getPageHeight(mPdfDoc, pageIndex);
        float screenWidth = mPdfSurfaceHolder.getSurfaceFrame().width();
        float screenHeight = mPdfSurfaceHolder.getSurfaceFrame().height();

        /**Portrait**/
        if(screenWidth < screenHeight){
            if( (pageWidth / pageHeight) < (screenWidth / screenHeight) ){
                //Situation one: fit height
                pageWidth *= (screenHeight / pageHeight);
                pageHeight = screenHeight;

                mPageRect.top = 0;
                mPageRect.left = (int)(screenWidth - pageWidth) / 2;
                mPageRect.right = (int)(mPageRect.left + pageWidth);
                mPageRect.bottom = (int)pageHeight;
            }else{
                //Situation two: fit width
                pageHeight *= (screenWidth / pageWidth);
                pageWidth = screenWidth;

                mPageRect.left = 0;
                mPageRect.top = (int)(screenHeight - pageHeight) / 2;
                mPageRect.bottom = (int)(mPageRect.top + pageHeight);
                mPageRect.right = (int)pageWidth;
            }
        }else{

            /**Landscape**/
            if( pageWidth > pageHeight ){
                //Situation one: fit height
                pageWidth *= (screenHeight / pageHeight);
                pageHeight = screenHeight;

                mPageRect.top = 0;
                mPageRect.left = (int)(screenWidth - pageWidth) / 2;
                mPageRect.right = (int)(mPageRect.left + pageWidth);
                mPageRect.bottom = (int)pageHeight;
            }else{
                //Situation two: fit width
                pageHeight *= (screenWidth / pageWidth);
                pageWidth = screenWidth;

                mPageRect.left = 0;
                mPageRect.top = 0;
                mPageRect.bottom = (int)(mPageRect.top + pageHeight);
                mPageRect.right = (int)pageWidth;
            }
        }

        isReset = true;
    }

    private void rectF2Rect(RectF inRectF, Rect outRect){
        outRect.left = (int)inRectF.left;
        outRect.right = (int)inRectF.right;
        outRect.top = (int)inRectF.top;
        outRect.bottom = (int)inRectF.bottom;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        boolean ret;

        ret = mZoomingDetector.onTouchEvent(event);
        if(!isScaling) ret |= mSlidingDetector.onTouchEvent(event);
        ret |= super.onTouchEvent(event);

        return ret;
    }

    private class SlidingDetector extends GestureDetector.SimpleOnGestureListener {

        private boolean checkFlippable(){
            return ( mPageRect.left >= mScreenRect.left &&
                        mPageRect.right <= mScreenRect.right );
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
            return moveRelative(distanceX,distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
            if(!isSurfaceCreated) return false;
            if(velocityX == 0f) return false;

            if(!checkFlippable()){
                Log.d(TAG, "Not flippable");
                return false;
            }

            if(velocityX < -200f){ //Forward
                Log.d(TAG, "Flip forward");
                nextPage();
                return true;
            }

            if(velocityX > 200f){ //Backward
                Log.d(TAG, "Flip backward");
                prevPage();
                return true;
            }

            return false;
        }
    }
    private class ZoomingDetector extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector){
            mAccumulateScale = 1f;
            isScaling = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector){
            return zoomCenteredTo(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector){
            PdfView.this.onScaleEnd();
        }
    }

    public boolean isScaling() {
        return isScaling;
    }

    public boolean zoomCenteredTo(float scaleFactor, float focusX, float focusY) {
        if(!isSurfaceCreated) return false;

        mAccumulateScale *= scaleFactor;
        mAccumulateScale = Math.max(1f, mAccumulateScale);
        float scaleValue = (mAccumulateScale > 1f)? scaleFactor : 1f;
        mTransformMatrix.setScale(scaleValue, scaleValue,
                focusX, focusY);
        mPageRectF.set(mPageRect);

        mTransformMatrix.mapRect(mPageRectF);

        rectF2Rect(mPageRectF, mPageRect);

        mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                mPageRect.left, mPageRect.top,
                mPageRect.width(), mPageRect.height());

        isReset = false;

        return true;
    }

    public void onScaleEnd() {
        if(mAccumulateScale == 1f && !mScreenRect.contains(mPageRect)){
            resetPageFit(mCurrentPageIndex);

            mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                    mPageRect.left, mPageRect.top,
                    mPageRect.width(), mPageRect.height());
        }

        isScaling = false;
        mAccumulateScale = 1f;
    }

    protected RectF getPageRectF() {
        return mPageRectF;
    }

    protected Rect getScreenRect() {
        return mScreenRect;
    }

    public boolean moveTo(float distanceX, float distanceY) {
        return moveRelative(mPageRect.left-distanceX, mPageRect.right-distanceY);
    }

    public boolean moveRelative(float distanceX, float distanceY) {

        if(!isSurfaceCreated) return false;
        Log.d(TAG, "Drag");

        distanceX *= -1f;
        distanceY *= -1f;

        if( (mPageRect.left <= mScreenRect.left && mPageRect.right <= mScreenRect.right && distanceX < 0) ||
                (mPageRect.right >= mScreenRect.right && mPageRect.left >= mScreenRect.left && distanceX > 0) )
            distanceX = 0f;
        if( (mPageRect.top <= mScreenRect.top && mPageRect.bottom <= mScreenRect.bottom && distanceY < 0) ||
                (mPageRect.bottom >= mScreenRect.bottom && mPageRect.top >= mScreenRect.top && distanceY > 0) )
            distanceY = 0f;

        //Portrait restriction
        if(isReset && mScreenRect.width() < mScreenRect.height()) distanceX = distanceY = 0f;
        if(isReset && mScreenRect.height() <= mScreenRect.width()) distanceX = 0f;

        if(distanceX == 0f && distanceY == 0f) return false;

        Log.d(TAG, "DistanceX: " + distanceX);
        Log.d(TAG, "DistanceY: " + distanceY);
        mPageRect.left += distanceX;
        mPageRect.right += distanceX;
        mPageRect.top += distanceY;
        mPageRect.bottom += distanceY;

        mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                mPageRect.left, mPageRect.top,
                mPageRect.width(), mPageRect.height());

        return true;

    }

    protected void recycle() {
        try{
            if(mPdfDoc != null && mDocFileStream != null){
                mPdfCore.closeDocument(mPdfDoc);
                Log.d("Main", "Close Document");

                mDocFileStream.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public Configurator fromUri(Uri uri) {
        return new Configurator(uri);
    }

    public void goToPage(int index) {
        if(index >= 0 && index < mPageCount){
            mCurrentPageIndex = index;
            Log.d(TAG, "Next Index: " + mCurrentPageIndex);
            mRenderPageWorker.submit(mRenderRunnable);
        }
    }
    public void nextPage() {
        goToPage(mCurrentPageIndex+1);
    }

    public void prevPage() {
        goToPage(mCurrentPageIndex-1);
    }

    public void firstPage() {
        goToPage(1);
    }

    public void LastPage() {
        goToPage(mPageCount);
    }

    public int getPageCount() {
        return mPageCount;
    }

    public int getCurrentPage() {
        return mCurrentPageIndex+1;
    }

    private void setOnPageChangedListener(OnPageChangedListener onPageChangedListener) {
        this.onPageChangedListener = onPageChangedListener;
    }

    private void setOnErrorOccuredListener(OnErrorOccurredListener onErrorOccurredListener) {
        this.onErrorOccurredListener = onErrorOccurredListener;
    }

    private void setOnLoadCompleteListener(OnLoadCompleteListener onLoadCompleteListener) {
        this.onLoadCompleteListener = onLoadCompleteListener;
    }



    public class Configurator {

        private final Uri uri;

        private OnLoadCompleteListener onLoadCompleteListener;

        private OnPageChangedListener onPageChangedListener;

        private OnErrorOccurredListener onErrorOccurredListener;

        private Configurator(Uri uri) {
            this.uri = uri;
        }

        public Configurator onLoad(OnLoadCompleteListener onLoadCompleteListener) {
            this.onLoadCompleteListener = onLoadCompleteListener;
            return this;
        }

        public Configurator onPageChanged(OnPageChangedListener onPageChangedListener) {
            this.onPageChangedListener = onPageChangedListener;
            return this;
        }

        public Configurator onErrorOccured(OnErrorOccurredListener onErrorOccurredListener) {
            this.onErrorOccurredListener = onErrorOccurredListener;
            return this;
        }

        public void load() {
            PdfView.this.recycle();
            PdfView.this.setOnLoadCompleteListener(onLoadCompleteListener);
            PdfView.this.setOnPageChangedListener(onPageChangedListener);
            PdfView.this.setOnErrorOccuredListener(onErrorOccurredListener);
            PdfView.this.loadDocument(uri);
        }
    }

}
