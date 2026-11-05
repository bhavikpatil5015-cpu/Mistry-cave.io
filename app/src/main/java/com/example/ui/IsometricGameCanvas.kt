package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.engine.ProceduralMaterialAtlas
import com.example.model.BiomeType
import com.example.model.CharacterAnimState
import com.example.model.CharacterSkin
import com.example.model.DestructiblePropEntity
import com.example.model.EnemyEntity
import com.example.model.GraphicsQuality
import com.example.model.HeroCharacter
import com.example.model.IsoTile
import com.example.model.RewardItemEntity
import com.example.model.RewardType
import com.example.model.TileType
import com.example.viewmodel.GameEngineState
import com.example.viewmodel.ParticleFx
import com.example.viewmodel.WeatherMote
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun IsometricGameCanvas(
    state: GameEngineState,
    hero: HeroCharacter,
    skin: CharacterSkin,
    particles: Array<ParticleFx>,
    weatherMotes: Array<WeatherMote>,
    onStepPhysics: (Float) -> Unit,
    onPinchZoom: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val topFacePath = remember { Path() }
    val leftFacePath = remember { Path() }
    val rightFacePath = remember { Path() }

    LaunchedEffect(state.level?.seed, state.victorySummary == null, !state.isDefeated) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = (nanos - lastNanos) / 1_000_000_000f
                    onStepPhysics(dt)
                }
                lastNanos = nanos
            }
        }
    }

    val level = state.level ?: return
    val quality = state.graphicsQuality
    val materialSet = remember(level.biome, quality) {
        ProceduralMaterialAtlas.getMaterialSet(level.biome, quality)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B12))
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    if (abs(zoomChange - 1f) > 0.01f) {
                        onPinchZoom((zoomChange - 1f) * 0.5f)
                    }
                }
            }
            .testTag("isometric_game_canvas")
    ) {
        val shakeOffset = if (state.screenShakeTimer > 0f) {
            sin(state.worldTime * 65f) * 7.5f * (state.screenShakeTimer / 0.24f)
        } else 0f

        val centerX = size.width * 0.5f + shakeOffset
        val centerY = size.height * 0.46f - shakeOffset * 0.5f

        val baseTileHalfW = (min(size.width, size.height) / 13.0f).coerceIn(26f, 56f)
        val tileHalfW = baseTileHalfW * state.cameraZoom
        val tileHalfH = tileHalfW * 0.52f
        val heightScale = tileHalfW * 1.18f

        // PASS 1: Atmospheric Biome Skybox & Deep Vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    level.biome.ambientLight.copy(alpha = 0.24f),
                    Color(0xFF0B101B),
                    Color(0xFF05080F)
                ),
                center = Offset(centerX, centerY),
                radius = max(size.width, size.height) * 0.80f
            )
        )

        val camWorldX = state.cameraX
        val camWorldY = state.cameraY

        fun worldToScreen(wx: Float, wy: Float, wz: Float): Offset {
            val dx = wx - camWorldX
            val dy = wy - camWorldY
            val sx = centerX + (dx - dy) * tileHalfW
            val sy = centerY + (dx + dy) * tileHalfH - wz * heightScale
            return Offset(sx, sy)
        }

        // PASS 2: Frustum Culling Window Scaled to Quality Tier & Camera Zoom
        val viewRadius = (quality.cullRadiusTiles / state.cameraZoom.coerceAtLeast(0.75f)).roundToInt().coerceIn(7, 14)
        val minGx = max(0, (camWorldX.toInt() - viewRadius))
        val maxGx = min(level.width - 1, (camWorldX.toInt() + viewRadius))
        val minGy = max(0, (camWorldY.toInt() - viewRadius))
        val maxGy = min(level.height - 1, (camWorldY.toInt() + viewRadius))

        val hintSet = if (state.showSolutionHintPath) {
            level.verifiedSolutionPath.toHashSet()
        } else {
            emptySet()
        }

        val rewardByTile = HashMap<Int, RewardItemEntity>()
        for (r in state.rewards) {
            // Keep chests visible while their lid opening animation completes
            if (!r.isCollected || ((r.type == RewardType.TREASURE_CHEST || r.type == RewardType.RARE_ARTEFACT) && r.openAnimProgress < 1f)) {
                rewardByTile[r.gx * 1000 + r.gy] = r
            }
        }

        val propByTile = HashMap<Int, DestructiblePropEntity>()
        for (prop in state.props) {
            if (!prop.isDestroyed || prop.destroyAnimProgress < 1f) {
                propByTile[prop.gx * 1000 + prop.gy] = prop
            }
        }

        val playerGridX = state.playerX.toInt()
        val playerGridY = state.playerY.toInt()

        // PASS 3: Isometric Painter's Algorithm Pass (Back-to-Front Depth Sorting)
        val minSum = minGx + minGy
        val maxSum = maxGx + maxGy

        for (sum in minSum..maxSum) {
            val startX = max(minGx, sum - maxGy)
            val endX = min(maxGx, sum - minGy)

            for (gx in startX..endX) {
                val gy = sum - gx
                val tile = level.tiles[gx][gy]
                if (tile.type == TileType.VOID) continue
                if (tile.isSecret && !tile.isRevealed && tile.type == TileType.SECRET_FLOOR) continue

                val hasNorthWestWall = (gx > 0 && !level.tiles[gx - 1][gy].type.isWalkable) ||
                    (gy > 0 && !level.tiles[gx][gy - 1].type.isWalkable)
                val aoCreaseCount = (if (gx > 0 && !level.tiles[gx - 1][gy].type.isWalkable) 1 else 0) +
                    (if (gy > 0 && !level.tiles[gx][gy - 1].type.isWalkable) 1 else 0)

                drawIsometricTileBlock(
                    tile = tile,
                    biome = level.biome,
                    quality = quality,
                    materialSet = materialSet,
                    worldTime = state.worldTime,
                    portalUnlocked = state.portalUnlocked,
                    isHintTile = hintSet.any { it.x == gx && it.y == gy },
                    hasCastShadow = hasNorthWestWall && tile.type.isWalkable,
                    aoCreaseCount = aoCreaseCount,
                    tileHalfW = tileHalfW,
                    tileHalfH = tileHalfH,
                    topPath = topFacePath,
                    leftPath = leftFacePath,
                    rightPath = rightFacePath,
                    worldToScreen = ::worldToScreen
                )

                // Draw destructible / animated biome prop on this tile
                val prop = propByTile[gx * 1000 + gy]
                if (prop != null) {
                    val propScreen = worldToScreen(gx + 0.5f, gy + 0.5f, tile.height)
                    drawDestructibleProp3D(
                        prop = prop,
                        quality = quality,
                        screenPos = propScreen,
                        tileHalfW = tileHalfW,
                        worldTime = state.worldTime
                    )
                }

                // Draw collectible reward or opening chest on this tile
                val reward = rewardByTile[gx * 1000 + gy]
                if (reward != null && (!reward.isSecret || tile.isRevealed)) {
                    val basePos = worldToScreen(gx + 0.5f, gy + 0.5f, tile.height)
                    drawReward3D(
                        reward = reward,
                        quality = quality,
                        screenPos = basePos,
                        tileHalfW = tileHalfW,
                        worldTime = state.worldTime
                    )
                }

                // Draw enemies (including real-time death animations)
                for (enemy in state.enemies) {
                    if (enemy.x.toInt() == gx && enemy.y.toInt() == gy) {
                        val eTileH = level.tiles[gx][gy].height
                        val enemyScreen = worldToScreen(enemy.x, enemy.y, eTileH)
                        drawEnemyCharacter3D(
                            enemy = enemy,
                            biome = level.biome,
                            quality = quality,
                            screenPos = enemyScreen,
                            tileHalfW = tileHalfW,
                            worldTime = state.worldTime
                        )
                    }
                }

                // Draw player & dash ghosts when in this depth slice
                if (gx == playerGridX && gy == playerGridY) {
                    val groundH = level.tiles[gx][gy].height
                    for (ghost in state.dashGhosts) {
                        val gPos = worldToScreen(ghost.worldX, ghost.worldY, groundH + ghost.worldZ)
                        drawCircle(
                            color = skin.glowColor.copy(alpha = ghost.alpha * 0.55f),
                            radius = tileHalfW * 0.32f,
                            center = Offset(gPos.x, gPos.y - tileHalfW * 0.45f)
                        )
                    }
                    val groundPos = worldToScreen(state.playerX, state.playerY, groundH)
                    val elevatedPos = worldToScreen(state.playerX, state.playerY, groundH + state.playerZ)
                    drawPlayerCharacter3D(
                        state = state,
                        hero = hero,
                        skin = skin,
                        quality = quality,
                        groundPos = groundPos,
                        elevatedPos = elevatedPos,
                        tileHalfW = tileHalfW
                    )
                }
            }
        }

        // PASS 4: Object-Pooled 2.5D Combat Particles
        for (p in particles) {
            if (p.active) {
                val ptScreen = worldToScreen(p.worldX, p.worldY, 0.3f + p.worldZ)
                val alpha = (1f - (p.life / p.maxLife)).coerceIn(0f, 1f)
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = tileHalfW * 0.16f * alpha + 2f,
                    center = ptScreen
                )
            }
        }

        // PASS 5: Real-Time Atmospheric Fog & Biome Weather Particles (Spores, Snow, Sand, Embers, Mist)
        if (quality.enableAtmosphericFogAndWeather) {
            drawRect(color = level.biome.fogColor)
            val moteCount = quality.maxWeatherParticles.coerceAtMost(weatherMotes.size)
            for (i in 0 until moteCount) {
                val m = weatherMotes[i]
                val wx = camWorldX + m.worldOffsetX
                val wy = camWorldY + m.worldOffsetY
                val screenPt = worldToScreen(wx, wy, m.worldZ)
                val pulseAlpha = (0.35f + 0.35f * sin(state.worldTime * 3f + m.phase)).coerceIn(0.15f, 0.85f)
                drawCircle(
                    color = level.biome.accentColor.copy(alpha = pulseAlpha),
                    radius = (tileHalfW * 0.08f * m.sizeFactor).coerceAtLeast(2f),
                    center = screenPt
                )
            }
        }

        // PASS 6: Real-Time Dynamic Player Lighting Halo
        if (quality.enableDynamicPointLights) {
            val playerLightScreen = worldToScreen(state.playerX, state.playerY, 0.5f + state.playerZ)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        skin.glowColor.copy(alpha = 0.16f),
                        level.biome.ambientLight.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = playerLightScreen,
                    radius = tileHalfW * 4.6f
                ),
                radius = tileHalfW * 4.6f,
                center = playerLightScreen
            )
        }

        // PASS 7: Floating Combat & Loot Popups
        for (popup in state.floatingPopups) {
            val pos = worldToScreen(popup.worldX, popup.worldY, 0.9f + popup.age * 1.15f)
            val alpha = (1f - popup.age / 1.1f).coerceIn(0f, 1f)
            val measured = textMeasurer.measure(
                text = popup.text,
                style = TextStyle(
                    color = popup.color.copy(alpha = alpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f)
            )
        }

        // PASS 8: Real-Time Level Transition Iris/Atmosphere Fade
        if (state.levelTransitionProgress > 0.01f) {
            drawRect(
                color = Color(0xFF060911).copy(alpha = state.levelTransitionProgress.coerceIn(0f, 1f))
            )
        }
    }
}

