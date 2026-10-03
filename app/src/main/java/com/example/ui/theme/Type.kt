package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

@OptIn(ExperimentalTextApi::class)
val JournalTypeface =
    FontFamily(
        Font(
            R.font.manrope,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.manrope,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.manrope,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            R.font.manrope,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
        Font(
            R.font.manrope,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800)),
        ),
    )

val Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = JournalTypeface,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 52.sp,
                letterSpacing = (-2).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = JournalTypeface,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = JournalTypeface,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 30.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = JournalTypeface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyLarge = TextStyle(fontFamily = JournalTypeface, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = JournalTypeface, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = JournalTypeface, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge =
            TextStyle(fontFamily = JournalTypeface, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        labelMedium =
            TextStyle(
                fontFamily = JournalTypeface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = JournalTypeface,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
    )
