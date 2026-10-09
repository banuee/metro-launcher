package dev.metro.launcher.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

enum class MetroIconShape(val key: String, val title: String) {
    DEFAULT("DEFAULT", "Оригинал"),
    SQUIRCLE("SQUIRCLE", "Сквиркл"),
    CIRCLE("CIRCLE", "Круг"),
    ROUNDED("ROUNDED", "Скругленный"),
    SQUARE("SQUARE", "Квадрат"),
}

object IconShapeHelper {
    fun getShape(shapeKey: String): Shape? {
        return when (shapeKey.uppercase()) {
            "CIRCLE" -> CircleShape
            "SQUIRCLE" -> RoundedCornerShape(percent = 32)
            "ROUNDED" -> RoundedCornerShape(percent = 20)
            "SQUARE" -> RoundedCornerShape(percent = 6)
            else -> null
        }
    }

    fun applyShape(modifier: Modifier, shapeKey: String): Modifier {
        val shape = getShape(shapeKey) ?: return modifier
        return modifier.clip(shape)
    }
}