private fun DrawScope.drawIsometricTileBlock(
    tile: IsoTile,
    biome: BiomeType,
    quality: GraphicsQuality,
    materialSet: ProceduralMaterialAtlas.BiomeMaterialSet,
    worldTime: Float,
    portalUnlocked: Boolean,
    isHintTile: Boolean,
    hasCastShadow: Boolean,
    aoCreaseCount: Int,
    tileHalfW: Float,
    tileHalfH: Float,
    topPath: Path,
    leftPath: Path,
    rightPath: Path,
    worldToScreen: (Float, Float, Float) -> Offset
) {
    val gx = tile.gx.toFloat()
    val gy = tile.gy.toFloat()

    val waveOffset = if (tile.type == TileType.LIQUID_HAZARD) {
        sin(worldTime * 3.8f + gx * 0.9f + gy * 0.9f) * 0.045f
    } else 0f

    val topZ = tile.height + waveOffset
    val baseZ = -0.18f

    val pNorth = worldToScreen(gx, gy, topZ)
    val pEast = worldToScreen(gx + 1f, gy, topZ)
    val pSouth = worldToScreen(gx + 1f, gy + 1f, topZ)
    val pWest = worldToScreen(gx, gy + 1f, topZ)

    val bEast = worldToScreen(gx + 1f, gy, baseZ)
    val bSouth = worldToScreen(gx + 1f, gy + 1f, baseZ)
    val bWest = worldToScreen(gx, gy + 1f, baseZ)

    val isWallLike = tile.type == TileType.ELEVATED_WALL ||
        tile.type == TileType.PILLAR_STRUCTURE ||
        tile.type == TileType.CRACKED_SECRET_WALL ||
        tile.type == TileType.LOCKED_DOOR

    val topColor: Color
    val leftColor: Color
    val rightColor: Color

    when (tile.type) {
        TileType.LIQUID_HAZARD -> {
            topColor = biome.liquidColor
            leftColor = biome.liquidColor.copy(alpha = 0.8f)
            rightColor = biome.liquidColor.copy(alpha = 0.65f)
        }
        TileType.ICE_FLOOR -> {
            topColor = Color(0xFF7DD3FC)
            leftColor = Color(0xFF38BDF8)
            rightColor = Color(0xFF0284C7)
        }
        TileType.SECRET_FLOOR -> {
            topColor = Color(0xFF6D28D9)
            leftColor = Color(0xFF5B21B6)
            rightColor = Color(0xFF4C1D95)
        }
        TileType.LOCKED_DOOR -> {
            topColor = Color(0xFFD97706)
            leftColor = Color(0xFFB45309)
            rightColor = Color(0xFF78350F)
        }
        TileType.UNLOCKED_DOOR -> {
            topColor = Color(0xFF059669)
            leftColor = Color(0xFF047857)
            rightColor = Color(0xFF065F46)
        }
        TileType.CRACKED_SECRET_WALL -> {
            topColor = biome.wallTop.copy(red = (biome.wallTop.red + 0.1f).coerceAtMost(1f))
            leftColor = biome.wallLeft
            rightColor = biome.wallRight
        }
        else -> {
            if (isWallLike) {
                topColor = biome.wallTop
                leftColor = biome.wallLeft
                rightColor = biome.wallRight
            } else {
                val checker = ((tile.gx + tile.gy) % 2 == 0)
                topColor = if (checker) biome.floorTop else biome.floorTop.copy(
                    red = (biome.floorTop.red * 0.92f),
                    green = (biome.floorTop.green * 0.92f),
                    blue = (biome.floorTop.blue * 0.92f)
                )
                leftColor = biome.floorLeft
                rightColor = biome.floorRight
            }
        }
    }

    // 1. Left Extruded Face
    leftPath.reset()
    leftPath.moveTo(pWest.x, pWest.y)
    leftPath.lineTo(pSouth.x, pSouth.y)
    leftPath.lineTo(bSouth.x, bSouth.y)
    leftPath.lineTo(bWest.x, bWest.y)
    leftPath.close()
    drawPath(leftPath, color = leftColor)

    // 2. Right Extruded Face
    rightPath.reset()
    rightPath.moveTo(pSouth.x, pSouth.y)
    rightPath.lineTo(pEast.x, pEast.y)
    rightPath.lineTo(bEast.x, bEast.y)
    rightPath.lineTo(bSouth.x, bSouth.y)
    rightPath.close()
    drawPath(rightPath, color = rightColor)

    // 3. Top Diamond Face
    topPath.reset()
    topPath.moveTo(pNorth.x, pNorth.y)
    topPath.lineTo(pEast.x, pEast.y)
    topPath.lineTo(pSouth.x, pSouth.y)
    topPath.lineTo(pWest.x, pWest.y)
    topPath.close()
    drawPath(topPath, color = topColor)

    val centerTop = Offset((pNorth.x + pSouth.x) * 0.5f, (pNorth.y + pSouth.y) * 0.5f)

    // 4. Procedural 4K-Ready Material Micro-Detail Stamp (ULTRA & HIGH modes)
    if (quality.enableSurfaceDetailPass) {
        val tex: ImageBitmap = when {
            tile.type == TileType.LIQUID_HAZARD -> materialSet.liquidTexture
            isWallLike -> materialSet.wallTexture
            else -> materialSet.floorTexture
        }
        val patchW = (tileHalfW * 0.72f).roundToInt().coerceAtLeast(8)
        val patchH = (tileHalfH * 0.72f).roundToInt().coerceAtLeast(6)
        drawImage(
            image = tex,
            dstOffset = IntOffset(
                (centerTop.x - patchW / 2f).roundToInt(),
                (centerTop.y - patchH / 2f).roundToInt()
            ),
            dstSize = IntSize(patchW, patchH),
            alpha = if (quality == GraphicsQuality.ULTRA) 0.34f else 0.22f,
            filterQuality = if (quality == GraphicsQuality.ULTRA) FilterQuality.High else FilterQuality.Low
        )
    }

    // 5. Cast Shadow & Contact Ambient Occlusion (AO)
    if (hasCastShadow) {
        drawPath(
            path = topPath,
            color = Color.Black.copy(alpha = if (quality.enableAmbientOcclusion) 0.24f else 0.16f)
        )
    }
    if (quality.enableAmbientOcclusion && aoCreaseCount > 0 && tile.type.isWalkable) {
        drawLine(
            color = Color.Black.copy(alpha = 0.28f * aoCreaseCount.coerceAtMost(2)),
            start = pWest,
            end = pNorth,
            strokeWidth = 3.2f
        )
    }

    // 3D Edge Bevel Highlight
    drawPath(
        path = topPath,
        color = Color.White.copy(alpha = if (isWallLike) 0.18f else 0.09f),
        style = Stroke(width = 1.2f)
    )

    if (isHintTile) {
        drawCircle(
            color = Color(0xFF22D3EE).copy(alpha = 0.60f),
            radius = tileHalfW * 0.26f,
            center = centerTop
        )
    }

    // 6. Special 2.5D Structures, Doors, Traps & Environmental Effects
    when (tile.type) {
        TileType.LOCKED_DOOR -> {
            drawCircle(
                color = Color(0xFFFFFBEB),
                radius = tileHalfW * 0.24f,
                center = centerTop
            )
            drawCircle(
                color = Color(0xFF0E131F),
                radius = tileHalfW * 0.10f,
                center = centerTop
            )
        }

        TileType.UNLOCKED_DOOR -> {
            drawCircle(
                color = Color(0xFF34D399).copy(alpha = 0.45f),
                radius = tileHalfW * 0.35f,
                center = centerTop,
                style = Stroke(width = 2.5f)
            )
        }

        TileType.TORCH_BRAZIER -> {
            val flicker = 0.85f + 0.15f * sin(worldTime * 11f + gx)
            if (quality.enableDynamicPointLights) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFB800).copy(alpha = 0.48f * flicker),
                            Color(0xFFF97316).copy(alpha = 0.18f * flicker),
                            Color.Transparent
                        ),
                        center = centerTop,
                        radius = tileHalfW * 2.4f
                    ),
                    radius = tileHalfW * 2.4f,
                    center = centerTop
                )
            }
            drawCircle(
                color = Color(0xFFF97316),
                radius = tileHalfW * 0.28f * flicker,
                center = Offset(centerTop.x, centerTop.y - tileHalfH * 0.5f)
            )
            drawCircle(
                color = Color(0xFFFDE047),
                radius = tileHalfW * 0.15f * flicker,
                center = Offset(centerTop.x, centerTop.y - tileHalfH * 0.65f)
            )
        }

        TileType.PILLAR_STRUCTURE -> {
            drawCircle(
                color = biome.accentColor,
                radius = tileHalfW * 0.24f,
                center = centerTop
            )
        }

        TileType.CRACKED_SECRET_WALL -> {
            drawLine(
                color = Color(0xFFC084FC),
                start = Offset(centerTop.x - tileHalfW * 0.3f, centerTop.y - tileHalfH * 0.2f),
                end = Offset(centerTop.x + tileHalfW * 0.3f, bSouth.y - tileHalfH * 0.5f),
                strokeWidth = 2.5f
            )
        }

        TileType.SPIKE_TRAP -> {
            val active = ((worldTime * 1.4f).toInt() % 2 == 0)
            val spikeOffsets = arrayOf(
                Offset(-tileHalfW * 0.25f, 0f),
                Offset(tileHalfW * 0.25f, 0f),
                Offset(0f, -tileHalfH * 0.25f),
                Offset(0f, tileHalfH * 0.25f)
            )
            for (off in spikeOffsets) {
                val base = centerTop + off
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = tileHalfW * 0.11f,
                    center = base
                )
                if (active) {
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = base,
                        end = Offset(base.x, base.y - tileHalfH * 0.95f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        TileType.FIRE_JET_TRAP -> {
            val active = ((worldTime * 1.1f + 0.5f).toInt() % 2 == 0)
            drawCircle(
                color = Color(0xFF7C2D12),
                radius = tileHalfW * 0.26f,
                center = centerTop
            )
            if (active) {
                val jetH = tileHalfH * (1.5f + 0.3f * sin(worldTime * 15f))
                drawLine(
                    color = Color(0xFFEF4444),
                    start = centerTop,
                    end = Offset(centerTop.x, centerTop.y - jetH),
                    strokeWidth = tileHalfW * 0.34f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFDE047),
                    start = centerTop,
                    end = Offset(centerTop.x, centerTop.y - jetH * 0.7f),
                    strokeWidth = tileHalfW * 0.18f,
                    cap = StrokeCap.Round
                )
            }
        }

        TileType.LIQUID_HAZARD -> {
            val shimmerX = sin(worldTime * 3.2f + gx + gy) * tileHalfW * 0.26f
            drawLine(
                color = biome.liquidGlow.copy(alpha = 0.75f),
                start = Offset(centerTop.x - tileHalfW * 0.26f + shimmerX, centerTop.y),
                end = Offset(centerTop.x + tileHalfW * 0.26f + shimmerX, centerTop.y),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round
            )
        }

        TileType.SWITCH_PEDESTAL -> {
            val orbColor = if (tile.isSwitchActivated) Color(0xFF10B981) else Color(0xFFF59E0B)
            drawCircle(
                color = orbColor.copy(alpha = 0.35f),
                radius = tileHalfW * 0.55f,
                center = Offset(centerTop.x, centerTop.y - tileHalfH * 0.35f)
            )
            drawCircle(
                color = orbColor,
                radius = tileHalfW * 0.28f,
                center = Offset(centerTop.x, centerTop.y - tileHalfH * 0.35f)
            )
        }

        TileType.START_PAD -> {
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                radius = tileHalfW * 0.48f,
                center = centerTop,
                style = Stroke(width = 3f)
            )
        }

        TileType.EXIT_PORTAL -> {
            val portalColor = if (portalUnlocked) Color(0xFF06B6D4) else Color(0xFFEF4444)
            val pulse = 1f + 0.14f * sin(worldTime * 5f)
            drawLine(
                color = Color(0xFFFFB800),
                start = Offset(centerTop.x - tileHalfW * 0.45f, centerTop.y),
                end = Offset(centerTop.x - tileHalfW * 0.45f, centerTop.y - tileHalfH * 2.15f),
                strokeWidth = 5.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFFB800),
                start = Offset(centerTop.x + tileHalfW * 0.45f, centerTop.y),
                end = Offset(centerTop.x + tileHalfW * 0.45f, centerTop.y - tileHalfH * 2.15f),
                strokeWidth = 5.5f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = portalColor.copy(alpha = 0.38f),
                radius = tileHalfW * 0.68f * pulse,
                center = Offset(centerTop.x, centerTop.y - tileHalfH * 1.05f)
            )
            drawCircle(
                color = portalColor,
                radius = tileHalfW * 0.40f * pulse,
                center = Offset(centerTop.x, centerTop.y - tileHalfH * 1.05f)
            )
        }

        else -> {}
    }
}

