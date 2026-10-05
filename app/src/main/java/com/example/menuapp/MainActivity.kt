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
import androidx.compose.ui.res.painterResource
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
import android.content.ComponentName
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.55f)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    finishAndRemoveTask()
                                }
                            }
                    ) {
                        AppDrawerScreen(
                            onAppClick = { app ->
                                openMockApp(app)
                            }
                        )
                    }
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
        val serviceName = app.mediaServiceName

        if (serviceName == null) {
            Toast.makeText(
                this,
                "${app.label} besitzt keinen gültigen Media-Service",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mediaComponent = ComponentName(
            app.packageName,
            serviceName
        )

        val intent = Intent("android.car.intent.action.MEDIA_TEMPLATE").apply {
            component = ComponentName(
                "com.android.car.media",
                "com.android.car.media.MediaDispatcherActivity"
            )

            putExtra(
                "android.car.intent.extra.MEDIA_COMPONENT",
                mediaComponent
            )

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val mediaActivity = intent.resolveActivity(packageManager)

        if (mediaActivity == null) {
            Toast.makeText(
                this,
                "Das Automotive-Media-Center ist nicht verfügbar",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(intent)
            finishAndRemoveTask()
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "${app.label} konnte nicht geöffnet werden",
                Toast.LENGTH_SHORT
            ).show()
        }
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
            component = ComponentName(
                app.packageName,
                activityName
            )

            // Ziel-App in einem eigenen Task öffnen.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)

            // Menü-Activity und deren alter Zustand werden entfernt.
            finishAndRemoveTask()
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "${app.label} konnte nicht geöffnet werden",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openMockApp(app: MockApp) {
        val intent = Intent(this, MockAppActivity::class.java).apply {
            putExtra(
                MockAppActivity.EXTRA_MOCK_APP_ID,
                app.id
            )
        }

        startActivity(intent)
    }

}

@Composable
fun AppDrawerScreen(
    onAppClick: (MockApp) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var openedMockApp by remember {
        mutableStateOf<MockApp?>(null)
    }

    val apps = mockApps

    val itemFocusRequesters = remember(apps) {
        apps.map { FocusRequester() }
    }

    LaunchedEffect(apps) {
        withFrameNanos { }

        itemFocusRequesters
            .firstOrNull()
            ?.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Anwendungen",
                fontSize = 28.sp,
                color = Color.White,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(
                    horizontal = 48.dp,
                    vertical = 32.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                items(
                    items = apps,
                    key = { app -> app.id }
                ) { app ->
                    val currentIndex = apps.indexOf(app)
                    val itemFocusRequester =
                        itemFocusRequesters.getOrNull(currentIndex)

                    if (itemFocusRequester != null) {
                        AppItem(
                            app = app,
                            focusRequester = itemFocusRequester,
                            onRotaryMove = { direction ->
                                val nextIndex = currentIndex + direction

                                if (nextIndex in apps.indices) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(nextIndex)
                                    }

                                    itemFocusRequesters
                                        .getOrNull(nextIndex)
                                        ?.requestFocus()
                                }
                            },
                            onClick = {
                                onAppClick(app)
                            }
                        )
                    }
                }
            }
        }

        openedMockApp?.let { app ->
            MockAppOverlay(
                app = app,
                onClose = {
                    openedMockApp = null
                }
            )
        }
    }
}

@Composable
fun AppItem(
    app: MockApp,
    focusRequester: FocusRequester,
    onRotaryMove: (Int) -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1.0f,
        label = "mock_app_item_scale"
    )

    val shape = RoundedCornerShape(16.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(132.dp)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .onRotaryScrollEvent { event ->
                val direction =
                    if (event.verticalScrollPixels > 0) 1 else -1

                onRotaryMove(direction)
                true
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (
                            event.key == Key.Enter ||
                                    event.key == Key.DirectionCenter
                            )
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                color = if (isFocused) {
                    Color(0xFF333333)
                } else {
                    Color(0xFF222222)
                },
                shape = shape
            )
            .border(
                border = if (isFocused) {
                    BorderStroke(3.dp, Color(0xFF64B5F6))
                } else {
                    BorderStroke(0.dp, Color.Transparent)
                },
                shape = shape
            )
            .clickable {
                onClick()
            }
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(id = app.iconResId),
            contentDescription = app.label,
            modifier = Modifier.size(72.dp)
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

@Composable
fun MockAppOverlay(
    app: MockApp,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable {
                onClose()
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(
                    color = Color(0xFF202124),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable {
                    // Touch innerhalb der Mock-App bleibt innerhalb
                    // des Mock-Fensters.
                }
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = app.iconResId),
                contentDescription = app.label,
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = app.label,
                color = Color.White,
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = app.description,
                color = Color.LightGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Mock-App für die Nutzerstudie",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}