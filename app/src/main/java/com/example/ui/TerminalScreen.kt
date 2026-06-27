package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Line
import com.example.engine.ShellEngine
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Maintain multiple active shell engine sessions
    val sessions = remember { mutableStateListOf<ShellEngine>() }
    var activeSessionIndex by remember { mutableStateOf(0) }

    // Initialize with two default sessions matching the High Density HTML spec
    if (sessions.isEmpty()) {
        val s1 = ShellEngine(context)
        val s2 = ShellEngine(context)
        // Set slightly different prompt headers for visual distinction
        s2.addOutput("Initialized secondary environment channel.")
        s2.addOutput("Ready for concurrent workspace commands.")
        s2.addOutput("")
        sessions.add(s1)
        sessions.add(s2)
    }

    // Safely get active session
    val activeEngine = if (activeSessionIndex in sessions.indices) sessions[activeSessionIndex] else sessions.first()
    val terminalLines by activeEngine.terminalLines.collectAsState()
    val currentPath by activeEngine.currentPath.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll to bottom when lines change
    LaunchedEffect(terminalLines.size, activeSessionIndex) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(
                color = DarkBackground,
                modifier = Modifier.statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Infinity Terminal Icon",
                                tint = CyberGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Infinity Shell",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = "bash • active",
                                    color = CyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Top Action Badges matching high-density specs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberMagenta.copy(alpha = 0.15f))
                                    .border(1.dp, CyberMagenta.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            activeEngine.clearScreen()
                                            activeEngine.addOutput("Session terminal environment refreshed.")
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "RESET",
                                    color = CyberMagenta,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        activeEngine.execute("help")
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Help Command Shortcut",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = BorderColor, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(KeyBarBackground)
                    .navigationBarsPadding()
            ) {
                // Command Quick-Helper Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SUGGESTIONS:",
                        color = TextDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("neofetch", "pkg list", "cmatrix", "clear").forEach { cmd ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TerminalBackground)
                                    .clickable {
                                        coroutineScope.launch {
                                            activeEngine.execute(cmd)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cmd,
                                    color = CyberGreen,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // High Density Extra Keys Bar matching HTML spec
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Esc", "Ctrl", "Alt", "Tab", "-", "/").forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (key == "Tab") {
                                        textInput += " "
                                    } else {
                                        textInput += "$key"
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = TextLight.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    // Arrow symbol key at end
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                // Mocks cursor history scroll
                                textInput = "neofetch"
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up Arrow Shortcut",
                            tint = TextLight.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // High Density Footer session status & action bar matching HTML spec
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(DarkBackground)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left side sessions selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sessions.forEachIndexed { index, _ ->
                            val isActive = index == activeSessionIndex
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { activeSessionIndex = index }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(2.dp)
                                        .background(if (isActive) CyberCyan else Color.Transparent)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "SESSION ${index + 1}",
                                    color = if (isActive) CyberCyan else TextLight.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // Right side plus button to spawn a new environment session
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BlueAccent)
                            .clickable {
                                val nextIndex = sessions.size + 1
                                val newSession = ShellEngine(context)
                                newSession.addOutput("Spawned sandbox session #$nextIndex runtime environments.")
                                sessions.add(newSession)
                                activeSessionIndex = sessions.size - 1
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Session",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalBackground)
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // LazyColumn containing live session lines
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(terminalLines) { line ->
                    when (line) {
                        is Line.Input -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${line.path} $",
                                    color = CyberGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = line.text,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        is Line.Output -> {
                            Text(
                                text = line.text,
                                color = if (line.isError) CyberMagenta else TextLight.copy(alpha = 0.85f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Inline interactive command prompt - seamless blend in pure black terminal background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currentPath $",
                    color = CyberGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                BasicTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_input"),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val targetCommand = textInput
                            textInput = ""
                            coroutineScope.launch {
                                activeEngine.execute(targetCommand)
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        val targetCommand = textInput
                        textInput = ""
                        coroutineScope.launch {
                            activeEngine.execute(targetCommand)
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command Button",
                        tint = CyberGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
