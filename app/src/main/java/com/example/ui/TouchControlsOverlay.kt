package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlayerSaveEntity
import com.example.model.GraphicsQuality
import com.example.viewmodel.GameEngineState
import com.example.viewmodel.LevelVictorySummary
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun GameHudAndControlsOverlay(
    state: GameEngineState,
    playerSave: PlayerSaveEntity?,
    onJoystickChanged: (Float, Float) -> Unit,
    onNudgeDirection: (Float, Float) -> Unit,
    onJump: () -> Unit,
    onDodge: () -> Unit,
    onAttack: () -> Unit,
    onInteract: () -> Unit,
    onToggleHintPath: () -> Unit,
    onCycleGraphicsQuality: () -> Unit,
    onAdjustZoom: (Float) -> Unit,
    onNextLevel: () -> Unit,
    onRetryLevel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val level = state.level ?: return

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Top Translucent Adventure HUD with Real-Time Graphics Quality, FPS & Clear Objectives
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                color = Color(0xD90E131F),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x33FFB800), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LVL ${level.levelNumber}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color(0xFFFFB800),
                                    modifier = Modifier.testTag("hud_level_number")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = level.biome.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    modifier = Modifier.testTag("hud_biome_name")
                                )
                            }
                            Text(
                                text = "${level.difficultyTier.title} • ${level.biome.weatherType.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        // Coins, Gems, Hint Route & Quick Restart Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CurrencyPill(
                                label = "${(playerSave?.coins ?: 250) + state.sessionCoinsEarned}",
                                accent = Color(0xFFFFB800),
                                tag = "hud_coins_badge"
                            )
                            CurrencyPill(
                                label = "${(playerSave?.gems ?: 20) + state.sessionGemsEarned}",
                                accent = Color(0xFF38BDF8),
                                tag = "hud_gems_badge"
                            )
                            IconButton(
                                onClick = onToggleHintPath,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.showSolutionHintPath) Color(0xFF06B6D4) else Color(0xFF1E293B)
                                    )
                                    .testTag("toggle_hint_path_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CompassCalibration,
                                    contentDescription = "Toggle Verified Escape Route",
                                    tint = if (state.showSolutionHintPath) Color.Black else Color(0xFF38BDF8)
                                )
                            }
                            IconButton(
                                onClick = onRetryLevel,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .testTag("hud_restart_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Restart Level",
                                    tint = Color(0xFFFFB800)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Real-Time Graphics Quality Switcher, FPS Monitor & Camera Zoom Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val qualityColor = when (state.graphicsQuality) {
                                GraphicsQuality.ULTRA -> Color(0xFF10B981)
                                GraphicsQuality.HIGH -> Color(0xFF38BDF8)
                                GraphicsQuality.PERFORMANCE -> Color(0xFFF59E0B)
                            }
                            Surface(
                                color = qualityColor.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, qualityColor, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onCycleGraphicsQuality)
                                    .testTag("hud_quality_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HighQuality,
                                        contentDescription = "Graphics Quality",
                                        tint = qualityColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = state.graphicsQuality.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }

                            Text(
                                text = "${state.currentFps} FPS • ${((state.frameTimeMs * 10).toInt() / 10f)}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.testTag("hud_fps_badge")
                            )
                        }

                        // Camera Zoom Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onAdjustZoom(-0.1f) }
                                    .testTag("hud_zoom_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Zoom Out",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(15.dp)
                                )
                            }
                            Text(
                                text = "${(state.cameraZoom * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8)
                            )
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onAdjustZoom(0.1f) }
                                    .testTag("hud_zoom_in_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom In",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Health, Stamina & Remaining Keys / Treasures Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "HP ${state.playerHp}/${state.playerMaxHp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier
                                        .width(72.dp)
                                        .testTag("hud_hp_text")
                                )
                                LinearProgressIndicator(
                                    progress = { (state.playerHp.toFloat() / state.playerMaxHp.toFloat()).coerceIn(0f, 1f) },
                                    color = Color(0xFFEF4444),
                                    trackColor = Color(0xFF334155),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "STA ${state.playerStamina.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.width(72.dp)
                                )
                                LinearProgressIndicator(
                                    progress = { (state.playerStamina / 100f).coerceIn(0f, 1f) },
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF334155),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val keysLeft = max(0, state.keysRequired - state.keysCollected)
                            ObjectiveChip(
                                icon = Icons.Default.Key,
                                text = if (keysLeft == 0) "Keys Done" else "$keysLeft Key Left",
                                active = keysLeft == 0,
                                color = Color(0xFF06B6D4),
                                tag = "hud_remaining_keys"
                            )
                            ObjectiveChip(
                                icon = Icons.Default.Diamond,
                                text = "${state.remainingTreasures} Loot",
                                active = state.remainingTreasures == 0,
                                color = Color(0xFFFFB800),
                                tag = "hud_remaining_treasures"
                            )
                            ObjectiveChip(
                                icon = Icons.Default.AutoAwesome,
                                text = "${state.secretsFoundInLevel}/${state.totalSecretsInLevel}",
                                active = state.secretsFoundInLevel > 0,
                                color = Color(0xFFA855F7),
                                tag = "hud_secrets_chip"
                            )
                        }
                    }

                    // Cinematic Boss Camera Health Bar if Boss is Engaged
                    if (state.bossCameraActive && state.activeBossName != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hud_boss_banner")
                        ) {
                            Text(
                                text = "BOSS ENGAGED: ${state.activeBossName.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF4444)
                            )
                            LinearProgressIndicator(
                                progress = { state.activeBossHpRatio.coerceIn(0f, 1f) },
                                color = Color(0xFFDC2626),
                                trackColor = Color(0xFF334155),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.currentObjectiveText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.portalUnlocked) Color(0xFF10B981) else Color(0xFFFFB800),
                        modifier = Modifier.testTag("hud_objective_text")
                    )
                    Text(
                        text = state.statusBanner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.testTag("hud_status_banner")
                    )
                }
            }
        }

        // 2. Bottom-Left Floating 360° Virtual Joystick + D-Pad & Bottom-Right Action Pad
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            JoystickAndDpadCluster(
                onVectorChanged = onJoystickChanged,
                onNudgeDirection = onNudgeDirection
            )

            ActionButtonsCluster(
                onJump = onJump,
                onDodge = onDodge,
                onAttack = onAttack,
                onInteract = onInteract
            )
        }

        // 3. Level Victory Modal
        val victory = state.victorySummary
        if (victory != null) {
            VictoryOverlayModal(
                summary = victory,
                onNextLevel = onNextLevel,
                onRestartLevel = onRetryLevel
            )
        }

        // 4. Defeat Modal
        if (state.isDefeated) {
            DefeatOverlayModal(
                levelNumber = level.levelNumber,
                biomeName = level.biome.displayName,
                onRetry = onRetryLevel
            )
        }
    }
}

