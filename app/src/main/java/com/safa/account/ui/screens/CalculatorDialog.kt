package com.safa.account.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DecimalFormat

// Robust Recursive Descent Mathematical Parser
fun evaluateExpression(str: String): Double {
    return object : Any() {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) x += parseTerm()
                else if (eat('-'.code)) x -= parseTerm()
                else return x
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('*'.code)) x *= parseFactor()
                else if (eat('/'.code)) {
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    x /= divisor
                }
                else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = this.pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, this.pos).toDouble()
            } else {
                throw RuntimeException("Unexpected operator")
            }

            return x
        }
    }.parse()
}

fun tryEvaluate(expr: String): Double? {
    return try {
        val raw = expr.replace("×", "*").replace("÷", "/").replace(" ", "")
        if (raw.isEmpty()) return 0.0
        evaluateExpression(raw)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CalculatorDialog(
    initialValue: String,
    title: String,
    lang: String = "BN",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var rawInput by remember { mutableStateOf(initialValue) }
    
    // Auto-calculate running output
    val liveResult = remember(rawInput) {
        val result = tryEvaluate(rawInput)
        if (result != null) {
            if (result % 1.0 == 0.0) {
                result.toLong().toString()
            } else {
                DecimalFormat("#,##0.##").format(result)
            }
        } else {
            ""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Elegant Bottom Keyboard container (Flat white style)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // prevent dismissing when clicking interior
                    )
                    .testTag("calculator_sheet"),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F7)), // Keyboard system background
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    // 1. PINNED Header (Always visible & crisp at the top!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Calculate,
                                    contentDescription = "",
                                    tint = Color(0xFF1C1C1E),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1C1E),
                                    fontSize = 13.sp
                                )
                            )
                        }
                        
                        IconButton(
                            onClick = onDismiss, 
                            modifier = Modifier
                                .size(26.dp)
                                .background(Color(0xFFE5E5EA), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close, 
                                contentDescription = "Close Calculator", 
                                tint = Color(0xFF48484A), 
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // 2. PINNED Display Pane (Always visible so the user sees typed numbers!)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5EA))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            // Raw expressions inputted typed
                            Text(
                                text = if (rawInput.isEmpty()) "0" else rawInput,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 16.sp
                                ),
                                color = Color(0xFF8E8E93),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Computed running result indicator in real-time
                            Text(
                                text = if (liveResult.isNotEmpty()) liveResult else "0",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary, // Smart system brand color
                                    fontSize = 22.sp
                                ),
                                textAlign = TextAlign.End,
                                maxLines = 1
                            )
                        }
                    }

                    // 3. Grid Of Buttons (Compact and perfectly heights-adjusted)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val gridButtons = listOf(
                            listOf("C", "÷", "×", "⌫"),
                            listOf("7", "8", "9", "-"),
                            listOf("4", "5", "6", "+"),
                            listOf("1", "2", "3", "("),
                            listOf(".", "0", ")", "=")
                        )

                        gridButtons.forEach { rowKeys ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowKeys.forEach { key ->
                                    CalculatorButton(
                                        key = key,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            when (key) {
                                                "C" -> rawInput = ""
                                                "⌫" -> {
                                                    if (rawInput.isNotEmpty()) {
                                                        rawInput = rawInput.dropLast(1)
                                                    }
                                                }
                                                "=" -> {
                                                    val finalResult = tryEvaluate(rawInput)
                                                    val cleanVal = if (finalResult != null) {
                                                        if (finalResult % 1.0 == 0.0) {
                                                            finalResult.toLong().toString()
                                                        } else {
                                                            finalResult.toString()
                                                        }
                                                    } else if (rawInput.isNotEmpty()) {
                                                        rawInput
                                                    } else {
                                                        "0"
                                                    }
                                                    onConfirm(cleanVal)
                                                }
                                                else -> {
                                                    // Handle appending safely
                                                    rawInput += key
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Safe bottom spacer (Safe Area / ফেইফ এরিয়া) so it clears the gesture pill/navigation bars properly
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val safeBottomHeight = if (navBarBottom > 32.dp) navBarBottom + 24.dp else 64.dp
                    Spacer(modifier = Modifier.height(safeBottomHeight))
                }
            }
        }
    }
}

@Composable
fun CalculatorButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isOperator = key in listOf("÷", "×", "-", "+", "=", "⌫", "C")
    val isAction = key in listOf("⌫", "C")
    
    // Material 3 Responsive Harmonized Colors
    val containerColor = when {
        isAction -> MaterialTheme.colorScheme.surfaceVariant
        isOperator -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isAction -> MaterialTheme.colorScheme.onSurfaceVariant
        isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .aspectRatio(1.9f) // Autoscale keyboard height based on column width for responsive layouts
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag("calc_btn_$key"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Strictly flat
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (key == "⌫") {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = key,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}
