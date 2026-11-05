package com.example.data

import com.example.engine.ProceduralLevelGenerator
import kotlinx.coroutines.flow.Flow

class GameRepository(private val dao: DarkEscapeDao) {

    val playerSaveFlow: Flow<PlayerSaveEntity?> = dao.observePlayerSave()
    val completedLevelsFlow: Flow<List<CompletedLevelEntity>> = dao.observeCompletedLevels()
    val missionsFlow: Flow<List<MissionAchievementEntity>> = dao.observeMissionsAndAchievements()

    suspend fun ensureInitialized(): PlayerSaveEntity {
        val existing = dao.getPlayerSaveOnce()
        val profile = if (existing == null) {
            val initialSeed = ProceduralLevelGenerator.computeSeedForLevel(1)
            val fresh = PlayerSaveEntity(currentLevelSeed = initialSeed)
            dao.upsertPlayerSave(fresh)
            fresh
        } else {
            existing
        }

        val existingMissions = dao.getMissionsOnce()
        if (existingMissions.isEmpty()) {
            dao.upsertMissions(defaultMissionsAndAchievements())
        }
        return profile
    }

    suspend fun updatePlayerSave(transform: (PlayerSaveEntity) -> PlayerSaveEntity): PlayerSaveEntity {
        val current = dao.getPlayerSaveOnce() ?: ensureInitialized()
        val updated = transform(current)
        dao.upsertPlayerSave(updated)
        return updated
    }

    suspend fun recordLevelCompletion(
        entry: CompletedLevelEntity,
        enemiesDefeatedDelta: Int,
        chestsOpenedDelta: Int
    ): PlayerSaveEntity {
        dao.insertCompletedLevel(entry)
        val current = dao.getPlayerSaveOnce() ?: ensureInitialized()
        val nextLevel = maxOf(current.currentLevel + 1, entry.levelNumber + 1)
        val nextSeed = ProceduralLevelGenerator.computeSeedForLevel(nextLevel)
        val updated = current.copy(
            currentLevel = nextLevel,
            highestLevelReached = maxOf(current.highestLevelReached, nextLevel),
            coins = current.coins + entry.coinsEarned,
            gems = current.gems + entry.gemsEarned,
            totalLevelsCompleted = current.totalLevelsCompleted + 1,
            totalEnemiesDefeated = current.totalEnemiesDefeated + enemiesDefeatedDelta,
            totalSecretsDiscovered = current.totalSecretsDiscovered + entry.secretsFound,
            totalChestsOpened = current.totalChestsOpened + chestsOpenedDelta,
            currentLevelSeed = nextSeed
        )
        dao.upsertPlayerSave(updated)
        incrementMissionProgress(
            levelsDelta = 1,
            enemiesDelta = enemiesDefeatedDelta,
            secretsDelta = entry.secretsFound,
            chestsDelta = chestsOpenedDelta,
            highestLevel = updated.highestLevelReached
        )
        return updated
    }

    private suspend fun incrementMissionProgress(
        levelsDelta: Int,
        enemiesDelta: Int,
        secretsDelta: Int,
        chestsDelta: Int,
        highestLevel: Int
    ) {
        val currentMissions = dao.getMissionsOnce()
        val updated = currentMissions.map { m ->
            val newProg = when (m.id) {
                "daily_escape_3" -> m.currentProgress + levelsDelta
                "daily_slayer_5" -> m.currentProgress + enemiesDelta
                "daily_secrets_2" -> m.currentProgress + secretsDelta
                "daily_chests_4" -> m.currentProgress + chestsDelta
                "ach_level_10" -> maxOf(m.currentProgress, highestLevel)
                "ach_level_50" -> maxOf(m.currentProgress, highestLevel)
                "ach_secrets_10" -> m.currentProgress + secretsDelta
                "ach_slayer_30" -> m.currentProgress + enemiesDelta
                else -> m.currentProgress
            }
            m.copy(currentProgress = newProg.coerceAtMost(m.targetProgress))
        }
        dao.upsertMissions(updated)
    }

    suspend fun claimMissionReward(missionId: String) {
        val missions = dao.getMissionsOnce()
        val target = missions.find { it.id == missionId } ?: return
        if (target.isClaimed || target.currentProgress < target.targetProgress) return

        dao.upsertMission(target.copy(isClaimed = true))
        updatePlayerSave { save ->
            save.copy(
                coins = save.coins + target.rewardCoins,
                gems = save.gems + target.rewardGems
            )
        }
    }

    private fun defaultMissionsAndAchievements(): List<MissionAchievementEntity> = listOf(
        MissionAchievementEntity(
            id = "daily_escape_3",
            title = "Daily Dungeon Runner",
            description = "Escape 3 procedurally generated levels.",
            isDailyMission = true,
            targetProgress = 3,
            currentProgress = 0,
            rewardCoins = 150,
            rewardGems = 5
        ),
        MissionAchievementEntity(
            id = "daily_slayer_5",
            title = "Daily Guardian Purge",
            description = "Defeat 5 biome sentinels in combat.",
            isDailyMission = true,
            targetProgress = 5,
            currentProgress = 0,
            rewardCoins = 180,
            rewardGems = 6
        ),
        MissionAchievementEntity(
            id = "daily_secrets_2",
            title = "Daily Secret Seeker",
            description = "Breach 2 hidden cracked walls and discover secret vaults.",
            isDailyMission = true,
            targetProgress = 2,
            currentProgress = 0,
            rewardCoins = 220,
            rewardGems = 8
        ),
        MissionAchievementEntity(
            id = "daily_chests_4",
            title = "Daily Treasure Hunter",
            description = "Collect 4 Royal Treasure Chests or Ancient Relics.",
            isDailyMission = true,
            targetProgress = 4,
            currentProgress = 0,
            rewardCoins = 160,
            rewardGems = 5
        ),
        MissionAchievementEntity(
            id = "ach_level_10",
            title = "Initiate Conqueror",
            description = "Reach Level 10 in the Infinite Escape Engine.",
            isDailyMission = false,
            targetProgress = 10,
            currentProgress = 1,
            rewardCoins = 400,
            rewardGems = 15
        ),
        MissionAchievementEntity(
            id = "ach_level_50",
            title = "Master of Biomes",
            description = "Reach Level 50 across rotating 2.5D worlds.",
            isDailyMission = false,
            targetProgress = 50,
            currentProgress = 1,
            rewardCoins = 1200,
            rewardGems = 45
        ),
        MissionAchievementEntity(
            id = "ach_secrets_10",
            title = "Architect of Secrets",
            description = "Uncover 10 hidden chambers across generated levels.",
            isDailyMission = false,
            targetProgress = 10,
            currentProgress = 0,
            rewardCoins = 600,
            rewardGems = 25
        ),
        MissionAchievementEntity(
            id = "ach_slayer_30",
            title = "Bane of the Abyss",
            description = "Defeat 30 dungeon guardians with your special attacks.",
            isDailyMission = false,
            targetProgress = 30,
            currentProgress = 0,
            rewardCoins = 800,
            rewardGems = 30
        )
    )
}