private fun DrawScope.drawDestructibleProp3D(
    prop: DestructiblePropEntity,
    quality: GraphicsQuality,
    screenPos: Offset,
    tileHalfW: Float,
    worldTime: Float
) {
    val sway = if (prop.type.isSwayingVegetation) {
        sin(worldTime * 3.5f + prop.id) * tileHalfW * 0.14f
    } else 0f

    if (prop.isDestroyed) {
        // Real-time shatter collapse animation
        val p = prop.destroyAnimProgress.coerceIn(0f, 1f)
        val alpha = (1f - p).coerceIn(0f, 1f)
        for (i in 0..3) {
            val angle = i * 1.57f + 0.4f
            val dist = p * tileHalfW * 0.7f
            drawCircle(
                color = prop.type.primaryColor.copy(alpha = alpha),
                radius = tileHalfW * 0.14f * (1f - p * 0.5f),
                center = Offset(
                    screenPos.x + cos(angle) * dist,
                    screenPos.y - tileHalfW * 0.2f + sin(angle) * dist * 0.5f
                )
            )
        }
        return
    }

    // Soft Ground Shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(screenPos.x - tileHalfW * 0.30f, screenPos.y - tileHalfW * 0.14f),
        size = Size(tileHalfW * 0.60f, tileHalfW * 0.28f)
    )

    val mainColor = if (prop.hitFlashTimer > 0f) Color.White else prop.type.primaryColor
    val propCenter = Offset(screenPos.x + sway * 0.5f, screenPos.y - tileHalfW * 0.42f)

    if (prop.type.isGlowingCrystal) {
        if (quality.enableDynamicPointLights) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        prop.type.glowColor.copy(alpha = 0.36f),
                        Color.Transparent
                    ),
                    center = propCenter,
                    radius = tileHalfW * 1.4f
                ),
                radius = tileHalfW * 1.4f,
                center = propCenter
            )
        }
        // Faceted 2.5D Crystal Spire
        drawLine(
            color = mainColor,
            start = screenPos,
            end = Offset(propCenter.x, propCenter.y - tileHalfW * 0.35f),
            strokeWidth = tileHalfW * 0.36f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = prop.type.glowColor,
            radius = tileHalfW * 0.14f,
            center = propCenter
        )
    } else if (prop.type.isSwayingVegetation) {
        // Swaying Jungle Totem & Vine Fronds
        drawLine(
            color = prop.type.secondaryColor,
            start = screenPos,
            end = propCenter,
            strokeWidth = tileHalfW * 0.32f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = mainColor,
            radius = tileHalfW * 0.30f,
            center = Offset(propCenter.x + sway, propCenter.y - tileHalfW * 0.12f)
        )
    } else {
        // 3D Crate / Sandstone Relic Urn
        val boxW = tileHalfW * 0.52f
        val boxH = tileHalfW * 0.48f
        drawRect(
            color = mainColor,
            topLeft = Offset(propCenter.x - boxW / 2f, propCenter.y - boxH / 2f),
            size = Size(boxW, boxH)
        )
        drawRect(
            color = prop.type.secondaryColor,
            topLeft = Offset(propCenter.x - boxW / 2f, propCenter.y - boxH / 2f),
            size = Size(boxW, boxH),
            style = Stroke(width = 2.5f)
        )
    }
}

