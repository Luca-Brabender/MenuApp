package com.example.menuapp

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.car.ui.FocusArea
import com.android.car.ui.FocusParkingView
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var menuRecyclerView: RecyclerView? = null
    private var selectorTile: View? = null
    private var selectorBackground: GradientDrawable? = null
    private var parkingView: FocusParkingView? = null
    private var rootView: FrameLayout? = null
    private var contentView: View? = null
    private var appGridButton: ImageButton? = null
    private var bgAnimator: ValueAnimator? = null
    private lateinit var appAdapter: LauncherAppAdapter
    private var currentPosition: Int = 0
    private var targetPosition: Int = 0
    private var isRotaryMode: Boolean = false
    private var isKnobTouched: Boolean = false
    private var isClosing: Boolean = false
    private var isLaunching: Boolean = false
    private var activeScroller: LinearSmoothScroller? = null
    private var lastTurnTimestamp: Long = 0L

    private val releaseSelectHandler = Handler(Looper.getMainLooper())
    private var isReleaseTimerActive: Boolean = false
    private var releaseAnimator: ValueAnimator? = null

    private val knobEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("MenuApp", "knobEventReceiver empfangen: action=${intent.action}")
            handleIntent(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark translucent black background overlay (~95% opacity)
        val root = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
        rootView = root

        // Rotary mode is only active if explicitly opened via Rotary/Knob; if opened via touch, focus frame is hidden
        val openedViaKnob = (intent?.action == ACTION_KNOB_FOUR_FINGERS) ||
                (intent?.action == ACTION_KNOB_TOUCH_DOWN) ||
                (intent?.getBooleanExtra("ROTARY_MODE", false) == true)
        isRotaryMode = openedViaKnob
        isKnobTouched = (intent?.action == ACTION_KNOB_TOUCH_DOWN)
        android.util.Log.d("MenuApp", "onCreate: action=${intent?.action}, openedViaKnob=$openedViaKnob, isRotaryMode=$isRotaryMode, isKnobTouched=$isKnobTouched")

        val focusParkingView = FocusParkingView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        parkingView = focusParkingView

        val focusArea = FocusArea(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        contentView = content

        // Sanfte Slide-In Transition von unten
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        content.translationY = if (screenHeight > 0) screenHeight else 1200f
        content.alpha = 0f

        val carouselContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(330)
            )
        }

        // Custom static blue selection tile positioned in the center (active for both touch and rotary)
        val tileBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(AndroidColor.argb(35, 26, 115, 232)) // Soft translucent blue glow
            setStroke(dp(4), AndroidColor.rgb(26, 115, 232)) // Solid AAOS rotary blue frame
        }
        selectorBackground = tileBg
        val selectorTileView = View(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(
                dp(274),
                dp(274),
                Gravity.CENTER
            )
            background = tileBg
            visibility = View.VISIBLE
        }
        selectorTile = selectorTileView

        appAdapter = LauncherAppAdapter(
            onClick = ::openApp,
            onItemFocused = { _ -> },
            onItemClicked = { position ->
                appAdapter.getApp(position)?.let(::openApp)
            },
            onItemHovered = { position ->
                if (!isRotaryMode && activeScroller == null && currentPosition != position) {
                    targetPosition = position
                    scrollToPositionCentered(position, smooth = true, requestFocus = false)
                }
            },
            initialRotaryMode = isRotaryMode
        )

        val recyclerView = RecyclerView(this).apply {
            layoutManager = object : LinearLayoutManager(
                this@MainActivity,
                RecyclerView.HORIZONTAL,
                false
            ) {
                override fun requestChildRectangleOnScreen(
                    parent: RecyclerView,
                    child: View,
                    rect: Rect,
                    immediate: Boolean,
                    focusedChildVisible: Boolean
                ): Boolean {
                    // Suppress default edge-scrolling to keep items centered
                    return false
                }
            }

            adapter = appAdapter

            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
            clipChildren = false
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        menuRecyclerView = recyclerView

        // Dynamic horizontal padding so first and last items center perfectly on screen
        fun updatePadding(w: Int) {
            val screenWidth = if (w > 0) w else resources.displayMetrics.widthPixels
            val itemTotalWidth = dp(278) // card 250dp + 14dp start margin + 14dp end margin
            val horizontalPadding = ((screenWidth - itemTotalWidth) / 2).coerceAtLeast(0)
            recyclerView.setPadding(horizontalPadding, dp(16), horizontalPadding, dp(16))
        }
        updatePadding(resources.displayMetrics.widthPixels)
        recyclerView.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val w = right - left
            if (w > 0) {
                val itemTotalWidth = dp(278)
                val horizontalPadding = ((w - itemTotalWidth) / 2).coerceAtLeast(0)
                if (recyclerView.paddingLeft != horizontalPadding) {
                    recyclerView.setPadding(horizontalPadding, dp(16), horizontalPadding, dp(16))
                }
            }
        }

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager ?: return
                    val centerView = snapHelper.findSnapView(lm)
                    Log.d("MenuApp", "onScrollStateChanged IDLE: centerView=$centerView, currentPos=$currentPosition, targetPos=$targetPosition, isKnobTouched=$isKnobTouched")
                    if (centerView != null) {
                        val pos = rv.getChildAdapterPosition(centerView)
                        if (pos != RecyclerView.NO_POSITION) {
                            val settledPos = if (isRotaryMode) targetPosition else pos
                            currentPosition = settledPos
                            targetPosition = settledPos
                            appAdapter.setSelectedPosition(settledPos)
                            if (isRotaryMode) {
                                centerView.requestFocus()
                                if (!isKnobTouched && !isReleaseTimerActive && !isLaunching && !isClosing) {
                                    Log.d("MenuApp", "onScrollStateChanged IDLE: Knob nicht berührt -> Starte Loslass-Timer an Pos $settledPos")
                                    scheduleReleaseSelect()
                                }
                            }
                        }
                    }
                }
            }

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dx != 0 && !isRotaryMode) {
                    val lm = rv.layoutManager ?: return
                    val centerView = snapHelper.findSnapView(lm)
                    if (centerView != null) {
                        val pos = rv.getChildAdapterPosition(centerView)
                        if (pos != RecyclerView.NO_POSITION && pos != currentPosition) {
                            currentPosition = pos
                            targetPosition = pos
                            appAdapter.setSelectedPosition(pos)
                        }
                    }
                }
            }
        })

        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            isFocusable = false
            isFocusableInTouchMode = false
            setOnTouchListener { _, event ->
                if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                    return@setOnTouchListener false
                }

                if (event.action == MotionEvent.ACTION_UP) {
                    closeMenuImmediately()
                }
                true
            }
        }

        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            isFocusable = false
            isFocusableInTouchMode = false
            setOnTouchListener { _, event ->
                if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                    return@setOnTouchListener false
                }

                if (event.action == MotionEvent.ACTION_UP) {
                    closeMenuImmediately()
                }
                true
            }
        }

        carouselContainer.addView(selectorTileView)
        carouselContainer.addView(recyclerView)

        content.addView(topSpacer)
        content.addView(carouselContainer)
        content.addView(bottomSpacer)

        focusArea.addView(content)

        root.addView(focusParkingView)
        root.addView(focusArea)

        val button = ImageButton(this).apply {
            setImageResource(R.drawable.ic_app_grid)
            contentDescription = getString(R.string.exit_app_drawer)
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setColorFilter(AndroidColor.WHITE)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            defaultFocusHighlightEnabled = false
            alpha = 0f
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(
                dp(72),
                dp(72),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(24)
                marginEnd = dp(24)
            }
            setOnClickListener {
                closeMenuImmediately()
            }
        }
        appGridButton = button
        root.addView(button)

        setContentView(root)
        refreshApps()
        handleIntent(intent)

        if (!isRotaryMode) {
            root.post {
                parkingView?.requestFocus()
            }
        }

        // Slide-in Animation ausführen
        root.post {
            playSlideInAnimation()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (isRotaryMode) {
                val vh = menuRecyclerView?.findViewHolderForAdapterPosition(currentPosition)
                vh?.itemView?.requestFocus()
            } else {
                parkingView?.requestFocus()
            }
        } else {
            cancelReleaseSelect("window_lost_focus")
        }
    }

    override fun onResume() {
        super.onResume()
        isClosing = false
        isLaunching = false
        val filter = IntentFilter().apply {
            addAction(ACTION_KNOB_FOUR_FINGERS)
            addAction(ACTION_KNOB_TOUCH_DOWN)
            addAction(ACTION_KNOB_TOUCH_UP)
            addAction(ACTION_KNOB_SELECT)
            addAction(ACTION_KNOB_CLOSE)
            addAction(AppOrderResetReceiver.ACTION_RESET_APP_ORDER)
            addAction(AppOrderResetReceiver.ACTION_SET_STANDARD_ROTARY)
            addAction(AppOrderResetReceiver.ACTION_SET_MODE)
            addAction(AppOrderResetReceiver.ACTION_SET_ROTARY_MODE)
            addAction(AppOrderResetReceiver.ACTION_INTERNAL_ORDER_RESET)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(knobEventReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(knobEventReceiver, filter)
        }
        sendBroadcast(Intent("com.example.menuapp.ACTION_VISIBILITY").apply {
            putExtra("extra_visible", true)
        })
        if (::appAdapter.isInitialized && appAdapter.itemCount == 0) {
            refreshApps()
        }
    }

    override fun onPause() {
        cancelReleaseSelect("on_pause")
        try {
            unregisterReceiver(knobEventReceiver)
        } catch (e: Exception) {
            Log.w("MenuApp", "Fehler beim Unregister von knobEventReceiver", e)
        }
        sendBroadcast(Intent("com.example.menuapp.ACTION_VISIBILITY").apply {
            putExtra("extra_visible", false)
        })
        super.onPause()
    }

    override fun onDestroy() {
        cancelReleaseSelect("on_destroy")
        sendBroadcast(Intent("com.example.menuapp.ACTION_VISIBILITY").apply {
            putExtra("extra_visible", false)
        })
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isClosing = false
        isLaunching = false
        val openedViaKnob = (intent.action == ACTION_KNOB_FOUR_FINGERS) ||
                (intent.action == ACTION_KNOB_TOUCH_DOWN) ||
                (intent.getBooleanExtra("ROTARY_MODE", false))
        setRotaryMode(openedViaKnob)
        playSlideInAnimation()
        handleIntent(intent)
    }

    private fun setRotaryMode(enabled: Boolean) {
        Log.d("MenuApp", "setRotaryMode: enabled=$enabled, prev=$isRotaryMode")
        isRotaryMode = enabled
        selectorTile?.visibility = View.VISIBLE
        appAdapter.setRotaryMode(enabled)

        if (enabled) {
            val recyclerView = menuRecyclerView ?: return
            recyclerView.post {
                val vh = recyclerView.findViewHolderForAdapterPosition(currentPosition)
                vh?.itemView?.requestFocus()
            }
        } else {
            cancelReleaseSelect("setRotaryMode_false")
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        cancelReleaseSelect("touch_event")
        return super.dispatchTouchEvent(ev)
    }

    private fun playSlideInAnimation() {
        val content = contentView ?: return
        val root = rootView
        val button = appGridButton

        val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1000f)

        bgAnimator?.cancel()
        content.animate().cancel()
        button?.animate()?.cancel()

        content.translationY = screenHeight
        content.alpha = 0f
        button?.alpha = 0f
        root?.setBackgroundColor(AndroidColor.TRANSPARENT)

        content.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()

        button?.animate()
            ?.alpha(1f)
            ?.setDuration(280)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        bgAnimator = ValueAnimator.ofInt(0, 242).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Int
                root?.setBackgroundColor(AndroidColor.argb(alpha, 0, 0, 0))
            }
            start()
        }
    }

    private fun closeMenuImmediately() {
        if (isClosing) return
        isClosing = true
        Log.d("MenuApp", "closeMenuImmediately ausgeführt")
        cancelReleaseSelect("close_menu")

        val content = contentView
        val root = rootView
        val button = appGridButton

        val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1000f)

        bgAnimator?.cancel()

        if (content != null) {
            content.animate().cancel()
            button?.animate()?.cancel()

            content.animate()
                .translationY(screenHeight)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    finishAndRemoveTask()
                    overridePendingTransition(0, 0)
                }
                .start()

            button?.animate()
                ?.alpha(0f)
                ?.setDuration(180)
                ?.start()

            bgAnimator = ValueAnimator.ofInt(242, 0).apply {
                duration = 220
                interpolator = AccelerateInterpolator()
                addUpdateListener { animator ->
                    val alpha = animator.animatedValue as Int
                    root?.setBackgroundColor(AndroidColor.argb(alpha, 0, 0, 0))
                }
                start()
            }
        } else {
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_KNOB_TOUCH_DOWN -> {
                isKnobTouched = true
                setRotaryMode(true)
                cancelReleaseSelect("touch_down")
            }
            ACTION_KNOB_TOUCH_UP -> {
                isKnobTouched = false
                if (isRotaryMode) {
                    scheduleReleaseSelect()
                }
            }
            ACTION_KNOB_FOUR_FINGERS -> {
                setRotaryMode(true)
                val direction = intent.getStringExtra(EXTRA_KNOB_DIRECTION) ?: return
                val count = intent.getIntExtra(EXTRA_KNOB_COUNT, 1)
                moveSelection(direction, count)
            }
            ACTION_KNOB_SELECT -> {
                cancelReleaseSelect("knob_select")
                openAppAtCurrentPosition()
            }
            ACTION_KNOB_CLOSE -> {
                closeMenuImmediately()
            }
            AppOrderResetReceiver.ACTION_RESET_APP_ORDER,
            AppOrderResetReceiver.ACTION_SET_STANDARD_ROTARY,
            AppOrderResetReceiver.ACTION_SET_MODE,
            AppOrderResetReceiver.ACTION_SET_ROTARY_MODE,
            AppOrderResetReceiver.ACTION_INTERNAL_ORDER_RESET -> {
                Log.i("MenuApp", "App-Reihenfolge Reset empfangen -> Aktualisiere Menüansicht")
                AppOrderManager.resetOrder(this)
                currentPosition = 0
                targetPosition = 0
                refreshApps()
            }
        }
    }

    private fun openAppAtCurrentPosition() {
        cancelReleaseSelect("open_app")
        val pos = if (targetPosition in 0 until appAdapter.itemCount) targetPosition else currentPosition
        appAdapter.getApp(pos)?.let(::openApp)
    }

    private fun scheduleReleaseSelect() {
        if (!isRotaryMode || isLaunching || isClosing) return
        if (!::appAdapter.isInitialized || appAdapter.itemCount == 0) return

        cancelReleaseSelect("reschedule")
        isReleaseTimerActive = true

        val now = SystemClock.uptimeMillis()
        val timeSinceLastTurn = now - lastTurnTimestamp
        val isAusholMovement = timeSinceLastTurn < ACTIVE_TURN_WINDOW_MS
        val delayMs = if (isAusholMovement) AUSHOL_RELEASE_DELAY_MS else DELIBERATE_RELEASE_DELAY_MS

        val pos = if (targetPosition in 0 until appAdapter.itemCount) targetPosition else currentPosition
        val targetApp = appAdapter.getApp(pos)
        Log.d(
            "MenuApp",
            "Knob losgelassen -> Starte ${delayMs}ms Loslass-Timer (Status: ${if (isAusholMovement) "AUSHOLEN [${delayMs}ms Puffer]" else "BEWUSST [${delayMs}ms]"}) für Position $pos (${targetApp?.label})"
        )
        startReleaseVisuals(delayMs, isAusholMovement)
        releaseSelectHandler.postDelayed(releaseSelectRunnable, delayMs)
    }

    private val releaseSelectRunnable = Runnable {
        if (isFinishing || isDestroyed || !isReleaseTimerActive || isLaunching || isClosing) return@Runnable
        val pos = if (targetPosition in 0 until appAdapter.itemCount) targetPosition else currentPosition
        val app = appAdapter.getApp(pos)
        Log.i("MenuApp", "Release-Timer abgelaufen -> Öffne automatisch: ${app?.label} (${app?.packageName})")
        openAppAtCurrentPosition()
    }

    private fun cancelReleaseSelect(reason: String = "") {
        val wasActive = isReleaseTimerActive
        isReleaseTimerActive = false
        releaseSelectHandler.removeCallbacks(releaseSelectRunnable)
        resetReleaseVisuals()
        if (wasActive && reason.isNotEmpty()) {
            Log.d("MenuApp", "cancelReleaseSelect: Timer abgebrochen wegen: $reason")
        }
    }

    private fun startReleaseVisuals(delayMs: Long, isAusholMovement: Boolean) {
        val tile = selectorTile ?: return
        val bg = selectorBackground
        tile.scaleX = 1.0f
        tile.scaleY = 1.0f

        releaseAnimator?.cancel()
        releaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = delayMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction

                // Dynamisches Atmen & Wachsen der blauen Kachel (1.0f bis 1.08f)
                val currentScale = 1.0f + (0.08f * fraction)
                tile.scaleX = currentScale
                tile.scaleY = currentScale

                // Blaues Leuchten & Glow kontinuierlich verstärken
                bg?.let {
                    val alpha = (35 + (85 * fraction)).toInt().coerceIn(35, 120)
                    it.setColor(AndroidColor.argb(alpha, 26, 115, 232))
                    val strokeW = dp(4 + (2 * fraction).toInt())
                    it.setStroke(strokeW, AndroidColor.rgb(41, 128, 250))
                }

                // Dezent synchrones Pop-Feedback für die zentrierte Kachel
                val targetPos = if (targetPosition in 0 until appAdapter.itemCount) targetPosition else currentPosition
                val vh = menuRecyclerView?.findViewHolderForAdapterPosition(targetPos) as? LauncherAppAdapter.ViewHolder
                vh?.updateReleaseProgress(fraction)
            }
            start()
        }
    }

    private fun resetReleaseVisuals() {
        releaseAnimator?.cancel()
        releaseAnimator = null
        selectorTile?.let { tile ->
            tile.scaleX = 1.0f
            tile.scaleY = 1.0f
        }
        selectorBackground?.let { bg ->
            bg.setColor(AndroidColor.argb(35, 26, 115, 232))
            bg.setStroke(dp(4), AndroidColor.rgb(26, 115, 232))
        }
        val targetPos = if (targetPosition in 0 until appAdapter.itemCount) targetPosition else currentPosition
        val vh = menuRecyclerView?.findViewHolderForAdapterPosition(targetPos) as? LauncherAppAdapter.ViewHolder
        vh?.updateReleaseProgress(0f)
    }

    private fun scrollToPositionCentered(
        targetPos: Int,
        smooth: Boolean = true,
        requestFocus: Boolean = true
    ) {
        val recyclerView = menuRecyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val adapter = recyclerView.adapter ?: return
        if (adapter.itemCount == 0) return

        val clampedPos = targetPos.coerceIn(0, adapter.itemCount - 1)
        targetPosition = clampedPos
        appAdapter.setSelectedPosition(clampedPos)

        if (smooth) {
            val scroller = object : LinearSmoothScroller(this) {
                override fun calculateDtToFit(
                    viewStart: Int,
                    viewEnd: Int,
                    boxStart: Int,
                    boxEnd: Int,
                    snapPreference: Int
                ): Int {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                }

                override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                    val distance = Math.abs(clampedPos - currentPosition).coerceAtLeast(1)
                    val baseSpeed = if (distance > 1) 25f else 40f
                    return baseSpeed / displayMetrics.densityDpi
                }

                override fun onStop() {
                    super.onStop()
                    if (this !== activeScroller) {
                        Log.d("MenuApp", "onStop: Scroller für Pos $clampedPos abgebrochen (aktuell target=$targetPosition)")
                        return
                    }
                    Log.d("MenuApp", "onStop: Ziel erreicht bei Pos $clampedPos")
                    activeScroller = null
                    currentPosition = clampedPos
                    targetPosition = clampedPos
                    appAdapter.setSelectedPosition(clampedPos)
                    if (isRotaryMode) {
                        val vh = recyclerView.findViewHolderForAdapterPosition(clampedPos)
                        vh?.itemView?.requestFocus()
                        if (!isKnobTouched && !isReleaseTimerActive && !isLaunching && !isClosing) {
                            Log.d("MenuApp", "onStop: Knob nicht berührt -> Starte Loslass-Timer an Zielposition $clampedPos")
                            scheduleReleaseSelect()
                        }
                    }
                }
            }
            scroller.targetPosition = clampedPos
            activeScroller = scroller
            layoutManager.startSmoothScroll(scroller)
        } else {
            activeScroller = null
            currentPosition = clampedPos
            val screenWidth = if (recyclerView.width > 0) recyclerView.width else resources.displayMetrics.widthPixels
            val itemTotalWidth = dp(278)
            val offset = (screenWidth - itemTotalWidth) / 2
            layoutManager.scrollToPositionWithOffset(clampedPos, offset)
            if (isRotaryMode) {
                recyclerView.post {
                    val vh = recyclerView.findViewHolderForAdapterPosition(clampedPos)
                    vh?.itemView?.requestFocus()
                    if (!isKnobTouched && !isReleaseTimerActive && !isLaunching && !isClosing) {
                        scheduleReleaseSelect()
                    }
                }
            }
        }
    }

    private fun moveSelection(direction: String, count: Int = 1) {
        lastTurnTimestamp = SystemClock.uptimeMillis()
        val recyclerView = menuRecyclerView ?: return
        val adapter = recyclerView.adapter ?: return
        if (adapter.itemCount == 0) {
            return
        }

        val offset = when (direction) {
            DIRECTION_NEXT -> 1
            DIRECTION_PREVIOUS -> -1
            else -> return
        }

        val nextTarget = (targetPosition + (offset * count)).coerceIn(0, adapter.itemCount - 1)
        targetPosition = nextTarget
        // Jeder Drehimpuls beweist aktive Navigation -> Timer sofort bedingungslos abbrechen!
        cancelReleaseSelect("knob_turn")
        scrollToPositionCentered(targetPosition, smooth = true, requestFocus = isRotaryMode)
    }

    private fun refreshApps() {
        val apps = loadLauncherApps()
        appAdapter.submitApps(apps)
        menuRecyclerView?.post {
            if (appAdapter.itemCount > 0) {
                val target = if (targetPosition in 0 until appAdapter.itemCount) targetPosition else currentPosition.coerceIn(0, appAdapter.itemCount - 1)
                currentPosition = target
                targetPosition = target
                appAdapter.setSelectedPosition(target)
                scrollToPositionCentered(target, smooth = false, requestFocus = isRotaryMode)
            }
        }
    }

    private fun openApp(app: LauncherApp) {
        if (isLaunching || isFinishing || isDestroyed) return
        isLaunching = true
        cancelReleaseSelect("open_app")
        try {
            AppOrderManager.recordAppLaunch(this, app.packageName)
            startActivity(app.launchIntent(this))
            finish()
        } catch (e: Exception) {
            android.util.Log.e("MenuApp", "Error launching app: ${app.packageName}", e)
            isLaunching = false
        }
    }

    override fun onBackPressed() {
        closeMenuImmediately()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        android.util.Log.d("MenuApp", "onKeyDown: keyCode=$keyCode, isRotaryMode=$isRotaryMode")
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                closeMenuImmediately()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isRotaryMode) setRotaryMode(true)
                moveSelection(DIRECTION_NEXT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isRotaryMode) setRotaryMode(true)
                moveSelection(DIRECTION_PREVIOUS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                cancelReleaseSelect("key_press")
                if (!isRotaryMode) setRotaryMode(true)
                openAppAtCurrentPosition()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(
        event: MotionEvent
    ): Boolean {
        val isRotaryEvent =
            event.action == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        android.util.Log.d("MenuApp", "dispatchGenericMotionEvent: isRotaryEvent=$isRotaryEvent, action=${event.action}, source=${event.source}")
        if (isRotaryEvent) {
            if (!isRotaryMode) {
                setRotaryMode(true)
            }
            val axis = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (axis > 0f) {
                moveSelection(DIRECTION_NEXT)
                return true
            } else if (axis < 0f) {
                moveSelection(DIRECTION_PREVIOUS)
                return true
            }
        }

        return super.dispatchGenericMotionEvent(event)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private const val ACTION_KNOB_FOUR_FINGERS =
            "com.example.carappdrawer.ACTION_KNOB_FOUR_FINGERS"
        private const val ACTION_KNOB_TOUCH_DOWN =
            "com.example.carappdrawer.ACTION_KNOB_TOUCH_DOWN"
        private const val ACTION_KNOB_TOUCH_UP =
            "com.example.carappdrawer.ACTION_KNOB_TOUCH_UP"
        private const val ACTION_KNOB_SELECT =
            "com.example.carappdrawer.ACTION_KNOB_SELECT"
        private const val ACTION_KNOB_CLOSE =
            "com.example.carappdrawer.ACTION_KNOB_CLOSE"

        private const val EXTRA_KNOB_DIRECTION =
            "com.example.carappdrawer.EXTRA_KNOB_DIRECTION"
        private const val EXTRA_KNOB_COUNT =
            "com.example.carappdrawer.EXTRA_KNOB_COUNT"

        private const val DIRECTION_NEXT = "NEXT"
        private const val DIRECTION_PREVIOUS = "PREV"
    }
}

