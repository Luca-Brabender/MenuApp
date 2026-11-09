package com.example.menuapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

data class LauncherApp(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
    val componentName: ComponentName
) {
    fun launchIntent(context: Context? = null): Intent {
        val baseIntent = if (context != null) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } else null

        val intent = baseIntent ?: Intent(Intent.ACTION_MAIN).apply {
            component = componentName
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (packageName == "app.organicmaps.web" || componentName.packageName == "app.organicmaps.web") {
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        return intent
    }
}

private val EXCLUDED_PACKAGES = setOf(
    "com.google.android.car.kitchensink",
    "com.android.car.bugreport",
    "com.android.car.cartelemetryapp",
    "com.google.android.car.adaslocation",
    "com.android.car.adaslocation.testapp",
    "com.google.android.car.garagemode.testapp",
    "com.google.android.car.networking.preferenceupdater",
    "com.android.car.networking.preferenceupdater",
    "com.google.android.car.networking.railway",
    "com.android.car.railway",
    "org.chromium.webview_shell",
    "com.google.android.car.evs",
    "com.android.car.evs",
    "com.android.camera2",
    "com.android.calendar",
    "com.android.quicksearchbox"
)

object AppOrderManager {
    private const val PREFS_NAME = "menu_app_prefs"
    private const val KEY_MRU_PACKAGES = "mru_packages"

    fun getMruPackages(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MRU_PACKAGES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun recordAppLaunch(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val current = getMruPackages(context).toMutableList()
        current.remove(packageName)
        current.add(0, packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MRU_PACKAGES, current.joinToString(","))
            .apply()
        android.util.Log.d("MenuApp", "AppOrderManager: App gestartet: $packageName. Neue MRU-Reihenfolge: $current")
    }

    fun resetOrder(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_MRU_PACKAGES)
            .apply()
        android.util.Log.i("MenuApp", "AppOrderManager: App-Reihenfolge auf Standard (Alphabetisch) zurückgesetzt.")
    }
}

fun Context.loadLauncherApps(): List<LauncherApp> {
    val launcherIntents = listOf(
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        },
        Intent(Intent.ACTION_MAIN).apply {
            addCategory("android.intent.category.CAR_LAUNCHER")
        }
    )

    val allApps = launcherIntents
        .flatMap { packageManager.queryIntentActivities(it, 0) }
        .asSequence()
        .filter { it.activityInfo.packageName != packageName && !EXCLUDED_PACKAGES.contains(it.activityInfo.packageName) }
        .distinctBy { it.activityInfo.packageName }
        .map { it.toLauncherApp(packageManager) }
        .toList()

    val mruPackages = AppOrderManager.getMruPackages(this)
    if (mruPackages.isEmpty()) {
        return allApps.sortedBy { it.label.lowercase() }
    }

    val appMap = allApps.associateBy { it.packageName }
    val mruApps = mutableListOf<LauncherApp>()
    for (pkg in mruPackages) {
        appMap[pkg]?.let { mruApps.add(it) }
    }

    val mruSet = mruApps.map { it.packageName }.toSet()
    val remainingApps = allApps
        .filter { it.packageName !in mruSet }
        .sortedBy { it.label.lowercase() }

    return mruApps + remainingApps
}

private fun ResolveInfo.toLauncherApp(
    packageManager: android.content.pm.PackageManager
): LauncherApp {
    val activityInfo = activityInfo
    return LauncherApp(
        packageName = activityInfo.packageName,
        label = loadLabel(packageManager).toString(),
        icon = loadIcon(packageManager),
        componentName = ComponentName(activityInfo.packageName, activityInfo.name)
    )
}
