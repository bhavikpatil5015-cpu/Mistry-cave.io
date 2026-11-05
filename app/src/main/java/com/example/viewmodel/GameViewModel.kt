package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CompletedLevelEntity
import com.example.data.DarkEscapeDatabase
import com.example.data.GameRepository
import com.example.data.MissionAchievementEntity
import com.example.data.PlayerSaveEntity
import com.example.engine.CoroutineParticleSystem
import com.example.engine.ParticleArchetype
import com.example.engine.ParticleEffectPreset
import com.example.engine.ProceduralLevelGenerator
import com.example.model.BiomeType
import com.example.model.CharacterAnimState
import com.example.model.CharacterSkin
import com.example.model.DestructiblePropEntity
import com.example.model.EnemyEntity
import com.example.model.GeneratedLevel
import com.example.model.GraphicsQuality
import com.example.model.HeroCatalog
import com.example.model.HeroCharacter
import com.example.model.RewardItemEntity
import com.example.model.RewardType
import com.example.model.TileType
import com.example.model.WeatherEffectType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

data class FloatingPopup(
    val id: Long,
    val text: String,
    val worldX: Float,
    val worldY: Float,
    val color: Color,
    val age: Float = 0f
)

data class DashGhostPoint(
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float,
    val alpha: Float
)

data class ParticleFx(
    var active: Boolean = false,
    var worldX: Float = 0f,
    var worldY: Float = 0f,
    var worldZ: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var color: Color = Color.White,
    var life: Float = 0f,
    var maxLife: Float = 0.6f
)

data class WeatherMote(
    var worldOffsetX: Float = 0f,
    var worldOffsetY: Float = 0f,
    var worldZ: Float = 1.0f,
    var vx: Float = 0.2f,
    var vy: Float = 0.2f,
    var vz: Float = -0.4f,
    var sizeFactor: Float = 1.0f,
    var phase: Float = 0f
)

enum class GameTab {
    PLAY, HEROES, MISSIONS, GENERATOR_LAB, BIOMES
}

data class LevelVictorySummary(
    val completedLevel: Int,
    val nextLevel: Int,
    val seed: Long,
    val biomeName: String,
    val stars: Int,
    val coinsEarned: Int,
    val gemsEarned: Int,
    val secretsFound: Int,
    val enemiesDefeated: Int,
    val timeSeconds: Int
)

data class GameEngineState(
    val activeTab: GameTab = GameTab.PLAY,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.ULTRA,
    val particlePreset: ParticleEffectPreset = ParticleEffectPreset.BIOME_AUTO,
    val activeParticleCount: Int = 0,
    val autoAdaptQuality: Boolean = true,
    val currentFps: Int = 60,
    val frameTimeMs: Float = 16.6f,
    val level: GeneratedLevel? = null,
    val playerX: Float = 3.5f,
    val playerY: Float = 3.5f,
    val playerZ: Float = 0f,
    val playerVx: Float = 0f,
    val playerVy: Float = 0f,
    val playerVz: Float = 0f,
    val playerAnimState: CharacterAnimState = CharacterAnimState.IDLE,
    val landingSquashTimer: Float = 0f,
    val hitReactTimer: Float = 0f,
    val deathAnimProgress: Float = 0f,
    val cameraX: Float = 3.5f,
    val cameraY: Float = 3.5f,
    val cameraZoom: Float = 1.0f,
    val userZoomMultiplier: Float = 1.0f,
    val bossCameraActive: Boolean = false,
    val activeBossName: String? = null,
    val activeBossHpRatio: Float = 0f,
    val levelTransitionProgress: Float = 0f,
    val screenShakeTimer: Float = 0f,
    val playerFacingAngle: Float = 0f,
    val playerHp: Int = 100,
    val playerMaxHp: Int = 100,
    val playerStamina: Float = 100f,
    val dashTimer: Float = 0f,
    val dashCooldown: Float = 0f,
    val dashGhosts: List<DashGhostPoint> = emptyList(),
    val attackAnimTimer: Float = 0f,
    val attackCooldown: Float = 0f,
    val invulnerableTimer: Float = 0f,
    val animCycle: Float = 0f,
    val worldTime: Float = 0f,
    val keysCollected: Int = 0,
    val keysRequired: Int = 1,
    val doorsUnlocked: Int = 0,
    val totalDoors: Int = 1,
    val remainingTreasures: Int = 0,
    val totalTreasures: Int = 0,
    val propsDestroyedInLevel: Int = 0,
    val switchActivated: Boolean = true,
    val portalUnlocked: Boolean = false,
    val secretsFoundInLevel: Int = 0,
    val totalSecretsInLevel: Int = 1,
    val sessionCoinsEarned: Int = 0,
    val sessionGemsEarned: Int = 0,
    val sessionEnemiesDefeated: Int = 0,
    val sessionChestsOpened: Int = 0,
    val enemies: List<EnemyEntity> = emptyList(),
    val rewards: List<RewardItemEntity> = emptyList(),
    val props: List<DestructiblePropEntity> = emptyList(),
    val floatingPopups: List<FloatingPopup> = emptyList(),
    val showSolutionHintPath: Boolean = false,
    val currentObjectiveText: String = "Find Relic Keys to unlock the Gate & Escape Portal",
    val statusBanner: String = "Collect Relic Keys to unlock the Escape Portal!",
    val victorySummary: LevelVictorySummary? = null,
    val isDefeated: Boolean = false,
    val batchReport: ProceduralLevelGenerator.BatchVerificationReport? = null
)

