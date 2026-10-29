package dev.altayeb.anon

import android.animation.ValueAnimator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/**
 * One motion vocabulary for the whole app (keyboard Canvas views and the Compose settings screen).
 * Rules: key presses are instant; things entering decelerate, things leaving accelerate;
 * a stagger never runs past ~300 ms in total.
 */
object Motion {
    const val FAST = 120L      // feedback: key fade-out, suggestion fade, icon state
    const val NORMAL = 180L    // panels and cards entering
    const val EXIT = 140L      // things leaving (shorter than entering)
    const val STAGGER = 22L    // between list items

    val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)
    val DECELERATE = PathInterpolator(0f, 0f, 0.2f, 1f)  // entering
    val ACCELERATE = PathInterpolator(0.3f, 0f, 1f, 0.3f) // leaving
    val POP = OvershootInterpolator(1.8f)                  // long-press popup

    /** User switch AND the system "Remove animations" / animator scale 0. */
    fun enabled(prefsOn: Boolean): Boolean = prefsOn && ValueAnimator.areAnimatorsEnabled()
}
