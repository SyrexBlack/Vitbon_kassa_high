package com.vitbon.kkm.features.rootdetection.domain

data class RootIndicator(val type: String, val detail: String)

sealed class RootCheckResult {
    object Clean : RootCheckResult()
    data class Detected(val indicators: List<RootIndicator>) : RootCheckResult()
}