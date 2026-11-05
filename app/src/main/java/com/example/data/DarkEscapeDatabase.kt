package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "player_save")
data class PlayerSaveEntity(
    @PrimaryKey val id: Int = 1,
    val currentLevel: Int = 1,
    val highestLevelReached: Int = 1,
    val coins: Int = 250,
    val gems: Int = 20,
    val selectedHeroId: String = "kaelen",
    val selectedSkinId: String = "default_skin",
    val unlockedHeroIdsCsv: String = "kaelen",
    val unlockedSkinIdsCsv: String = "default_skin",
    val totalLevelsCompleted: Int = 0,
    val totalEnemiesDefeated: Int = 0,
    val totalSecretsDiscovered: Int = 0,
    val totalChestsOpened: Int = 0,
    val currentLevelSeed: Long = 0L
)

@Entity(tableName = "completed_levels")
data class CompletedLevelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val levelNumber: Int,
    val seed: Long,
    val biomeId: String,
    val biomeName: String,
    val difficultyTitle: String,
    val starsEarned: Int,
    val coinsEarned: Int,
    val gemsEarned: Int,
    val secretsFound: Int,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "missions_achievements")
data class MissionAchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val isDailyMission: Boolean,
    val targetProgress: Int,
    val currentProgress: Int,
    val rewardCoins: Int,
    val rewardGems: Int,
    val isClaimed: Boolean = false
)

@Dao
interface DarkEscapeDao {
    @Query("SELECT * FROM player_save WHERE id = 1")
    fun observePlayerSave(): Flow<PlayerSaveEntity?>

    @Query("SELECT * FROM player_save WHERE id = 1")
    suspend fun getPlayerSaveOnce(): PlayerSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayerSave(save: PlayerSaveEntity)

    @Query("SELECT * FROM completed_levels ORDER BY levelNumber DESC, timestamp DESC LIMIT 100")
    fun observeCompletedLevels(): Flow<List<CompletedLevelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedLevel(entry: CompletedLevelEntity)

    @Query("SELECT * FROM missions_achievements ORDER BY isDailyMission DESC, id ASC")
    fun observeMissionsAndAchievements(): Flow<List<MissionAchievementEntity>>

    @Query("SELECT * FROM missions_achievements")
    suspend fun getMissionsOnce(): List<MissionAchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMissions(items: List<MissionAchievementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMission(item: MissionAchievementEntity)
}

@Database(
    entities = [
        PlayerSaveEntity::class,
        CompletedLevelEntity::class,
        MissionAchievementEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DarkEscapeDatabase : RoomDatabase() {
    abstract fun dao(): DarkEscapeDao

    companion object {
        @Volatile
        private var INSTANCE: DarkEscapeDatabase? = null

        fun getInstance(context: Context): DarkEscapeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DarkEscapeDatabase::class.java,
                    "dark_escape_25d.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
