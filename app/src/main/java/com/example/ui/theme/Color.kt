package com.amresalehin.emreshots.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Emreh Global Visual Design System - Palette & Tokens
// Core Principle: Soft, tactile, monochromatic, atmospheric, and functional.
// =========================================================================

// Official Design Reference Palette
val SoftWhite = Color(0xFFF5F5F3)
val MistGray = Color(0xFFD9DAD7)
val NeutralGray = Color(0xFF8C8C8A)
val Charcoal = Color(0xFF414141)
val NearBlack = Color(0xFF202020)
val SeaGreen = Color(0xFF78B7A7) // Principal Accent
val LightPink = Color(0xFFF1D7DD) // Secondary Accent

// Light Appearance Tokens (Neutral-dominated, soft, tactile)
val BackgroundLight = SoftWhite
val OnBackgroundLight = NearBlack

val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = NearBlack

val SurfaceVariantLight = Color(0xFFEBECE9)
val OnSurfaceVariantLight = Color(0xFF5A5C59)

val SurfaceContainerLight = Color(0xFFF0F1EE)
val SurfaceContainerHighLight = Color(0xFFE4E5E2)

val OutlineLight = MistGray
val OutlineVariantLight = Color(0xFFE3E5E1)

// Sea Green as Principal Accent (selective and purposeful)
val PrimaryLight = Color(0xFF427B6E) // Accessible contrast on Soft White/White
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE1EFEA) // Soft sea-green wash
val OnPrimaryContainerLight = Color(0xFF153F36)

// Light Pink as Secondary Accent (selective and purposeful)
val SecondaryLight = Color(0xFF945F6B) // Refined muted rose with high legibility
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFF9E8EB) // Soft pink wash
val OnSecondaryContainerLight = Color(0xFF422129)

val TertiaryLight = Color(0xFF5E7972)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFE4EDE9)
val OnTertiaryContainerLight = Color(0xFF1D352F)

// Dark Appearance Tokens (Near-black, atmospheric, soft tactile depth)
val BackgroundDark = Color(0xFF161716)
val OnBackgroundDark = SoftWhite

val SurfaceDark = Color(0xFF1F2120)
val OnSurfaceDark = SoftWhite

val SurfaceVariantDark = Color(0xFF2B2D2C)
val OnSurfaceVariantDark = Color(0xFFB4B6B1)

val SurfaceContainerDark = Color(0xFF252726)
val SurfaceContainerHighDark = Color(0xFF323433)

val OutlineDark = Color(0xFF3E413F)
val OutlineVariantDark = Color(0xFF2C2F2D)

// Sea Green Principal Accent in Dark Appearance
val PrimaryDark = SeaGreen // #78B7A7
val OnPrimaryDark = Color(0xFF122E26)
val PrimaryContainerDark = Color(0xFF22433A)
val OnPrimaryContainerDark = Color(0xFFC7E8DE)

// Light Pink Secondary Accent in Dark Appearance
val SecondaryDark = LightPink // #F1D7DD
val OnSecondaryDark = Color(0xFF44252D)
val SecondaryContainerDark = Color(0xFF4B3238)
val OnSecondaryContainerDark = Color(0xFFFCEBED)

val TertiaryDark = Color(0xFFA6C4BB)
val OnTertiaryDark = Color(0xFF1A352F)
val TertiaryContainerDark = Color(0xFF314943)
val OnTertiaryContainerDark = Color(0xFFD6EAE3)

// Functional Status Tokens (calm, desaturated, non-jarring)
val SuccessEmerald = Color(0xFF5BAA93)
val WarningAmber = Color(0xFFD49B55)
val DangerRose = Color(0xFFC46A74)

// Atmospheric Scrim & Glass Overlays (selective, delicate)
val AtmosphericDarkScrim = Color(0xCC161716)
val AtmosphericLightScrim = Color(0xCCF5F5F3)
val DelicateBorderLight = Color(0x1F202020)
val DelicateBorderDark = Color(0x2EFFFFFF)
