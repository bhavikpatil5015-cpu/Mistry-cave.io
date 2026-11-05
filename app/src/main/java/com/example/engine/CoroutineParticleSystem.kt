package com.example.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.model.BiomeType
import com.example.model.DestructiblePropEntity
import com.example.model.GeneratedLevel
import com.example.model.GraphicsQuality
import com.example.model.RewardItemEntity
import com.example.model.RewardType
import com.example.model.TileType
import com.example.model.WeatherEffectType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * User-selectable or biome-automatic real-time environmental particle mode.
 */
enum class ParticleEffectPreset(val label: String, val badgeColor: Color) {
    BIOME_AUTO("FX: Biome Auto", Color(0xFF38BDF8)),
    FALLING_SNOW("FX: Glacial Snow", Color(0xFFE0F2FE)),
    RISING_LAVA_SPARKS("FX: Lava Sparks", Color(0xFFF97316)),
    MAGICAL_GLOW("FX: Arcane Glow", Color(0xFFC084FC)),
    ELEMENTAL_CONVERGENCE("FX: Elemental Storm", Color(0xFF10B981));

    fun next(): ParticleEffectPreset = when (this) {
        BIOME_AUTO -> FALLING_SNOW
        FALLING_SNOW -> RISING_LAVA_SPARKS
        RISING_LAVA_SPARKS -> MAGICAL_GLOW
        MAGICAL_GLOW -> ELEMENTAL_CONVERGENCE
        ELEMENTAL_CONVERGENCE -> BIOME_AUTO
    }
}

/**
 * Particle archetype identifier stored in the Structure-of-Arrays (SoA) buffer.
 */
object ParticleArchetype {
    const val INACTIVE = 0
    const val SNOWFLAKE = 1
    const val LAVA_SPARK = 2
    const val MAGIC_GLOW = 3
    const val COMBAT_BURST = 4
}

/**
 * High-performance, zero-allocation Structure-of-Arrays (SoA) particle system driven by Kotlin
 * coroutines and rendered directly onto Jetpack Compose Canvas.
 *
 * Pre-allocates contiguous primitive FloatArray / IntArray buffers for up to [MAX_CAPACITY]
 * simultaneous 2.5D particles to eliminate GC pauses and maximize CPU cache locality.
 */
