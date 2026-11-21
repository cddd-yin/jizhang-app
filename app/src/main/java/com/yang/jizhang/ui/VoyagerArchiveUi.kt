package com.yang.jizhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yang.jizhang.R

internal val ArchiveInk = Color(0xFF292631)

@Composable
internal fun VoyagerPvWordmark(modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(R.drawable.voyager_pv_wordmark),
        contentDescription = "Voyager Ledger",
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.aspectRatio(880f / 332f),
    )
}

@Composable
internal fun VoyagerMusicStaff(modifier: Modifier = Modifier, fullPhrase: Boolean = false) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(if (fullPhrase) 42.dp else 24.dp)) {
        if (size.width < 70.dp.toPx()) return@Canvas
        val decorated = fullPhrase && size.width >= 196.dp.toPx()
        val gap = if (fullPhrase) 4.8.dp.toPx() else size.height * .15f
        val staffTop = if (fullPhrase) (size.height - 4f * gap) / 2f else size.height * .22f
        val phraseStart = if (decorated) 20.dp.toPx() else 0f
        val phraseEnd = size.width - if (decorated) 25.dp.toPx() else 0f
        val phraseWidth = phraseEnd - phraseStart
        val inset = 2.dp.toPx()
        val lineWidth = .75.dp.toPx()
        val staffInk = Brush.horizontalGradient(
            0f to ink.copy(alpha = .10f),
            .16f to ink.copy(alpha = .39f),
            .84f to ink.copy(alpha = .39f),
            1f to ink.copy(alpha = .10f),
        )
        repeat(5) { index ->
            val y = staffTop + index * gap
            drawLine(staffInk, Offset(inset, y), Offset(size.width - inset, y), lineWidth)
        }
        val noteWidth = 6.7.dp.toPx()
        val noteHeight = 4.2.dp.toPx()
        fun drawNote(position: Float, staffStep: Float, stemDown: Boolean = false): Offset {
            val x = phraseStart + phraseWidth * position
            val y = staffTop + staffStep * gap
            rotate(-22f, pivot = Offset(x, y)) {
                drawOval(ink, Offset(x - noteWidth / 2, y - noteHeight / 2), Size(noteWidth, noteHeight))
            }
            val direction = if (stemDown) 1f else -1f
            val stemX = x - direction * noteWidth * .4f
            val stemEnd = Offset(stemX, y + direction * 2.65f * gap)
            drawLine(ink, Offset(stemX, y + direction * .5.dp.toPx()), stemEnd,
                strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            return stemEnd
        }

        fun drawBeam(start: Offset, end: Offset) {
            drawLine(ink, start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }

        if (fullPhrase && size.width >= 120.dp.toPx()) {
            drawNote(.13f, 3.5f)
            drawBeam(drawNote(.24f, 2.5f), drawNote(.33f, 2f))
            drawNote(.43f, 3f)

            val barX = phraseStart + phraseWidth * .49f
            drawLine(ink.copy(alpha = .42f), Offset(barX, staffTop), Offset(barX, staffTop + 4f * gap), lineWidth)

            drawBeam(drawNote(.55f, 1.5f, stemDown = true), drawNote(.64f, .5f, stemDown = true))
            drawNote(.75f, 1.5f, stemDown = true)
            drawNote(.87f, 3f)
        } else {
            drawNote(.24f, 3.5f)
            drawBeam(drawNote(.44f, 2.75f), drawNote(.55f, 1.75f))
            drawNote(.76f, 2.25f)
        }

        if (decorated) {
            drawTrebleClef(ink, Offset(3.dp.toPx(), 1.dp.toPx()))
            drawStaffCoil(ink, Offset(size.width - 29.dp.toPx(), staffTop + 2f * gap))
        }
    }
}

private fun DrawScope.drawTrebleClef(ink: Color, origin: Offset) {
    val unit = 1.dp.toPx()
    withTransform({
        translate(origin.x, origin.y)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        val clef = Path().apply {
            moveTo(11.6f, 25.8f)
            cubicTo(8.5f, 24.9f, 8.5f, 21.1f, 11f, 19.3f)
            cubicTo(14.2f, 16.9f, 19.1f, 18.6f, 19.4f, 22.6f)
            cubicTo(19.9f, 27.8f, 15.3f, 31f, 10.1f, 30.1f)
            cubicTo(4.9f, 29.2f, 2.4f, 25.8f, 3.3f, 21.5f)
            cubicTo(4.1f, 17.7f, 7.8f, 14.8f, 11.9f, 10.6f)
            cubicTo(15.7f, 6.7f, 16.6f, 2.5f, 14.1f, 1f)
            cubicTo(11.5f, 2.6f, 10.1f, 6f, 10.4f, 10.8f)
            cubicTo(10.7f, 17.1f, 12.3f, 24.6f, 13.6f, 31f)
            cubicTo(14.6f, 35.9f, 15.6f, 38.7f, 11.8f, 39.8f)
            cubicTo(9.5f, 40.4f, 7.1f, 38.8f, 8.3f, 36.7f)
        }
        drawPath(clef, ink, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(ink, radius = 1.6f, center = Offset(8.3f, 36.7f))
    }
}

private fun DrawScope.drawStaffCoil(ink: Color, origin: Offset) {
    val unit = 1.dp.toPx()
    withTransform({
        translate(origin.x, origin.y)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        val rear = Path()
        val front = Path()
        repeat(3) { turn ->
            val x = turn * 6.6f
            rear.moveTo(x - .5f, 0f)
            rear.cubicTo(x - 1.1f, -2.8f, x + 2.7f, -4.15f, x + 4.95f, -4.15f)
            rear.cubicTo(x + 7.2f, -4.15f, x + 9.9f, -2.3f, x + 10.4f, 0f)

            front.moveTo(x + 3.8f, 0f)
            front.cubicTo(x + 3.8f, 2.3f, x + 3.9f, 4.15f, x + 1.65f, 4.15f)
            front.cubicTo(x - .8f, 4.15f, x - 1.4f, 1.8f, x - .5f, 0f)
        }
        val wire = Stroke(1.1f, cap = StrokeCap.Round)
        drawPath(rear, ink.copy(alpha = .48f), style = wire)
        drawPath(front, ink.copy(alpha = .90f), style = wire)
    }
}

@Composable
internal fun VoyagerPageHeading(title: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageTitle(title, Modifier.semantics { heading() })
        VoyagerMusicStaff(Modifier.weight(1f))
    }
}