@Composable
private fun CurrencyPill(label: String, accent: Color, tag: String) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50))
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ObjectiveChip(
    icon: ImageVector,
    text: String,
    active: Boolean,
    color: Color,
    tag: String
) {
    Surface(
        color = if (active) color.copy(alpha = 0.22f) else Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .border(
                1.dp,
                if (active) color else Color(0xFF334155),
                RoundedCornerShape(10.dp)
            )
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun JoystickAndDpadCluster(
    onVectorChanged: (Float, Float) -> Unit,
    onNudgeDirection: (Float, Float) -> Unit
) {
    val currentOnVectorChanged by rememberUpdatedState(onVectorChanged)
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOrigin by remember { mutableStateOf<Offset?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(142.dp)
                .testTag("virtual_joystick")
                .pointerInput(Unit) {
                    val maxRadius = size.width * 0.36f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = down.position
                        dragOrigin = origin
                        knobOffset = Offset.Zero

                        var pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                break
                            }
                            pointerId = change.id
                            change.consume()
                            val raw = change.position - origin
                            val dist = hypot(raw.x, raw.y)
                            knobOffset = if (dist > maxRadius && dist > 0.001f) {
                                Offset((raw.x / dist) * maxRadius, (raw.y / dist) * maxRadius)
                            } else {
                                raw
                            }
                            currentOnVectorChanged(
                                (knobOffset.x / maxRadius).coerceIn(-1f, 1f),
                                (knobOffset.y / maxRadius).coerceIn(-1f, 1f)
                            )
                        }

                        dragOrigin = null
                        knobOffset = Offset.Zero
                        currentOnVectorChanged(0f, 0f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = dragOrigin ?: Offset(size.width / 2f, size.height / 2f)
                val outerR = size.width * 0.42f
                drawCircle(
                    color = Color(0x990E131F),
                    radius = outerR,
                    center = center
                )
                drawCircle(
                    color = Color(0x8838BDF8),
                    radius = outerR,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f)
                )
                drawCircle(
                    color = Color(0x3338BDF8),
                    radius = outerR * 0.55f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
                drawCircle(
                    color = Color(0xFFFFB800),
                    radius = size.width * 0.17f,
                    center = center + knobOffset
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DpadMiniButton(
                icon = Icons.Default.KeyboardArrowUp,
                desc = "Move Up",
                tag = "dpad_up",
                onClick = { onNudgeDirection(0f, -1f) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DpadMiniButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    desc = "Move Left",
                    tag = "dpad_left",
                    onClick = { onNudgeDirection(-1f, 0f) }
                )
                DpadMiniButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    desc = "Move Right",
                    tag = "dpad_right",
                    onClick = { onNudgeDirection(1f, 0f) }
                )
            }
            DpadMiniButton(
                icon = Icons.Default.KeyboardArrowDown,
                desc = "Move Down",
                tag = "dpad_down",
                onClick = { onNudgeDirection(0f, 1f) }
            )
        }
    }
}

@Composable
private fun DpadMiniButton(
    icon: ImageVector,
    desc: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xCC1E293B),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x5538BDF8), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ActionButtonsCluster(
    onJump: () -> Unit,
    onDodge: () -> Unit,
    onAttack: () -> Unit,
    onInteract: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameActionButton(
                label = "JUMP",
                icon = Icons.Default.Bolt,
                bgColor = Color(0xFF0284C7),
                tag = "action_jump_button",
                onClick = onJump
            )
            GameActionButton(
                label = "DASH",
                icon = Icons.Default.Shield,
                bgColor = Color(0xFF7C3AED),
                tag = "action_dodge_button",
                onClick = onDodge
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameActionButton(
                label = "USE",
                icon = Icons.Default.Key,
                bgColor = Color(0xFF059669),
                tag = "action_interact_button",
                onClick = onInteract
            )
            GameActionButton(
                label = "STRIKE",
                icon = Icons.Default.FlashOn,
                bgColor = Color(0xFFD97706),
                tag = "action_attack_button",
                onClick = onAttack
            )
        }
    }
}

