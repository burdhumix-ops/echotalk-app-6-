package co.znxdnd.echotalk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom vector-drawn Staunton Chess pieces using Compose Canvas Paths (0..100 normalized grid).
 * Crisp silhouettes with distinct White/Black fills and contrast rim strokes ensure readability
 * on both light and dark Tournament Slate squares at any resolution.
 */
@Composable
fun ChessVectorPiece(
    pieceCode: String,
    modifier: Modifier = Modifier,
    pieceSize: Dp = 36.dp
) {
    if (pieceCode.isBlank()) return
    val isWhite = pieceCode.first().isUpperCase()
    val kind = pieceCode.uppercase()

    val fillColor = if (isWhite) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val rimColor = if (isWhite) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val detailColor = if (isWhite) Color(0xFF334155) else Color(0xFF94A3B8)

    Canvas(modifier = modifier.size(pieceSize)) {
        val s = size.minDimension / 100f
        val ox = (size.width - 100f * s) * 0.5f
        val oy = (size.height - 100f * s) * 0.5f

        withTransform({
            translate(left = ox, top = oy)
            scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
        }) {
            val stroke = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val fineStroke = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            when (kind) {
                "P" -> drawPawn(fillColor, rimColor, detailColor, stroke, fineStroke)
                "R" -> drawRook(fillColor, rimColor, detailColor, stroke, fineStroke)
                "N" -> drawKnight(fillColor, rimColor, detailColor, stroke, fineStroke)
                "B" -> drawBishop(fillColor, rimColor, detailColor, stroke, fineStroke)
                "Q" -> drawQueen(fillColor, rimColor, detailColor, stroke, fineStroke)
                "K" -> drawKing(fillColor, rimColor, detailColor, stroke, fineStroke)
            }
        }
    }
}

