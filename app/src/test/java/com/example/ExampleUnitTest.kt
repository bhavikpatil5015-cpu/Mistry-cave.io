package com.example

import android.app.Application
import com.example.engine.ProceduralLevelGenerator
import com.example.model.BiomeType
import com.example.model.DifficultyTier
import com.example.model.IsoTile
import com.example.model.RewardType
import com.example.model.TileType
import com.example.viewmodel.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ExampleUnitTest {

    @Test
    fun verifyTwentyConsecutiveGeneratedLevelsAreSolvableAndUnique() {
        val report = ProceduralLevelGenerator.verifyConsecutiveLevels(startLevel = 1, count = 20)
        assertEquals(20, report.levelsTested)
        assertTrue("Every generated level must be solvable via A* pathfinding", report.allSolvable)
        assertEquals("Every consecutive generated level must have a unique layout", 20, report.uniqueLayoutsCount)
        assertTrue("Multiple biomes must be covered across 20 levels", report.biomesCovered >= 6)
    }

    @Test
    fun verifyLockedDoorsKeysAndCollectiblesAreGeneratedValidly() {
        val lvl = ProceduralLevelGenerator.generateLevel(levelNumber = 1)
        assertTrue("Level must have at least 1 key", lvl.keyPoints.isNotEmpty())
        assertTrue("Level must have locked door(s)", lvl.lockedDoorPoints.isNotEmpty())
        for (doorPt in lvl.lockedDoorPoints) {
            assertEquals(TileType.LOCKED_DOOR, lvl.tiles[doorPt.x][doorPt.y].type)
            assertFalse("Locked door must block movement until unlocked", lvl.tiles[doorPt.x][doorPt.y].type.isWalkable)
        }
        assertTrue("Unlocked door must be walkable", TileType.UNLOCKED_DOOR.isWalkable)
        assertTrue("Level must spawn collectible coins/chests/keys", lvl.initialRewards.size >= 3)
        assertTrue("Level must spawn coins or chests", lvl.initialRewards.any { it.type != RewardType.RELIC_KEY })
    }

    @Test
    fun verifyDifficultyTiersScaleAcrossAllRanges() {
        val tierLevels = listOf(1, 15, 40, 80, 200, 550)
        for (lvl in tierLevels) {
            val generated = ProceduralLevelGenerator.generateLevel(lvl)
            assertNotNull(generated)
            assertEquals(lvl, generated.levelNumber)
            assertTrue(generated.keyPoints.isNotEmpty())
            assertTrue(generated.verifiedSolutionPath.size >= 2)
            assertEquals(generated.startPoint, generated.verifiedSolutionPath.first())
            assertEquals(generated.exitPoint, generated.verifiedSolutionPath.last())
        }
        assertEquals(DifficultyTier.TUTORIAL_1_10, DifficultyTier.forLevel(5))
        assertEquals(DifficultyTier.TRAPS_ENEMIES_11_30, DifficultyTier.forLevel(20))
        assertEquals(DifficultyTier.ADVANCED_31_60, DifficultyTier.forLevel(45))
        assertEquals(DifficultyTier.COMPLEX_61_100, DifficultyTier.forLevel(85))
        assertEquals(DifficultyTier.EXPERT_101_500, DifficultyTier.forLevel(250))
        assertEquals(DifficultyTier.ENDLESS_501_PLUS, DifficultyTier.forLevel(600))
    }

    @Test
    fun verifyAllEightBiomesGenerateValidMaps() {
        for ((index, biome) in BiomeType.entries.withIndex()) {
            val lvl = ProceduralLevelGenerator.generateLevel(
                levelNumber = index * 3 + 1,
                explicitBiome = biome
            )
            assertEquals(biome, lvl.biome)
            assertTrue(lvl.verifiedSolutionPath.isNotEmpty())
            assertTrue(lvl.initialRewards.isNotEmpty())
        }
    }

    @Test
    fun verifyAllGameplayControlsEnemyAiTrapsDoorsAndNextLevelGeneration() {
        val vm = GameViewModel(Application(), injectedRepository = null)
        val level = vm.engineState.value.level!!
        val startX = vm.engineState.value.playerX
        val startY = vm.engineState.value.playerY

        // 1. Test 360-degree Joystick Movement & Smooth Camera Lerp
        val nextStep = level.verifiedSolutionPath.getOrNull(1) ?: level.startPoint
        val stepDx = (nextStep.x + 0.5f) - startX
        val stepDy = (nextStep.y + 0.5f) - startY
        vm.updateJoystick(stepDx - stepDy, stepDx + stepDy)
        repeat(6) { vm.stepPhysics(0.03f) }
        vm.updateJoystick(0f, 0f)

        val movedState = vm.engineState.value
        assertTrue(
            "Player must move smoothly in 360 directions",
            hypot(movedState.playerX - startX, movedState.playerY - startY) > 0.05f
        )

        // 2. Test Jump & Dash Controls
        vm.onJumpPressed()
        assertTrue("Jump must set positive vertical velocity", vm.engineState.value.playerVz > 0f)
        vm.stepPhysics(0.04f)
        assertTrue("Player Z height must rise during jump", vm.engineState.value.playerZ > 0f)
        repeat(35) { vm.stepPhysics(0.04f) }
        assertEquals("Player must land back on ground (Z=0)", 0f, vm.engineState.value.playerZ, 0.01f)

        vm.onDodgeDashPressed()
        assertTrue("Dash must activate dashTimer", vm.engineState.value.dashTimer > 0f)
        assertTrue("Dash must spawn afterimage ghosts", vm.engineState.value.dashGhosts.isNotEmpty())

        // 3. Test Wall Collision Detection
        assertFalse(
            "Border wall tile (0,0) must block player collision",
            vm.canOccupyPosition(level, 0.5f, 0.5f, allowJumpable = false)
        )

        // 4. Test Coin & Treasure Collection
        val treasureItem = vm.engineState.value.rewards.first { it.type != RewardType.RELIC_KEY }
        val treasuresBefore = vm.engineState.value.remainingTreasures
        vm.teleportPlayerForTest(treasureItem.gx + 0.5f, treasureItem.gy + 0.5f)
        vm.stepPhysics(0.02f)
        assertEquals(
            "Collecting a coin/treasure must decrement remainingTreasures",
            treasuresBefore - 1,
            vm.engineState.value.remainingTreasures
        )

        // 5. Test Enemy Detection, Chase & Attack Damage
        val enemy = vm.engineState.value.enemies.first()
        val initialEnemyHp = enemy.hp
        val initialEnemyX = enemy.x
        val initialEnemyY = enemy.y
        vm.teleportPlayerForTest(enemy.x + 1.0f, enemy.y)
        vm.stepPhysics(0.04f)
        val alertedEnemy = vm.engineState.value.enemies.first { it.id == enemy.id }
        assertTrue("Enemy must detect player and become alerted", alertedEnemy.isAlerted)
        assertTrue(
            "Enemy must move/chase toward the player",
            hypot(alertedEnemy.x - initialEnemyX, alertedEnemy.y - initialEnemyY) > 0.001f
        )

        vm.onAttackPressed()
        val struckEnemy = vm.engineState.value.enemies.find { it.id == enemy.id }
        if (struckEnemy != null) {
            assertTrue("Player Strike attack must damage the enemy", struckEnemy.hp < initialEnemyHp)
        }

        // 6. Test Traps Causing Actual Damage
        val trapGx = level.startPoint.x
        val trapGy = level.startPoint.y
        level.tiles[trapGx][trapGy] = IsoTile(trapGx, trapGy, TileType.LIQUID_HAZARD)
        val hpBeforeTrap = vm.engineState.value.playerHp
        vm.teleportPlayerForTest(trapGx + 0.5f, trapGy + 0.5f)
        vm.stepPhysics(0.02f)
        assertTrue("Trap/Hazard tile must deal real HP damage", vm.engineState.value.playerHp < hpBeforeTrap)
        level.tiles[trapGx][trapGy] = IsoTile(trapGx, trapGy, TileType.START_PAD)

        // 7. Test Key Collection Unlocking Locked Doors & Exit Portal Victory
        for (kp in level.keyPoints) {
            vm.teleportPlayerForTest(kp.x + 0.5f, kp.y + 0.5f)
            vm.stepPhysics(0.02f)
        }
        assertEquals(
            "All required keys must be collected",
            level.keyPoints.size,
            vm.engineState.value.keysCollected
        )

        val doorPt = level.lockedDoorPoints.first()
        assertEquals(TileType.LOCKED_DOOR, level.tiles[doorPt.x][doorPt.y].type)
        vm.teleportPlayerForTest(doorPt.x + 0.5f, doorPt.y + 0.5f)
        vm.onInteractPressed()
        assertEquals(
            "Relic Key must unlock LOCKED_DOOR into UNLOCKED_DOOR",
            TileType.UNLOCKED_DOOR,
            level.tiles[doorPt.x][doorPt.y].type
        )

        assertTrue("Exit portal must now be unlocked", vm.engineState.value.portalUnlocked)
        vm.teleportPlayerForTest(level.exitPoint.x + 0.5f, level.exitPoint.y + 0.5f)
        vm.stepPhysics(0.02f)
        assertNotNull("Stepping onto unlocked Exit Portal must trigger Victory Summary", vm.engineState.value.victorySummary)
        assertEquals(1, vm.engineState.value.victorySummary?.completedLevel)

        // 8. Test Automatic Next-Level Generation after Victory
        val lvl1Seed = level.seed
        vm.advanceToNextGeneratedLevel()
        val lvl2 = vm.engineState.value.level
        assertNotNull(lvl2)
        assertEquals("Next level must be Level 2", 2, lvl2?.levelNumber)
        assertNotEquals("Level 2 must have a unique procedural seed", lvl1Seed, lvl2?.seed)
    }

    @Test
    fun verifyRealTimeGraphicsQualityDestructiblePropsAndAnimations() {
        val vm = GameViewModel(Application(), injectedRepository = null)

        // 1. Test GraphicsQuality modes (ULTRA -> HIGH -> PERFORMANCE -> ULTRA)
        assertEquals(com.example.model.GraphicsQuality.ULTRA, vm.engineState.value.graphicsQuality)
        vm.cycleGraphicsQuality()
        assertEquals(com.example.model.GraphicsQuality.HIGH, vm.engineState.value.graphicsQuality)
        vm.cycleGraphicsQuality()
        assertEquals(com.example.model.GraphicsQuality.PERFORMANCE, vm.engineState.value.graphicsQuality)
        vm.cycleGraphicsQuality()
        assertEquals(com.example.model.GraphicsQuality.ULTRA, vm.engineState.value.graphicsQuality)

        // 2. Test Dynamic Camera Zoom
        val baseZoom = vm.engineState.value.cameraZoom
        vm.adjustCameraZoom(0.25f)
        assertTrue("Camera zoom should increase", vm.engineState.value.cameraZoom > baseZoom)
        vm.adjustCameraZoom(-0.5f)
        assertTrue("Camera zoom should decrease", vm.engineState.value.cameraZoom < baseZoom)

        // 3. Test Destructible Environmental Props & Combat Shattering
        val props = vm.engineState.value.props
        assertTrue("Generated level must contain destructible biome props", props.isNotEmpty())
        val firstProp = props.first()
        vm.teleportPlayerForTest(firstProp.gx + 0.5f, firstProp.gy + 0.5f)
        vm.onAttackPressed()
        val updatedProp = vm.engineState.value.props.first { it.id == firstProp.id }
        assertTrue("Destructible prop must take damage or shatter on player strike", updatedProp.hp < firstProp.hp || updatedProp.isDestroyed)
        assertEquals(com.example.model.CharacterAnimState.ATTACK, vm.engineState.value.playerAnimState)
    }

    @Test
    fun verifyCoroutineParticleSystemSnowLavaAndMagicEffects() {
        val vm = GameViewModel(Application(), injectedRepository = null)
        val ps = vm.coroutineParticleSystem
        assertTrue("Particle system must warm up active particles on level load", ps.activeCount > 0)

        // 1. Test Falling Snow Preset & Downward Kinematics
        vm.setParticlePreset(com.example.engine.ParticleEffectPreset.FALLING_SNOW)
        assertEquals(com.example.engine.ParticleEffectPreset.FALLING_SNOW, vm.engineState.value.particlePreset)
        repeat(8) { ps.stepSimulation(0.04f) }
        val snowCount = (0 until ps.maxCapacity).count { ps.type[it] == com.example.engine.ParticleArchetype.SNOWFLAKE }
        assertTrue("Falling Snow preset must spawn active SNOWFLAKE particles", snowCount > 10)

        // 2. Test Rising Lava Sparks Preset & Upward Thermal Buoyancy
        vm.setParticlePreset(com.example.engine.ParticleEffectPreset.RISING_LAVA_SPARKS)
        repeat(10) { ps.stepSimulation(0.04f) }
        val lavaIndices = (0 until ps.maxCapacity).filter { ps.type[it] == com.example.engine.ParticleArchetype.LAVA_SPARK }
        assertTrue("Rising Lava Sparks preset must spawn active LAVA_SPARK particles", lavaIndices.isNotEmpty())
        val sampleLavaIdx = lavaIndices.first()
        assertTrue("Lava sparks must have positive upward vertical velocity (vz > 0)", ps.vz[sampleLavaIdx] > 0f)

        // 3. Test Magical Glowing Particles Preset
        vm.setParticlePreset(com.example.engine.ParticleEffectPreset.MAGICAL_GLOW)
        repeat(10) { ps.stepSimulation(0.04f) }
        val magicCount = (0 until ps.maxCapacity).count { ps.type[it] == com.example.engine.ParticleArchetype.MAGIC_GLOW }
        assertTrue("Magical Glow preset must spawn active MAGIC_GLOW particles", magicCount > 0)

        // 4. Test Elemental Convergence & Coroutine Background Loop
        vm.setParticlePreset(com.example.engine.ParticleEffectPreset.ELEMENTAL_CONVERGENCE)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val job = ps.startCoroutineSimulation(scope)
        assertTrue("Coroutine simulation job must be active", job.isActive)
        Thread.sleep(60)
        ps.stopCoroutineSimulation()
        assertFalse("Coroutine simulation job must stop cleanly", job.isActive)
        assertTrue("Active particle count must remain populated", ps.activeCount > 20)
    }
}


