package com.playwe.playlist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.playwe.playlist.ui.theme.AccentAmber
import com.playwe.playlist.ui.theme.AccentBlue
import com.playwe.playlist.ui.theme.AccentRed
import com.playwe.playlist.ui.theme.DarkBackground
import com.playwe.playlist.ui.theme.SurfaceBorder
import com.playwe.playlist.ui.theme.SurfaceCard
import com.playwe.playlist.ui.theme.TextMuted
import com.playwe.playlist.ui.theme.TextPrimary
import com.playwe.playlist.ui.theme.TextSecondary
import com.playwe.playlist.viewmodel.TimerState
import java.util.Locale

@Composable
fun CountdownTimerDialog(
    isOpen: Boolean,
    timerState: TimerState,
    timerMinutes: Int,
    timerSeconds: Int,
    remainingSeconds: Int,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val formattedRemaining = String.format(
        Locale.US,
        "%d:%02d",
        remainingSeconds / 60,
        remainingSeconds % 60
    )

    val textColor = when {
        timerState == TimerState.RUNNING && remainingSeconds <= 5 -> AccentRed
        timerState == TimerState.RUNNING -> AccentBlue
        timerState == TimerState.PAUSED -> AccentAmber
        else -> TextPrimary
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                .testTag("timer_dialog"),
            color = DarkBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Countdown Timer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("timer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Time Display / Inputs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (timerState == TimerState.IDLE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Minutes
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(60.dp)
                                        .background(SurfaceCard, RoundedCornerShape(12.dp))
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = timerMinutes.toString(),
                                        onValueChange = { str ->
                                            val num = str.toIntOrNull() ?: 0
                                            onMinutesChange(num.coerceIn(0, 99))
                                        },
                                        textStyle = TextStyle(
                                            color = TextPrimary,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        cursorBrush = SolidColor(AccentBlue),
                                        modifier = Modifier.testTag("timer_minutes_input")
                                    )
                                }
                                Text(
                                    text = "MIN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Text(
                                text = ":",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Light,
                                color = TextMuted,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 18.dp)
                            )

                            // Seconds
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(60.dp)
                                        .background(SurfaceCard, RoundedCornerShape(12.dp))
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = timerSeconds.toString(),
                                        onValueChange = { str ->
                                            val num = str.toIntOrNull() ?: 0
                                            onSecondsChange(num.coerceIn(0, 59))
                                        },
                                        textStyle = TextStyle(
                                            color = TextPrimary,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        cursorBrush = SolidColor(AccentBlue),
                                        modifier = Modifier.testTag("timer_seconds_input")
                                    )
                                }
                                Text(
                                    text = "SEC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = formattedRemaining,
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            modifier = Modifier.testTag("timer_countdown_display")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (timerState) {
                        TimerState.IDLE -> {
                            Button(
                                onClick = onStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("timer_start_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Start",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                        TimerState.RUNNING -> {
                            Button(
                                onClick = onPause,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("timer_pause_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "Pause", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Button(
                                onClick = onStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("timer_stop_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "Stop", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                        TimerState.PAUSED -> {
                            Button(
                                onClick = onStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("timer_resume_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "Resume", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Button(
                                onClick = onStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("timer_stop_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "Stop", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
