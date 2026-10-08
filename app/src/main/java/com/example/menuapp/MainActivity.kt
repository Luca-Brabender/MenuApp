package com.example.menuapp

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.car.ui.FocusArea
import com.android.car.ui.FocusParkingView
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var menuRecyclerView: RecyclerView? = null
    private lateinit var appAdapter: LauncherAppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }

        val focusParkingView = FocusParkingView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

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

        appAdapter = LauncherAppAdapter(
            onClick = ::openApp
        )

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.HORIZONTAL,
                false
            )

            adapter = appAdapter

            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false

            setPadding(
                dp(32),
                dp(16),
                dp(32),
                dp(16)
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240)
            )
        }

        menuRecyclerView = recyclerView

        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            isClickable = true
            setOnClickListener {
                finishAndRemoveTask()
            }
        }

        content.addView(topSpacer)
        content.addView(recyclerView)

        content.setBackgroundColor(
            AndroidColor.argb(
                150,
                0,
                0,
                0
            )
        )

        topSpacer.isClickable = true
        topSpacer.setOnClickListener {
            finishAndRemoveTask()
        }

        focusArea.addView(content)

        root.addView(focusParkingView)
        root.addView(focusArea)


        setContentView(root)
        refreshApps()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::appAdapter.isInitialized) {
            refreshApps()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_KNOB_FOUR_FINGERS) {
            return
        }

        val direction = intent.getStringExtra(EXTRA_KNOB_DIRECTION)
            ?: return

        moveSelection(direction)
    }

    private fun moveSelection(direction: String) {
        val recyclerView = menuRecyclerView ?: return
        val adapter = recyclerView.adapter ?: return
        if (adapter.itemCount == 0) {
            return
        }

        val focusedView = recyclerView.focusedChild
        val focusedPosition = focusedView
            ?.let { recyclerView.getChildAdapterPosition(it) }
            ?.takeUnless { it == RecyclerView.NO_POSITION }
            ?: 0

        val offset = when (direction) {
            DIRECTION_NEXT -> 1
            DIRECTION_PREVIOUS -> -1
            else -> return
        }

        val targetPosition = (focusedPosition + offset)
            .coerceIn(0, adapter.itemCount - 1)

        recyclerView.smoothScrollToPosition(targetPosition)

        recyclerView.post {
            recyclerView
                .findViewHolderForAdapterPosition(targetPosition)
                ?.itemView
                ?.requestFocus()
        }


    }

    private fun refreshApps() {
        appAdapter.submitApps(loadLauncherApps())
        menuRecyclerView?.post {
            menuRecyclerView
                ?.findViewHolderForAdapterPosition(0)
                ?.itemView
                ?.requestFocus()
        }
    }

    private fun openApp(app: LauncherApp) {
        startActivity(app.launchIntent())
    }

    override fun dispatchGenericMotionEvent(
        event: MotionEvent
    ): Boolean {
        val isRotaryEvent =
            event.action == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)

        return super.dispatchGenericMotionEvent(event)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private const val ACTION_KNOB_FOUR_FINGERS =
            "com.example.carappdrawer.ACTION_KNOB_FOUR_FINGERS"

        private const val EXTRA_KNOB_DIRECTION =
            "com.example.carappdrawer.EXTRA_KNOB_DIRECTION"

        private const val DIRECTION_NEXT = "NEXT"
        private const val DIRECTION_PREVIOUS = "PREV"
    }
}

private class LauncherAppAdapter(
    private val onClick: (LauncherApp) -> Unit
) : RecyclerView.Adapter<LauncherAppAdapter.ViewHolder>() {

    private var apps: List<LauncherApp> = emptyList()

    fun submitApps(apps: List<LauncherApp>) {
        this.apps = apps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setPadding(
                context.dp(16),
                context.dp(16),
                context.dp(16),
                context.dp(16)
            )

            layoutParams = RecyclerView.LayoutParams(
                context.dp(200),
                context.dp(200)
            ).apply {
                marginEnd = context.dp(20)
            }
        }

        return ViewHolder(card)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    inner class ViewHolder(
        private val card: LinearLayout
    ) : RecyclerView.ViewHolder(card) {

        fun bind(app: LauncherApp) {
            card.removeAllViews()
            card.background = null
            card.scaleX = 1f
            card.scaleY = 1f

            val icon = ImageView(card.context).apply {
                setImageDrawable(app.icon)
                contentDescription = app.label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(
                    card.context.dp(96),
                    card.context.dp(96)
                )
            }

            val label = TextView(card.context).apply {
                text = app.label
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(AndroidColor.WHITE)
                maxLines = 1
                setPadding(
                    0,
                    card.context.dp(8),
                    0,
                    0
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            card.addView(icon)
            card.addView(label)

            card.setOnClickListener {
                onClick(app)
            }

            card.setOnFocusChangeListener { view, hasFocus ->
                view.scaleX = if (hasFocus) 1.05f else 1f
                view.scaleY = if (hasFocus) 1.05f else 1f
                label.setTextColor(
                    if (hasFocus) {
                        AndroidColor.rgb(144, 202, 249)
                    } else {
                        AndroidColor.WHITE
                    }
                )

                if (hasFocus) {
                    card.parent
                        ?.requestChildFocus(card, card)
                }
            }
        }
    }
}

private fun android.content.Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}