private class LauncherAppAdapter(
    private val onClick: (LauncherApp) -> Unit,
    private val onItemFocused: (Int) -> Unit = {},
    private val onItemClicked: (Int) -> Unit = {},
    private val onItemHovered: (Int) -> Unit = {},
    initialRotaryMode: Boolean = false
) : RecyclerView.Adapter<LauncherAppAdapter.ViewHolder>() {

    private var apps: List<LauncherApp> = emptyList()
    private val viewHolders = mutableSetOf<ViewHolder>()
    var selectedPosition: Int = 0
        private set
    var isRotaryMode: Boolean = initialRotaryMode
        private set

    fun submitApps(apps: List<LauncherApp>) {
        this.apps = apps
        notifyDataSetChanged()
    }

    fun getApp(position: Int): LauncherApp? {
        return apps.getOrNull(position)
    }

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        viewHolders.forEach { holder ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                holder.updateVisualState(pos == selectedPosition)
            }
        }
    }

    fun setRotaryMode(enabled: Boolean) {
        if (isRotaryMode == enabled) return
        isRotaryMode = enabled
        viewHolders.forEach { holder ->
            holder.setRotaryMode(enabled)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = isRotaryMode
            isFocusableInTouchMode = false
            defaultFocusHighlightEnabled = false
            isClickable = true
            setPadding(
                context.dp(16),
                context.dp(16),
                context.dp(16),
                context.dp(16)
            )

            layoutParams = RecyclerView.LayoutParams(
                context.dp(250),
                context.dp(250)
            ).apply {
                marginStart = context.dp(14)
                marginEnd = context.dp(14)
            }
        }

        return ViewHolder(card).also(viewHolders::add)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(apps[position])
        holder.setRotaryMode(isRotaryMode)
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    inner class ViewHolder(
        private val card: LinearLayout
    ) : RecyclerView.ViewHolder(card) {
        private var boundApp: LauncherApp? = null
        private lateinit var iconView: ImageView
        private lateinit var labelView: TextView

        fun bind(app: LauncherApp) {
            boundApp = app
            card.removeAllViews()
            card.background = null

            val icon = ImageView(card.context).apply {
                setImageDrawable(app.icon)
                contentDescription = app.label
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    card.context.dp(140),
                    card.context.dp(140)
                )
            }
            iconView = icon

            val label = TextView(card.context).apply {
                text = app.label
                textSize = 32f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setTextColor(AndroidColor.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(
                    0,
                    card.context.dp(10),
                    0,
                    0
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            labelView = label

            card.addView(icon)
            card.addView(label)

            updateVisualState(bindingAdapterPosition == selectedPosition)

            card.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClicked(pos)
                }
            }

            card.setOnFocusChangeListener { _, _ ->
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemFocused(pos)
                    updateVisualState(pos == selectedPosition)
                }
            }

            card.setOnHoverListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            onItemHovered(pos)
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        fun setRotaryMode(enabled: Boolean) {
            card.isFocusable = enabled
            if (!enabled && card.hasFocus()) {
                card.clearFocus()
            }
            updateVisualState(bindingAdapterPosition == selectedPosition)
        }

        fun updateVisualState(isSelected: Boolean) {
            card.scaleX = if (isSelected) 1.0f else 0.90f
            card.scaleY = if (isSelected) 1.0f else 0.90f
            card.alpha = if (isSelected) 1.0f else 0.40f
            if (::iconView.isInitialized) {
                iconView.scaleX = 1.0f
                iconView.scaleY = 1.0f
            }
            if (::labelView.isInitialized) {
                labelView.setTextColor(
                    if (isSelected) {
                        AndroidColor.rgb(144, 202, 249)
                    } else {
                        AndroidColor.rgb(160, 160, 160)
                    }
                )
            }
        }

        fun updateReleaseProgress(progress: Float) {
            val scale = 1.0f + (0.05f * progress)
            card.scaleX = scale
            card.scaleY = scale
            if (::iconView.isInitialized) {
                val iconScale = 1.0f + (0.06f * progress)
                iconView.scaleX = iconScale
                iconView.scaleY = iconScale
            }
        }
    }
}

private const val DELIBERATE_RELEASE_DELAY_MS = 500L
private const val AUSHOL_RELEASE_DELAY_MS = 750L
private const val ACTIVE_TURN_WINDOW_MS = 300L

private fun android.content.Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}
