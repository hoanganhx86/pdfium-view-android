package com.shockwave.pdfium;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
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

import static com.shockwave.pdfium.util.Constants.*;



public class PdfView extends SurfaceView {
    private static final String TAG = PdfView.class.getName();

    private PdfiumCore mPdfCore;

    private PdfDocument mPdfDoc = null;
    private FileInputStream mDocFileStream = null;

    private DragPinchManager dragPinchManager;

    private int mCurrentPageIndex = 0;
    private int mPageCount = 0;

    private float zoom = MINIMUM_ZOOM;


    private SurfaceHolder mPdfSurfaceHolder;
    private boolean isSurfaceCreated = false;

    private final Rect mPageRect = new Rect();
    private final Rect mScreenRect = new Rect();
    private final Matrix mTransformMatrix = new Matrix();
    private boolean isZoomed = false;


    private final ExecutorService mPreLoadPageWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService mRenderPageWorker = Executors.newSingleThreadExecutor();

    private Runnable mRenderRunnable;

    private OnPageChangedListener onPageChangedListener;
    private OnErrorOccurredListener onErrorOccurredListener;
    private OnLoadCompleteListener onLoadCompleteListener;


    public PdfView(final Context c,AttributeSet set) {
        super(c,set);
        mPdfCore = new PdfiumCore(c);

        dragPinchManager = new DragPinchManager(this);

        mRenderRunnable = new Runnable() {
            @Override
            public void run() {
                loadPageIfNeed(mCurrentPageIndex);
                resetPageFit();
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
                recycle();
                Log.w(TAG, "Surface Destroy");
            }
        });
    }

    private void loadDocument(Uri fileUri) {
        try{
            mDocFileStream = new FileInputStream(fileUri.getPath());

            mPdfDoc = mPdfCore.newDocument(mDocFileStream.getFD());
            if (DEBUG_MODE) Log.d("Main", "Open Document");

            mPageCount = mPdfCore.getPageCount(mPdfDoc);
            if (DEBUG_MODE) Log.d(TAG, "Page Count: " + mPageCount);
            if (onLoadCompleteListener!=null) {
                onLoadCompleteListener.loadComplete(mPageCount);
            }
        }catch(IOException e) {
            e.printStackTrace();
            if (DEBUG_MODE) Log.e("Main", "Data uri: " + fileUri.toString());
            if (onErrorOccurredListener != null) {
                onErrorOccurredListener.errorOccured();
            }
        }
    }

    private void loadPageIfNeed(final int pageIndex){
        if( pageIndex >= 0 && pageIndex < mPageCount && !mPdfDoc.hasPage(pageIndex) ){
            if (DEBUG_MODE) Log.d(TAG, "Load page: " + pageIndex);
            mPdfCore.openPage(mPdfDoc, pageIndex);
        }
    }

    private void updateSurface(SurfaceHolder holder){
        mPdfSurfaceHolder = holder;
        mScreenRect.set(holder.getSurfaceFrame());
    }

    protected void resetPageFit(){
        int pageIndex = mCurrentPageIndex;
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

        isZoomed = false;
        zoom = MINIMUM_ZOOM;
        render();
    }

    private void rectF2Rect(RectF inRectF, Rect outRect){
        outRect.left = (int)inRectF.left;
        outRect.right = (int)inRectF.right;
        outRect.top = (int)inRectF.top;
        outRect.bottom = (int)inRectF.bottom;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        boolean ret = false;

        //ret = mZoomingDetector.onTouchEvent(event);
        //if(!isZooming) ret |= mSlidingDetector.onTouchEvent(event);
        ret |= super.onTouchEvent(event);

        return ret;
    }

    public boolean isZoomed() {
        return isZoomed;
    }

    public void zoomTo(float zoom, PointF pivot) {
        //It allows to work with matrixes.
        RectF mPageRectF = new RectF();
        //Check zoom levels
        if (this.zoom*zoom < MINIMUM_ZOOM) {
            resetPageFit();
        } else {
            if (this.zoom*zoom > MAXIMUM_ZOOM) {
                zoom = MAXIMUM_ZOOM / this.zoom;
            }
            this.zoom *= zoom;
            float focusX = pivot.x;
            float focusY = pivot.y;
            mTransformMatrix.setScale(zoom, zoom,
                    focusX, focusY);
            mPageRectF.set(mPageRect);
            mTransformMatrix.mapRect(mPageRectF);
            if (DEBUG_MODE) Log.d("PdfView", "Zoom: " + this.zoom);
            rectF2Rect(mPageRectF, mPageRect);
            isZoomed = true;
            //Fix movement while zooming
            float moveX = 0f;
            float moveY = 0f;
            if (mPageRect.left > 0) {
                moveX -= mPageRect.left;
            }
            if (mPageRect.top > 0) {
                moveY -= mPageRect.top;
            }
            if (mPageRect.right < mScreenRect.width()) {
                moveX = mScreenRect.width() - mPageRect.right;
            }
            if (mPageRect.bottom < mScreenRect.height()) {
                moveY = mScreenRect.height() - mPageRect.bottom;
            }
            moveRelative(moveX,moveY,false);
        }
    }

    public float getZoom() {
        return this.zoom;
    }

    public Rect getPageRect() {
        return mPageRect;
    }

    protected Rect getScreenRect() {
        return mScreenRect;
    }

    public void moveTo(float distanceX, float distanceY,boolean render) {
        if(!isSurfaceCreated) return;
            if (DEBUG_MODE) Log.d(TAG, "DistanceX: " + distanceX);
            if (DEBUG_MODE) Log.d(TAG, "DistanceY: " + distanceY);
            int deltaX = mPageRect.width();
            int deltaY = mPageRect.height();
            mPageRect.left = (int) (distanceX);
            mPageRect.right = (mPageRect.left + deltaX);
            mPageRect.top = (int) (distanceY);
            mPageRect.bottom = (mPageRect.top + deltaY);
            if (render) {
                render();
            }
    }

    public void moveRelative(float distanceX, float distanceY) {
        moveRelative(distanceX,distanceY,true);
    }

    public void moveRelative(float distanceX, float distanceY,boolean render) {
        if (isZoomed) {
            float newLeft = mPageRect.left + distanceX;
            float newRight = mPageRect.right + distanceX;
            float newTop = mPageRect.top + distanceY;
            float newBottom = mPageRect.bottom + distanceY;

            //Don't move more than needed each side.
            if (distanceX > 0 && newRight >= mScreenRect.right && newLeft >= mScreenRect.left) {
                distanceX = (mScreenRect.left - mPageRect.left);
            }
            if (distanceX < 0 && newLeft <= mScreenRect.left && newRight <= mScreenRect.right) {
                distanceX = (mScreenRect.right - mPageRect.right);
            }
            if (distanceY > 0 && newBottom >= mScreenRect.bottom && newTop >= mScreenRect.top) {
                distanceY = (mScreenRect.top - mPageRect.top);
            }
            if (distanceY < 0 && newTop <= mScreenRect.top && newBottom <= mScreenRect.bottom) {
                distanceY = (mScreenRect.bottom - mPageRect.bottom);
            }
            //If the height of the document is contained on entire view
            if (mScreenRect.height() > mPageRect.height()) {
                //Center vertical
                moveTo(mPageRect.left + distanceX, (mScreenRect.height() - mPageRect.height()) / 2,render);
            }
            //if width of document is contained on entire view:
            else if (mScreenRect.width() > mPageRect.width()) {
                //Center horizontal
                moveTo(((mScreenRect.width() - mPageRect.width()) / 2), mPageRect.top + distanceY,render);
            } else {
                moveTo(mPageRect.left + distanceX, mPageRect.top + distanceY,render);

            }
        }
    }

    protected void recycle() {
        try{
            if(mPdfDoc != null && mDocFileStream != null){
                mPdfCore.closeDocument(mPdfDoc);
                if (DEBUG_MODE) Log.d("Main", "Close Document");

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

    public void render() {
        mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                mPageRect.left, mPageRect.top,
                mPageRect.width(), mPageRect.height());
    }

}
