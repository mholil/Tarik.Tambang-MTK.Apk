package com.example

import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MathQuestion(val text: String, val answer: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tug_of_war_game_container"),
                    containerColor = Color.White
                ) { innerPadding ->
                    TugOfWarGameScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TugOfWarGameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isWebMode by remember { mutableStateOf(false) }

    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (e: Exception) { null }
    }

    val soundPool = remember {
        try {
            SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
        } catch (e: Exception) { null }
    }
    var wrongSoundId by remember { mutableIntStateOf(0) }
    var correctSoundId by remember { mutableIntStateOf(0) }
    var isSoundLoaded by remember { mutableStateOf(false) }

    DisposableEffect(context, soundPool) {
        if (soundPool != null) {
            soundPool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) {
                    isSoundLoaded = true
                }
            }
            wrongSoundId = try { soundPool.load(context, R.raw.wrong_answer, 1) } catch (e: Exception) { 0 }
            correctSoundId = try { soundPool.load(context, R.raw.correct_answer, 1) } catch (e: Exception) { 0 }
        }
        onDispose {
            soundPool?.release()
        }
    }

    fun playTone(toneType: Int, durationMs: Int = 100) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {}
    }

    fun playWrongAudio() {
        playTone(ToneGenerator.TONE_PROP_NACK, 200)
        if (wrongSoundId != 0) {
            try {
                soundPool?.play(wrongSoundId, 1f, 1f, 1, 0, 1f)
            } catch (_: Exception) {}
        }
    }

    fun playCorrectAudio() {
        playTone(ToneGenerator.TONE_PROP_ACK, 150)
        if (correctSoundId != 0) {
            try {
                soundPool?.play(correctSoundId, 1f, 1f, 1, 0, 1f)
            } catch (_: Exception) {}
        }
    }

    var selectedDuration by remember { mutableIntStateOf(60) }
    var selectedMaxRange by remember { mutableIntStateOf(10) }
    var timeRemaining by remember { mutableIntStateOf(60) }
    var isGameRunning by remember { mutableStateOf(false) }
    var isMusicEnabled by remember { mutableStateOf(false) }

    var targetRopePos by remember { mutableFloatStateOf(0f) }
    val animatedRopePos by animateFloatAsState(
        targetValue = targetRopePos,
        animationSpec = tween(durationMillis = 250),
        label = "ropeAnimation"
    )

    var blueScore by remember { mutableIntStateOf(0) }
    var redScore by remember { mutableIntStateOf(0) }

    var qBlue by remember { mutableStateOf(generateQuestion(selectedMaxRange)) }
    var qRed by remember { mutableStateOf(generateQuestion(selectedMaxRange)) }

    var inputBlue by remember { mutableStateOf("") }
    var inputRed by remember { mutableStateOf("") }

    var isWrongBlue by remember { mutableStateOf(false) }
    var isWrongRed by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf(true) }
    var winnerName by remember { mutableStateOf<String?>(null) }

    // Countdown Timer Loop
    LaunchedEffect(isGameRunning, timeRemaining, selectedDuration) {
        if (isGameRunning && selectedDuration > 0) {
            if (timeRemaining > 0) {
                delay(1000L)
                timeRemaining -= 1
            } else {
                isGameRunning = false
                winnerName = when {
                    targetRopePos < 0 -> "BLUE"
                    targetRopePos > 0 -> "RED"
                    else -> "DRAW"
                }
                playTone(ToneGenerator.TONE_CDMA_HIGH_L, 400)
                showDialog = true
            }
        }
    }

    fun checkWinCondition() {
        val winThreshold = 6.5f
        if (targetRopePos <= -winThreshold) {
            targetRopePos = -winThreshold
            isGameRunning = false
            winnerName = "BLUE"
            playTone(ToneGenerator.TONE_CDMA_HIGH_L, 400)
            showDialog = true
        } else if (targetRopePos >= winThreshold) {
            targetRopePos = winThreshold
            isGameRunning = false
            winnerName = "RED"
            playTone(ToneGenerator.TONE_CDMA_HIGH_L, 400)
            showDialog = true
        }
    }

    fun startNewGame() {
        targetRopePos = 0f
        blueScore = 0
        redScore = 0
        qBlue = generateQuestion(selectedMaxRange)
        qRed = generateQuestion(selectedMaxRange)
        inputBlue = ""
        inputRed = ""
        isWrongBlue = false
        isWrongRed = false
        timeRemaining = if (selectedDuration == 0) 999 else selectedDuration
        winnerName = null
        showDialog = false
        isGameRunning = true
        playTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun triggerWrongBlue() {
        coroutineScope.launch {
            isWrongBlue = true
            delay(650L)
            isWrongBlue = false
        }
    }

    fun triggerWrongRed() {
        coroutineScope.launch {
            isWrongRed = true
            delay(650L)
            isWrongRed = false
        }
    }

    BackHandler(enabled = isGameRunning && !showDialog) {
        showDialog = true
    }

    fun onKeyBlue(valStr: String) {
        if (!isGameRunning) return

        when (valStr) {
            "DEL" -> {
                playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                if (inputBlue.isNotEmpty()) inputBlue = inputBlue.dropLast(1)
            }
            "OK" -> {
                if (inputBlue.isEmpty()) {
                    playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                    return
                }
                val userAns = inputBlue.toIntOrNull()
                if (userAns == qBlue.answer) {
                    playCorrectAudio()
                    blueScore++
                    targetRopePos -= 1f
                    qBlue = generateQuestion(selectedMaxRange)
                    checkWinCondition()
                } else {
                    playWrongAudio()
                    triggerWrongBlue()
                    targetRopePos = (targetRopePos + 0.3f).coerceAtMost(10f)
                }
                inputBlue = ""
            }
            else -> {
                playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                if (inputBlue.length < 3) inputBlue += valStr
            }
        }
    }

    fun onKeyRed(valStr: String) {
        if (!isGameRunning) return

        when (valStr) {
            "DEL" -> {
                playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                if (inputRed.isNotEmpty()) inputRed = inputRed.dropLast(1)
            }
            "OK" -> {
                if (inputRed.isEmpty()) {
                    playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                    return
                }
                val userAns = inputRed.toIntOrNull()
                if (userAns == qRed.answer) {
                    playCorrectAudio()
                    redScore++
                    targetRopePos += 1f
                    qRed = generateQuestion(selectedMaxRange)
                    checkWinCondition()
                } else {
                    playWrongAudio()
                    triggerWrongRed()
                    targetRopePos = (targetRopePos - 0.3f).coerceAtLeast(-10f)
                }
                inputRed = ""
            }
            else -> {
                playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                if (inputRed.length < 3) inputRed += valStr
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        if (isWebMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()

                            val htmlStr = try {
                                ctx.assets.open("tug_of_war_math.html").bufferedReader().use { it.readText() }
                            } catch (e: Exception) {
                                "<html><body><h2>Error loading HTML asset</h2></body></html>"
                            }
                            loadDataWithBaseURL("https://local/", htmlStr, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Button(
                    onClick = { isWebMode = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text("Ke Mode Native", color = Color.White, fontSize = 12.sp)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    TeamPanel(
                        teamName = "TIM BIRU (P1)",
                        score = blueScore,
                        headerColor = Color(0xFF1976D2),
                        cardBorderColor = Color(0xFF90CAF9),
                        textColor = Color(0xFF0D47A1),
                        question = qBlue.text,
                        currentInput = inputBlue,
                        isWrong = isWrongBlue,
                        onKeyClick = { onKeyBlue(it) },
                        modifier = Modifier
                            .weight(0.3f)
                            .fillMaxHeight()
                    )

                    ArenaPanel(
                        selectedDuration = selectedDuration,
                        timeRemaining = timeRemaining,
                        ropePosition = animatedRopePos,
                        isGameRunning = isGameRunning,
                        isMusicEnabled = isMusicEnabled,
                        onToggleMusic = { isMusicEnabled = !isMusicEnabled },
                        onRestartClick = { showDialog = true },
                        winnerName = winnerName,
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                    )

                    TeamPanel(
                        teamName = "TIM MERAH (P2)",
                        score = redScore,
                        headerColor = Color(0xFFE53935),
                        cardBorderColor = Color(0xFFEF9A9A),
                        textColor = Color(0xFFB71C1C),
                        question = qRed.text,
                        currentInput = inputRed,
                        isWrong = isWrongRed,
                        onKeyClick = { onKeyRed(it) },
                        modifier = Modifier
                            .weight(0.3f)
                            .fillMaxHeight()
                    )
                }

                if (showDialog) {
                    GameOverlayDialog(
                        winnerName = winnerName,
                        blueScore = blueScore,
                        redScore = redScore,
                        selectedDuration = selectedDuration,
                        selectedRange = selectedMaxRange,
                        onDurationSelect = { selectedDuration = it },
                        onRangeSelect = { selectedMaxRange = it },
                        onStartGame = { startNewGame() }
                    )
                }
            }
        }
    }
}

fun generateQuestion(maxRange: Int): MathQuestion {
    val isAdd = Random.nextBoolean()
    return if (isAdd) {
        val a = Random.nextInt(1, maxRange + 1)
        val b = Random.nextInt(1, maxRange + 1)
        MathQuestion("$a + $b", a + b)
    } else {
        val a = Random.nextInt(2, maxRange + 1)
        val b = Random.nextInt(1, a)
        MathQuestion("$a - $b", a - b)
    }
}

@Composable
fun TeamPanel(
    teamName: String,
    score: Int,
    headerColor: Color,
    cardBorderColor: Color,
    textColor: Color,
    question: String,
    currentInput: String,
    isWrong: Boolean,
    onKeyClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentCardBorder = if (isWrong) Color(0xFFD32F2F) else cardBorderColor
    val currentCardBg = if (isWrong) Color(0xFFFFEBEE) else Color.White

    Column(
        modifier = modifier
            .background(Color.White)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(headerColor)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = teamName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "Skor: $score",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.5.dp, currentCardBorder, RoundedCornerShape(12.dp))
                    .background(currentCardBg)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = question,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(36.dp)
                        .border(1.dp, if (isWrong) Color(0xFFE53935) else Color.LightGray, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentInput,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            if (isWrong) {
                Surface(
                    color = Color(0xFFD32F2F),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-10).dp)
                ) {
                    Text(
                        text = "❌ SALAH!",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        val keys = listOf("7", "8", "9", "4", "5", "6", "1", "2", "3", "DEL", "0", "OK")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0..2) {
                        val keyVal = keys[row * 3 + col]
                        KeypadButton(
                            text = if (keyVal == "DEL") "⌫" else if (keyVal == "OK") "✓" else keyVal,
                            isSpecial = keyVal == "DEL" || keyVal == "OK",
                            isOk = keyVal == "OK",
                            onClick = { onKeyClick(keyVal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    isSpecial: Boolean,
    isOk: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isOk -> Color(0xFF4CAF50)
        isSpecial -> Color(0xFFFFF3E0)
        else -> Color(0xFFF5F5F5)
    }
    val txtColor = when {
        isOk -> Color.White
        isSpecial -> Color(0xFFE65100)
        else -> Color(0xFF333333)
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, if (isOk) Color(0xFF388E3C) else Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = txtColor
        )
    }
}

@Composable
fun ArenaPanel(
    selectedDuration: Int,
    timeRemaining: Int,
    ropePosition: Float,
    isGameRunning: Boolean,
    isMusicEnabled: Boolean,
    onToggleMusic: () -> Unit,
    onRestartClick: () -> Unit,
    winnerName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val timerTxt = if (selectedDuration == 0) "Bebas" else {
            val mins = timeRemaining / 60
            val secs = timeRemaining % 60
            String.format("%02d:%02d", mins, secs)
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFFEBEE))
                .border(2.dp, Color(0xFFFFCDD2), RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Text(
                text = timerTxt,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFD32F2F)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
                .background(Color.White)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTugOfWarArena(
                    ropePos = ropePosition,
                    winnerName = winnerName
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Button(
                onClick = onRestartClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Pengaturan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = onToggleMusic,
                colors = ButtonDefaults.buttonColors(containerColor = if (isMusicEnabled) Color(0xFF7E57C2) else Color(0xFFB0BEC5)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isMusicEnabled) "🎵 Musik: ON" else "🔇 Musik: OFF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

fun DrawScope.drawCartoonPlayerFigure(
    centerX: Float,
    centerY: Float,
    teamColor: Color,
    isLeftTeam: Boolean
) {
    val headRadius = 18.dp.toPx()
    val headY = centerY - 38.dp.toPx()
    val headX = centerX

    // Head / Skin
    drawCircle(
        color = Color(0xFFFFD180),
        radius = headRadius,
        center = Offset(headX, headY)
    )

    // Hair
    drawCircle(
        color = Color(0xFF37474F),
        radius = headRadius * 0.9f,
        center = Offset(headX, headY - 6.dp.toPx())
    )

    // Eyes
    val eyeOffsetX = if (isLeftTeam) 6.dp.toPx() else -6.dp.toPx()
    drawCircle(
        color = Color.Black,
        radius = 2.5f.dp.toPx(),
        center = Offset(headX + eyeOffsetX, headY - 2.dp.toPx())
    )

    // Body / Shirt
    val bodyWidth = 24.dp.toPx()
    val bodyHeight = 32.dp.toPx()
    val bodyTopY = headY + headRadius * 0.7f
    drawRoundRect(
        color = teamColor,
        topLeft = Offset(headX - bodyWidth / 2f, bodyTopY),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )

    // Arms holding the rope
    val handX = if (isLeftTeam) headX + 18.dp.toPx() else headX - 18.dp.toPx()
    drawLine(
        color = Color(0xFFFFD180),
        start = Offset(headX, bodyTopY + 10.dp.toPx()),
        end = Offset(handX, centerY),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Legs
    val legY = bodyTopY + bodyHeight
    val leg1X = headX - 7.dp.toPx()
    val leg2X = headX + 7.dp.toPx()
    val legDir = if (isLeftTeam) -6.dp.toPx() else 6.dp.toPx()
    drawLine(
        color = Color(0xFF263238),
        start = Offset(leg1X, legY),
        end = Offset(leg1X + legDir, legY + 22.dp.toPx()),
        strokeWidth = 7.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF263238),
        start = Offset(leg2X, legY),
        end = Offset(leg2X + legDir, legY + 22.dp.toPx()),
        strokeWidth = 7.dp.toPx(),
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawTugOfWarArena(
    ropePos: Float,
    winnerName: String?
) {
    val w = size.width
    val h = size.height
    val centerY = h * 0.55f

    val centerX = w / 2f
    val maxShiftPx = w * 0.30f
    val offsetPx = (ropePos / 6.5f) * maxShiftPx
    val ropeCenterX = centerX + offsetPx

    // Ground line
    drawLine(
        color = Color(0xFFECEFF1),
        start = Offset(10f, centerY + 55.dp.toPx()),
        end = Offset(w - 10f, centerY + 55.dp.toPx()),
        strokeWidth = 3.dp.toPx()
    )

    // Dashed center marker line
    drawLine(
        color = Color(0xFFB0BEC5),
        start = Offset(centerX, centerY - h * 0.35f),
        end = Offset(centerX, centerY + h * 0.35f),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    )

    // Rope connecting both teams
    drawLine(
        color = Color(0xFF8D6E63),
        start = Offset(ropeCenterX - w * 0.45f, centerY),
        end = Offset(ropeCenterX + w * 0.45f, centerY),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Center Red Ribbon Marker
    drawCircle(
        color = Color(0xFFE53935),
        radius = 10.dp.toPx(),
        center = Offset(ropeCenterX, centerY)
    )

    // Render Blue Team (1 Player on Left)
    val blueColor = Color(0xFF2196F3)
    val blueOffsets = listOf(115.dp.toPx())
    for (off in blueOffsets) {
        drawCartoonPlayerFigure(
            centerX = ropeCenterX - off,
            centerY = centerY,
            teamColor = blueColor,
            isLeftTeam = true
        )
    }

    // Render Red Team (1 Player on Right)
    val redColor = Color(0xFFF44336)
    val redOffsets = listOf(115.dp.toPx())
    for (off in redOffsets) {
        drawCartoonPlayerFigure(
            centerX = ropeCenterX + off,
            centerY = centerY,
            teamColor = redColor,
            isLeftTeam = false
        )
    }

    if (winnerName != null) {
        val colors = listOf(Color(0xFF2196F3), Color(0xFFF44336), Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFF9C27B0))
        for (i in 0..50) {
            val cx = (i * 17) % w
            val cy = (i * 23) % (h * 0.8f)
            val color = colors[i % colors.size]
            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

@Composable
fun GameOverlayDialog(
    winnerName: String?,
    blueScore: Int,
    redScore: Int,
    selectedDuration: Int,
    selectedRange: Int,
    onDurationSelect: (Int) -> Unit,
    onRangeSelect: (Int) -> Unit,
    onStartGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.96f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .width(440.dp)
                .padding(12.dp)
                .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val title = when (winnerName) {
                    "BLUE" -> "TIM BIRU MENANG! 🎉"
                    "RED" -> "TIM MERAH MENANG! 🎉"
                    "DRAW" -> "HASIL SERI (DRAW)! 🤝"
                    else -> "Tug of War: Math"
                }

                val titleColor = when (winnerName) {
                    "BLUE" -> Color(0xFF1565C0)
                    "RED" -> Color(0xFFC62828)
                    "DRAW" -> Color(0xFFF57F17)
                    else -> Color(0xFF1565C0)
                }

                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                val subtitle = when (winnerName) {
                    "BLUE" -> "Tim Biru berhasil menarik tali lebih jauh! (Skor: $blueScore)"
                    "RED" -> "Tim Merah berhasil menarik tali lebih jauh! (Skor: $redScore)"
                    "DRAW" -> "Kedua tim sama-sama kuat! (Biru: $blueScore | Merah: $redScore)"
                    else -> "Pilih Tingkat Soal & Durasi Waktu untuk Mulai!"
                }

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF555555),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "RENTANG ANGKA SOAL:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF78909C)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val rangeOptions = listOf(
                        10 to "1 - 10 (Mudah)",
                        20 to "1 - 20 (Sedang)",
                        50 to "1 - 50 (Tantangan)"
                    )
                    rangeOptions.forEach { (rangeVal, label) ->
                        val isSelected = selectedRange == rangeVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1976D2) else Color(0xFFF5F5F5))
                                .border(1.dp, if (isSelected) Color(0xFF0D47A1) else Color(0xFFCFD8DC), RoundedCornerShape(12.dp))
                                .clickable { onRangeSelect(rangeVal) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF455A64)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "DURASI WAKTU PERMAINAN:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF78909C)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val timerOptions = listOf(30 to "30 Detik", 60 to "60 Detik", 90 to "90 Detik", 0 to "Bebas")
                    timerOptions.forEach { (sec, label) ->
                        val isSelected = selectedDuration == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF26A69A) else Color(0xFFF5F5F5))
                                .border(1.dp, if (isSelected) Color(0xFF00897B) else Color(0xFFCFD8DC), RoundedCornerShape(12.dp))
                                .clickable { onDurationSelect(sec) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF455A64)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStartGame,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (winnerName != null) "Main Lagi" else "Mulai Main",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
