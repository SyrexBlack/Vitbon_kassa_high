package com.vitbon.kkm.features.rootdetection.domain

import android.content.Context

interface RootDetector {
    suspend fun check(context: Context): RootCheckResult
}