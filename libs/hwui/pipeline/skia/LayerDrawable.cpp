/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "LayerDrawable.h"
#include "GlLayer.h"
#include "VkLayer.h"

#include "GrBackendSurface.h"
#include "SkColorFilter.h"
#include "SkSurface.h"
#include "gl/GrGLTypes.h"

namespace android {
namespace uirenderer {
namespace skiapipeline {

void LayerDrawable::onDraw(SkCanvas* canvas) {
    Layer* layer = mLayerUpdater->backingLayer();
    if (layer) {
        DrawLayer(canvas->getGrContext(), canvas, layer);
    }
}

bool LayerDrawable::DrawLayer(GrContext* context, SkCanvas* canvas, Layer* layer,
                              const SkRect* dstRect) {
    if (context == nullptr) {
        SkDEBUGF(("Attempting to draw LayerDrawable into an unsupported surface"));
        return false;
    }
    // transform the matrix based on the layer
    SkMatrix layerTransform;
    layer->getTransform().copyTo(layerTransform);
    sk_sp<SkImage> layerImage;
    const int layerWidth = layer->getWidth();
    const int layerHeight = layer->getHeight();
    const int bufferWidth = layer->getBufferWidth();
    const int bufferHeight = layer->getBufferHeight();
    if (bufferWidth <= 0 || bufferHeight <=0) {
        return false;
    }
    if (layer->getApi() == Layer::Api::OpenGL) {
        GlLayer* glLayer = static_cast<GlLayer*>(layer);
        GrGLTextureInfo externalTexture;
        externalTexture.fTarget = glLayer->getRenderTarget();
        externalTexture.fID = glLayer->getTextureId();
        // The format may not be GL_RGBA8, but given the DeferredLayerUpdater and GLConsumer don't
        // expose that info we use it as our default.  Further, given that we only use this texture
        // as a source this will not impact how Skia uses the texture.  The only potential affect
        // this is anticipated to have is that for some format types if we are not bound as an OES
        // texture we may get invalid results for SKP capture if we read back the texture.
        externalTexture.fFormat = GL_RGBA8;
        GrBackendTexture backendTexture(bufferWidth, bufferHeight, GrMipMapped::kNo, externalTexture);
        layerImage = SkImage::MakeFromTexture(context, backendTexture, kTopLeft_GrSurfaceOrigin,
                                              kPremul_SkAlphaType, nullptr);
    } else {
        SkASSERT(layer->getApi() == Layer::Api::Vulkan);
        VkLayer* vkLayer = static_cast<VkLayer*>(layer);
        canvas->clear(SK_ColorGREEN);
        layerImage = vkLayer->getImage();
    }

    if (layerImage) {
        SkMatrix textureMatrixInv;
        layer->getTexTransform().copyTo(textureMatrixInv);
        // TODO: after skia bug https://bugs.chromium.org/p/skia/issues/detail?id=7075 is fixed
        // use bottom left origin and remove flipV and invert transformations.
        SkMatrix flipV;
        flipV.setAll(1, 0, 0, 0, -1, 1, 0, 0, 1);
        textureMatrixInv.preConcat(flipV);
        textureMatrixInv.preScale(1.0f / layerWidth, 1.0f / layerHeight);
        textureMatrixInv.postScale(bufferWidth, bufferHeight);
        SkMatrix textureMatrix;
        if (!textureMatrixInv.invert(&textureMatrix)) {
            textureMatrix = textureMatrixInv;
        }

        SkMatrix matrix;
        if (dstRect) {
            // Destination rectangle is set only when we are trying to read back the content
            // of the layer. In this case we don't want to apply layer transform.
            matrix = textureMatrix;
        } else {
            matrix = SkMatrix::Concat(layerTransform, textureMatrix);
        }

        SkPaint paint;
        paint.setAlpha(layer->getAlpha());
        paint.setBlendMode(layer->getMode());
        paint.setColorFilter(layer->getColorSpaceWithFilter());

        const bool nonIdentityMatrix = !matrix.isIdentity();
        if (nonIdentityMatrix) {
            canvas->save();
            canvas->concat(matrix);
        }
        const SkMatrix& totalMatrix = canvas->getTotalMatrix();
        if (dstRect) {
            SkMatrix matrixInv;
            if (!matrix.invert(&matrixInv)) {
                matrixInv = matrix;
            }
            SkRect srcRect = SkRect::MakeIWH(layerWidth, layerHeight);
            matrixInv.mapRect(&srcRect);
            SkRect skiaDestRect = *dstRect;
            matrixInv.mapRect(&skiaDestRect);
            // If (matrix is identity or an integer translation) and (src/dst buffers size match),
            // then use nearest neighbor, otherwise use bilerp sampling.
            // Integer translation is defined as when src rect and dst rect align fractionally.
            // Skia TextureOp has the above logic build-in, but not NonAAFillRectOp. TextureOp works
            // only for SrcOver blending and without color filter (readback uses Src blending).
            bool isIntegerTranslate = totalMatrix.isTranslate()
                    && SkScalarFraction(skiaDestRect.fLeft + totalMatrix[SkMatrix::kMTransX])
                    == SkScalarFraction(srcRect.fLeft)
                    && SkScalarFraction(skiaDestRect.fTop + totalMatrix[SkMatrix::kMTransY])
                    == SkScalarFraction(srcRect.fTop);
            if (layer->getForceFilter() || !isIntegerTranslate) {
                paint.setFilterQuality(kLow_SkFilterQuality);
            }
            canvas->drawImageRect(layerImage.get(), srcRect, skiaDestRect, &paint,
                                  SkCanvas::kFast_SrcRectConstraint);
        } else {
            bool isIntegerTranslate = totalMatrix.isTranslate()
                    && SkScalarIsInt(totalMatrix[SkMatrix::kMTransX])
                    && SkScalarIsInt(totalMatrix[SkMatrix::kMTransY]);
            if (layer->getForceFilter() || !isIntegerTranslate) {
                paint.setFilterQuality(kLow_SkFilterQuality);
            }
            canvas->drawImage(layerImage.get(), 0, 0, &paint);
        }
        // restore the original matrix
        if (nonIdentityMatrix) {
            canvas->restore();
        }
    }

    return layerImage;
}

};  // namespace skiapipeline
};  // namespace uirenderer
};  // namespace android
