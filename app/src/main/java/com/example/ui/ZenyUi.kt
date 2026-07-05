package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import coil.compose.AsyncImage
import com.example.data.CalendarEvent
import com.example.data.ChatMessage
import com.example.data.ZenyTask
import com.example.data.ZenyWorkflow
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenyUi(viewModel: ZenyViewModel) {
    var currentTab by remember { mutableStateOf(0) } // 0: Chat, 1: Agenda, 2: Automate

    val chatHistory by viewModel.chatHistory.collectAsState()
    val calendarEvents by viewModel.allEvents.collectAsState()
    val zenyTasks by viewModel.allTasks.collectAsState()
    val zenyWorkflows by viewModel.allWorkflows.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()

    var showAddEventDialog by remember { mutableStateOf(false) }
    var showAddAutomationDialog by remember { mutableStateOf(false) }
    var showAddWorkflowDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = CosmicSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Filled.ChatBubble, contentDescription = "Zeny Chat") },
                    label = { Text("Zeny Chat", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = CosmicSurfaceElevated
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Agenda") },
                    label = { Text("Agenda", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = CosmicSurfaceElevated
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Filled.SettingsInputAntenna, contentDescription = "Automations") },
                    label = { Text("Automations", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonTeal,
                        selectedTextColor = NeonTeal,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = CosmicSurfaceElevated
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Setup / Missing API Key warning banner
            if (!viewModel.isApiKeyConfigured()) {
                ApiKeyWarningBanner()
            }

            // Tabs Content Switcher
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    0 -> ZenyChatTab(
                        viewModel = viewModel,
                        chatHistory = chatHistory,
                        isRecording = isRecording,
                        isLoading = isLoading,
                        errorMessage = errorMessage
                    )
                    1 -> ZenyAgendaTab(
                        viewModel = viewModel,
                        calendarEvents = calendarEvents,
                        searchFilter = searchFilter,
                        onAddEventClick = { showAddEventDialog = true }
                    )
                    2 -> ZenyAutomationsTab(
                        viewModel = viewModel,
                        zenyTasks = zenyTasks,
                        zenyWorkflows = zenyWorkflows,
                        onAddAutomationClick = { showAddAutomationDialog = true },
                        onAddWorkflowClick = { showAddWorkflowDialog = true }
                    )
                }
            }
        }
    }

    // Modal dialogs for manual interactive insertions
    if (showAddEventDialog) {
        AddEventDialog(
            onDismiss = { showAddEventDialog = false },
            onSave = { title, desc, category, date, hour, minute ->
                viewModel.addLocalEvent(title, desc, category, date, hour, minute)
                showAddEventDialog = false
            }
        )
    }

    if (showAddAutomationDialog) {
        AddAutomationDialog(
            onDismiss = { showAddAutomationDialog = false },
            onSave = { title, desc, trigger ->
                viewModel.addLocalTask(title, desc, trigger)
                showAddAutomationDialog = false
            }
        )
    }

    if (showAddWorkflowDialog) {
        AddWorkflowDialog(
            onDismiss = { showAddWorkflowDialog = false },
            onSave = { title, trigger, steps ->
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val stepsJson = moshi.adapter(List::class.java).toJson(steps)
                viewModel.addLocalWorkflow(title, trigger, stepsJson)
                showAddWorkflowDialog = false
            }
        )
    }
}

@Composable
fun ApiKeyWarningBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceElevated.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Sandbox Info",
                tint = NeonCyan,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Sandbox Environment Running",
                    color = NeonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add your GEMINI_API_KEY in the AI Studio Secrets panel to enable live scheduling and speech interactions.",
                    color = TextGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ==================== CHAT TAB (ZENY HUB) ====================

