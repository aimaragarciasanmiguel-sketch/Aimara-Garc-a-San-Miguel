package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

@Entity(tableName = "projects")
data class BookProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Standard sizes: A4 (210x297), A5 (148x210), US Trade Novel (152.4 x 228.6 / 6x9 inches)
    val pageWidthMm: Float = 152.4f,
    val pageHeightMm: Float = 228.6f,
    val marginMmLeft: Float = 15f,
    val marginMmRight: Float = 15f,
    val marginMmTop: Float = 20f,
    val marginMmBottom: Float = 20f,
    val bleedMm: Float = 3.175f, // 1/8 inch is a standard print bleed (approx 3.175mm)
    val showPageNumbers: Boolean = true,
    val pageNumbersStartAtPage: Int = 1,
    val pageNumbersStartFromValue: Int = 1,
    val ruledLinesEnabled: Boolean = false,
    val ruledLinesCount: Int = 30,
    val ruledLinesStartPage: Int = 1,
    val ruledLinesEndPage: Int = 370,
    val a5TextLimitEnabled: Boolean = false,
    val a5TextLimitStartPage: Int = 1,
    val a5TextLimitEndPage: Int = 370,
    val a5TextLimitWidthMm: Float = 110f,
    val a5TextLimitLines: Int = 30,
    val rulersEnabled: Boolean = true,
    val paddingMmLeft: Float = 5f,
    val paddingMmRight: Float = 5f,
    val paddingMmTop: Float = 5f,
    val paddingMmBottom: Float = 5f
)

@Entity(tableName = "pages")
data class BookPage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val pageNumber: Int,
    val elements: List<LayoutElement> = emptyList()
)

data class LayoutElement(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "text" | "image" | "shape"
    val xMm: Float,
    val yMm: Float,
    val widthMm: Float,
    val heightMm: Float,
    val rotation: Float = 0f,
    val zIndex: Int = 0,

    // Text features
    val textContent: String = "",
    val fontSizeSp: Float = 12f,
    val textColorHex: String = "#1A1A1A",
    val textAlignment: String = "LEFT", // LEFT, CENTER, RIGHT, JUSTIFY
    val fontFamily: String = "Times New Roman", // Times New Roman, Sans-Serif, Monospace
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val lineHeightMultiplier: Float = 1.3f,
    val textBackgroundColorHex: String = "#00000000", // Default clear
    val textBorderColorHex: String = "#00000000",
    val textBorderWidthMm: Float = 0f,

    // Image features
    val imageUrl: String = "",
    val imageScaleType: String = "CROP", // FIT or CROP
    val isPlaceholder: Boolean = true,
    val placeholderColorHex: String = "#E2E8F0",
    val isAspectLocked: Boolean = true,

    // Shape elements (rectangles, circles/ellipses)
    val shapeType: String = "RECTANGLE", // RECTANGLE, ELLIPSE, LINE
    val shapeFillColorHex: String = "#E2E8F0",
    val shapeStrokeColorHex: String = "#475569",
    val shapeStrokeWidthMm: Float = 1.0f
)

class BookConverters {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val listType = Types.newParameterizedType(List::class.java, LayoutElement::class.java)
    private val adapter = moshi.adapter<List<LayoutElement>>(listType)

    @TypeConverter
    fun fromElementsList(elements: List<LayoutElement>?): String {
        return adapter.toJson(elements ?: emptyList())
    }

    @TypeConverter
    fun toElementsList(json: String?): List<LayoutElement> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
