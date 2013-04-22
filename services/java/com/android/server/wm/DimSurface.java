/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm;

import android.graphics.PixelFormat;
import android.util.Slog;
import android.view.Surface;
import android.view.SurfaceSession;

import java.io.PrintWriter;

class DimSurface {
    static final String TAG = "DimSurface";

    Surface mDimSurface;
    boolean mDimShown = false;
    int mDimColor = 0;
    int mLayer = -1;
    /**
     * Author: Onskreen
     * Date: 21/12/2011
     *
     * Setting DimSurface position variables
     */
    int mDimX = 0, mDimY = 0;

    int mLastDimWidth, mLastDimHeight;

    DimSurface(SurfaceSession session, final int layerStack) {
        try {
            if (WindowManagerService.DEBUG_SURFACE_TRACE) {
                mDimSurface = new WindowStateAnimator.SurfaceTrace(session,
                    "DimSurface",
                    16, 16, PixelFormat.OPAQUE,
                    Surface.FX_SURFACE_DIM | Surface.HIDDEN);
            } else {
                mDimSurface = new Surface(session, "DimSurface",
                    16, 16, PixelFormat.OPAQUE,
                    Surface.FX_SURFACE_DIM | Surface.HIDDEN);
            }
            if (WindowManagerService.SHOW_TRANSACTIONS ||
                    WindowManagerService.SHOW_SURFACE_ALLOC) Slog.i(WindowManagerService.TAG,
                            "  DIM " + mDimSurface + ": CREATE");
            mDimSurface.setLayerStack(layerStack);
            mDimSurface.setAlpha(0.0f);
            mDimSurface.show();
        } catch (Exception e) {
            Slog.e(WindowManagerService.TAG, "Exception creating Dim surface", e);
        }
    }

    /**
     * Show the dim surface.
     */
    void show(int dw, int dh, int layer, int color) {
        if (mDimSurface == null) {
            Slog.e(TAG, "show: no Surface");
            return;
        }

        if (!mDimShown) {
            if (WindowManagerService.SHOW_TRANSACTIONS) Slog.i(WindowManagerService.TAG, "  DIM " + mDimSurface + ": SHOW pos=(0,0) (" +
                    dw + "x" + dh + " layer=" + layer + ")");
            mDimShown = true;
            try {
                mLastDimWidth = dw;
                mLastDimHeight = dh;
                //mDimSurface.setPosition(0, 0);
                /**
                 * Author: Onskreen
                 * Date: 21/12/2011
                 *
                 * set the correct position based on the x,y set in
                 * performLayoutAndPlaceSurfacesLockedInner method.
                 */
                mDimSurface.setPosition(mDimX, mDimY);
                mDimSurface.setSize(dw, dh);
                mDimSurface.setLayer(layer);
                mDimSurface.show();
            } catch (RuntimeException e) {
                Slog.w(WindowManagerService.TAG, "Failure showing dim surface", e);
            }
        } else if (mLastDimWidth != dw || mLastDimHeight != dh || mDimColor != color
                || mLayer != layer) {
            if (WindowManagerService.SHOW_TRANSACTIONS) Slog.i(WindowManagerService.TAG, "  DIM " + mDimSurface + ": pos=(0,0) (" +
                    dw + "x" + dh + " layer=" + layer + ")");
            mLastDimWidth = dw;
            mLastDimHeight = dh;
            mLayer = layer;
            mDimColor = color;
            mDimSurface.setSize(dw, dh);
            mDimSurface.setLayer(layer);
            mDimSurface.setAlpha(((color>>24)&0xff)/255.0f);
        }
    }

    void hide() {
        if (mDimSurface == null) {
            Slog.e(TAG, "hide: no Surface");
            return;
        }

        if (mDimShown) {
            mDimShown = false;
            try {
                if (WindowManagerService.SHOW_TRANSACTIONS) Slog.i(WindowManagerService.TAG, "  HIDE " + mDimSurface);
                mDimSurface.hide();
            } catch (RuntimeException e) {
                Slog.w(WindowManagerService.TAG, "Illegal argument exception hiding dim surface");
            }
        }
    }

    void kill() {
        if (mDimSurface != null) {
            mDimSurface.destroy();
            mDimSurface = null;
        }
    }

    public void printTo(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("mDimSurface="); pw.println(mDimSurface);
        pw.print(prefix); pw.print("mDimShown="); pw.print(mDimShown);
                pw.print(" mLayer="); pw.print(mLayer);
                pw.print(" mDimColor=0x"); pw.println(Integer.toHexString(mDimColor));
        pw.print(prefix); pw.print("mLastDimWidth="); pw.print(mLastDimWidth);
                pw.print(" mLastDimWidth="); pw.println(mLastDimWidth);
    }
}
