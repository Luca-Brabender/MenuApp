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
    fun launchIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = componentName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
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

    return launcherIntents
        .flatMap { packageManager.queryIntentActivities(it, 0) }
        .asSequence()
        .filter { it.activityInfo.packageName != packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { it.toLauncherApp(packageManager) }
        .sortedBy { it.label.lowercase() }
        .toList()
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
