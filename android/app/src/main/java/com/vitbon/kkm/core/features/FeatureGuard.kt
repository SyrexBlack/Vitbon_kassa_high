package com.vitbon.kkm.core.features

/**
 * Аннотация для UI-элементов, доступных только при активном модуле.
 * Usage: @FeatureRequired(FeatureFlag.EGAAIS_ENABLED)
 */
@Retention(AnnotationRetention.SOURCE)
annotation class FeatureRequired(val flag: FeatureFlag)

/**
 * Проверка перед показом UI элемента ЕГАИС / ЧЗ.
 * Возвращает true если модуль доступен.
 */
inline fun FeatureManager.check(flag: FeatureFlag, block: () -> Unit) {
    if (isEnabledSync(flag)) block()
}

/**
 * Extension для лёгкой проверки в Compose.
 */
val FeatureFlag.isActive: Boolean
    get() = throw IllegalStateException("Use FeatureManager.isEnabledSync()")
