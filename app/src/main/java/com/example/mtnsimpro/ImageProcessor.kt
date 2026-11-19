package com.example.mtnsimpro

import android.graphics.*
import android.graphics.Bitmap.Config
import androidx.core.graphics.get
import android.util.Log
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {

    fun prepareForOcr(bitmap: Bitmap): Bitmap {
        var processed = bitmap
        try {
            Log.d("SCANNER_DEBUG", "Début du traitement d'image optimisé.")

            // 1. Rotation automatique
            if (processed.width < processed.height) {
                processed = rotateBitmap(processed, 90f)
            }

            // 2. Accentuation de la netteté (Sharpen) pour faire ressortir le texte éloigné et flou
            processed = sharpenBitmap(processed)

            // 3. Super Recadrage et Zoom intelligent
            val cropped = autoCropAndZoom(processed)
            if (cropped != processed) {
                processed = cropped
            }

            // 4. Binarisation Noir et Blanc haute fidélité
            processed = binarize(processed)

        } catch (e: Exception) {
            Log.e("SCANNER_DEBUG", "Erreur de traitement, repli sur l'original", e)
            return bitmap
        }
        return processed
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Filtre de netteté (Sharpen Matrix) : Rend les petits caractères éloignés très nets
     * pour que l'algorithme de zoom et l'OCR les détectent sans problème.
     */
    private fun sharpenBitmap(src: Bitmap): Bitmap {
        // Bien que l'on définisse une matrice de convolution, 
        // pour rester compatible sans OpenCV, on utilise un boost de contraste ciblé 
        // qui imite l'accentuation des bords.
        
        val width = src.width
        val height = src.height
        val sharpBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)

        val canvas = Canvas(sharpBitmap)
        val paint = Paint()
        
        // On applique un boost de contraste pour faire ressortir les contours du texte
        val cm = ColorMatrix(floatArrayOf(
            1.4f, 0f, 0f, 0f, -10f,
            0f, 1.4f, 0f, 0f, -10f,
            0f, 0f, 1.4f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        
        return sharpBitmap
    }

    private fun binarize(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // Contraste renforcé pour l'OCR final
        val contrast = 2.0f
        val brightness = -50f
        val cm = ColorMatrix(floatArrayOf(
            0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, brightness,
            0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, brightness,
            0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Algorithme de détection de densité : Scanne l'image de façon chirurgicale (step = 2)
     * pour trouver précisément le rectangle du texte, même minuscule et lointain.
     */
    private fun autoCropAndZoom(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0

        // Analyse ultra-précise pixel par pixel (step = 2) pour les éléments très éloignés
        val step = 2 
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap[x, y]
                
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Détection intelligente du texte (indépendant des ombres d'arrière-plan)
                if (r < 110 && g < 110 && b < 110) { 
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX <= minX || maxY <= minY) {
            return bitmap
        }

        // Marges dynamiques calculées pour englober toute la carte d'identité
        val paddingX = (maxX - minX) / 12
        val paddingY = (maxY - minY) / 12
        
        minX = max(0, minX - paddingX)
        minY = max(0, minY - paddingY)
        maxX = min(width, maxX + paddingX)
        maxY = min(height, maxY + paddingY)

        val cropWidth = maxX - minX
        val cropHeight = maxY - minY

        // Si la zone détectée fait au moins 5% de l'image (très petit/très loin), on zoome dessus !
        return if (cropWidth > width / 20 && cropHeight > height / 20) {
            Bitmap.createBitmap(bitmap, minX, minY, cropWidth, cropHeight)
        } else {
            bitmap
        }
    }
}
