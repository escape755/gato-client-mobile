package com.gato.client.util

import com.gato.client.game.TranslationManager
import java.util.Locale

inline val String.translatedSelf: String
    get() {
        return TranslationManager.getTranslationMap(Locale.getDefault().language)[this]
            ?: this
    }