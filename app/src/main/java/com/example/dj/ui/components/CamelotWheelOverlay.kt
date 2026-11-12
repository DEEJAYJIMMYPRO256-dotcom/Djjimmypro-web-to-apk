package com.example.dj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object CamelotHelper {
  private val keyMap =
    mapOf(
      "Am" to "8A",
      "C" to "8B",
      "Em" to "9A",
      "G" to "9B",
      "Bm" to "10A",
      "D" to "10B",
      "F#m" to "11A",
      "A" to "11B",
      "C#m" to "12A",
      "E" to "12B",
      "G#m" to "1A",
      "B" to "1B",
      "D#m" to "2A",
      "F#" to "2B",
      "Bbm" to "3A",
      "Db" to "3B",
      "Fm" to "4A",
      "Ab" to "4B",
      "Cm" to "5A",
      "Eb" to "5B",
      "Gm" to "6A",
      "Bb" to "6B",
      "Dm" to "7A",
      "F" to "7B",
    )

  fun toCamelot(musicalKey: String): String = keyMap[musicalKey] ?: "8A"

  fun compatibleCamelotKeys(camelotKey: String): Set<String> {
    val num = camelotKey.dropLast(1).toIntOrNull() ?: return setOf(camelotKey)
    val letter = camelotKey.lastOrNull() ?: 'A'
    val prev = if (num == 1) 12 else num - 1
    val next = if (num == 12) 1 else num + 1
    val otherLetter = if (letter == 'A') 'B' else 'A'
    return setOf("$num$letter", "$prev$letter", "$next$letter", "$num$otherLetter")
  }

  fun isCompatible(referenceMusicalKey: String, candidateMusicalKey: String): Boolean {
    val refCam = toCamelot(referenceMusicalKey)
    val candCam = toCamelot(candidateMusicalKey)
    return candCam in compatibleCamelotKeys(refCam)
  }
}

@Composable
fun CamelotWheelOverlay(
  referenceKey: String,
  accentColor: Color,
  onDismiss: () -> Unit,
) {
  val refCamelot = CamelotHelper.toCamelot(referenceKey)
  val compatible = CamelotHelper.compatibleCamelotKeys(refCamelot)

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "CAMELOT HARMONIC WHEEL",
            style = MaterialTheme.typography.titleSmall,
            color = accentColor,
          )
          Text(
            text = "Reference: $referenceKey ($refCamelot) • Compatible: ${compatible.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(onClick = onDismiss) { Text("Close") }
      }

      Spacer(Modifier.height(12.dp))

      Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
          val cx = size.width / 2f
          val cy = size.height / 2f
          val outerR = size.minDimension * 0.43f
          val innerR = size.minDimension * 0.27f

          drawCircle(
            color = Color(0xFF2D384C),
            radius = outerR,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
          )
          drawCircle(
            color = Color(0xFF2D384C),
            radius = innerR,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
          )

          for (hour in 1..12) {
            val angleRad = ((hour - 3) * 30.0) * PI / 180.0
            val outerNode =
              Offset(
                x = cx + outerR * cos(angleRad).toFloat(),
                y = cy + outerR * sin(angleRad).toFloat(),
              )
            val innerNode =
              Offset(
                x = cx + innerR * cos(angleRad).toFloat(),
                y = cy + innerR * sin(angleRad).toFloat(),
              )

            val outerKey = "${hour}B"
            val innerKey = "${hour}A"

            val outerColor =
              when {
                outerKey == refCamelot -> accentColor
                outerKey in compatible -> Color(0xFF00E676)
                else -> Color(0xFF2B3445)
              }
            val innerColor =
              when {
                innerKey == refCamelot -> accentColor
                innerKey in compatible -> Color(0xFF00E676)
                else -> Color(0xFF2B3445)
              }

            drawCircle(color = outerColor, radius = 12.dp.toPx(), center = outerNode)
            drawCircle(color = innerColor, radius = 10.dp.toPx(), center = innerNode)
          }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = refCamelot,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
          )
          Text(
            text = referenceKey,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(color = accentColor, label = "Active ($refCamelot)")
        LegendDot(color = Color(0xFF00E676), label = "Harmonic Match")
      }
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier =
        Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp))
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