private fun DrawScope.drawReward3D(
    reward: RewardItemEntity,
    quality: GraphicsQuality,
    screenPos: Offset,
    tileHalfW: Float,
    worldTime: Float
) {
    val bob = if (reward.isCollected) 0f else sin(worldTime * 4f + reward.id) * tileHalfW * 0.14f
    val itemCenter = Offset(screenPos.x, screenPos.y - tileHalfW * 0.38f + bob)

    if (quality.enableSoftShadows) {
        drawOval(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(screenPos.x - tileHalfW * 0.32f, screenPos.y - tileHalfW * 0.16f),
            size = Size(tileHalfW * 0.64f, tileHalfW * 0.32f)
        )
    }
    drawOval(
        color = Color.Black.copy(alpha = 0.42f),
        topLeft = Offset(screenPos.x - tileHalfW * 0.24f, screenPos.y - tileHalfW * 0.12f),
        size = Size(tileHalfW * 0.48f, tileHalfW * 0.24f)
    )

    when (reward.type) {
        RewardType.RELIC_KEY -> {
            drawCircle(
                color = Color(0xFF06B6D4).copy(alpha = 0.42f),
                radius = tileHalfW * 0.50f,
                center = itemCenter
            )
            drawCircle(
                color = Color(0xFFFFB800),
                radius = tileHalfW * 0.25f,
                center = itemCenter,
                style = Stroke(width = 4.2f)
            )
        }
        RewardType.TREASURE_CHEST, RewardType.RARE_ARTEFACT -> {
            val boxW = tileHalfW * 0.58f
            val boxH = tileHalfW * 0.44f
            val openProg = reward.openAnimProgress.coerceIn(0f, 1f)
            // Chest Base
            drawRect(
                color = if (reward.type == RewardType.RARE_ARTEFACT) Color(0xFFA855F7) else Color(0xFFB45309),
                topLeft = Offset(itemCenter.x - boxW / 2f, itemCenter.y - boxH / 2f),
                size = Size(boxW, boxH)
            )
            // Animated Swinging Lid + Rising Gold Light Beam when opened!
            if (openProg > 0f) {
                val beamAlpha = (1f - openProg).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFFFFB800).copy(alpha = beamAlpha * 0.65f),
                    radius = tileHalfW * (0.4f + openProg * 0.6f),
                    center = Offset(itemCenter.x, itemCenter.y - openProg * tileHalfW * 0.5f)
                )
            }
            drawRect(
                color = Color(0xFFFFB800),
                topLeft = Offset(
                    itemCenter.x - boxW / 2f,
                    itemCenter.y - boxH / 2f - openProg * tileHalfW * 0.22f
                ),
                size = Size(boxW, boxH * (1f - openProg * 0.35f)),
                style = Stroke(width = 2.5f)
            )
        }
        RewardType.GEM -> {
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = tileHalfW * 0.23f,
                center = itemCenter
            )
        }
        RewardType.HEALTH_ELIXIR -> {
            drawCircle(
                color = Color(0xFF10B981),
                radius = tileHalfW * 0.23f,
                center = itemCenter
            )
        }
        RewardType.COIN -> {
            drawCircle(
                color = Color(0xFFFFB800),
                radius = tileHalfW * 0.20f,
                center = itemCenter
            )
            drawCircle(
                color = Color(0xFFFFFBEB),
                radius = tileHalfW * 0.08f,
                center = itemCenter
            )
        }
    }
}