private fun DrawScope.drawPedestal(
    fill: Color,
    rim: Color,
    detail: Color,
    stroke: Stroke,
    fineStroke: Stroke,
    topY: Float = 74f
) {
    val base = Path().apply {
        moveTo(24f, 88f)
        lineTo(76f, 88f)
        lineTo(76f, 82f)
        lineTo(70f, topY)
        lineTo(30f, topY)
        lineTo(24f, 82f)
        close()
    }
    drawPath(base, fill, style = Fill)
    drawPath(base, rim, style = stroke)
    drawLine(detail, Offset(27f, 81f), Offset(73f, 81f), strokeWidth = fineStroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawPawn(
    fill: Color,
    rim: Color,
    detail: Color,
    stroke: Stroke,
    fineStroke: Stroke
) {
    val body = Path().apply {
        moveTo(32f, 75f)
        quadraticTo(41f, 60f, 43f, 45f)
        lineTo(57f, 45f)
        quadraticTo(59f, 60f, 68f, 75f)
        close()
    }
    drawPath(body, fill, style = Fill)
    drawPath(body, rim, style = stroke)

    drawPedestal(fill, rim, detail, stroke, fineStroke, topY = 74f)

    // Collar
    val collar = Path().apply {
        moveTo(37f, 45f)
        lineTo(63f, 45f)
        lineTo(61f, 40f)
        lineTo(39f, 40f)
        close()
    }
    drawPath(collar, fill, style = Fill)
    drawPath(collar, rim, style = fineStroke)

    // Head sphere
    drawCircle(color = fill, radius = 13.5f, center = Offset(50f, 28f))
    drawCircle(color = rim, radius = 13.5f, center = Offset(50f, 28f), style = stroke)
}

private fun DrawScope.drawRook(
    fill: Color,
    rim: Color,
    detail: Color,
    stroke: Stroke,
    fineStroke: Stroke
) {
    val tower = Path().apply {
        moveTo(31f, 75f)
        lineTo(35f, 36f)
        lineTo(65f, 36f)
        lineTo(69f, 75f)
        close()
    }
    drawPath(tower, fill, style = Fill)
    drawPath(tower, rim, style = stroke)

    // Battlement crown with 3 merlons
    val crown = Path().apply {
        moveTo(28f, 36f)
        lineTo(28f, 18f)
        lineTo(37f, 18f)
        lineTo(37f, 25f)
        lineTo(45f, 25f)
        lineTo(45f, 18f)
        lineTo(55f, 18f)
        lineTo(55f, 25f)
        lineTo(63f, 25f)
        lineTo(63f, 18f)
        lineTo(72f, 18f)
        lineTo(72f, 36f)
        close()
    }
    drawPath(crown, fill, style = Fill)
    drawPath(crown, rim, style = stroke)

    drawLine(detail, Offset(34f, 42f), Offset(66f, 42f), strokeWidth = fineStroke.width, cap = StrokeCap.Round)
    drawPedestal(fill, rim, detail, stroke, fineStroke, topY = 74f)
}

private fun DrawScope.drawKnight(
    fill: Color,
    rim: Color,
    detail: Color,
    stroke: Stroke,
    fineStroke: Stroke
) {
    val horse = Path().apply {
        moveTo(31f, 75f)
        cubicTo(31f, 56f, 35f, 34f, 46f, 22f)
        lineTo(49f, 14f)
        lineTo(55f, 20f)
        lineTo(61f, 14f)
        lineTo(63f, 23f)
        cubicTo(74f, 28f, 78f, 38f, 74f, 46f)
        lineTo(63f, 48f)
        cubicTo(59f, 48f, 55f, 44f, 52f, 43f)
        cubicTo(55f, 53f, 64f, 60f, 68f, 75f)
        close()
    }
    drawPath(horse, fill, style = Fill)
    drawPath(horse, rim, style = stroke)

    // Eye & mane contour
    drawCircle(color = detail, radius = 2.2f, center = Offset(58f, 31f))
    drawLine(detail, Offset(40f, 36f), Offset(37f, 62f), strokeWidth = fineStroke.width, cap = StrokeCap.Round)

    drawPedestal(fill, rim, detail, stroke, fineStroke, topY = 74f)
}

private fun DrawScope.drawBishop(
    fill: Color,
    rim: Color,
    detail: Color,
    stroke: Stroke,
    fineStroke: Stroke
) {
    val stem = Path().apply {
        moveTo(32f, 75f)
        quadraticTo(40f, 61f, 41f, 50f)
        lineTo(59f, 50f)
        quadraticTo(60f, 61f, 68f, 75f)
        close()
    }
    drawPath(stem, fill, style = Fill)
    drawPath(stem, rim, style = stroke)

    // Mitre head
    val mitre = Path().apply {
        moveTo(39f, 48f)
        cubicTo(34f, 35f, 42f, 21f, 50f, 19f)
        cubicTo(58f, 21f, 66f, 35f, 61f, 48f)
        close()
    }
    drawPath(mitre, fill, style = Fill)
    drawPath(mitre, rim, style = stroke)

    // Diagonal mitre cleft & finial orb
    drawLine(detail, Offset(44f, 38f), Offset(56f, 27f), strokeWidth = 2.8f, cap = StrokeCap.Round)
    drawCircle(color = fill, radius = 4.5f, center = Offset(50f, 14f))
    drawCircle(color = rim, radius = 4.5f, center = Offset(50f, 14f), style = fineStroke)

    // Collar band
    drawLine(detail, Offset(38f, 50f), Offset(62f, 50f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawPedestal(fill, rim, detail, stroke, fineStroke, topY = 74f)
}

private fun DrawScope.drawQueen(
    fill: Color,
    rim: Color,
    detail: Color,
    stroke: Stroke,
    fineStroke: Stroke
) {
    val crownBody = Path().apply {
        moveTo(32f, 75f)
        lineTo(25f, 29f)
        lineTo(38f, 44f)
        lineTo(43f, 22f)
        lineTo(50f, 42f)
        lineTo(57f, 22f)
        lineTo(62f, 44f)
        lineTo(75f, 29f)
        lineTo(68f, 75f)
        close()
    }
    drawPath(crownBody, fill, style = Fill)
    drawPath(crownBody, rim, style = stroke)

    // Coronet pearl tips
    val tips = listOf(
        Offset(24f, 26f),
        Offset(43f, 19f),
        Offset(57f, 19f),
        Offset(76f, 26f)
    )
    tips.forEach { pt ->
        drawCircle(color = fill, radius = 3.6f, center = pt)
        drawCircle(color = rim, radius = 3.6f, center = pt, style = fineStroke)
    }

    drawLine(detail, Offset(34f, 66f), Offset(66f, 66f), strokeWidth = fineStroke.width, cap = StrokeCap.Round)
    drawPedestal(fill, rim, detail, stroke, fineStroke, topY = 74f)
}

private fun DrawScope.drawKing(
    fill: Color,
    rim: Color,
    detail: Color,
    stroke: Stroke,
    fineStroke: Stroke
) {
    // Royal Cross Finial
    drawLine(rim, Offset(50f, 8f), Offset(50f, 24f), strokeWidth = 4.2f, cap = StrokeCap.Round)
    drawLine(rim, Offset(43f, 14f), Offset(57f, 14f), strokeWidth = 4.0f, cap = StrokeCap.Round)
    drawLine(fill, Offset(50f, 9f), Offset(50f, 23f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawLine(fill, Offset(44f, 14f), Offset(56f, 14f), strokeWidth = 1.8f, cap = StrokeCap.Round)

    // Crown lobes & flared waist
    val kingBody = Path().apply {
        moveTo(32f, 75f)
        lineTo(29f, 42f)
        cubicTo(24f, 28f, 43f, 24f, 50f, 34f)
        cubicTo(57f, 24f, 76f, 28f, 71f, 42f)
        lineTo(68f, 75f)
        close()
    }
    drawPath(kingBody, fill, style = Fill)
    drawPath(kingBody, rim, style = stroke)

    drawLine(detail, Offset(50f, 34f), Offset(50f, 66f), strokeWidth = fineStroke.width, cap = StrokeCap.Round)
    drawLine(detail, Offset(34f, 66f), Offset(66f, 66f), strokeWidth = fineStroke.width, cap = StrokeCap.Round)
    drawPedestal(fill, rim, detail, stroke, fineStroke, topY = 74f)
}
