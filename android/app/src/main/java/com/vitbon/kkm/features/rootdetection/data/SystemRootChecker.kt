package com.vitbon.kkm.features.rootdetection.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.features.rootdetection.domain.RootIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SystemRootChecker : RootDetector {

    override suspend fun check(context: Context): RootCheckResult = withContext(Dispatchers.IO) {
        val indicators = mutableListOf<RootIndicator>()

        if (detectSuBinary()) {
            indicators += RootIndicator("su_binary", "su binary found on filesystem")
        }
        if (detectMagiskApp(context)) {
            indicators += RootIndicator("magisk_app", "com.topjohnwu.magisk")
        }
        if (detectDangerousProps()) {
            indicators += RootIndicator("dangerous_props", "ro.debuggable=1 or ro.secure=0")
        }
        if (detectRwSystem()) {
            indicators += RootIndicator("rw_system", "writable system partition")
        }
        if (detectTestKeys()) {
            indicators += RootIndicator("test_keys", "build tags contain test-keys")
        }
        if (detectZygisk()) {
            indicators += RootIndicator("zygisk_detected", "Zygisk/Riru modules present")
        }

        if (indicators.isEmpty()) {
            RootCheckResult.Clean
        } else {
            RootCheckResult.Detected(indicators)
        }
    }

    private fun detectSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/vendor/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun detectMagiskApp(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.topjohnwu.magisk", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun detectDangerousProps(): Boolean {
        return try {
            val debuggable = isSystemPropertyTrue("ro.debuggable")
            val secure = isSystemPropertyTrue("ro.secure")
            debuggable || secure
        } catch (e: Exception) {
            false
        }
    }

    private fun isSystemPropertyTrue(prop: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", prop))
            val reader = process.inputStream.bufferedReader()
            val value = reader.readLine()?.trim()
            reader.close()
            value == "1"
        } catch (e: Exception) {
            false
        }
    }

    private fun detectRwSystem(): Boolean {
        return try {
            val testFile = File("/system/test_write_permission")
            testFile.createNewFile()
            testFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun detectTestKeys(): Boolean {
        return Build.TAGS.contains("test-keys")
    }

    private fun detectZygisk(): Boolean {
        return try {
            val zygiskDir = File("/data/misc/zaru")
            val daemonPid = File("/data/misc/zaru/daemon.pid")
            val mapsFile = File("/proc/self/maps")
            zygiskDir.exists() || daemonPid.exists() || mapsFile.readText().contains("zaru")
        } catch (e: Exception) {
            false
        }
    }
}