@Composable
private fun GameActionButton(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = bgColor.copy(alpha = 0.92f),
        shape = CircleShape,
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun VictoryOverlayModal(
    summary: LevelVictorySummary,
    onNextLevel: () -> Unit,
    onRestartLevel: () -> Unit
) {
    val currentOnNextLevel by rememberUpdatedState(onNextLevel)
    var countdown by remember(summary.completedLevel, summary.seed) { mutableIntStateOf(3) }

    LaunchedEffect(summary.completedLevel, summary.seed) {
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
        currentOnNextLevel()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC060911))
            .padding(24.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color(0xFFFFB800), RoundedCornerShape(24.dp))
                .testTag("victory_modal")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "PORTAL ESCAPED!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFFFB800)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Level ${summary.completedLevel} • ${summary.biomeName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..3) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Star $i",
                            tint = if (i <= summary.stars) Color(0xFFFFB800) else Color(0xFF475569),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn("Coins", "+${summary.coinsEarned}", Color(0xFFFFB800))
                    StatColumn("Gems", "+${summary.gemsEarned}", Color(0xFF38BDF8))
                    StatColumn("Secrets", "${summary.secretsFound}", Color(0xFFA855F7))
                    StatColumn("Time", "${summary.timeSeconds}s", Color(0xFF10B981))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Auto-generating Level ${summary.nextLevel} in ${countdown}s...",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF38BDF8)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNextLevel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB800)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("next_level_button")
                ) {
                    Text(
                        text = "Next Level (${summary.nextLevel}) Now",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF0E131F)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRestartLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("victory_restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Replay Level ${summary.completedLevel}",
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DefeatOverlayModal(
    levelNumber: Int,
    biomeName: String,
    onRetry: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC060911))
            .padding(24.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(24.dp))
                .testTag("defeat_modal")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "FALLEN IN THE DARK",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFEF4444)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Level $levelNumber • $biomeName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("retry_level_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restart Level $levelNumber",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8)
        )
    }
}
