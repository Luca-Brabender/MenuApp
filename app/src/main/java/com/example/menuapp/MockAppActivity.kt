package com.example.menuapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MockAppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mockAppId = intent.getStringExtra(EXTRA_MOCK_APP_ID)

        val app = mockApps.firstOrNull { it.id == mockAppId }

        if (app == null) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                MockAppScreen(
                    app = app,
                    onClose = {
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_MOCK_APP_ID =
            "com.example.menuapp.EXTRA_MOCK_APP_ID"
    }
}

@Composable
private fun MockAppScreen(
    app: MockApp,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) {
                detectTapGestures {
                    onClose()
                }
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
                    // Touch innerhalb der Fake-App bleibt dort.
                }
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = app.description,
                color = Color.LightGray,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Fake-App für die Nutzerstudie",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}