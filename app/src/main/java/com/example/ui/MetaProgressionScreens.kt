package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CompletedLevelEntity
import com.example.data.MissionAchievementEntity
import com.example.data.PlayerSaveEntity
import com.example.engine.ProceduralLevelGenerator
import com.example.model.BiomeType
import com.example.model.CharacterSkin
import com.example.model.DifficultyTier
import com.example.model.GeneratedLevel
import com.example.model.HeroCatalog
import com.example.model.HeroCharacter

@Composable
fun HeroesArmoryScreen(
    playerSave: PlayerSaveEntity?,
    onSelectOrUnlockHero: (HeroCharacter) -> Unit,
    onSelectOrUnlockSkin: (CharacterSkin) -> Unit,
    modifier: Modifier = Modifier
) {
    val unlockedHeroes = (playerSave?.unlockedHeroIdsCsv ?: "kaelen").split(",").toSet()
    val unlockedSkins = (playerSave?.unlockedSkinIdsCsv ?: "default_skin").split(",").toSet()
    val selectedHeroId = playerSave?.selectedHeroId ?: "kaelen"
    val selectedSkinId = playerSave?.selectedSkinId ?: "default_skin"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E131F))
            .padding(16.dp)
            .testTag("heroes_armory_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "HEROES & 2.5D SKINS ARMORY",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFFB800)
            )
            Text(
                text = "Coins: ${playerSave?.coins ?: 250} • Gems: ${playerSave?.gems ?: 20}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF38BDF8)
            )
        }

        items(HeroCatalog.heroes, key = { it.id }) { hero ->
            val isUnlocked = hero.id in unlockedHeroes
            val isSelected = hero.id == selectedHeroId
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFB800) else Color(0xFF334155),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(hero.primaryColor)
                                .border(2.dp, hero.capeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = hero.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${hero.title} • Ability: ${hero.abilityName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "HP ${hero.maxHp} • ATK ${hero.attackDamage} • SPD ${(hero.speedMultiplier * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    Button(
                        onClick = { onSelectOrUnlockHero(hero) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isSelected -> Color(0xFF10B981)
                                isUnlocked -> Color(0xFF38BDF8)
                                else -> Color(0xFFFFB800)
                            }
                        ),
                        modifier = Modifier.testTag("hero_btn_${hero.id}")
                    ) {
                        Text(
                            text = when {
                                isSelected -> "Equipped"
                                isUnlocked -> "Equip"
                                else -> "${hero.costCoins}G / ${hero.costGems}💎"
                            },
                            color = Color(0xFF0E131F)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "CHARACTER ARMOR SKINS",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFFFB800)
            )
        }

        items(HeroCatalog.skins, key = { it.id }) { skin ->
            val isUnlocked = skin.id in unlockedSkins
            val isSelected = skin.id == selectedSkinId
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(skin.armorColor)
                                .border(2.dp, skin.trimColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = skin.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { onSelectOrUnlockSkin(skin) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF10B981) else Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.testTag("skin_btn_${skin.id}")
                    ) {
                        Text(
                            text = when {
                                isSelected -> "Active"
                                isUnlocked -> "Use Skin"
                                else -> "${skin.costGems} Gems"
                            },
                            color = Color(0xFF0E131F)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MissionsAchievementsScreen(
    missions: List<MissionAchievementEntity>,
    playerSave: PlayerSaveEntity?,
    onClaimMission: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E131F))
            .padding(16.dp)
            .testTag("missions_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "DAILY MISSIONS & ACHIEVEMENTS",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFFB800)
            )
            Text(
                text = "Completed Levels: ${playerSave?.totalLevelsCompleted ?: 0} • Secrets: ${playerSave?.totalSecretsDiscovered ?: 0} • Guardians Defeated: ${playerSave?.totalEnemiesDefeated ?: 0}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )
        }

        items(missions, key = { it.id }) { m ->
            val completed = m.currentProgress >= m.targetProgress
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (m.isDailyMission) "DAILY • ${m.title}" else "ACHIEVEMENT • ${m.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (m.isDailyMission) Color(0xFF38BDF8) else Color(0xFFFFB800)
                            )
                            Text(
                                text = m.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                        if (m.isClaimed) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Claimed",
                                tint = Color(0xFF10B981)
                            )
                        } else {
                            Button(
                                onClick = { onClaimMission(m.id) },
                                enabled = completed,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB800)
                                ),
                                modifier = Modifier.testTag("claim_mission_${m.id}")
                            ) {
                                Text(
                                    text = if (completed) "Claim +${m.rewardCoins}G" else "${m.currentProgress}/${m.targetProgress}",
                                    color = Color(0xFF0E131F)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (m.currentProgress.toFloat() / m.targetProgress.toFloat()).coerceIn(0f, 1f) },
                        color = if (completed) Color(0xFF10B981) else Color(0xFF38BDF8),
                        trackColor = Color(0xFF334155),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun ProceduralGeneratorLabScreen(
    currentLevel: GeneratedLevel?,
    completedLevels: List<CompletedLevelEntity>,
    batchReport: ProceduralLevelGenerator.BatchVerificationReport?,
    onRunBatchVerification: (Int) -> Unit,
    onJumpToLevel: (Int, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E131F))
            .padding(16.dp)
            .testTag("generator_lab_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "INFINITE PROCEDURAL ENGINE LAB",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFFB800)
            )
            Text(
                text = "Inspect real-time seed telemetry, A* solvability verification, adaptive difficulty tiers, and test consecutive generated levels.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )
        }

        if (currentLevel != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF06B6D4), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ACTIVE LEVEL #${currentLevel.levelNumber} TELEMETRY",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF06B6D4)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Deterministic Seed: ${currentLevel.seed}\n" +
                                "• Environment: ${currentLevel.biome.displayName}\n" +
                                "• Adaptive Tier: ${currentLevel.difficultyTier.title} (${currentLevel.difficultyTier.levelRangeLabel})\n" +
                                "• Grid Dimensions: ${currentLevel.width}x${currentLevel.height} Isometric Blocks\n" +
                                "• Verified A* Solution Path: ${currentLevel.verifiedSolutionPath.size} Steps (${currentLevel.validationPasses} Pass)\n" +
                                "• Secret Vaults Carved: ${currentLevel.secretRoomPoints.size} • Enemies: ${currentLevel.initialEnemies.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Batch Verification Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Solvability Verifier",
                            tint = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONSECUTIVE LEVEL SOLVABILITY TESTER",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF10B981)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onRunBatchVerification(15) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_consecutive_levels_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Verify",
                            tint = Color(0xFF0E131F)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate & Validate 15 Consecutive Levels",
                            color = Color(0xFF0E131F)
                        )
                    }

                    if (batchReport != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF0E131F),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_verification_report")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "BATCH REPORT (Levels ${batchReport.startLevel}–${batchReport.startLevel + batchReport.levelsTested - 1})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFFFFB800)
                                )
                                Text(
                                    text = "100% Solvable: ${if (batchReport.allSolvable) "YES (Verified by A*)" else "NO"}\n" +
                                        "Unique Non-Repetitive Layouts: ${batchReport.uniqueLayoutsCount}/${batchReport.levelsTested}\n" +
                                        "Biomes Tested: ${batchReport.biomesCovered} • Avg Path: ${batchReport.averagePathLength} tiles\n" +
                                        "Total Generation Time: ${batchReport.totalTimeMs} ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Adaptive Difficulty Tier Preview Jumpers
        item {
            Text(
                text = "TEST ADAPTIVE DIFFICULTY TIERS (LEVELS 1 TO 501+)",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFFFB800)
            )
        }

        val sampleTierLevels = listOf(
            Pair(1, DifficultyTier.TUTORIAL_1_10),
            Pair(15, DifficultyTier.TRAPS_ENEMIES_11_30),
            Pair(35, DifficultyTier.ADVANCED_31_60),
            Pair(75, DifficultyTier.COMPLEX_61_100),
            Pair(150, DifficultyTier.EXPERT_101_500),
            Pair(505, DifficultyTier.ENDLESS_501_PLUS)
        )

        items(sampleTierLevels, key = { it.first }) { (sampleLvl, tier) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${tier.levelRangeLabel} • ${tier.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = tier.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onJumpToLevel(sampleLvl, null) },
                        modifier = Modifier.testTag("jump_tier_lvl_$sampleLvl")
                    ) {
                        Text("Play Lvl $sampleLvl", color = Color(0xFFFFB800))
                    }
                }
            }
        }

        if (completedLevels.isNotEmpty()) {
            item {
                Text(
                    text = "COMPLETED LEVEL SEED ARCHIVE",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFFFB800)
                )
            }
            items(completedLevels, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Level ${item.levelNumber} • ${item.biomeName} (${item.starsEarned}★)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Seed #${item.seed.toString(16).uppercase()} • ${item.durationSeconds}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Button(
                            onClick = { onJumpToLevel(item.levelNumber, item.seed) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("Replay Seed", color = Color(0xFF0E131F))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BiomesCodexScreen(
    onPlayBiomeLevel: (BiomeType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E131F))
            .padding(16.dp)
            .testTag("biomes_codex_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "8 INFINITE 2.5D ENVIRONMENTS",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFFB800)
            )
            Text(
                text = "Environments rotate automatically every 3 levels, or launch directly into any biome below.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )
        }

        items(BiomeType.entries, key = { it.id }) { biome ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, biome.floorTop, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(biome.floorTop)
                                .border(2.dp, biome.wallTop, RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = biome.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${biome.subtitle} • Sentinel: ${biome.enemyName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = biome.accentColor
                            )
                            Text(
                                text = biome.mechanicDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onPlayBiomeLevel(biome) },
                        colors = ButtonDefaults.buttonColors(containerColor = biome.floorTop),
                        modifier = Modifier.testTag("play_biome_${biome.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Generate",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