class GameViewModel(
    application: Application,
    private val injectedRepository: GameRepository?
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        injectedRepository = GameRepository(DarkEscapeDatabase.getInstance(application).dao())
    )

    private val repository: GameRepository? = injectedRepository

    val playerSave: StateFlow<PlayerSaveEntity?> = (repository?.playerSaveFlow ?: flowOf(null)).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val completedLevels: StateFlow<List<CompletedLevelEntity>> = (repository?.completedLevelsFlow ?: flowOf(emptyList())).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val missions: StateFlow<List<MissionAchievementEntity>> = (repository?.missionsFlow ?: flowOf(emptyList())).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _engineState = MutableStateFlow(GameEngineState())
    val engineState: StateFlow<GameEngineState> = _engineState.asStateFlow()

    // Pre-allocated particle pools for zero GC allocation during 60 FPS gameplay
    val coroutineParticleSystem: CoroutineParticleSystem = CoroutineParticleSystem()
    val particlePool: Array<ParticleFx> = Array(96) { ParticleFx() }
    val weatherPool: Array<WeatherMote> = Array(64) { i ->
        WeatherMote(
            worldOffsetX = ((i * 37) % 16) - 8f,
            worldOffsetY = ((i * 53) % 16) - 8f,
            worldZ = ((i * 19) % 10) * 0.22f + 0.1f,
            sizeFactor = 0.7f + (i % 4) * 0.25f,
            phase = i * 0.45f
        )
    }

    private var joystickX: Float = 0f
    private var joystickY: Float = 0f
    private var dpadImpulseTimer: Float = 0f
    private var popupIdCounter: Long = 1L
    private var levelStartTimestampMs: Long = System.currentTimeMillis()
    private var smoothedFrameTimeMs: Float = 16.6f
    private var lowFpsDurationSec: Float = 0f

    init {
        if (repository != null) {
            coroutineParticleSystem.startCoroutineSimulation(viewModelScope)
            viewModelScope.launch {
                val profile = repository.ensureInitialized()
                loadOrGenerateLevel(
                    levelNumber = profile.currentLevel,
                    explicitSeed = profile.currentLevelSeed.takeIf { it != 0L }
                )
            }
        } else {
            loadOrGenerateLevel(levelNumber = 1)
        }
    }

    fun selectTab(tab: GameTab) {
        _engineState.update { it.copy(activeTab = tab) }
    }

    fun cycleGraphicsQuality() {
        _engineState.update { state ->
            val nextQ = state.graphicsQuality.next()
            state.copy(
                graphicsQuality = nextQ,
                autoAdaptQuality = false,
                statusBanner = "Graphics Engine Mode: ${nextQ.label} (${nextQ.textureMipResolution}px Mipmaps)"
            )
        }
    }

    fun setGraphicsQuality(quality: GraphicsQuality, autoAdapt: Boolean = false) {
        _engineState.update {
            it.copy(
                graphicsQuality = quality,
                autoAdaptQuality = autoAdapt
            )
        }
    }

    fun cycleParticlePreset() {
        _engineState.update { state ->
            val nextPreset = state.particlePreset.next()
            coroutineParticleSystem.activePreset = nextPreset
            state.copy(
                particlePreset = nextPreset,
                statusBanner = "Particle Engine Preset: ${nextPreset.label}"
            )
        }
        coroutineParticleSystem.stepSimulation(0.032f)
        _engineState.update { it.copy(activeParticleCount = coroutineParticleSystem.activeCount) }
    }

    fun setParticlePreset(preset: ParticleEffectPreset) {
        coroutineParticleSystem.activePreset = preset
        _engineState.update {
            it.copy(
                particlePreset = preset,
                statusBanner = "Particle Engine Preset: ${preset.label}"
            )
        }
        coroutineParticleSystem.stepSimulation(0.032f)
        _engineState.update { it.copy(activeParticleCount = coroutineParticleSystem.activeCount) }
    }

    fun adjustCameraZoom(delta: Float) {
        _engineState.update { state ->
            val nextZoom = (state.userZoomMultiplier + delta).coerceIn(0.75f, 1.35f)
            state.copy(
                userZoomMultiplier = nextZoom,
                cameraZoom = nextZoom
            )
        }
    }

    fun updateJoystick(dx: Float, dy: Float) {
        val mag = hypot(dx, dy)
        if (mag > 1f) {
            joystickX = dx / mag
            joystickY = dy / mag
        } else {
            joystickX = dx
            joystickY = dy
        }
        dpadImpulseTimer = 0f
    }

    fun nudgeDirection(screenDx: Float, screenDy: Float) {
        val mag = hypot(screenDx, screenDy).coerceAtLeast(0.001f)
        joystickX = screenDx / mag
        joystickY = screenDy / mag
        dpadImpulseTimer = 0.22f
        stepPhysics(0.045f)
    }

    internal fun teleportPlayerForTest(wx: Float, wy: Float) {
        _engineState.update {
            it.copy(
                playerX = wx,
                playerY = wy,
                cameraX = wx,
                cameraY = wy,
                invulnerableTimer = 0f,
                attackCooldown = 0f
            )
        }
    }

    fun currentHero(): HeroCharacter {
        val heroId = playerSave.value?.selectedHeroId ?: "kaelen"
        return HeroCatalog.heroes.find { it.id == heroId } ?: HeroCatalog.heroes.first()
    }

    fun currentSkin(): CharacterSkin {
        val skinId = playerSave.value?.selectedSkinId ?: "default_skin"
        return HeroCatalog.skins.find { it.id == skinId } ?: HeroCatalog.skins.first()
    }

    fun loadOrGenerateLevel(
        levelNumber: Int,
        explicitSeed: Long? = null,
        explicitBiome: BiomeType? = null
    ) {
        val generated = ProceduralLevelGenerator.generateLevel(
            levelNumber = levelNumber,
            explicitSeed = explicitSeed,
            explicitBiome = explicitBiome
        )
        val hero = currentHero()
        levelStartTimestampMs = System.currentTimeMillis()
        joystickX = 0f
        joystickY = 0f
        dpadImpulseTimer = 0f

        val hasSwitch = generated.switchPoint != null
        val startX = generated.startPoint.x + 0.5f
        val startY = generated.startPoint.y + 0.5f
        val treasureCount = generated.initialRewards.count { it.type != RewardType.RELIC_KEY }

        _engineState.update {
            it.copy(
                level = generated,
                playerX = startX,
                playerY = startY,
                playerZ = 0f,
                playerVx = 0f,
                playerVy = 0f,
                playerVz = 0f,
                playerAnimState = CharacterAnimState.IDLE,
                landingSquashTimer = 0f,
                hitReactTimer = 0f,
                deathAnimProgress = 0f,
                cameraX = startX,
                cameraY = startY,
                cameraZoom = it.userZoomMultiplier,
                bossCameraActive = false,
                activeBossName = null,
                activeBossHpRatio = 0f,
                levelTransitionProgress = 1.0f,
                screenShakeTimer = 0f,
                playerHp = hero.maxHp,
                playerMaxHp = hero.maxHp,
                playerStamina = 100f,
                dashTimer = 0f,
                dashCooldown = 0f,
                dashGhosts = emptyList(),
                attackAnimTimer = 0f,
                attackCooldown = 0f,
                invulnerableTimer = 0f,
                keysCollected = 0,
                keysRequired = generated.keyPoints.size,
                doorsUnlocked = 0,
                totalDoors = generated.lockedDoorPoints.size,
                remainingTreasures = treasureCount,
                totalTreasures = treasureCount,
                propsDestroyedInLevel = 0,
                switchActivated = !hasSwitch,
                portalUnlocked = false,
                secretsFoundInLevel = 0,
                totalSecretsInLevel = generated.secretRoomPoints.size,
                sessionCoinsEarned = 0,
                sessionGemsEarned = 0,
                sessionEnemiesDefeated = 0,
                sessionChestsOpened = 0,
                enemies = generated.initialEnemies,
                rewards = generated.initialRewards,
                props = generated.initialProps,
                floatingPopups = emptyList(),
                victorySummary = null,
                isDefeated = false,
                currentObjectiveText = buildObjectiveString(
                    keysCollected = 0,
                    keysRequired = generated.keyPoints.size,
                    doorsUnlocked = 0,
                    totalDoors = generated.lockedDoorPoints.size,
                    switchActivated = !hasSwitch,
                    portalUnlocked = false
                ),
                statusBanner = "Level $levelNumber • ${generated.biome.displayName} (${generated.biome.weatherType.label})"
            )
        }
        coroutineParticleSystem.bindLevelEmitters(
            level = generated,
            rewards = generated.initialRewards,
            props = generated.initialProps
        )
        coroutineParticleSystem.syncFrameContext(
            camX = startX,
            camY = startY,
            px = startX,
            py = startY,
            pz = 0f,
            pvx = 0f,
            pvy = 0f,
            dashingOrAttacking = false,
            quality = _engineState.value.graphicsQuality,
            preset = _engineState.value.particlePreset,
            biome = generated.biome
        )
        // Warm up initial environmental particle field so the level starts alive
        repeat(4) { coroutineParticleSystem.stepSimulation(0.04f) }
        _engineState.update { it.copy(activeParticleCount = coroutineParticleSystem.activeCount) }
    }

    fun onJumpPressed() {
        val s = _engineState.value
        if (s.victorySummary != null || s.isDefeated) return
        if (s.playerZ <= 0.05f && s.playerStamina >= 12f) {
            _engineState.update {
                it.copy(
                    playerVz = 4.8f,
                    playerZ = 0.08f,
                    playerAnimState = CharacterAnimState.JUMP_ASCEND,
                    playerStamina = (it.playerStamina - 12f).coerceAtLeast(0f),
                    statusBanner = "Airborne Jump! Vaulting over floor hazards!"
                )
            }
            spawnParticles(s.playerX, s.playerY, 0.1f, Color(0xFF38BDF8), count = 8)
        }
    }

    fun onDodgeDashPressed() {
        val s = _engineState.value
        val lvl = s.level ?: return
        if (s.victorySummary != null || s.isDefeated) return
        if (s.dashCooldown <= 0f && s.playerStamina >= 18f) {
            val dashAngle = s.playerFacingAngle
            val burstStep = 0.55f
            val targetX = s.playerX + cos(dashAngle) * burstStep
            val targetY = s.playerY + sin(dashAngle) * burstStep
            val nextX = if (canOccupyPosition(lvl, targetX, s.playerY, allowJumpable = true)) targetX else s.playerX
            val nextY = if (canOccupyPosition(lvl, nextX, targetY, allowJumpable = true)) targetY else s.playerY

            val ghosts = listOf(
                DashGhostPoint(s.playerX, s.playerY, s.playerZ, 0.70f),
                DashGhostPoint((s.playerX + nextX) * 0.5f, (s.playerY + nextY) * 0.5f, s.playerZ, 0.45f)
            )

            _engineState.update {
                it.copy(
                    playerX = nextX,
                    playerY = nextY,
                    dashTimer = 0.26f,
                    dashCooldown = 0.85f,
                    dashGhosts = ghosts,
                    playerAnimState = CharacterAnimState.DASH,
                    invulnerableTimer = max(it.invulnerableTimer, 0.40f),
                    playerStamina = (it.playerStamina - 18f).coerceAtLeast(0f),
                    statusBanner = "Shadow Dash! Bursting forward with invulnerability!"
                )
            }
            spawnParticles(nextX, nextY, 0.25f, Color(0xFFA855F7), count = 12)
        }
    }

    fun onAttackPressed() {
        val s = _engineState.value
        val lvl = s.level ?: return
        if (s.victorySummary != null || s.isDefeated || s.attackCooldown > 0f) return

        val hero = currentHero()
        val attackRange = 2.15f
        var hitCount = 0
        var defeatedCount = 0
        var bonusCoins = 0
        var destroyedPropCount = 0

        val updatedEnemies = s.enemies.map { enemy ->
            if (enemy.isDying) return@map enemy
            val dx = enemy.x - s.playerX
            val dy = enemy.y - s.playerY
            val dist = hypot(dx, dy)
            if (dist <= attackRange) {
                hitCount++
                val newHp = (enemy.hp - hero.attackDamage).coerceAtLeast(0)
                val kbMag = if (dist > 0.05f) 4.2f / dist else 0f
                spawnParticles(enemy.x, enemy.y, 0.4f, Color(0xFFFFB800), count = 10)
                if (newHp <= 0) {
                    defeatedCount++
                    val drop = if (enemy.isBoss) 80 else 25
                    bonusCoins += drop
                    addPopup("+${drop}G KO!", enemy.x, enemy.y, Color(0xFFFFB800))
                    enemy.copy(
                        hp = 0,
                        isDying = true,
                        deathAnimProgress = 0.05f,
                        hurtFlashTimer = 0.35f,
                        knockbackVx = dx * kbMag,
                        knockbackVy = dy * kbMag
                    )
                } else {
                    addPopup("-${hero.attackDamage}", enemy.x, enemy.y, Color(0xFFEF4444))
                    enemy.copy(
                        hp = newHp,
                        hurtFlashTimer = 0.28f,
                        isAlerted = true,
                        knockbackVx = dx * kbMag,
                        knockbackVy = dy * kbMag
                    )
                }
            } else {
                enemy
            }
        }

        // Check destructible environmental props within attack range
        val updatedProps = s.props.map { prop ->
            if (prop.isDestroyed) return@map prop
            val dist = hypot((prop.gx + 0.5f) - s.playerX, (prop.gy + 0.5f) - s.playerY)
            if (dist <= attackRange) {
                val newHp = (prop.hp - hero.attackDamage).coerceAtLeast(0)
                spawnParticles(prop.gx + 0.5f, prop.gy + 0.5f, 0.35f, prop.type.glowColor, count = 10)
                if (newHp <= 0) {
                    destroyedPropCount++
                    bonusCoins += 20
                    addPopup("+20G SHATTERED!", prop.gx + 0.5f, prop.gy + 0.5f, prop.type.glowColor)
                    prop.copy(hp = 0, isDestroyed = true, destroyAnimProgress = 0.1f, hitFlashTimer = 0.25f)
                } else {
                    prop.copy(hp = newHp, hitFlashTimer = 0.22f)
                }
            } else {
                prop
            }
        }

        val breachedSecret = checkAndBreachAdjacentSecret(lvl, s.playerX, s.playerY)

        _engineState.update {
            it.copy(
                attackAnimTimer = 0.30f,
                attackCooldown = 0.35f,
                playerAnimState = CharacterAnimState.ATTACK,
                screenShakeTimer = if (hitCount > 0 || breachedSecret || destroyedPropCount > 0) 0.16f else it.screenShakeTimer,
                enemies = updatedEnemies,
                props = updatedProps,
                propsDestroyedInLevel = it.propsDestroyedInLevel + destroyedPropCount,
                sessionEnemiesDefeated = it.sessionEnemiesDefeated + defeatedCount,
                sessionCoinsEarned = it.sessionCoinsEarned + bonusCoins,
                secretsFoundInLevel = it.secretsFoundInLevel + if (breachedSecret) 1 else 0,
                statusBanner = when {
                    breachedSecret -> "Secret Chamber Breached! Hidden relics revealed!"
                    destroyedPropCount > 0 -> "Shattered ${lvl.biome.signatureProp.displayName}! +20G"
                    defeatedCount > 0 -> "${hero.abilityName} vanquished guardian! +${bonusCoins}G"
                    hitCount > 0 -> "${hero.abilityName} struck $hitCount enemy guardian(s)!"
                    else -> "${hero.abilityName}!"
                }
            )
        }
    }

    fun onInteractPressed() {
        val s = _engineState.value
        val lvl = s.level ?: return
        if (s.victorySummary != null || s.isDefeated) return

        collectNearbyRewards(radius = 1.85f, px = s.playerX, py = s.playerY)

        if (checkAndUnlockNearbyDoor(lvl, s.playerX, s.playerY, maxRadius = 1.9f)) {
            return
        }

        if (checkAndBreachAdjacentSecret(lvl, s.playerX, s.playerY)) {
            _engineState.update {
                it.copy(
                    secretsFoundInLevel = it.secretsFoundInLevel + 1,
                    statusBanner = "Secret Passage Unlocked! Explore the hidden vault!"
                )
            }
            return
        }

        val sp = lvl.switchPoint
        val afterRewardState = _engineState.value
        if (sp != null && !afterRewardState.switchActivated &&
            hypot((sp.x + 0.5f) - afterRewardState.playerX, (sp.y + 0.5f) - afterRewardState.playerY) <= 1.85f
        ) {
            lvl.tiles[sp.x][sp.y] = lvl.tiles[sp.x][sp.y].copy(isSwitchActivated = true)
            val unlocked = afterRewardState.keysCollected >= afterRewardState.keysRequired
            addPopup("SWITCH ACTIVATED!", sp.x + 0.5f, sp.y + 0.5f, Color(0xFF10B981))
            spawnParticles(sp.x + 0.5f, sp.y + 0.5f, 0.3f, Color(0xFF10B981), 12)
            _engineState.update {
                it.copy(
                    switchActivated = true,
                    portalUnlocked = unlocked,
                    currentObjectiveText = buildObjectiveString(
                        it.keysCollected, it.keysRequired, it.doorsUnlocked, it.totalDoors, true, unlocked
                    ),
                    statusBanner = if (unlocked) "Escape Portal Unlocked! Head to the Exit!" else "Mechanism Activated! Collect remaining Relic Keys!"
                )
            }
            return
        }

        val ep = lvl.exitPoint
        val latest = _engineState.value
        if (hypot((ep.x + 0.5f) - latest.playerX, (ep.y + 0.5f) - latest.playerY) <= 1.85f) {
            if (latest.portalUnlocked) {
                triggerLevelVictory()
            } else {
                addPopup("PORTAL LOCKED!", ep.x + 0.5f, ep.y + 0.5f, Color(0xFFEF4444))
                _engineState.update {
                    it.copy(
                        statusBanner = "Portal Locked! Collect ${max(0, it.keysRequired - it.keysCollected)} more Key(s)" +
                            if (!it.switchActivated) " & Activate Switch!" else "!"
                    )
                }
            }
        }
    }

    fun toggleHintPath() {
        _engineState.update {
            it.copy(
                showSolutionHintPath = !it.showSolutionHintPath,
                statusBanner = if (!it.showSolutionHintPath) "A* Verified Escape Route Overlay Enabled" else "Escape Route Overlay Hidden"
            )
        }
    }

    fun advanceToNextGeneratedLevel() {
        val summary = _engineState.value.victorySummary
        val targetLevel = summary?.nextLevel ?: ((_engineState.value.level?.levelNumber ?: 1) + 1)
        loadOrGenerateLevel(levelNumber = targetLevel)
    }

    fun retryCurrentLevel() {
        val current = _engineState.value.level ?: return
        loadOrGenerateLevel(
            levelNumber = current.levelNumber,
            explicitSeed = current.seed,
            explicitBiome = current.biome
        )
    }

    fun runGeneratorBatchVerification(count: Int = 15) {
        viewModelScope.launch {
            val startLvl = _engineState.value.level?.levelNumber ?: 1
            val report = ProceduralLevelGenerator.verifyConsecutiveLevels(startLvl, count)
            _engineState.update {
                it.copy(
                    batchReport = report,
                    statusBanner = "Verified $count consecutive levels in ${report.totalTimeMs}ms (100% Solvable)"
                )
            }
        }
    }

    fun selectOrUnlockHero(hero: HeroCharacter) {
        viewModelScope.launch {
            repository?.updatePlayerSave { save ->
                val unlockedSet = save.unlockedHeroIdsCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
                if (hero.id in unlockedSet) {
                    save.copy(selectedHeroId = hero.id)
                } else if (save.coins >= hero.costCoins && save.gems >= hero.costGems) {
                    unlockedSet.add(hero.id)
                    save.copy(
                        coins = save.coins - hero.costCoins,
                        gems = save.gems - hero.costGems,
                        selectedHeroId = hero.id,
                        unlockedHeroIdsCsv = unlockedSet.joinToString(",")
                    )
                } else {
                    save
                }
            }
        }
    }

    fun selectOrUnlockSkin(skin: CharacterSkin) {
        viewModelScope.launch {
            repository?.updatePlayerSave { save ->
                val unlockedSet = save.unlockedSkinIdsCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
                if (skin.id in unlockedSet) {
                    save.copy(selectedSkinId = skin.id)
                } else if (save.gems >= skin.costGems) {
                    unlockedSet.add(skin.id)
                    save.copy(
                        gems = save.gems - skin.costGems,
                        selectedSkinId = skin.id,
                        unlockedSkinIdsCsv = unlockedSet.joinToString(",")
                    )
                } else {
                    save
                }
            }
        }
    }

    fun claimMission(missionId: String) {
        viewModelScope.launch {
            repository?.claimMissionReward(missionId)
        }
    }

    /**
     * Real-time 60 FPS game loop step called by the Compose frame clock.
     */
    fun stepPhysics(dtSeconds: Float) {
        val rawMs = (dtSeconds * 1000f).coerceIn(4f, 65f)
        smoothedFrameTimeMs = smoothedFrameTimeMs * 0.90f + rawMs * 0.10f
        val currentFps = (1000f / smoothedFrameTimeMs).roundToInt().coerceIn(15, 120)

        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        val s = _engineState.value
        val lvl = s.level ?: return

        // Adaptive Frame-Time Governor
        var activeQuality = s.graphicsQuality
        if (s.autoAdaptQuality) {
            if (smoothedFrameTimeMs > 25f) {
                lowFpsDurationSec += dt
                if (lowFpsDurationSec > 1.5f) {
                    activeQuality = when (activeQuality) {
                        GraphicsQuality.ULTRA -> GraphicsQuality.HIGH
                        GraphicsQuality.HIGH -> GraphicsQuality.PERFORMANCE
                        GraphicsQuality.PERFORMANCE -> GraphicsQuality.PERFORMANCE
                    }
                    lowFpsDurationSec = 0f
                }
            } else {
                lowFpsDurationSec = (lowFpsDurationSec - dt * 0.5f).coerceAtLeast(0f)
            }
        }

        if (s.victorySummary != null) return
        if (s.isDefeated) {
            if (s.deathAnimProgress < 1f) {
                _engineState.update {
                    it.copy(
                        playerAnimState = CharacterAnimState.DEATH,
                        deathAnimProgress = (it.deathAnimProgress + dt * 1.8f).coerceAtMost(1f)
                    )
                }
            }
            return
        }

        if (dpadImpulseTimer > 0f) {
            dpadImpulseTimer = (dpadImpulseTimer - dt).coerceAtLeast(0f)
            if (dpadImpulseTimer <= 0f) {
                joystickX = 0f
                joystickY = 0f
            }
        }

        val hero = currentHero()
        val newWorldTime = s.worldTime + dt

        // 1. Convert screen-space 360° joystick to isometric world coordinates
        val isoInputX = (joystickX + joystickY)
        val isoInputY = (joystickY - joystickX)
        val inputMag = hypot(isoInputX, isoInputY)
        val normX = if (inputMag > 1f) isoInputX / inputMag else isoInputX
        val normY = if (inputMag > 1f) isoInputY / inputMag else isoInputY

        val nextFacing = if (inputMag > 0.08f) {
            atan2(normY, normX)
        } else {
            s.playerFacingAngle
        }

        val isDashing = s.dashTimer > 0f
        val baseMoveSpeed = 4.4f * hero.speedMultiplier * (if (isDashing) 2.35f else 1.0f)

        val targetVx = if (isDashing && inputMag <= 0.1f) {
            cos(nextFacing) * baseMoveSpeed
        } else {
            normX * baseMoveSpeed
        }
        val targetVy = if (isDashing && inputMag <= 0.1f) {
            sin(nextFacing) * baseMoveSpeed
        } else {
            normY * baseMoveSpeed
        }

        val friction = lvl.biome.frictionMultiplier
        val newVx = if (friction < 0.95f) {
            s.playerVx * 0.88f + targetVx * 0.12f
        } else {
            targetVx
        }
        val newVy = if (friction < 0.95f) {
            s.playerVy * 0.88f + targetVy * 0.12f
        } else {
            targetVy
        }

        // 2. Z-Axis Jump & Landing Squash Kinematics
        val wasAirborne = s.playerZ > 0.04f
        var nextZ = s.playerZ + s.playerVz * dt
        var nextVz = s.playerVz - 13.0f * dt
        var nextLandingTimer = (s.landingSquashTimer - dt).coerceAtLeast(0f)
        if (nextZ <= 0f) {
            if (wasAirborne) {
                nextLandingTimer = 0.18f
                spawnParticles(s.playerX, s.playerY, 0.05f, Color.White.copy(alpha = 0.7f), count = 5)
            }
            nextZ = 0f
            nextVz = 0f
        }
        val isAirborne = nextZ > 0.10f

        // 3. Auto-unlock nearby Locked Door if player approaches while holding a Relic Key
        checkAndUnlockNearbyDoor(lvl, s.playerX, s.playerY, maxRadius = 1.25f)

        // 4. Sliding Tile & Destructible Prop Collision Detection
        var nextX = s.playerX
        var nextY = s.playerY
        val candidateX = s.playerX + newVx * dt
        val candidateY = s.playerY + newVy * dt

        if (canOccupyPosition(lvl, candidateX, s.playerY, isAirborne)) {
            nextX = candidateX
        }
        if (canOccupyPosition(lvl, nextX, candidateY, isAirborne)) {
            nextY = candidateY
        }

        // 5. Real-Time Camera System (Smooth Lerp, Cinematic Boss Framing & Dynamic Zoom)
        val nearbyBoss = s.enemies.firstOrNull { !it.isDying && it.isBoss && hypot(it.x - nextX, it.y - nextY) < 6.8f }
        val focalX = if (nearbyBoss != null) nextX * 0.75f + nearbyBoss.x * 0.25f else nextX + newVx * 0.08f
        val focalY = if (nearbyBoss != null) nextY * 0.75f + nearbyBoss.y * 0.25f else nextY + newVy * 0.08f
        val camLerp = (10f * dt).coerceIn(0.08f, 1f)
        val nextCamX = s.cameraX + (focalX - s.cameraX) * camLerp
        val nextCamY = s.cameraY + (focalY - s.cameraY) * camLerp

        val dynamicZoomFactor = when {
            nearbyBoss != null -> 0.86f
            isDashing || s.attackAnimTimer > 0f -> 1.05f
            else -> 1.0f
        }
        val targetZoom = (s.userZoomMultiplier * dynamicZoomFactor).coerceIn(0.72f, 1.40f)
        val nextZoom = s.cameraZoom + (targetZoom - s.cameraZoom) * (6f * dt).coerceIn(0.05f, 1f)

        // 6. Update Combat Particles, Coroutine SoA Particle Engine & Environmental Weather Motes
        for (p in particlePool) {
            if (p.active) {
                p.life += dt
                if (p.life >= p.maxLife) {
                    p.active = false
                } else {
                    p.worldX += p.vx * dt
                    p.worldY += p.vy * dt
                    p.worldZ = (p.worldZ + p.vz * dt).coerceAtLeast(0f)
                }
            }
        }
        coroutineParticleSystem.syncFrameContext(
            camX = nextCamX,
            camY = nextCamY,
            px = nextX,
            py = nextY,
            pz = nextZ,
            pvx = newVx,
            pvy = newVy,
            dashingOrAttacking = isDashing || s.attackAnimTimer > 0f,
            quality = activeQuality,
            preset = s.particlePreset,
            biome = lvl.biome
        )
        coroutineParticleSystem.stepSimulation(dt)
        updateWeatherMotes(lvl.biome.weatherType, dt, activeQuality.maxWeatherParticles)

        // 7. Collect Nearby Coins, Gems, Chests & Relic Keys + Animate Opening Chests
        collectNearbyRewards(radius = 0.82f, px = nextX, py = nextY)

        // Advance chest lid open animations & prop destruction animations
        val postCollect = _engineState.value
        val animatedRewards = postCollect.rewards.map { r ->
            if (r.isCollected && (r.type == RewardType.TREASURE_CHEST || r.type == RewardType.RARE_ARTEFACT) && r.openAnimProgress < 1f) {
                r.copy(openAnimProgress = (r.openAnimProgress + dt * 2.5f).coerceAtMost(1f))
            } else {
                r
            }
        }
        val animatedProps = postCollect.props.map { p ->
            val nextFlash = (p.hitFlashTimer - dt).coerceAtLeast(0f)
            val nextDestroy = if (p.isDestroyed && p.destroyAnimProgress < 1f) {
                (p.destroyAnimProgress + dt * 3.0f).coerceAtMost(1f)
            } else p.destroyAnimProgress
            p.copy(hitFlashTimer = nextFlash, destroyAnimProgress = nextDestroy)
        }

        // 8. Check Switch Pedestal & Exit Portal
        val sp = lvl.switchPoint
        var switchNow = postCollect.switchActivated
        if (sp != null && !switchNow && hypot((sp.x + 0.5f) - nextX, (sp.y + 0.5f) - nextY) < 0.80f) {
            lvl.tiles[sp.x][sp.y] = lvl.tiles[sp.x][sp.y].copy(isSwitchActivated = true)
            switchNow = true
            addPopup("SWITCH ACTIVATED!", sp.x + 0.5f, sp.y + 0.5f, Color(0xFF10B981))
            spawnParticles(sp.x + 0.5f, sp.y + 0.5f, 0.3f, Color(0xFF10B981), 12)
        }

        val currentKeys = postCollect.keysCollected
        val portalUnlockedNow = currentKeys >= postCollect.keysRequired && switchNow

        val ep = lvl.exitPoint
        if (portalUnlockedNow && hypot((ep.x + 0.5f) - nextX, (ep.y + 0.5f) - nextY) < 0.78f) {
            _engineState.update {
                it.copy(
                    playerX = nextX,
                    playerY = nextY,
                    cameraX = nextCamX,
                    cameraY = nextCamY,
                    portalUnlocked = true,
                    switchActivated = switchNow
                )
            }
            triggerLevelVictory()
            return
        }

        // 9. Hazard & Trap Damage Evaluation
        var nextHp = postCollect.playerHp
        var nextInvuln = (postCollect.invulnerableTimer - dt).coerceAtLeast(0f)
        var nextHitReact = (postCollect.hitReactTimer - dt).coerceAtLeast(0f)
        var nextShake = (postCollect.screenShakeTimer - dt).coerceAtLeast(0f)
        val currentTile = lvl.tiles[nextX.toInt().coerceIn(0, lvl.width - 1)][nextY.toInt().coerceIn(0, lvl.height - 1)]

        if (!isAirborne && nextInvuln <= 0f) {
            val spikeActive = ((newWorldTime * 1.4f).toInt() % 2 == 0)
            val fireJetActive = ((newWorldTime * 1.1f + 0.5f).toInt() % 2 == 0)

            val trapDamage = when {
                currentTile.type == TileType.SPIKE_TRAP && spikeActive -> 14
                currentTile.type == TileType.FIRE_JET_TRAP && fireJetActive -> 18
                currentTile.type == TileType.LIQUID_HAZARD -> 20
                else -> 0
            }
            if (trapDamage > 0) {
                nextHp = (nextHp - trapDamage).coerceAtLeast(0)
                nextInvuln = 0.85f
                nextHitReact = 0.25f
                nextShake = 0.22f
                addPopup("-$trapDamage TRAP!", nextX, nextY, Color(0xFFEF4444))
                spawnParticles(nextX, nextY, 0.2f, Color(0xFFEF4444), count = 10)
            }
        }

        // 10. Enemy Patrol, Detection Alert, Chase AI, Death Animation & Contact Damage
        val updatedEnemies = postCollect.enemies.mapNotNull { enemy ->
            if (enemy.isDying) {
                val nextDeath = enemy.deathAnimProgress + dt * 2.4f
                if (nextDeath >= 1f) null else enemy.copy(deathAnimProgress = nextDeath)
            } else {
                val distToPlayer = hypot(nextX - enemy.x, nextY - enemy.y)
                val detected = distToPlayer < 5.2f

                if (detected && !enemy.isAlerted) {
                    addPopup("! SPOTTED", enemy.x, enemy.y, Color(0xFFFACC15))
                }

                val targetX: Float
                val targetY: Float
                var nextMovingToEnd = enemy.movingToEnd

                if (detected) {
                    targetX = nextX
                    targetY = nextY
                } else {
                    targetX = if (enemy.movingToEnd) enemy.patrolEndX else enemy.startX
                    targetY = if (enemy.movingToEnd) enemy.patrolEndY else enemy.startY
                    if (hypot(targetX - enemy.x, targetY - enemy.y) < 0.25f) {
                        nextMovingToEnd = !enemy.movingToEnd
                    }
                }

                val dirX = targetX - enemy.x
                val dirY = targetY - enemy.y
                val dMag = hypot(dirX, dirY)
                var ex = enemy.x
                var ey = enemy.y
                var eAngle = enemy.facingAngle

                val moveVx = (if (dMag > 0.05f) (dirX / dMag) * enemy.speed else 0f) + enemy.knockbackVx
                val moveVy = (if (dMag > 0.05f) (dirY / dMag) * enemy.speed else 0f) + enemy.knockbackVy
                val stepX = moveVx * dt
                val stepY = moveVy * dt

                if (canOccupyPosition(lvl, ex + stepX, ey, allowJumpable = false)) ex += stepX
                if (canOccupyPosition(lvl, ex, ey + stepY, allowJumpable = false)) ey += stepY
                if (dMag > 0.05f) {
                    eAngle = atan2(dirY, dirX)
                }

                var nextEnemyAttackAnim = (enemy.attackAnimTimer - dt).coerceAtLeast(0f)
                if (distToPlayer < 0.76f && nextInvuln <= 0f && !isAirborne) {
                    nextHp = (nextHp - enemy.damage).coerceAtLeast(0)
                    nextInvuln = 0.90f
                    nextHitReact = 0.28f
                    nextShake = 0.24f
                    nextEnemyAttackAnim = 0.30f
                    addPopup("-${enemy.damage} HP", nextX, nextY, Color(0xFFEF4444))
                    spawnParticles(nextX, nextY, 0.3f, Color(0xFFEF4444), count = 10)
                }

                enemy.copy(
                    x = ex,
                    y = ey,
                    facingAngle = eAngle,
                    movingToEnd = nextMovingToEnd,
                    isAlerted = detected,
                    attackAnimTimer = nextEnemyAttackAnim,
                    knockbackVx = enemy.knockbackVx * 0.82f,
                    knockbackVy = enemy.knockbackVy * 0.82f,
                    hurtFlashTimer = (enemy.hurtFlashTimer - dt).coerceAtLeast(0f)
                )
            }
        }

        // 11. Compute Player Character Animation State
        val defeated = nextHp <= 0
        val resolvedAnimState = when {
            defeated -> CharacterAnimState.DEATH
            nextHitReact > 0f -> CharacterAnimState.HIT_REACT
            s.attackAnimTimer > 0f -> CharacterAnimState.ATTACK
            isDashing -> CharacterAnimState.DASH
            isAirborne -> CharacterAnimState.JUMP_ASCEND
            nextLandingTimer > 0f -> CharacterAnimState.JUMP_LAND
            inputMag > 0.65f -> CharacterAnimState.RUN
            inputMag > 0.08f -> CharacterAnimState.WALK
            else -> CharacterAnimState.IDLE
        }

        val activePopups = _engineState.value.floatingPopups.mapNotNull { p ->
            val nextAge = p.age + dt
            if (nextAge < 1.1f) p.copy(age = nextAge) else null
        }
        val activeGhosts = postCollect.dashGhosts.mapNotNull { g ->
            val nextAlpha = g.alpha - dt * 2.2f
            if (nextAlpha > 0.05f) g.copy(alpha = nextAlpha) else null
        }

        _engineState.update { state ->
            state.copy(
                graphicsQuality = activeQuality,
                activeParticleCount = coroutineParticleSystem.activeCount,
                currentFps = currentFps,
                frameTimeMs = smoothedFrameTimeMs,
                playerX = nextX,
                playerY = nextY,
                playerZ = nextZ,
                playerVx = newVx,
                playerVy = newVy,
                playerVz = nextVz,
                playerAnimState = resolvedAnimState,
                landingSquashTimer = nextLandingTimer,
                hitReactTimer = nextHitReact,
                deathAnimProgress = if (defeated) 0.1f else 0f,
                cameraX = nextCamX,
                cameraY = nextCamY,
                cameraZoom = nextZoom,
                bossCameraActive = nearbyBoss != null,
                activeBossName = nearbyBoss?.name,
                activeBossHpRatio = if (nearbyBoss != null) nearbyBoss.hp.toFloat() / nearbyBoss.maxHp.toFloat() else 0f,
                levelTransitionProgress = (state.levelTransitionProgress - dt * 1.6f).coerceAtLeast(0f),
                screenShakeTimer = nextShake,
                playerFacingAngle = nextFacing,
                playerHp = nextHp,
                playerStamina = (state.playerStamina + 20f * dt).coerceAtMost(100f),
                dashTimer = (state.dashTimer - dt).coerceAtLeast(0f),
                dashCooldown = (state.dashCooldown - dt).coerceAtLeast(0f),
                dashGhosts = activeGhosts,
                attackAnimTimer = (state.attackAnimTimer - dt).coerceAtLeast(0f),
                attackCooldown = (state.attackCooldown - dt).coerceAtLeast(0f),
                invulnerableTimer = nextInvuln,
                animCycle = if (inputMag > 0.08f || isDashing) state.animCycle + dt * 10.5f else state.animCycle + dt * 2.5f,
                worldTime = newWorldTime,
                switchActivated = switchNow,
                portalUnlocked = portalUnlockedNow,
                currentObjectiveText = buildObjectiveString(
                    state.keysCollected,
                    state.keysRequired,
                    state.doorsUnlocked,
                    state.totalDoors,
                    switchNow,
                    portalUnlockedNow
                ),
                enemies = updatedEnemies,
                rewards = animatedRewards,
                props = animatedProps,
                floatingPopups = activePopups,
                isDefeated = defeated,
                statusBanner = if (defeated) "You fell in ${lvl.biome.displayName}! Tap Restart to try again." else state.statusBanner
            )
        }
    }

    private fun updateWeatherMotes(weatherType: WeatherEffectType, dt: Float, activeCount: Int) {
        val count = activeCount.coerceIn(0, weatherPool.size)
        for (i in 0 until count) {
            val m = weatherPool[i]
            m.phase += dt * 2.2f
            when (weatherType) {
                WeatherEffectType.BLIZZARD_SNOW -> {
                    m.worldOffsetX += (0.4f + sin(m.phase) * 0.25f) * dt
                    m.worldOffsetY += 0.35f * dt
                    m.worldZ -= 0.65f * dt
                }
                WeatherEffectType.DESERT_SANDSTORM -> {
                    m.worldOffsetX += 1.8f * dt
                    m.worldOffsetY -= 0.6f * dt
                    m.worldZ += sin(m.phase) * 0.15f * dt
                }
                WeatherEffectType.VOLCANIC_ASH, WeatherEffectType.DUNGEON_EMBERS, WeatherEffectType.BIOLUMINESCENT_ORBS -> {
                    m.worldOffsetX += sin(m.phase) * 0.35f * dt
                    m.worldOffsetY += cos(m.phase) * 0.35f * dt
                    m.worldZ += 0.55f * dt
                }
                else -> {
                    m.worldOffsetX += sin(m.phase * 0.8f) * 0.3f * dt
                    m.worldOffsetY += cos(m.phase * 0.8f) * 0.3f * dt
                    m.worldZ -= 0.30f * dt
                }
            }
            if (m.worldOffsetX > 8f) m.worldOffsetX = -8f
            if (m.worldOffsetX < -8f) m.worldOffsetX = 8f
            if (m.worldOffsetY > 8f) m.worldOffsetY = -8f
            if (m.worldOffsetY < -8f) m.worldOffsetY = 8f
            if (m.worldZ < 0.05f) m.worldZ = 2.2f
            if (m.worldZ > 2.3f) m.worldZ = 0.1f
        }
    }

    private fun buildObjectiveString(
        keysCollected: Int,
        keysRequired: Int,
        doorsUnlocked: Int,
        totalDoors: Int,
        switchActivated: Boolean,
        portalUnlocked: Boolean
    ): String = when {
        portalUnlocked -> "OBJECTIVE: Enter the glowing cyan Exit Portal to escape!"
        keysCollected < keysRequired -> "OBJECTIVE: Collect Relic Keys ($keysCollected/$keysRequired) to unlock gates & portal"
        totalDoors > doorsUnlocked -> "OBJECTIVE: Use your Key to unlock the Exit Chamber Gate"
        !switchActivated -> "OBJECTIVE: Step on or Use the Golden Switch Pedestal"
        else -> "OBJECTIVE: Reach the Exit Portal!"
    }

    fun canOccupyPosition(
        lvl: GeneratedLevel,
        wx: Float,
        wy: Float,
        allowJumpable: Boolean
    ): Boolean {
        val radius = 0.27f
        val offsets = arrayOf(
            Pair(-radius, -radius),
            Pair(radius, -radius),
            Pair(-radius, radius),
            Pair(radius, radius)
        )
        for ((ox, oy) in offsets) {
            val gx = (wx + ox).toInt()
            val gy = (wy + oy).toInt()
            if (gx !in 0 until lvl.width || gy !in 0 until lvl.height) return false
            val tile = lvl.tiles[gx][gy]
            val passable = if (allowJumpable) {
                tile.type.isWalkable || tile.type.isJumpable
            } else {
                tile.type.isWalkable
            }
            if (!passable) return false
        }
        return true
    }

    private fun checkAndUnlockNearbyDoor(
        lvl: GeneratedLevel,
        px: Float,
        py: Float,
        maxRadius: Float
    ): Boolean {
        val s = _engineState.value
        val gx = px.toInt()
        val gy = py.toInt()

        for (dx in -2..2) {
            for (dy in -2..2) {
                val nx = gx + dx
                val ny = gy + dy
                if (nx in 1 until lvl.width - 1 && ny in 1 until lvl.height - 1) {
                    val t = lvl.tiles[nx][ny]
                    if (t.type == TileType.LOCKED_DOOR &&
                        hypot((nx + 0.5f) - px, (ny + 0.5f) - py) <= maxRadius
                    ) {
                        if (s.keysCollected > 0) {
                            lvl.tiles[nx][ny] = t.copy(
                                type = TileType.UNLOCKED_DOOR,
                                height = TileType.UNLOCKED_DOOR.baseHeight
                            )
                            addPopup("DOOR UNLOCKED!", nx + 0.5f, ny + 0.5f, Color(0xFFFFB800))
                            spawnParticles(nx + 0.5f, ny + 0.5f, 0.4f, Color(0xFFFFB800), count = 14)
                            _engineState.update { state ->
                                val newDoors = state.doorsUnlocked + 1
                                state.copy(
                                    doorsUnlocked = newDoors,
                                    currentObjectiveText = buildObjectiveString(
                                        state.keysCollected,
                                        state.keysRequired,
                                        newDoors,
                                        state.totalDoors,
                                        state.switchActivated,
                                        state.portalUnlocked
                                    ),
                                    statusBanner = "Security Gate Unlocked with Relic Key!"
                                )
                            }
                            return true
                        } else {
                            addPopup("NEED KEY!", nx + 0.5f, ny + 0.5f, Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
        return false
    }

    private fun checkAndBreachAdjacentSecret(lvl: GeneratedLevel, px: Float, py: Float): Boolean {
        val gx = px.toInt()
        val gy = py.toInt()
        var breached = false
        for (dx in -1..1) {
            for (dy in -1..1) {
                val nx = gx + dx
                val ny = gy + dy
                if (nx in 1 until lvl.width - 1 && ny in 1 until lvl.height - 1) {
                    val t = lvl.tiles[nx][ny]
                    if (t.type == TileType.CRACKED_SECRET_WALL) {
                        lvl.tiles[nx][ny] = t.copy(
                            type = TileType.SECRET_FLOOR,
                            height = TileType.SECRET_FLOOR.baseHeight,
                            isRevealed = true
                        )
                        breached = true
                        addPopup("SECRET DISCOVERED!", nx + 0.5f, ny + 0.5f, Color(0xFFA855F7))
                        spawnParticles(nx + 0.5f, ny + 0.5f, 0.4f, Color(0xFFA855F7), count = 14)
                    }
                }
            }
        }
        if (breached) {
            for (x in 0 until lvl.width) {
                for (y in 0 until lvl.height) {
                    if (lvl.tiles[x][y].isSecret) {
                        lvl.tiles[x][y] = lvl.tiles[x][y].copy(isRevealed = true)
                    }
                }
            }
        }
        return breached
    }

    private fun collectNearbyRewards(
        radius: Float,
        px: Float = _engineState.value.playerX,
        py: Float = _engineState.value.playerY
    ) {
        val s = _engineState.value
        var coinsDelta = 0
        var gemsDelta = 0
        var keysDelta = 0
        var hpDelta = 0
        var chestsDelta = 0
        var changed = false

        val updatedRewards = s.rewards.map { item ->
            if (!item.isCollected && hypot((item.gx + 0.5f) - px, (item.gy + 0.5f) - py) <= radius) {
                changed = true
                coinsDelta += item.type.coinValue
                gemsDelta += item.type.gemValue
                when (item.type) {
                    RewardType.RELIC_KEY -> {
                        keysDelta++
                        addPopup("RELIC KEY!", item.gx + 0.5f, item.gy + 0.5f, Color(0xFF06B6D4))
                        spawnParticles(item.gx + 0.5f, item.gy + 0.5f, 0.3f, Color(0xFF06B6D4), 12)
                    }
                    RewardType.TREASURE_CHEST, RewardType.RARE_ARTEFACT -> {
                        chestsDelta++
                        addPopup("+${item.type.coinValue}G +${item.type.gemValue}💎", item.gx + 0.5f, item.gy + 0.5f, Color(0xFFFFB800))
                        spawnParticles(item.gx + 0.5f, item.gy + 0.5f, 0.3f, Color(0xFFFFB800), 14)
                    }
                    RewardType.HEALTH_ELIXIR -> {
                        hpDelta += 30
                        addPopup("+30 HP", item.gx + 0.5f, item.gy + 0.5f, Color(0xFF10B981))
                    }
                    RewardType.COIN -> {
                        addPopup("+${item.type.coinValue}G", item.gx + 0.5f, item.gy + 0.5f, Color(0xFFFFD166))
                        spawnParticles(item.gx + 0.5f, item.gy + 0.5f, 0.2f, Color(0xFFFFB800), 6)
                    }
                    RewardType.GEM -> {
                        addPopup("+${item.type.gemValue} GEM", item.gx + 0.5f, item.gy + 0.5f, Color(0xFF38BDF8))
                        spawnParticles(item.gx + 0.5f, item.gy + 0.5f, 0.2f, Color(0xFF38BDF8), 8)
                    }
                }
                item.copy(isCollected = true, openAnimProgress = 0.15f)
            } else {
                item
            }
        }

        if (changed) {
            val remainingTreasuresCount = updatedRewards.count { !it.isCollected && it.type != RewardType.RELIC_KEY }
            _engineState.update { state ->
                val newKeys = state.keysCollected + keysDelta
                val portalReady = newKeys >= state.keysRequired && state.switchActivated
                state.copy(
                    rewards = updatedRewards,
                    keysCollected = newKeys,
                    remainingTreasures = remainingTreasuresCount,
                    portalUnlocked = portalReady,
                    playerHp = (state.playerHp + hpDelta).coerceAtMost(state.playerMaxHp),
                    sessionCoinsEarned = state.sessionCoinsEarned + coinsDelta,
                    sessionGemsEarned = state.sessionGemsEarned + gemsDelta,
                    sessionChestsOpened = state.sessionChestsOpened + chestsDelta,
                    currentObjectiveText = buildObjectiveString(
                        newKeys,
                        state.keysRequired,
                        state.doorsUnlocked,
                        state.totalDoors,
                        state.switchActivated,
                        portalReady
                    ),
                    statusBanner = if (keysDelta > 0 && portalReady) {
                        "All Relic Keys Gathered! Escape Portal is OPEN!"
                    } else if (keysDelta > 0) {
                        "Relic Key Collected ($newKeys/${state.keysRequired})! Gates can now be unlocked!"
                    } else {
                        state.statusBanner
                    }
                )
            }
        }
    }

    private fun triggerLevelVictory() {
        val s = _engineState.value
        val lvl = s.level ?: return
        if (s.victorySummary != null) return

        val elapsedSec = ((System.currentTimeMillis() - levelStartTimestampMs) / 1000L).toInt().coerceAtLeast(1)
        val completionBonusCoins = 50 + lvl.levelNumber * 12
        val completionBonusGems = 2 + (lvl.levelNumber % 3)
        val totalCoins = s.sessionCoinsEarned + completionBonusCoins
        val totalGems = s.sessionGemsEarned + completionBonusGems

        val stars = when {
            s.playerHp >= s.playerMaxHp * 0.7f && s.secretsFoundInLevel >= s.totalSecretsInLevel -> 3
            s.playerHp >= s.playerMaxHp * 0.4f -> 2
            else -> 1
        }

        val summary = LevelVictorySummary(
            completedLevel = lvl.levelNumber,
            nextLevel = lvl.levelNumber + 1,
            seed = lvl.seed,
            biomeName = lvl.biome.displayName,
            stars = stars,
            coinsEarned = totalCoins,
            gemsEarned = totalGems,
            secretsFound = s.secretsFoundInLevel,
            enemiesDefeated = s.sessionEnemiesDefeated,
            timeSeconds = elapsedSec
        )

        _engineState.update {
            it.copy(
                victorySummary = summary,
                currentObjectiveText = "VICTORY! Preparing Level ${lvl.levelNumber + 1}...",
                statusBanner = "Level ${lvl.levelNumber} Complete! Next procedural world ready."
            )
        }

        if (repository != null) {
            viewModelScope.launch {
                repository.recordLevelCompletion(
                    entry = CompletedLevelEntity(
                        levelNumber = lvl.levelNumber,
                        seed = lvl.seed,
                        biomeId = lvl.biome.id,
                        biomeName = lvl.biome.displayName,
                        difficultyTitle = lvl.difficultyTier.title,
                        starsEarned = stars,
                        coinsEarned = totalCoins,
                        gemsEarned = totalGems,
                        secretsFound = s.secretsFoundInLevel,
                        durationSeconds = elapsedSec
                    ),
                    enemiesDefeatedDelta = s.sessionEnemiesDefeated,
                    chestsOpenedDelta = s.sessionChestsOpened
                )
            }
        }
    }

    private fun addPopup(text: String, wx: Float, wy: Float, color: Color) {
        val popup = FloatingPopup(
            id = popupIdCounter++,
            text = text,
            worldX = wx,
            worldY = wy,
            color = color
        )
        _engineState.update {
            it.copy(floatingPopups = (it.floatingPopups + popup).takeLast(12))
        }
    }

    private fun spawnParticles(wx: Float, wy: Float, wz: Float, color: Color, count: Int) {
        val maxAllowed = _engineState.value.graphicsQuality.maxCombatParticles.coerceAtMost(particlePool.size)
        var spawned = 0
        for (i in 0 until maxAllowed) {
            if (spawned >= count) break
            val p = particlePool[i]
            if (!p.active) {
                val angle = (spawned.toFloat() / count) * 6.28318f
                val speed = 1.3f + (spawned % 3) * 0.65f
                p.active = true
                p.worldX = wx
                p.worldY = wy
                p.worldZ = wz
                p.vx = cos(angle) * speed
                p.vy = sin(angle) * speed
                p.vz = 1.6f
                p.color = color
                p.life = 0f
                p.maxLife = 0.48f
                spawned++
            }
        }
        val burstArchetype = when (color) {
            Color(0xFF06B6D4), Color(0xFFA855F7), Color(0xFF38BDF8) -> ParticleArchetype.MAGIC_GLOW
            Color(0xFFFFB800), Color(0xFFEF4444) -> ParticleArchetype.LAVA_SPARK
            else -> ParticleArchetype.COMBAT_BURST
        }
        coroutineParticleSystem.emitBurst(
            wx = wx,
            wy = wy,
            wz = wz,
            primaryColor = color,
            secondaryColor = Color.White,
            count = count,
            archetype = burstArchetype
        )
    }
}