@Composable
fun ZenyChatTab(
    viewModel: ZenyViewModel,
    chatHistory: List<ChatMessage>,
    isRecording: Boolean,
    isLoading: Boolean,
    errorMessage: String?
) {
    val micAmp by viewModel.voiceEngine.micAmplitude.collectAsState()
    val listState = rememberLazyListState()

    // Scroll chat to bottom when items change
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Futuristic Glowing Orb and Status Header
        Spacer(modifier = Modifier.height(16.dp))
        ZenyAvatarHeader(isRecording = isRecording, micAmplitude = micAmp)

        Spacer(modifier = Modifier.height(16.dp))

        // Chats list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(chatHistory) { msg ->
                ChatBubbleRow(msg = msg)
            }

            if (isLoading) {
                item {
                    ZenyLoadingBubble()
                }
            }
        }

        // Error message overlay
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = Color.Red,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // Voice Controls / Waveform Bar and TextInput
        BottomInputControls(
            viewModel = viewModel,
            isRecording = isRecording,
            micAmplitude = micAmp
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ZenyAvatarHeader(isRecording: Boolean, micAmplitude: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val scaleFactor = if (isRecording) 1f + (micAmplitude * 0.3f) else pulseScale

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .shadow(24.dp, shape = CircleShape, spotColor = NeonCyan)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan.copy(alpha = 0.3f), Color.Transparent),
                            center = center,
                            radius = size.width * scaleFactor
                        )
                    )
                }
                .border(2.dp, Brush.linearGradient(listOf(NeonCyan, NeonBlue)), CircleShape)
                .padding(4.dp)
        ) {
            AsyncImage(
                model = "/app/src/main/res/drawable/zeny_ai_orb_1783240747318.jpg",
                contentDescription = "Zeny AI",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                fallback = androidx.compose.ui.res.painterResource(id = android.R.drawable.presence_online)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isRecording) "Zeny is Listening..." else "Zeny AI",
            color = if (isRecording) NeonTeal else TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = if (isRecording) "Speak clearly. Tap to complete." else "Active & Ready",
            color = TextGray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ChatBubbleRow(msg: ChatMessage) {
    val isModel = msg.sender == "model"
    val align = if (isModel) Alignment.Start else Alignment.End
    val bg = if (isModel) CosmicSurface else CosmicSurfaceElevated
    val borderBrush = if (isModel) {
        Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.3f), NeonBlue.copy(alpha = 0.3f)))
    } else {
        Brush.linearGradient(listOf(NeonTeal.copy(alpha = 0.2f), Color.Transparent))
    }

    val bubbleShape = if (isModel) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 0.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .border(1.dp, borderBrush, bubbleShape)
                .background(bg, bubbleShape)
                .padding(12.dp)
        ) {
            Text(
                text = msg.message,
                color = TextWhite,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ZenyLoadingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "Loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(40.dp)
                .background(CosmicSurface, RoundedCornerShape(12.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    val delayAlpha = alpha
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(delayAlpha)
                            .background(NeonCyan, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomInputControls(
    viewModel: ZenyViewModel,
    isRecording: Boolean,
    micAmplitude: Float
) {
    var textInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceRecording()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text Input Field
        TextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("Ask Zeny to schedule/automate...", color = TextGray) },
            modifier = Modifier
                .weight(1f)
                .testTag("prompt_input")
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(24.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CosmicSurface,
                unfocusedContainerColor = CosmicSurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            trailingIcon = {
                if (textInput.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            viewModel.sendPrompt(textInput)
                            textInput = ""
                        },
                        modifier = Modifier.testTag("submit_button")
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = NeonCyan)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Large Glowing Neon Microphone Button
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape)
                .background(
                    if (isRecording) Brush.linearGradient(listOf(NeonTeal, NeonCyan))
                    else Brush.linearGradient(listOf(NeonCyan, NeonBlue)),
                    CircleShape
                )
                .clickable {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.toggleVoiceRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                // Interactive Waveform feedback inside the mic button
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = size.width / 2 * (1f + micAmplitude * 0.4f)
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop recording",
                    tint = CosmicBlack,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Voice prompt",
                    tint = CosmicBlack,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ==================== AGENDA / CALENDAR TAB ====================

@Composable
fun ZenyAgendaTab(
    viewModel: ZenyViewModel,
    calendarEvents: List<CalendarEvent>,
    searchFilter: String,
    onAddEventClick: () -> Unit
) {
    var selectedDateIndex by remember { mutableStateOf(0) }
    val days = remember { getCalendarDays() }

    // Synchronize filtering
    val searchInput = remember { mutableStateOf(searchFilter) }
    LaunchedEffect(searchFilter) {
        searchInput.value = searchFilter
    }

    val filteredEvents = calendarEvents.filter { event ->
        val dateMatches = isSameDay(event.startTime, days[selectedDateIndex])
        val textMatches = if (searchInput.value.isNotEmpty()) {
            event.title.contains(searchInput.value, ignoreCase = true) ||
                    event.description.contains(searchInput.value, ignoreCase = true)
        } else true
        dateMatches && textMatches
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Search and Heading Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar Agenda",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (calendarEvents.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear Chat Log", tint = TextGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text search input
            TextField(
                value = searchInput.value,
                onValueChange = {
                    searchInput.value = it
                    viewModel.setSearchQuery(it)
                },
                placeholder = { Text("Search your agenda...", color = TextGray) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextGray) },
                trailingIcon = {
                    if (searchInput.value.isNotEmpty()) {
                        IconButton(onClick = {
                            searchInput.value = ""
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextGray)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CosmicSurface,
                    unfocusedContainerColor = CosmicSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Date Scroller
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(days.size) { index ->
                    val day = days[index]
                    val isSelected = selectedDateIndex == index
                    DateCardItem(
                        dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(day.time),
                        dayNumber = SimpleDateFormat("d", Locale.getDefault()).format(day.time),
                        isSelected = isSelected,
                        onClick = { selectedDateIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive Event List
            if (filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.EventNote,
                            contentDescription = "No events",
                            tint = TextGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No events scheduled for this day.", color = TextGray, fontSize = 14.sp)
                        Text(text = "Tell Zeny AI to add one or tap '+' below.", color = TextGray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp) // padding for FAB
                ) {
                    items(filteredEvents) { event ->
                        CalendarEventCard(
                            event = event,
                            onToggleComplete = { viewModel.toggleEventCompletion(event) },
                            onDelete = { viewModel.deleteEvent(event) }
                        )
                    }
                }
            }
        }

        // Floating action button for interactive additions
        FloatingActionButton(
            onClick = onAddEventClick,
            containerColor = NeonBlue,
            contentColor = CosmicBlack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .shadow(12.dp, CircleShape)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Calendar Event")
        }
    }
}

@Composable
fun DateCardItem(
    dayName: String,
    dayNumber: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Brush.linearGradient(listOf(NeonBlue, NeonCyan)) else Brush.linearGradient(listOf(CosmicSurface, CosmicSurface))
    val textCol = if (isSelected) CosmicBlack else TextWhite
    val borderCol = if (isSelected) Color.Transparent else CosmicSurfaceElevated

    Column(
        modifier = Modifier
            .width(64.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = dayName.uppercase(), color = textCol.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = dayNumber, color = textCol, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun CalendarEventCard(
    event: CalendarEvent,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = when (event.category.lowercase()) {
        "work" -> NeonBlue
        "meeting" -> NeonCyan
        "fitness" -> NeonTeal
        "social" -> Color(0xFFFFD700) // Neon Yellow/Gold
        else -> TextGray
    }

    val startTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.startTime))
    val endTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.endTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Event core info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    color = if (event.isCompleted) TextGray else TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    style = if (event.isCompleted) MaterialTheme.typography.bodyMedium.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, contentDescription = "Time", tint = TextGray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "$startTimeStr - $endTimeStr", color = TextGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = event.category, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (event.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = event.description, color = TextGray, fontSize = 12.sp)
                }
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = event.isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonTeal,
                        uncheckedColor = TextGray
                    )
                )

                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Event", tint = TextGray)
                }
            }
        }
    }
}

// ==================== AUTOMATIONS TAB ====================

@Composable
fun ZenyAutomationsTab(
    viewModel: ZenyViewModel,
    zenyTasks: List<ZenyTask>,
    zenyWorkflows: List<ZenyWorkflow>,
    onAddAutomationClick: () -> Unit,
    onAddWorkflowClick: () -> Unit
) {
    var intentConsoleInput by remember { mutableStateOf("") }
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Advanced Workflows, 1: Simple Tasks

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Personalized Automation Hub",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Zeny AI parses complex instructions, designs pipelines, and automates workflows.",
                color = TextGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // AI Natural Language Scheduler Console
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonTeal.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Intelligent Automation Architect",
                        color = NeonTeal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Define triggers and nested multi-step sequences in plain language. Zeny parses and saves custom automation logic instantly:",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    TextField(
                        value = intentConsoleInput,
                        onValueChange = { intentConsoleInput = it },
                        placeholder = { Text("e.g., When I receive an email from boss, save attachment to ZenyFolder, and send a reply...", color = TextGray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CosmicSurfaceElevated,
                            unfocusedContainerColor = CosmicSurfaceElevated,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (intentConsoleInput.isNotEmpty()) {
                                viewModel.sendPrompt(intentConsoleInput)
                                intentConsoleInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal, contentColor = CosmicBlack),
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FlashOn, contentDescription = "Parse", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Deploy Automation", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Segmented Selector for Workflows vs Simple Rules
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CosmicSurface)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedSubTab == 0) CosmicSurfaceElevated else Color.Transparent)
                        .clickable { selectedSubTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sequence Pipelines (${zenyWorkflows.size})",
                        color = if (selectedSubTab == 0) NeonTeal else TextGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedSubTab == 1) CosmicSurfaceElevated else Color.Transparent)
                        .clickable { selectedSubTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Simple Tasks (${zenyTasks.size})",
                        color = if (selectedSubTab == 1) NeonTeal else TextGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedSubTab == 0) {
                // SEQUENTIAL WORKFLOWS
                if (zenyWorkflows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.AltRoute,
                                contentDescription = "No workflows",
                                tint = TextGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "No custom multi-step pipelines configured.", color = TextGray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Try writing one above to parse or click + below.", color = TextGray.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(zenyWorkflows) { workflow ->
                            ZenyWorkflowCard(
                                workflow = workflow,
                                onToggleActive = { viewModel.toggleWorkflowActive(workflow) },
                                onDelete = { viewModel.deleteWorkflow(workflow) }
                            )
                        }
                    }
                }
            } else {
                // SIMPLE AUTOMATION TASKS
                if (zenyTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.SettingsInputAntenna,
                                contentDescription = "No tasks",
                                tint = TextGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "No simple tasks registered.", color = TextGray, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(zenyTasks) { task ->
                            ZenyTaskCard(
                                task = task,
                                onToggleStatus = { viewModel.toggleTaskActive(task) },
                                onDelete = { viewModel.deleteTask(task) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button corresponding to current active tab!
        FloatingActionButton(
            onClick = {
                if (selectedSubTab == 0) onAddWorkflowClick() else onAddAutomationClick()
            },
            containerColor = NeonTeal,
            contentColor = CosmicBlack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .shadow(12.dp, CircleShape)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Automation")
        }
    }
}

@Composable
fun ZenyWorkflowCard(
    workflow: ZenyWorkflow,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    // Parse stepsJson into list
    val stepsList = remember(workflow.stepsJson) {
        try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(List::class.java)
            val raw = adapter.fromJson(workflow.stepsJson) as? List<Map<String, String>>
            raw ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AltRoute, contentDescription = null, tint = NeonTeal)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.title,
                        color = if (workflow.isActive) TextWhite else TextGray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Trigger: ${workflow.triggerPhrase}",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = workflow.isActive,
                    onCheckedChange = { onToggleActive() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonTeal,
                        checkedTrackColor = NeonTeal.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextGray,
                        uncheckedTrackColor = CosmicSurfaceElevated
                    )
                )

                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Workflow", tint = TextGray)
                }
            }

            if (stepsList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CosmicSurfaceElevated)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Pipeline Sequence:", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // Steps chain
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stepsList.forEachIndexed { index, step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicSurfaceElevated.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(NeonTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Text((index + 1).toString(), color = CosmicBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = when(step["type"]?.uppercase()) {
                                    "SAVE_ATTACHMENT" -> Icons.Filled.Save
                                    "CREATE_EVENT" -> Icons.Filled.Event
                                    "SEND_REPLY" -> Icons.Filled.Reply
                                    "SEARCH_INFO" -> Icons.Filled.Search
                                    else -> Icons.Filled.Notifications
                                },
                                contentDescription = null,
                                tint = NeonTeal,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step["type"] ?: "STEP",
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = step["parameter"] ?: "",
                                    color = TextGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (index < stepsList.size - 1) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .width(2.dp)
                                    .height(10.dp)
                                    .background(NeonTeal.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddWorkflowDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, trigger: String, steps: List<Map<String, String>>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    val steps = remember { mutableStateListOf<Map<String, String>>() }

    // State for temporary step addition
    var stepType by remember { mutableStateOf("SAVE_ATTACHMENT") }
    var stepParam by remember { mutableStateOf("") }

    val stepTypes = listOf(
        "SAVE_ATTACHMENT" to "Save Attachment",
        "CREATE_EVENT" to "Create Event",
        "SEND_REPLY" to "Send Reply",
        "SEARCH_INFO" to "Search Info",
        "NOTIFY" to "Send Notification"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Build Custom Pipeline",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure a sequential automation pipeline.",
                    color = TextGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Workflow Name (e.g., Email Ingestion)", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    placeholder = { Text("Trigger (e.g., When email arrives from boss)", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pipeline Steps (${steps.size})",
                    color = NeonTeal,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // List of added steps
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(steps.size) { index ->
                        val step = steps[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicSurfaceElevated, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when(step["type"]?.uppercase()) {
                                    "SAVE_ATTACHMENT" -> Icons.Filled.Save
                                    "CREATE_EVENT" -> Icons.Filled.Event
                                    "SEND_REPLY" -> Icons.Filled.Reply
                                    "SEARCH_INFO" -> Icons.Filled.Search
                                    else -> Icons.Filled.Notifications
                                },
                                contentDescription = null,
                                tint = NeonTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(step["type"] ?: "", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(step["parameter"] ?: "", color = TextGray, fontSize = 11.sp)
                            }
                            IconButton(
                                onClick = { steps.removeAt(index) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove Step", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (steps.isEmpty()) {
                    Text("No steps added yet. Add a step below.", color = TextGray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add dynamic step section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CosmicSurfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text("Add Next Action Step:", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Step Type selector Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(stepTypes) { (type, label) ->
                            val isSelected = stepType == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) NeonTeal else CosmicSurfaceElevated)
                                    .clickable { stepType = type }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(label, color = if (isSelected) CosmicBlack else TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = stepParam,
                        onValueChange = { stepParam = it },
                        placeholder = { Text("Step Parameter (e.g. Folder path, Event title)", color = TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurface, unfocusedContainerColor = CosmicSurface)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (stepParam.isNotEmpty()) {
                                steps.add(mapOf("type" to stepType, "parameter" to stepParam))
                                stepParam = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceElevated, contentColor = NeonTeal),
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Step", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Step", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotEmpty() && trigger.isNotEmpty() && steps.isNotEmpty()) {
                                onSave(title, trigger, steps.toList())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal, contentColor = CosmicBlack)
                    ) {
                        Text("Save Workflow", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ZenyTaskCard(
    task: ZenyTask,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Trigger Active",
                tint = if (task.isActive) NeonTeal else TextGray,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = if (task.isActive) TextWhite else TextGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (task.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = task.description, color = TextGray, fontSize = 12.sp)
                }
                if (task.triggerPhrase.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, contentDescription = "Schedule", tint = NeonTeal, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Trigger: ${task.triggerPhrase}", color = NeonTeal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Switch(
                checked = task.isActive,
                onCheckedChange = { onToggleStatus() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CosmicBlack,
                    checkedTrackColor = NeonTeal,
                    uncheckedThumbColor = TextGray,
                    uncheckedTrackColor = CosmicSurfaceElevated
                )
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = TextGray)
            }
        }
    }
}

// ==================== MANUAL EVENT DIALOG ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, category: String, date: Date, hour: Int, minute: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Work") }
    var hourInput by remember { mutableStateOf("15") }
    var minuteInput by remember { mutableStateOf("00") }

    val categories = listOf("Work", "Meeting", "Fitness", "Social", "General")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "New Agenda Item",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Event title...", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    placeholder = { Text("Details...", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Time Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = hourInput,
                        onValueChange = { hourInput = it },
                        placeholder = { Text("Hour (0-23)", color = TextGray) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                    )
                    TextField(
                        value = minuteInput,
                        onValueChange = { minuteInput = it },
                        placeholder = { Text("Min (0-59)", color = TextGray) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Category", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = category == cat
                        val color = when (cat.lowercase()) {
                            "work" -> NeonBlue
                            "meeting" -> NeonCyan
                            "fitness" -> NeonTeal
                            "social" -> Color(0xFFFFD700)
                            else -> TextGray
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) color else CosmicSurfaceElevated)
                                .clickable { category = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) CosmicBlack else TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotEmpty()) {
                                onSave(
                                    title,
                                    desc,
                                    category,
                                    Date(),
                                    hourInput.toIntOrNull() ?: 9,
                                    minuteInput.toIntOrNull() ?: 0
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = CosmicBlack)
                    ) {
                        Text("Save Event", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== MANUAL AUTOMATION DIALOG ====================

@Composable
fun AddAutomationDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, trigger: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CosmicSurfaceElevated, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "New Automation Rule",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Task / Action name...", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    placeholder = { Text("Description...", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    placeholder = { Text("Trigger criteria (e.g., daily at 9am)", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedContainerColor = CosmicSurfaceElevated, unfocusedContainerColor = CosmicSurfaceElevated)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotEmpty()) {
                                onSave(title, desc, trigger)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal, contentColor = CosmicBlack)
                    ) {
                        Text("Create Rule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== CALENDAR HELPERS ====================

fun getCalendarDays(): List<Calendar> {
    val list = mutableListOf<Calendar>()
    val current = Calendar.getInstance()
    // Align with Sunday of the current week
    current.set(Calendar.DAY_OF_WEEK, current.firstDayOfWeek)
    for (i in 0 until 7) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = current.timeInMillis
        }
        list.add(cal)
        current.add(Calendar.DAY_OF_YEAR, 1)
    }
    return list
}

fun isSameDay(time1: Long, calendar: Calendar): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    return cal1.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
}
