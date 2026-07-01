package com.sloopworks.dayfold.client

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

// The back-gesture SETTLE (commit/cancel) spec. A no-bounce spring, because the settle
// begins at the finger's partial position: a spring continues to rest over whatever distance
// remains (short → quick, long → smooth) with no front-load jump and no overshoot. MediumLow
// stiffness ≈ a calm ~300ms settle that matches the tap-open/close feel.
val BackSettleSpring: FiniteAnimationSpec<Float> =
  spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

// Standard decelerate (≈ PathInterpolator(0,0,0,1)). Google: feed the predictive-back
// preview a decelerate curve, never raw-linear progress, so motion is more apparent
// at gesture start.
private val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

fun decelerateProgress(raw: Float): Float = Decelerate.transform(raw.coerceIn(0f, 1f))
