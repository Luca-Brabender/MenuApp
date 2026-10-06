package com.example.menuapp

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.car.ui.FocusArea
import com.android.car.ui.FocusParkingView
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

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

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.HORIZONTAL,
                false
            )

            adapter = MockAppAdapter(
                apps = mockApps,
                onClick = ::openMockApp
            )

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

        recyclerView.post {
            val firstViewHolder =
                recyclerView.findViewHolderForAdapterPosition(0)

            firstViewHolder?.itemView?.requestFocus()

            if (firstViewHolder == null && mockApps.isNotEmpty()) {
                recyclerView.scrollToPosition(0)
                recyclerView.post {
                    recyclerView
                        .findViewHolderForAdapterPosition(0)
                        ?.itemView
                        ?.requestFocus()
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_KNOB_FOUR_FINGERS) {
            Toast.makeText(
                this,
                "4-Finger-Knob-Geste empfangen",
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

    override fun dispatchGenericMotionEvent(
        event: MotionEvent
    ): Boolean {
        val isRotaryEvent =
            event.action == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)

        if (isRotaryEvent) {
            Log.d(
                ROTARY_LOG_TAG,
                "Rotary-Event empfangen: " +
                        "axis=${event.getAxisValue(MotionEvent.AXIS_SCROLL)}, " +
                        "source=${event.source}, " +
                        "deviceId=${event.deviceId}, " +
                        "device=${event.device?.name}"
            )
        }

        return super.dispatchGenericMotionEvent(event)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private const val ROTARY_LOG_TAG = "MenuAppRotary"

        private const val ACTION_KNOB_FOUR_FINGERS =
            "com.example.carappdrawer.ACTION_KNOB_FOUR_FINGERS"
    }
}

private class MockAppAdapter(
    private val apps: List<MockApp>,
    private val onClick: (MockApp) -> Unit
) : RecyclerView.Adapter<MockAppAdapter.ViewHolder>() {

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

        fun bind(app: MockApp) {
            card.removeAllViews()

            val icon = ImageView(card.context).apply {
                setImageResource(app.iconResId)
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
                view.background = createCardBackground(
                    focused = hasFocus
                )

                if (hasFocus) {
                    card.parent
                        ?.requestChildFocus(card, card)
                }
            }

            card.background = createCardBackground(
                focused = card.hasFocus()
            )
        }

        private fun createCardBackground(
            focused: Boolean
        ): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = card.context.dp(16).toFloat()
                setColor(
                    if (focused) {
                        AndroidColor.rgb(51, 51, 51)
                    } else {
                        AndroidColor.rgb(34, 34, 34)
                    }
                )

                if (focused) {
                    setStroke(
                        card.context.dp(3),
                        AndroidColor.rgb(100, 181, 246)
                    )
                }
            }
        }
    }
}

private fun android.content.Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}