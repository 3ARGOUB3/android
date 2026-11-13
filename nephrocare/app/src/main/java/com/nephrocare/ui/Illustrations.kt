package com.nephrocare.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.nephrocare.R

private val Artery = Color(0xFFD84343)
private val Vein = Color(0xFF2F6FD0)
private val Skin = Color(0xFFF2CDB0)
private val KidneyColor = Color(0xFFB5523B)
private val Dialysate = Color(0xFFE0B020)

private fun DrawScope.label(text: String, x: Float, y: Float, color: Color, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = size.width / 26f
        textAlign = align
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** A forearm with an arteriovenous fistula and the two dialysis needles. */
@Composable
fun FistulaIllustration(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val tArtery = stringResource(R.string.illus_artery)
    val tVein = stringResource(R.string.illus_vein_arterialized)
    val tAnast = stringResource(R.string.illus_anastomosis)
    val tNeedleA = stringResource(R.string.illus_needle_arterial)
    val tNeedleV = stringResource(R.string.illus_needle_venous)
    val tHand = stringResource(R.string.illus_hand)
    Canvas(modifier.aspectRatio(1.6f)) {
        val w = size.width
        val h = size.height
        // Forearm and hand
        drawRoundRect(Skin, Offset(w * 0.12f, h * 0.30f), Size(w * 0.84f, h * 0.40f), CornerRadius(h * 0.2f))
        drawOval(Skin, Offset(w * 0.0f, h * 0.27f), Size(w * 0.2f, h * 0.46f))
        label(tHand, w * 0.09f, h * 0.84f, ink)
        // Artery (deep, thin) and arterialised vein (superficial, wide)
        val artery = Path().apply { moveTo(w * 0.2f, h * 0.60f); cubicTo(w * 0.45f, h * 0.62f, w * 0.7f, h * 0.60f, w * 0.95f, h * 0.62f) }
        drawPath(artery, Artery, style = Stroke(width = h * 0.035f, cap = StrokeCap.Round))
        val vein = Path().apply { moveTo(w * 0.24f, h * 0.58f); cubicTo(w * 0.3f, h * 0.40f, w * 0.6f, h * 0.38f, w * 0.95f, h * 0.40f) }
        drawPath(vein, Vein, style = Stroke(width = h * 0.07f, cap = StrokeCap.Round))
        // Anastomosis
        drawCircle(ink, radius = h * 0.045f, center = Offset(w * 0.24f, h * 0.59f), style = Stroke(width = 4f))
        // Thrill waves
        for (i in 1..3) {
            drawArc(ink.copy(alpha = 0.5f), -60f, 120f, false,
                Offset(w * 0.24f + i * h * 0.03f - h * 0.08f, h * 0.51f - i * h * 0.01f), Size(h * 0.16f, h * 0.16f),
                style = Stroke(width = 3f))
        }
        // Needles
        fun needle(x: Float, color: Color) {
            rotate(-35f, Offset(x, h * 0.40f)) {
                drawLine(Color.Gray, Offset(x, h * 0.40f), Offset(x, h * 0.13f), strokeWidth = h * 0.018f, cap = StrokeCap.Round)
                drawRect(color, Offset(x - h * 0.03f, h * 0.06f), Size(h * 0.06f, h * 0.08f))
            }
        }
        needle(w * 0.48f, Artery)
        needle(w * 0.74f, Vein)
        label(tNeedleA, w * 0.40f, h * 0.10f, Artery, bold = true)
        label(tNeedleV, w * 0.70f, h * 0.10f, Vein, bold = true)
        label(tVein, w * 0.62f, h * 0.30f, Vein)
        label(tArtery, w * 0.62f, h * 0.80f, Artery)
        label(tAnast, w * 0.30f, h * 0.95f, ink)
    }
}

/** Kidneys, ureters and bladder. */
@Composable
fun KidneyIllustration(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val tKidney = stringResource(R.string.illus_kidney)
    val tUreter = stringResource(R.string.illus_ureter)
    val tBladder = stringResource(R.string.illus_bladder)
    val tAorta = stringResource(R.string.illus_aorta)
    val tCava = stringResource(R.string.illus_vena_cava)
    Canvas(modifier.aspectRatio(1.1f)) {
        val w = size.width
        val h = size.height
        // Aorta and vena cava
        drawLine(Artery, Offset(w * 0.47f, h * 0.02f), Offset(w * 0.47f, h * 0.70f), strokeWidth = w * 0.035f)
        drawLine(Vein, Offset(w * 0.54f, h * 0.02f), Offset(w * 0.54f, h * 0.70f), strokeWidth = w * 0.035f)
        // Renal vessels
        drawLine(Artery, Offset(w * 0.47f, h * 0.30f), Offset(w * 0.27f, h * 0.30f), strokeWidth = w * 0.02f)
        drawLine(Artery, Offset(w * 0.47f, h * 0.30f), Offset(w * 0.73f, h * 0.30f), strokeWidth = w * 0.02f)
        drawLine(Vein, Offset(w * 0.54f, h * 0.34f), Offset(w * 0.27f, h * 0.34f), strokeWidth = w * 0.02f)
        drawLine(Vein, Offset(w * 0.54f, h * 0.34f), Offset(w * 0.73f, h * 0.34f), strokeWidth = w * 0.02f)
        // Kidneys (bean shapes)
        fun kidney(cx: Float, mirror: Boolean) {
            val s = if (mirror) -1f else 1f
            val p = Path().apply {
                moveTo(cx + s * w * 0.06f, h * 0.12f)
                cubicTo(cx - s * w * 0.16f, h * 0.06f, cx - s * w * 0.20f, h * 0.52f, cx + s * w * 0.04f, h * 0.52f)
                cubicTo(cx + s * w * 0.12f, h * 0.52f, cx + s * w * 0.10f, h * 0.40f, cx + s * w * 0.07f, h * 0.36f)
                cubicTo(cx + s * w * 0.04f, h * 0.32f, cx + s * w * 0.05f, h * 0.27f, cx + s * w * 0.08f, h * 0.24f)
                cubicTo(cx + s * w * 0.13f, h * 0.18f, cx + s * w * 0.12f, h * 0.13f, cx + s * w * 0.06f, h * 0.12f)
                close()
            }
            drawPath(p, KidneyColor)
        }
        kidney(w * 0.20f, false)
        kidney(w * 0.80f, true)
        // Ureters and bladder
        val ureterL = Path().apply { moveTo(w * 0.26f, h * 0.40f); cubicTo(w * 0.30f, h * 0.60f, w * 0.38f, h * 0.75f, w * 0.44f, h * 0.82f) }
        val ureterR = Path().apply { moveTo(w * 0.74f, h * 0.40f); cubicTo(w * 0.70f, h * 0.60f, w * 0.62f, h * 0.75f, w * 0.56f, h * 0.82f) }
        drawPath(ureterL, Dialysate, style = Stroke(width = w * 0.012f))
        drawPath(ureterR, Dialysate, style = Stroke(width = w * 0.012f))
        drawOval(Dialysate.copy(alpha = 0.8f), Offset(w * 0.38f, h * 0.78f), Size(w * 0.24f, h * 0.18f))
        label(tKidney, w * 0.15f, h * 0.60f, ink, bold = true)
        label(tKidney, w * 0.85f, h * 0.60f, ink, bold = true)
        label(tUreter, w * 0.22f, h * 0.76f, ink)
        label(tBladder, w * 0.50f, h * 0.995f, ink)
        label(tAorta, w * 0.40f, h * 0.06f, Artery, align = Paint.Align.RIGHT)
        label(tCava, w * 0.61f, h * 0.06f, Vein, align = Paint.Align.LEFT)
    }
}

/** Blood leaves the arm, is pumped through the dialyser and comes back; dialysate flows the other way. */
@Composable
fun DialysisIllustration(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val tPatient = stringResource(R.string.illus_patient)
    val tPump = stringResource(R.string.illus_pump)
    val tDialyzer = stringResource(R.string.illus_dialyzer)
    val tFresh = stringResource(R.string.illus_fresh_dialysate)
    val tUsed = stringResource(R.string.illus_used_dialysate)
    val tToMachine = stringResource(R.string.illus_blood_out)
    val tBack = stringResource(R.string.illus_blood_back)
    Canvas(modifier.aspectRatio(1.4f)) {
        val w = size.width
        val h = size.height
        // Patient's arm
        drawRoundRect(Skin, Offset(w * 0.03f, h * 0.40f), Size(w * 0.22f, h * 0.18f), CornerRadius(h * 0.09f))
        label(tPatient, w * 0.14f, h * 0.68f, ink, bold = true)
        // Arterial line to pump
        val out = Path().apply { moveTo(w * 0.15f, h * 0.42f); cubicTo(w * 0.18f, h * 0.15f, w * 0.30f, h * 0.15f, w * 0.40f, h * 0.18f) }
        drawPath(out, Artery, style = Stroke(width = h * 0.022f))
        label(tToMachine, w * 0.22f, h * 0.12f, Artery)
        // Pump
        drawCircle(Color.Gray, radius = h * 0.08f, center = Offset(w * 0.46f, h * 0.20f))
        drawCircle(Color.White, radius = h * 0.05f, center = Offset(w * 0.46f, h * 0.20f))
        drawLine(Color.DarkGray, Offset(w * 0.46f - h * 0.05f, h * 0.20f), Offset(w * 0.46f + h * 0.05f, h * 0.20f), strokeWidth = 5f)
        label(tPump, w * 0.46f, h * 0.36f, ink)
        // Line to dialyser top
        drawLine(Artery, Offset(w * 0.52f, h * 0.18f), Offset(w * 0.72f, h * 0.18f), strokeWidth = h * 0.022f)
        // Dialyser
        drawRoundRect(Color(0xFFDDE8F3), Offset(w * 0.68f, h * 0.18f), Size(w * 0.09f, h * 0.56f), CornerRadius(w * 0.03f))
        for (i in 1..5) {
            val x = w * 0.68f + i * w * 0.015f
            drawLine(Color(0xFF9DB6CF), Offset(x, h * 0.22f), Offset(x, h * 0.70f), strokeWidth = 2f)
        }
        label(tDialyzer, w * 0.725f, h * 0.84f, ink, bold = true)
        // Venous line back to the arm
        val back = Path().apply { moveTo(w * 0.725f, h * 0.74f); cubicTo(w * 0.70f, h * 0.95f, w * 0.30f, h * 0.95f, w * 0.20f, h * 0.56f) }
        drawPath(back, Vein, style = Stroke(width = h * 0.022f))
        label(tBack, w * 0.42f, h * 0.97f, Vein)
        // Dialysate in (bottom) and out (top), against the blood flow
        drawLine(Dialysate, Offset(w * 0.98f, h * 0.66f), Offset(w * 0.77f, h * 0.66f), strokeWidth = h * 0.018f)
        drawLine(Dialysate.copy(alpha = 0.6f), Offset(w * 0.77f, h * 0.28f), Offset(w * 0.98f, h * 0.28f), strokeWidth = h * 0.018f)
        label(tFresh, w * 0.88f, h * 0.62f, ink)
        label(tUsed, w * 0.88f, h * 0.25f, ink)
    }
}