private fun DrawScope.drawEnemyCharacter3D(
    enemy: EnemyEntity,
    biome: BiomeType,
    quality: GraphicsQuality,
    screenPos: Offset,
    tileHalfW: Float,
    worldTime: Float
) {
    val deathScale = if (enemy.isDying) (1f - enemy.deathAnimProgress).coerceIn(0.1f, 1f) else 1f
    val scale = (if (enemy.isBoss) 1.34f else 1.0f) * deathScale
    val bob = if (enemy.isDying) 0f else abs(sin(worldTime * 6.5f + enemy.id)) * tileHalfW * 0.11f
    val lungeOffset = if (enemy.attackAnimTimer > 0f) {
        Offset(
            cos(enemy.facingAngle) * tileHalfW * 0.28f,
            sin(enemy.facingAngle) * tileHalfW * 0.16f
        )
    } else Offset.Zero

    if (quality.enableSoftShadows) {
        drawOval(
            color = Color.Black.copy(alpha = 0.25f * deathScale),
            topLeft = Offset(screenPos.x - tileHalfW * 0.45f * scale, screenPos.y - tileHalfW * 0.22f * scale),
            size = Size(tileHalfW * 0.90f * scale, tileHalfW * 0.44f * scale)
        )
    }
    drawOval(
        color = Color.Black.copy(alpha = 0.5f * deathScale),
        topLeft = Offset(screenPos.x - tileHalfW * 0.36f * scale, screenPos.y - tileHalfW * 0.18f * scale),
        size = Size(tileHalfW * 0.72f * scale, tileHalfW * 0.36f * scale)
    )

    val bodyColor = if (enemy.hurtFlashTimer > 0f || enemy.isDying) {
        Color.White
    } else if (enemy.isBoss) {
        Color(0xFFDC2626)
    } else if (enemy.isAlerted) {
        Color(0xFFF43F5E)
    } else {
        Color(0xFFE11D48)
    }

    val torsoCenter = Offset(
        screenPos.x + lungeOffset.x,
        screenPos.y - tileHalfW * 0.48f * scale - bob + lungeOffset.y
    )
    drawCircle(
        color = bodyColor.copy(alpha = deathScale),
        radius = tileHalfW * 0.34f * scale,
        center = torsoCenter
    )
    val headCenter = Offset(torsoCenter.x, torsoCenter.y - tileHalfW * 0.32f * scale)
    drawCircle(
        color = biome.wallTop.copy(alpha = deathScale),
        radius = tileHalfW * 0.24f * scale,
        center = headCenter
    )
    drawCircle(
        color = Color(0xFFFDE047).copy(alpha = deathScale),
        radius = tileHalfW * 0.07f * scale,
        center = Offset(headCenter.x - 4f, headCenter.y)
    )
    drawCircle(
        color = Color(0xFFFDE047).copy(alpha = deathScale),
        radius = tileHalfW * 0.07f * scale,
        center = Offset(headCenter.x + 4f, headCenter.y)
    )

    if (enemy.isAlerted && !enemy.isDying) {
        val alertCenter = Offset(headCenter.x, headCenter.y - tileHalfW * 0.58f * scale)
        drawCircle(
            color = Color(0xFFFACC15),
            radius = tileHalfW * 0.12f,
            center = alertCenter
        )
    }

    if (!enemy.isDying) {
        val barW = tileHalfW * 0.85f * scale
        val barH = 5f
        val barTop = Offset(headCenter.x - barW / 2f, headCenter.y - tileHalfW * 0.38f * scale)
        drawRect(
            color = Color.Black.copy(alpha = 0.7f),
            topLeft = barTop,
            size = Size(barW, barH)
        )
        val hpRatio = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
        drawRect(
            color = Color(0xFFEF4444),
            topLeft = barTop,
            size = Size(barW * hpRatio, barH)
        )
    }
}