class CoroutineParticleSystem(
    val maxCapacity: Int = MAX_CAPACITY
) {
    companion object {
        const val MAX_CAPACITY = 512
    }

    // Structure-of-Arrays (SoA) contiguous primitive buffers
    val type = IntArray(maxCapacity)
    val worldX = FloatArray(maxCapacity)
    val worldY = FloatArray(maxCapacity)
    val worldZ = FloatArray(maxCapacity)
    val prevWorldX = FloatArray(maxCapacity)
    val prevWorldY = FloatArray(maxCapacity)
    val prevWorldZ = FloatArray(maxCapacity)

    val vx = FloatArray(maxCapacity)
    val vy = FloatArray(maxCapacity)
    val vz = FloatArray(maxCapacity)

    val age = FloatArray(maxCapacity)
    val maxLife = FloatArray(maxCapacity)
    val size = FloatArray(maxCapacity)
    val phase = FloatArray(maxCapacity)
    val spinSpeed = FloatArray(maxCapacity)
    val primaryColorArgb = IntArray(maxCapacity)
    val secondaryColorArgb = IntArray(maxCapacity)
    val groundRippleTimer = FloatArray(maxCapacity)

    // Volatile simulation target state updated from the main game loop
    @Volatile
    var cameraWorldX: Float = 5f

    @Volatile
    var cameraWorldY: Float = 5f

    @Volatile
    var playerWorldX: Float = 5f

    @Volatile
    var playerWorldY: Float = 5f

    @Volatile
    var playerWorldZ: Float = 0f

    @Volatile
    var playerVelocityX: Float = 0f

    @Volatile
    var playerVelocityY: Float = 0f

    @Volatile
    var isPlayerDashingOrAttacking: Boolean = false

    @Volatile
    var activePreset: ParticleEffectPreset = ParticleEffectPreset.BIOME_AUTO

    @Volatile
    var activeQuality: GraphicsQuality = GraphicsQuality.ULTRA

    @Volatile
    var activeBiome: BiomeType = BiomeType.ANCIENT_JUNGLE_RUINS

    // Cached emitter anchors extracted from the current level (lava tiles, torches, keys, portal, crystals)
    private val thermalEmitterPoints = FloatArray(64 * 2)
    private var thermalEmitterCount = 0

    private val arcaneEmitterPoints = FloatArray(64 * 2)
    private var arcaneEmitterCount = 0

    private var windClock: Float = 0f
    private var spawnCursor: Int = 0
    private var randomSeed: Int = 0x5A17C9E3
    private val activeParticleCounter = AtomicInteger(0)
    private var simulationJob: Job? = null

    // Reusable Path for drawing diamond/crystalline cores on Canvas without per-frame allocations
    private val starPath = Path()

    val activeCount: Int
        get() = activeParticleCounter.get()

    /**
     * Starts a background coroutine loop on [dispatcher] that continuously updates particle
     * kinematics, wind turbulence, thermal buoyancy, and orbital attraction at ~60 Hz.
     */
    fun startCoroutineSimulation(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): Job {
        simulationJob?.cancel()
        val job = scope.launch(dispatcher) {
            var lastTimeNs = System.nanoTime()
            while (isActive) {
                val nowNs = System.nanoTime()
                val dt = ((nowNs - lastTimeNs) / 1_000_000_000f).coerceIn(0.004f, 0.050f)
                lastTimeNs = nowNs
                stepSimulation(dt)
                delay(16L)
            }
        }
        simulationJob = job
        return job
    }

    fun stopCoroutineSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    /**
     * Synchronizes level emitters (lava/fire/torch coordinates and arcane relic/portal/crystal coordinates)
     * whenever a new procedural level is generated or modified.
     */
    fun bindLevelEmitters(
        level: GeneratedLevel,
        rewards: List<RewardItemEntity>,
        props: List<DestructiblePropEntity>
    ) {
        activeBiome = level.biome
        var tCount = 0
        var aCount = 0

        for (gx in 0 until level.width) {
            for (gy in 0 until level.height) {
                val tile = level.tiles[gx][gy]
                when (tile.type) {
                    TileType.LIQUID_HAZARD, TileType.FIRE_JET_TRAP, TileType.TORCH_BRAZIER -> {
                        if (tCount < 64) {
                            thermalEmitterPoints[tCount * 2] = gx + 0.5f
                            thermalEmitterPoints[tCount * 2 + 1] = gy + 0.5f
                            tCount++
                        }
                    }
                    TileType.EXIT_PORTAL, TileType.SWITCH_PEDESTAL, TileType.START_PAD -> {
                        if (aCount < 64) {
                            arcaneEmitterPoints[aCount * 2] = gx + 0.5f
                            arcaneEmitterPoints[aCount * 2 + 1] = gy + 0.5f
                            aCount++
                        }
                    }
                    else -> Unit
                }
            }
        }

        for (reward in rewards) {
            if (!reward.isCollected && aCount < 64) {
                arcaneEmitterPoints[aCount * 2] = reward.gx + 0.5f
                arcaneEmitterPoints[aCount * 2 + 1] = reward.gy + 0.5f
                aCount++
            }
        }

        for (prop in props) {
            if (!prop.isDestroyed && prop.type.isGlowingCrystal && aCount < 64) {
                arcaneEmitterPoints[aCount * 2] = prop.gx + 0.5f
                arcaneEmitterPoints[aCount * 2 + 1] = prop.gy + 0.5f
                aCount++
            }
        }

        thermalEmitterCount = tCount
        arcaneEmitterCount = aCount
    }

    /**
     * Updates live player and camera coordinates from the game engine.
     */
    fun syncFrameContext(
        camX: Float,
        camY: Float,
        px: Float,
        py: Float,
        pz: Float,
        pvx: Float,
        pvy: Float,
        dashingOrAttacking: Boolean,
        quality: GraphicsQuality,
        preset: ParticleEffectPreset,
        biome: BiomeType
    ) {
        cameraWorldX = camX
        cameraWorldY = camY
        playerWorldX = px
        playerWorldY = py
        playerWorldZ = pz
        playerVelocityX = pvx
        playerVelocityY = pvy
        isPlayerDashingOrAttacking = dashingOrAttacking
        activeQuality = quality
        activePreset = preset
        activeBiome = biome
    }

    /**
     * Advances all active particles and spawns continuous environmental particles (falling snow,
     * rising lava sparks, and magical glowing particles) up to the current quality budget.
     * Safe to call from either the background coroutine or directly in deterministic unit tests.
     */
    @Synchronized
    fun stepSimulation(dt: Float) {
        val clampedDt = dt.coerceIn(0.001f, 0.1f)
        windClock += clampedDt

        val targetBudget = when (activeQuality) {
            GraphicsQuality.ULTRA -> 240
            GraphicsQuality.HIGH -> 140
            GraphicsQuality.PERFORMANCE -> 64
        }.coerceAtMost(maxCapacity)

        // 1. Determine desired environmental distribution based on preset or biome
        val (wantSnow, wantLava, wantMagic) = resolveActiveArchetypes(activePreset, activeBiome)

        // 2. Count and update existing particles in-place
        var alive = 0
        var envAlive = 0
        val camX = cameraWorldX
        val camY = cameraWorldY
        val px = playerWorldX
        val py = playerWorldY
        val windGustX = sin(windClock * 0.85f) * 0.65f + cos(windClock * 1.9f) * 0.25f
        val windGustY = cos(windClock * 0.65f) * 0.35f

        for (i in 0 until maxCapacity) {
            val t = type[i]
            if (t == ParticleArchetype.INACTIVE) continue

            val nextAge = age[i] + clampedDt
            val lifeLimit = maxLife[i]
            if (nextAge >= lifeLimit) {
                type[i] = ParticleArchetype.INACTIVE
                continue
            }
            age[i] = nextAge
            prevWorldX[i] = worldX[i]
            prevWorldY[i] = worldY[i]
            prevWorldZ[i] = worldZ[i]

            val pPhase = phase[i] + spinSpeed[i] * clampedDt
            phase[i] = pPhase

            when (t) {
                ParticleArchetype.SNOWFLAKE -> {
                    envAlive++
                    // If snowflake has touched ground, animate frost melt ring before despawning
                    if (groundRippleTimer[i] > 0f) {
                        groundRippleTimer[i] += clampedDt
                        if (groundRippleTimer[i] > 0.45f) {
                            type[i] = ParticleArchetype.INACTIVE
                            continue
                        }
                    } else {
                        // Sinusoidal flutter + wind gust + player dash wake turbulence
                        val flutterX = sin(pPhase * 2.4f) * 0.55f + windGustX
                        val flutterY = cos(pPhase * 1.8f) * 0.40f + windGustY
                        var curVx = vx[i] * 0.94f + flutterX * 0.06f
                        var curVy = vy[i] * 0.94f + flutterY * 0.06f

                        val distToPlayer = hypot(worldX[i] - px, worldY[i] - py)
                        if (distToPlayer < 1.8f && (isPlayerDashingOrAttacking || hypot(playerVelocityX, playerVelocityY) > 1.5f)) {
                            val pushFactor = (1.8f - distToPlayer) * 2.2f
                            curVx += ((worldX[i] - px) / distToPlayer.coerceAtLeast(0.15f)) * pushFactor * clampedDt
                            curVy += ((worldY[i] - py) / distToPlayer.coerceAtLeast(0.15f)) * pushFactor * clampedDt
                        }

                        vx[i] = curVx
                        vy[i] = curVy
                        worldX[i] += curVx * clampedDt
                        worldY[i] += curVy * clampedDt
                        worldZ[i] += vz[i] * clampedDt

                        // Wrap around camera frustum so snow always blankets the visible viewport
                        wrapAroundCamera(i, camX, camY, span = 9.5f)

                        if (worldZ[i] <= 0.25f) {
                            worldZ[i] = 0.25f
                            groundRippleTimer[i] = 0.01f
                        }
                    }
                }

                ParticleArchetype.LAVA_SPARK -> {
                    envAlive++
                    // Buoyant thermal updraft + curl-noise helical swirl
                    val curlX = -sin(pPhase * 3.8f + worldZ[i] * 2.5f) * 1.15f
                    val curlY = cos(pPhase * 3.4f + worldZ[i] * 2.5f) * 1.15f
                    vx[i] = vx[i] * 0.92f + (curlX + windGustX * 0.4f) * 0.08f
                    vy[i] = vy[i] * 0.92f + (curlY + windGustY * 0.4f) * 0.08f
                    vz[i] = (vz[i] + 0.55f * clampedDt).coerceAtMost(2.8f)

                    worldX[i] += vx[i] * clampedDt
                    worldY[i] += vy[i] * clampedDt
                    worldZ[i] += vz[i] * clampedDt

                    wrapAroundCamera(i, camX, camY, span = 9.5f)
                }

                ParticleArchetype.MAGIC_GLOW -> {
                    envAlive++
                    // Harmonic orbital drift + gentle attraction toward player when dashing/attacking
                    val orbitX = cos(pPhase * 1.7f) * 0.75f
                    val orbitY = sin(pPhase * 1.7f) * 0.75f
                    val bobZ = sin(pPhase * 2.3f) * 0.35f

                    var steerX = 0f
                    var steerY = 0f
                    val dxP = px - worldX[i]
                    val dyP = py - worldY[i]
                    val distP = hypot(dxP, dyP)
                    if (distP in 0.35f..4.2f) {
                        val pull = if (isPlayerDashingOrAttacking) 1.65f else 0.42f
                        // Tangential swirl around the hero
                        steerX = (dxP / distP) * pull * 0.4f - (dyP / distP) * pull
                        steerY = (dyP / distP) * pull * 0.4f + (dxP / distP) * pull
                    }

                    vx[i] = vx[i] * 0.90f + (orbitX + steerX) * 0.10f
                    vy[i] = vy[i] * 0.90f + (orbitY + steerY) * 0.10f
                    vz[i] = bobZ

                    worldX[i] += vx[i] * clampedDt
                    worldY[i] += vy[i] * clampedDt
                    worldZ[i] = (worldZ[i] + vz[i] * clampedDt).coerceIn(0.32f, 2.4f)

                    wrapAroundCamera(i, camX, camY, span = 9.5f)
                }

                ParticleArchetype.COMBAT_BURST -> {
                    // Ballistic arc with gravity and floor bounce
                    vz[i] -= 9.8f * clampedDt
                    worldX[i] += vx[i] * clampedDt
                    worldY[i] += vy[i] * clampedDt
                    worldZ[i] += vz[i] * clampedDt
                    if (worldZ[i] < 0.25f) {
                        worldZ[i] = 0.25f
                        vz[i] = abs(vz[i]) * 0.35f
                        vx[i] *= 0.72f
                        vy[i] *= 0.72f
                    }
                }
            }
            alive++
        }

        // 3. Spawn new environmental particles to maintain steady real-time density
        val deficit = (targetBudget - envAlive).coerceAtLeast(0)
        val maxSpawnThisStep = min(deficit, 18)
        for (s in 0 until maxSpawnThisStep) {
            val chosenArchetype = pickArchetypeToSpawn(s, wantSnow, wantLava, wantMagic)
            spawnEnvironmentalParticle(chosenArchetype, camX, camY)
            alive++
        }

        activeParticleCounter.set(alive)
    }

    /**
     * Emits an immediate burst of particles at a world coordinate (combat hits, chest opening,
     * relic key pickup, dash trails, or prop shattering).
     */
    @Synchronized
    fun emitBurst(
        wx: Float,
        wy: Float,
        wz: Float,
        primaryColor: Color,
        secondaryColor: Color = Color.White,
        count: Int = 14,
        archetype: Int = ParticleArchetype.COMBAT_BURST
    ) {
        val pArgb = colorToFastArgb(primaryColor)
        val sArgb = colorToFastArgb(secondaryColor)
        val safeCount = count.coerceIn(1, 48)

        for (n in 0 until safeCount) {
            val idx = allocateSlot()
            val angle = (n.toFloat() / safeCount) * 6.283185f + nextRandomFloat(-0.25f, 0.25f)
            val speed = nextRandomFloat(1.2f, 3.8f)

            type[idx] = archetype
            worldX[idx] = wx
            worldY[idx] = wy
            worldZ[idx] = wz
            prevWorldX[idx] = wx
            prevWorldY[idx] = wy
            prevWorldZ[idx] = wz

            vx[idx] = cos(angle) * speed
            vy[idx] = sin(angle) * speed
            vz[idx] = nextRandomFloat(0.9f, 2.8f)

            age[idx] = 0f
            maxLife[idx] = when (archetype) {
                ParticleArchetype.MAGIC_GLOW -> nextRandomFloat(1.1f, 2.2f)
                ParticleArchetype.LAVA_SPARK -> nextRandomFloat(0.8f, 1.6f)
                else -> nextRandomFloat(0.45f, 0.95f)
            }
            size[idx] = nextRandomFloat(0.85f, 1.65f)
            phase[idx] = nextRandomFloat(0f, 6.28f)
            spinSpeed[idx] = nextRandomFloat(2.0f, 5.5f)
            primaryColorArgb[idx] = pArgb
            secondaryColorArgb[idx] = sArgb
            groundRippleTimer[idx] = 0f
        }
    }

    private fun resolveActiveArchetypes(
        preset: ParticleEffectPreset,
        biome: BiomeType
    ): Triple<Boolean, Boolean, Boolean> = when (preset) {
        ParticleEffectPreset.FALLING_SNOW -> Triple(true, false, false)
        ParticleEffectPreset.RISING_LAVA_SPARKS -> Triple(false, true, false)
        ParticleEffectPreset.MAGICAL_GLOW -> Triple(false, false, true)
        ParticleEffectPreset.ELEMENTAL_CONVERGENCE -> Triple(true, true, true)
        ParticleEffectPreset.BIOME_AUTO -> when (biome.weatherType) {
            WeatherEffectType.BLIZZARD_SNOW -> Triple(true, false, true)
            WeatherEffectType.VOLCANIC_ASH, WeatherEffectType.DUNGEON_EMBERS -> Triple(false, true, true)
            WeatherEffectType.BIOLUMINESCENT_ORBS, WeatherEffectType.SPECTRAL_MIST,
            WeatherEffectType.JUNGLE_SPORES, WeatherEffectType.SOLAR_RAYS -> Triple(false, true, true)
            WeatherEffectType.DESERT_SANDSTORM -> Triple(false, true, true)
        }
    }

    private fun pickArchetypeToSpawn(
        index: Int,
        wantSnow: Boolean,
        wantLava: Boolean,
        wantMagic: Boolean
    ): Int {
        if (wantSnow && !wantLava && !wantMagic) return ParticleArchetype.SNOWFLAKE
        if (!wantSnow && wantLava && !wantMagic) return ParticleArchetype.LAVA_SPARK
        if (!wantSnow && !wantLava && wantMagic) return ParticleArchetype.MAGIC_GLOW

        return when (index % 3) {
            0 -> if (wantSnow) ParticleArchetype.SNOWFLAKE else if (wantLava) ParticleArchetype.LAVA_SPARK else ParticleArchetype.MAGIC_GLOW
            1 -> if (wantLava) ParticleArchetype.LAVA_SPARK else if (wantMagic) ParticleArchetype.MAGIC_GLOW else ParticleArchetype.SNOWFLAKE
            else -> if (wantMagic) ParticleArchetype.MAGIC_GLOW else if (wantSnow) ParticleArchetype.SNOWFLAKE else ParticleArchetype.LAVA_SPARK
        }
    }

    private fun spawnEnvironmentalParticle(archetype: Int, camX: Float, camY: Float) {
        val idx = allocateSlot()
        type[idx] = archetype
        age[idx] = 0f
        groundRippleTimer[idx] = 0f
        phase[idx] = nextRandomFloat(0f, 6.28318f)
        spinSpeed[idx] = nextRandomFloat(1.4f, 4.2f)

        when (archetype) {
            ParticleArchetype.SNOWFLAKE -> {
                val wx = camX + nextRandomFloat(-8.8f, 8.8f)
                val wy = camY + nextRandomFloat(-8.8f, 8.8f)
                val wz = nextRandomFloat(1.35f, 3.10f)
                worldX[idx] = wx
                worldY[idx] = wy
                worldZ[idx] = wz
                prevWorldX[idx] = wx
                prevWorldY[idx] = wy
                prevWorldZ[idx] = wz

                vx[idx] = nextRandomFloat(-0.35f, 0.55f)
                vy[idx] = nextRandomFloat(0.10f, 0.45f)
                vz[idx] = nextRandomFloat(-0.95f, -0.48f)
                maxLife[idx] = nextRandomFloat(3.2f, 6.0f)
                size[idx] = nextRandomFloat(0.65f, 1.75f)
                primaryColorArgb[idx] = 0xFFFFFFFF.toInt()
                secondaryColorArgb[idx] = 0xFF7DD3FC.toInt()
            }

            ParticleArchetype.LAVA_SPARK -> {
                // Anchor 55% of lava sparks to real magma/torch/fire tiles when near camera
                var wx = camX + nextRandomFloat(-8.2f, 8.2f)
                var wy = camY + nextRandomFloat(-8.2f, 8.2f)
                var wz = nextRandomFloat(0.12f, 0.65f)

                if (thermalEmitterCount > 0 && nextRandomFloat(0f, 1f) < 0.55f) {
                    val pick = (abs(nextRandomInt()) % thermalEmitterCount) * 2
                    val ex = thermalEmitterPoints[pick]
                    val ey = thermalEmitterPoints[pick + 1]
                    if (hypot(ex - camX, ey - camY) < 10.5f) {
                        wx = ex + nextRandomFloat(-0.42f, 0.42f)
                        wy = ey + nextRandomFloat(-0.42f, 0.42f)
                        wz = 0.18f
                    }
                }

                worldX[idx] = wx
                worldY[idx] = wy
                worldZ[idx] = wz
                prevWorldX[idx] = wx
                prevWorldY[idx] = wy
                prevWorldZ[idx] = wz

                vx[idx] = nextRandomFloat(-0.55f, 0.55f)
                vy[idx] = nextRandomFloat(-0.55f, 0.55f)
                vz[idx] = nextRandomFloat(0.75f, 1.95f)
                maxLife[idx] = nextRandomFloat(1.4f, 3.1f)
                size[idx] = nextRandomFloat(0.75f, 1.60f)
                primaryColorArgb[idx] = 0xFFFFFBEB.toInt()
                secondaryColorArgb[idx] = 0xFFF97316.toInt()
            }

            ParticleArchetype.MAGIC_GLOW -> {
                // Anchor 60% of magical particles to relic keys, exit portals, or glowing crystals
                var wx = camX + nextRandomFloat(-8.5f, 8.5f)
                var wy = camY + nextRandomFloat(-8.5f, 8.5f)
                var wz = nextRandomFloat(0.35f, 1.75f)

                if (arcaneEmitterCount > 0 && nextRandomFloat(0f, 1f) < 0.60f) {
                    val pick = (abs(nextRandomInt()) % arcaneEmitterCount) * 2
                    val ex = arcaneEmitterPoints[pick]
                    val ey = arcaneEmitterPoints[pick + 1]
                    if (hypot(ex - camX, ey - camY) < 10.5f) {
                        wx = ex + nextRandomFloat(-0.65f, 0.65f)
                        wy = ey + nextRandomFloat(-0.65f, 0.65f)
                        wz = nextRandomFloat(0.35f, 1.35f)
                    }
                }

                worldX[idx] = wx
                worldY[idx] = wy
                worldZ[idx] = wz
                prevWorldX[idx] = wx
                prevWorldY[idx] = wy
                prevWorldZ[idx] = wz

                vx[idx] = nextRandomFloat(-0.40f, 0.40f)
                vy[idx] = nextRandomFloat(-0.40f, 0.40f)
                vz[idx] = nextRandomFloat(-0.20f, 0.35f)
                maxLife[idx] = nextRandomFloat(2.4f, 5.2f)
                size[idx] = nextRandomFloat(0.80f, 1.70f)

                val palette = when (abs(nextRandomInt()) % 4) {
                    0 -> Pair(0xFF67E8F9.toInt(), 0xFF06B6D4.toInt()) // Arcane Cyan
                    1 -> Pair(0xFFE879F9.toInt(), 0xFF9333EA.toInt()) // Eldritch Violet
                    2 -> Pair(0xFF6EE7B7.toInt(), 0xFF10B981.toInt()) // Bioluminescent Emerald
                    else -> Pair(0xFFFDE047.toInt(), 0xFFF59E0B.toInt()) // Solar Relic Gold
                }
                primaryColorArgb[idx] = palette.first
                secondaryColorArgb[idx] = palette.second
            }
        }
    }

    private fun wrapAroundCamera(i: Int, camX: Float, camY: Float, span: Float) {
        val dx = worldX[i] - camX
        val dy = worldY[i] - camY
        var wrapped = false
        if (dx > span) {
            worldX[i] -= span * 2f
            wrapped = true
        } else if (dx < -span) {
            worldX[i] += span * 2f
            wrapped = true
        }
        if (dy > span) {
            worldY[i] -= span * 2f
            wrapped = true
        } else if (dy < -span) {
            worldY[i] += span * 2f
            wrapped = true
        }
        if (wrapped) {
            prevWorldX[i] = worldX[i]
            prevWorldY[i] = worldY[i]
        }
    }

    private fun allocateSlot(): Int {
        for (attempt in 0 until maxCapacity) {
            val candidate = (spawnCursor + attempt) % maxCapacity
            if (type[candidate] == ParticleArchetype.INACTIVE) {
                spawnCursor = (candidate + 1) % maxCapacity
                return candidate
            }
        }
        val fallback = spawnCursor
        spawnCursor = (spawnCursor + 1) % maxCapacity
        return fallback
    }

    private fun nextRandomInt(): Int {
        var x = randomSeed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        randomSeed = x
        return x
    }

    private fun nextRandomFloat(minVal: Float, maxVal: Float): Float {
        val normalized = (nextRandomInt() and 0x7FFFFFFF).toFloat() / Int.MAX_VALUE.toFloat()
        return minVal + normalized * (maxVal - minVal)
    }

    private fun colorToFastArgb(color: Color): Int {
        val a = (color.alpha * 255f).toInt().coerceIn(0, 255)
        val r = (color.red * 255f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Renders all active particles directly onto the Compose [DrawScope] Canvas with specialized
     * visual shaders/primitives for Falling Snow, Rising Lava Sparks, Magical Glowing Orbs, and
     * Combat Bursts.
     */
    fun renderToCanvas(
        drawScope: DrawScope,
        tileHalfW: Float,
        quality: GraphicsQuality,
        worldToScreen: (Float, Float, Float) -> Offset
    ) = with(drawScope) {
        val canvasW = size.width
        val canvasH = size.height
        val enableHighDetail = quality != GraphicsQuality.PERFORMANCE

        for (i in 0 until maxCapacity) {
            val archetype = type[i]
            if (archetype == ParticleArchetype.INACTIVE) continue

            val screenPos = worldToScreen(worldX[i], worldY[i], worldZ[i])
            if (screenPos.x < -48f || screenPos.x > canvasW + 48f ||
                screenPos.y < -48f || screenPos.y > canvasH + 48f
            ) {
                continue
            }

            val lifeRatio = (age[i] / maxLife[i].coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            val fadeEnvelope = sin(lifeRatio * 3.1415927f).coerceIn(0f, 1f)
            val pSize = this@CoroutineParticleSystem.size[i]
            val pPhase = phase[i]

            when (archetype) {
                ParticleArchetype.SNOWFLAKE -> {
                    val ripple = groundRippleTimer[i]
                    if (ripple > 0f) {
                        // Ground-contact frost settle ripple
                        val rippleProgress = (ripple / 0.45f).coerceIn(0f, 1f)
                        val rippleAlpha = (1f - rippleProgress) * 0.65f
                        drawCircle(
                            color = Color(0xFFE0F2FE).copy(alpha = rippleAlpha),
                            radius = tileHalfW * (0.06f + rippleProgress * 0.22f),
                            center = screenPos
                        )
                    } else {
                        val flakeRadius = (tileHalfW * 0.085f * pSize).coerceAtLeast(2.2f)
                        val alpha = (0.35f + 0.60f * fadeEnvelope).coerceIn(0.15f, 0.95f)

                        // Soft icy halo
                        if (enableHighDetail) {
                            drawCircle(
                                color = Color(0xFF7DD3FC).copy(alpha = alpha * 0.28f),
                                radius = flakeRadius * 2.1f,
                                center = screenPos
                            )
                        }

                        // Crystalline 6-point dendritic star arms for foreground flakes
                        if (enableHighDetail && pSize > 1.15f) {
                            val armLen = flakeRadius * 1.45f
                            for (arm in 0 until 3) {
                                val angle = pPhase * 0.35f + arm * 1.0471976f
                                val dx = cos(angle) * armLen
                                val dy = sin(angle) * armLen
                                drawLine(
                                    color = Color.White.copy(alpha = alpha * 0.92f),
                                    start = Offset(screenPos.x - dx, screenPos.y - dy),
                                    end = Offset(screenPos.x + dx, screenPos.y + dy),
                                    strokeWidth = 1.6f,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else {
                            drawCircle(
                                color = Color.White.copy(alpha = alpha),
                                radius = flakeRadius,
                                center = screenPos
                            )
                        }
                    }
                }

                ParticleArchetype.LAVA_SPARK -> {
                    // Blackbody thermal color gradient: White-Hot -> Yellow -> Orange -> Crimson -> Ember
                    val sparkColor = when {
                        lifeRatio < 0.22f -> Color(0xFFFFFBEB)
                        lifeRatio < 0.48f -> Color(0xFFFDE047)
                        lifeRatio < 0.75f -> Color(0xFFF97316)
                        else -> Color(0xFFEF4444)
                    }
                    val flicker = (0.72f + 0.28f * sin(pPhase * 5.0f)).coerceIn(0.25f, 1f)
                    val alpha = ((1f - lifeRatio * 0.85f) * flicker).coerceIn(0.1f, 1f)
                    val sparkRadius = (tileHalfW * 0.075f * pSize * (1f - lifeRatio * 0.4f)).coerceAtLeast(1.8f)

                    // Velocity-aligned motion-blur tail streak
                    val tailWorldX = worldX[i] - vx[i] * 0.065f
                    val tailWorldY = worldY[i] - vy[i] * 0.065f
                    val tailWorldZ = (worldZ[i] - vz[i] * 0.065f).coerceAtLeast(0.1f)
                    val tailScreen = worldToScreen(tailWorldX, tailWorldY, tailWorldZ)

                    if (enableHighDetail) {
                        drawCircle(
                            color = Color(0xFFEA580C).copy(alpha = alpha * 0.32f),
                            radius = sparkRadius * 2.8f,
                            center = screenPos
                        )
                    }

                    drawLine(
                        color = sparkColor.copy(alpha = alpha * 0.88f),
                        start = tailScreen,
                        end = screenPos,
                        strokeWidth = sparkRadius * 1.45f,
                        cap = StrokeCap.Round
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = sparkRadius * 0.65f,
                        center = screenPos
                    )
                }

                ParticleArchetype.MAGIC_GLOW -> {
                    val primaryCol = Color(primaryColorArgb[i])
                    val secondaryCol = Color(secondaryColorArgb[i])
                    val twinkle = (0.55f + 0.45f * sin(pPhase * 3.2f)).coerceIn(0.2f, 1f)
                    val alpha = (fadeEnvelope * twinkle).coerceIn(0.12f, 0.98f)
                    val orbRadius = (tileHalfW * 0.095f * pSize).coerceAtLeast(2.4f)

                    // Gossamer orbital trail
                    val trailPos = worldToScreen(
                        worldX[i] - vx[i] * 0.08f,
                        worldY[i] - vy[i] * 0.08f,
                        worldZ[i] - vz[i] * 0.08f
                    )
                    drawLine(
                        color = secondaryCol.copy(alpha = alpha * 0.45f),
                        start = trailPos,
                        end = screenPos,
                        strokeWidth = orbRadius * 0.9f,
                        cap = StrokeCap.Round
                    )

                    // Multi-stop radial luminescence halo
                    if (enableHighDetail) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryCol.copy(alpha = alpha * 0.52f),
                                    secondaryCol.copy(alpha = alpha * 0.18f),
                                    Color.Transparent
                                ),
                                center = screenPos,
                                radius = orbRadius * 3.4f
                            ),
                            radius = orbRadius * 3.4f,
                            center = screenPos
                        )
                    } else {
                        drawCircle(
                            color = secondaryCol.copy(alpha = alpha * 0.35f),
                            radius = orbRadius * 2.2f,
                            center = screenPos
                        )
                    }

                    // Sparkling 4-point arcane star core
                    val starArm = orbRadius * (1.15f + 0.35f * twinkle)
                    starPath.reset()
                    starPath.moveTo(screenPos.x, screenPos.y - starArm)
                    starPath.lineTo(screenPos.x + starArm * 0.32f, screenPos.y - starArm * 0.32f)
                    starPath.lineTo(screenPos.x + starArm, screenPos.y)
                    starPath.lineTo(screenPos.x + starArm * 0.32f, screenPos.y + starArm * 0.32f)
                    starPath.lineTo(screenPos.x, screenPos.y + starArm)
                    starPath.lineTo(screenPos.x - starArm * 0.32f, screenPos.y + starArm * 0.32f)
                    starPath.lineTo(screenPos.x - starArm, screenPos.y)
                    starPath.lineTo(screenPos.x - starArm * 0.32f, screenPos.y - starArm * 0.32f)
                    starPath.close()

                    drawPath(
                        path = starPath,
                        color = Color.White.copy(alpha = alpha)
                    )
                }

                ParticleArchetype.COMBAT_BURST -> {
                    val burstCol = Color(primaryColorArgb[i])
                    val alpha = (1f - lifeRatio).coerceIn(0f, 1f)
                    val radius = (tileHalfW * 0.14f * pSize * alpha).coerceAtLeast(2.0f)
                    val tailScreen = worldToScreen(
                        worldX[i] - vx[i] * 0.04f,
                        worldY[i] - vy[i] * 0.04f,
                        worldZ[i] - vz[i] * 0.04f
                    )
                    drawLine(
                        color = burstCol.copy(alpha = alpha * 0.85f),
                        start = tailScreen,
                        end = screenPos,
                        strokeWidth = radius * 1.2f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.9f),
                        radius = radius * 0.55f,
                        center = screenPos
                    )
                }
            }
        }
    }
}
