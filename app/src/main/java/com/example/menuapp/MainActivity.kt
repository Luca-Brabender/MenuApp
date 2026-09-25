package com.example.menuapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import android.content.ComponentName

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String?,
    val mediaServiceName: String?,
    val icon: Drawable,
    val isMediaApp: Boolean = false
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212) // Dunkler Automotive-Hintergrund
                ) {
                    AppDrawerScreen(
                        onAppClick = { app ->
                            launchApp(app)
                        }
                    )
                }
            }
        }

        // Prüfen, ob die Activity bereits durch eine Knob-Geste gestartet wurde
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Hier schlägt später dein 4-Finger-Knob-Event auf!
        if (intent?.action == "com.example.carappdrawer.ACTION_KNOB_FOUR_FINGERS") {
            // TODO: Logik für automatisches Weiterschalten & Starten im Hintergrund
            Toast.makeText(this, "4-Finger Knob-Geste empfangen!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMediaApp(app: AppEntry) {
        val serviceName = app.mediaServiceName ?: return

        val mediaComponent = ComponentName(
            app.packageName,
            serviceName
        )

        val intent = Intent("android.car.intent.action.MEDIA_TEMPLATE").apply {
            component = ComponentName(
                "com.android.car.media",
                "com.android.car.media.MediaDispatcherActivity"
            )

            // AAOS Media Center
            putExtra(
                "android.car.intent.extra.MEDIA_COMPONENT",
                mediaComponent
            )

            // Fallback für ältere Media-Center-Versionen
            putExtra(
                Intent.EXTRA_COMPONENT_NAME,
                mediaComponent
            )
        }

        startActivity(intent)
    }


    private fun launchApp(app: AppEntry) {
        if (app.isMediaApp) {
            openMediaApp(app)
            return
        }

        val activityName = app.activityName

        if (activityName == null) {
            Toast.makeText(
                this,
                "${app.label} besitzt keine startbare Oberfläche",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(app.packageName, activityName)
        }

        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "${app.label} konnte nicht geöffnet werden",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}



@Composable
fun AppDrawerScreen(onAppClick: (AppEntry) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    // Lädt alle installierten Apps mit Launcher-Intent
    LaunchedEffect(Unit) {
        val pm = context.packageManager

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val carLauncherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory("android.intent.category.CAR_LAUNCHER")
        }

        val mediaServiceIntent = Intent("android.media.browse.MediaBrowserService")
        val compatMediaServiceIntent =
            Intent("android.media.browse.MediaBrowserServiceCompat")

        val launcherApps =
            pm.queryIntentActivities(launcherIntent, 0) +
                    pm.queryIntentActivities(carLauncherIntent, 0)

        val mediaServices =
            pm.queryIntentServices(mediaServiceIntent, 0) +
                    pm.queryIntentServices(compatMediaServiceIntent, 0)



        val appEntries = launcherApps
            .map { resolveInfo ->
                AppEntry(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    mediaServiceName = null,
                    icon = resolveInfo.loadIcon(pm),
                    isMediaApp = false
                )
            }
            .toMutableList()

        val existingPackages = appEntries
            .mapTo(mutableSetOf()) { it.packageName }

        mediaServices
            .filter { it.serviceInfo.packageName !in existingPackages }
            .forEach { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo
                val applicationInfo = serviceInfo.applicationInfo

                appEntries += AppEntry(
                    label = applicationInfo.loadLabel(pm).toString(),
                    packageName = serviceInfo.packageName,
                    activityName = null,
                    mediaServiceName = serviceInfo.name,
                    icon = applicationInfo.loadIcon(pm),
                    isMediaApp = true
                )
            }

        apps = appEntries
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Anwendungen",
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Raster für Touch-Bedienung (auf dem Pi 4 Display gut nutzbar)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(apps) { app ->
                AppItem(
                    app = app,
                    onClick = { onAppClick(app) }
                )
            }
        }
    }
}

@Composable
fun AppItem(app: AppEntry, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF222222), shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = app.label,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}