private fun DrawScope.drawPlayerCharacter3D(
    state: GameEngineState,
    hero: HeroCharacter,
    skin: CharacterSkin,
    quality: GraphicsQuality,
    groundPos: Offset,
    elevatedPos: Offset,
    tileHalfW: Float
) {
    // 1. Multi-Pass Soft Ground Shadow & Directional Facing Ring
    val shadowScale = (1f - state.playerZ * 0.35f).coerceIn(0.45f, 1f)
    if (quality.enableSoftShadows) {
        drawOval(
            color = Color.Black.copy(alpha = 0.26f * shadowScale),
            topLeft = Offset(
                groundPos.x - tileHalfW * 0.48f * shadowScale,
                groundPos.y - tileHalfW * 0.24f * shadowScale
            ),
            size = Size(tileHalfW * 0.96f * shadowScale, tileHalfW * 0.48f * shadowScale)
        )
    }
    drawOval(
        color = Color.Black.copy(alpha = 0.55f * shadowScale),
        topLeft = Offset(
            groundPos.x - tileHalfW * 0.38f * shadowScale,
            groundPos.y - tileHalfW * 0.19f * shadowScale
        ),
        size = Size(tileHalfW * 0.76f * shadowScale, tileHalfW * 0.38f * shadowScale)
    )

    val dirScreenAngle = state.playerFacingAngle - 0.785398f
    drawCircle(
        color = skin.glowColor.copy(alpha = 0.78f),
        radius = tileHalfW * 0.08f,
        center = Offset(
            groundPos.x + cos(dirScreenAngle) * tileHalfW * 0.42f,
            groundPos.y + sin(dirScreenAngle) * tileHalfW * 0.22f
        )
    )

    // 2. Attack Shockwave Arc if attacking
    if (state.attackAnimTimer > 0f) {
        val progress = 1f - (state.attackAnimTimer / 0.30f)
        drawCircle(
            color = skin.glowColor.copy(alpha = (1f - progress) * 0.75f),
            radius = tileHalfW * (0.85f + progress * 1.75f),
            center = elevatedPos,
            style = Stroke(width = 6.5f)
        )
    }

    // 3. State-Driven Animation Offsets (Walk, Run, Jump Land Squash, Hit React, Death)
    val strideAmplitude = when (state.playerAnimState) {
        CharacterAnimState.RUN, CharacterAnimState.DASH -> 0.17f
        CharacterAnimState.WALK -> 0.12f
        else -> 0.02f
    }
    val stride = sin(state.animCycle) * tileHalfW * strideAmplitude
    val bob = abs(cos(state.animCycle)) * tileHalfW * (if (state.playerAnimState == CharacterAnimState.IDLE) 0.03f else 0.07f)
    val squashY = if (state.playerAnimState == CharacterAnimState.JUMP_LAND) tileHalfW * 0.10f else 0f
    val deathDrop = if (state.playerAnimState == CharacterAnimState.DEATH) state.deathAnimProgress * tileHalfW * 0.35f else 0f

    // 4. Animated Striding Boots
    drawCircle(
        color = Color(0xFF1E293B),
        radius = tileHalfW * 0.13f,
        center = Offset(elevatedPos.x - tileHalfW * 0.14f + stride, elevatedPos.y - tileHalfW * 0.10f)
    )
    drawCircle(
        color = Color(0xFF1E293B),
        radius = tileHalfW * 0.13f,
        center = Offset(elevatedPos.x + tileHalfW * 0.14f - stride, elevatedPos.y - tileHalfW * 0.10f)
    )

    // 5. Flowing Hero Cape
    val capeCenter = Offset(
        elevatedPos.x - cos(dirScreenAngle) * tileHalfW * 0.18f,
        elevatedPos.y - tileHalfW * 0.42f - bob + squashY + deathDrop
    )
    drawCircle(
        color = hero.capeColor,
        radius = tileHalfW * 0.34f,
        center = capeCenter
    )

    // 6. 3D Armored Torso
    val torsoCenter = Offset(elevatedPos.x, elevatedPos.y - tileHalfW * 0.48f - bob + squashY + deathDrop)
    val bodyColor = when {
        state.playerAnimState == CharacterAnimState.HIT_REACT -> Color(0xFFEF4444)
        state.invulnerableTimer > 0f && ((state.worldTime * 16f).toInt() % 2 == 0) -> Color.White
        else -> skin.armorColor
    }
    drawCircle(
        color = bodyColor,
        radius = tileHalfW * 0.33f,
        center = torsoCenter
    )
    drawCircle(
        color = skin.trimColor,
        radius = tileHalfW * 0.33f,
        center = torsoCenter,
        style = Stroke(width = 2.5f)
    )

    // 7. Articulated Weapon Blade / Arcane Staff in Hand
    val swingAngle = if (state.attackAnimTimer > 0f) {
        dirScreenAngle + (1f - state.attackAnimTimer / 0.30f) * 3.14f
    } else {
        dirScreenAngle + 0.5f
    }
    val handStart = Offset(torsoCenter.x + cos(swingAngle) * tileHalfW * 0.24f, torsoCenter.y + sin(swingAngle) * tileHalfW * 0.15f)
    val bladeEnd = Offset(handStart.x + cos(swingAngle) * tileHalfW * 0.50f, handStart.y + sin(swingAngle) * tileHalfW * 0.32f - tileHalfW * 0.15f)
    drawLine(
        color = skin.glowColor,
        start = handStart,
        end = bladeEnd,
        strokeWidth = 4.4f,
        cap = StrokeCap.Round
    )

    // 8. Hero Head & Plumed Crest
    val headCenter = Offset(torsoCenter.x, torsoCenter.y - tileHalfW * 0.34f)
    drawCircle(
        color = Color(0xFFFDE68A),
        radius = tileHalfW * 0.23f,
        center = headCenter
    )
    drawCircle(
        color = skin.trimColor,
        radius = tileHalfW * 0.14f,
        center = Offset(headCenter.x, headCenter.y - tileHalfW * 0.16f)
    )
}
