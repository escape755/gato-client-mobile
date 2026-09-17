package com.gato.client.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gato.client.R

enum class ModuleCategory(
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int
) {

    Combat(
        iconResId = R.drawable.swords_24px,
        labelResId = R.string.combat
    ),
    Motion(
        iconResId = R.drawable.sprint_24px,
        labelResId = R.string.motion
    ),
    Visual(
        iconResId = R.drawable.view_in_ar_24px,
        labelResId = R.string.visual
    ),
    Effect(
        iconResId = R.drawable.masked_transitions_24px,
        labelResId = R.string.effect
    ),
    Particle(
        iconResId = R.drawable.particles_24px,
        labelResId = R.string.particle
    ),
    Misc(
        iconResId = R.drawable.toc_24px,
        labelResId = R.string.misc
    )
}