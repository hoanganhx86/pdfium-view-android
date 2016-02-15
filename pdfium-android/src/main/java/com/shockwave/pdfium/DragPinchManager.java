/**
 * Copyright 2014 Joan Zapata
 *
 * This file is part of Android-PdfView.
 *
 * Android-PdfView is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Android-PdfView is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Android-PdfView.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.shockwave.pdfium;

import android.graphics.PointF;

import com.shockwave.pdfium.util.DragPinchListener;
import com.shockwave.pdfium.util.DragPinchListener.OnDoubleTapListener;
import com.shockwave.pdfium.util.DragPinchListener.OnDragListener;
import com.shockwave.pdfium.util.DragPinchListener.OnPinchListener;

import static com.shockwave.pdfium.util.Constants.Pinch.QUICK_MOVE_THRESHOLD_DISTANCE;
import static com.shockwave.pdfium.util.Constants.Pinch.QUICK_MOVE_THRESHOLD_TIME;

/**
 * @author Joan Zapata
 *         This Manager takes care of moving the PdfView,
 *         set its zoom track user actions.
 */
class DragPinchManager implements OnDragListener, OnPinchListener, OnDoubleTapListener {

    private PdfView PdfView;

    private DragPinchListener dragPinchListener;

    private long startDragTime;

    private float startDragX;
    private float startDragY;

    public DragPinchManager(PdfView PdfView) {
        this.PdfView = PdfView;
        dragPinchListener = new DragPinchListener();
        dragPinchListener.setOnDragListener(this);
        dragPinchListener.setOnPinchListener(this);
        dragPinchListener.setOnDoubleTapListener(this);
        PdfView.setOnTouchListener(dragPinchListener);
    }
    
    public void enableDoubletap(boolean enableDoubletap){
        if (enableDoubletap) {
            dragPinchListener.setOnDoubleTapListener(this);
        } else {
            dragPinchListener.setOnDoubleTapListener(null);
        }
    }
    
    @Override
    public void onPinch(float dr, PointF pivot) {
        PdfView.zoomCenteredTo(dr, pivot.x, pivot.y);
    }

    @Override
    public void startDrag(float x, float y) {
        startDragTime = System.currentTimeMillis();
        startDragX = x;
        startDragY = y;
    }

    @Override
    public void onDrag(float dx, float dy) {
        if (isZooming()) {
            PdfView.moveRelative(dx, dy);
        }
    }

    @Override
    public void endDrag(float x, float y) {
        if (!isZooming()) {
            	float distance;
                distance = x - startDragX;
                long time = System.currentTimeMillis() - startDragTime;
                int diff = distance > 0 ? -1 : +1;

                if (isQuickMove(distance, time) || isPageChange(distance)) {
                    PdfView.goToPage(PdfView.getCurrentPage() + diff);
                } else {
                    PdfView.goToPage(PdfView.getCurrentPage());
                }
            }
    }

    public boolean isZooming() {
        return PdfView.isScaling();
    }

    private boolean isPageChange(float distance) {
        return Math.abs(distance) > Math.abs((PdfView.getScreenRect().width()) / 3);
    }

    private boolean isQuickMove(float dx, long dt) {
        return Math.abs(dx) >= QUICK_MOVE_THRESHOLD_DISTANCE && //
                dt <= QUICK_MOVE_THRESHOLD_TIME;
    }

    @Override
    public void onDoubleTap(float x, float y) {
        if (isZooming()) {
            //PdfView.resetZoomWithAnimation();
        }
    }
}
