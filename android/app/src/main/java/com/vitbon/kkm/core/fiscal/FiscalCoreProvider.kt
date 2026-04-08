package com.vitbon.kkm.core.fiscal

import android.content.Context
import com.vitbon.kkm.core.fiscal.msposk.MSPOSKFiscalCore
import com.vitbon.kkm.core.fiscal.neva.Neva01FFiscalCore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Фабрика FiscalCore по модели устройства.
 * Выбирает реализацию на основе build-типа или ручной настройки.
 */
@Singleton
class FiscalCoreProvider @Inject constructor(
    private val context: Context,
    private val config: FiscalConfig
) {
    suspend fun get(): FiscalCore {
        return when (config.model) {
            FiscalDeviceModel.MSPOS_K -> {
                MSPOSKFiscalCore(context).also {
                    if (!it.initialize()) throw FiscalException(-1, "Failed to init MSPOS-K")
                }
            }
            FiscalDeviceModel.NEVA_01F -> {
                Neva01FFiscalCore(context).also {
                    if (!it.initialize()) throw FiscalException(-1, "Failed to init Нева 01Ф")
                }
            }
        }
    }
}

enum class FiscalDeviceModel {
    MSPOS_K,
    NEVA_01F
}

/** Конфигурация FiscalCore (ручная настройка в Settings) */
data class FiscalConfig(
    val model: FiscalDeviceModel = FiscalDeviceModel.MSPOS_K,
    val host: String = "localhost",
    val port: Int = 8443
)
