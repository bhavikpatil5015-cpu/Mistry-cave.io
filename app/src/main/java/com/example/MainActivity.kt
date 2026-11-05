package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.BiomesCodexScreen
import com.example.ui.GameHudAndControlsOverlay
import com.example.ui.HeroesArmoryScreen
import com.example.ui.IsometricGameCanvas
import com.example.ui.MissionsAchievementsScreen
import com.example.ui.ProceduralGeneratorLabScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameTab
import com.example.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DarkEscapeApp()
            }
        }
    }
}

@Composable
fun DarkEscapeApp(
    viewModel: GameViewModel = viewModel()
) {
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val playerSave by viewModel.playerSave.collectAsStateWithLifecycle()
    val completedLevels by viewModel.completedLevels.collectAsStateWithLifecycle()
    val missions by viewModel.missions.collectAsStateWithLifecycle()

    if (engineState.activeTab != GameTab.PLAY) {
        BackHandler {
            viewModel.selectTab(GameTab.PLAY)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0E131F),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = engineState.activeTab == GameTab.PLAY,
                    onClick = { viewModel.selectTab(GameTab.PLAY) },
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = "Adventure") },
                    label = { Text("Escape") },
                    colors = navItemColors(),
                    modifier = Modifier.testTag("nav_tab_play")
                )
                NavigationBarItem(
                    selected = engineState.activeTab == GameTab.HEROES,
                    onClick = { viewModel.selectTab(GameTab.HEROES) },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Heroes") },
                    label = { Text("Heroes") },
                    colors = navItemColors(),
                    modifier = Modifier.testTag("nav_tab_heroes")
                )
                NavigationBarItem(
                    selected = engineState.activeTab == GameTab.MISSIONS,
                    onClick = { viewModel.selectTab(GameTab.MISSIONS) },
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Missions") },
                    label = { Text("Missions") },
                    colors = navItemColors(),
                    modifier = Modifier.testTag("nav_tab_missions")
                )
                NavigationBarItem(
                    selected = engineState.activeTab == GameTab.GENERATOR_LAB,
                    onClick = { viewModel.selectTab(GameTab.GENERATOR_LAB) },
                    icon = { Icon(Icons.Default.Science, contentDescription = "Generator Lab") },
                    label = { Text("Gen Lab") },
                    colors = navItemColors(),
                    modifier = Modifier.testTag("nav_tab_generator")
                )
                NavigationBarItem(
                    selected = engineState.activeTab == GameTab.BIOMES,
                    onClick = { viewModel.selectTab(GameTab.BIOMES) },
                    icon = { Icon(Icons.Default.Landscape, contentDescription = "Biomes") },
                    label = { Text("Biomes") },
                    colors = navItemColors(),
                    modifier = Modifier.testTag("nav_tab_biomes")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (engineState.activeTab) {
                GameTab.PLAY -> {
                    val hero = viewModel.currentHero()
                    val skin = viewModel.currentSkin()
                    IsometricGameCanvas(
                        state = engineState,
                        hero = hero,
                        skin = skin,
                        particles = viewModel.particlePool,
                        weatherMotes = viewModel.weatherPool,
                        onStepPhysics = viewModel::stepPhysics,
                        onPinchZoom = viewModel::adjustCameraZoom
                    )
                    GameHudAndControlsOverlay(
                        state = engineState,
                        playerSave = playerSave,
                        onJoystickChanged = viewModel::updateJoystick,
                        onNudgeDirection = viewModel::nudgeDirection,
                        onJump = viewModel::onJumpPressed,
                        onDodge = viewModel::onDodgeDashPressed,
                        onAttack = viewModel::onAttackPressed,
                        onInteract = viewModel::onInteractPressed,
                        onToggleHintPath = viewModel::toggleHintPath,
                        onCycleGraphicsQuality = viewModel::cycleGraphicsQuality,
                        onAdjustZoom = viewModel::adjustCameraZoom,
                        onNextLevel = viewModel::advanceToNextGeneratedLevel,
                        onRetryLevel = viewModel::retryCurrentLevel
                    )
                }

                GameTab.HEROES -> {
                    HeroesArmoryScreen(
                        playerSave = playerSave,
                        onSelectOrUnlockHero = viewModel::selectOrUnlockHero,
                        onSelectOrUnlockSkin = viewModel::selectOrUnlockSkin
                    )
                }

                GameTab.MISSIONS -> {
                    MissionsAchievementsScreen(
                        missions = missions,
                        playerSave = playerSave,
                        onClaimMission = viewModel::claimMission
                    )
                }

                GameTab.GENERATOR_LAB -> {
                    ProceduralGeneratorLabScreen(
                        currentLevel = engineState.level,
                        completedLevels = completedLevels,
                        batchReport = engineState.batchReport,
                        onRunBatchVerification = viewModel::runGeneratorBatchVerification,
                        onJumpToLevel = { lvl, seed ->
                            viewModel.loadOrGenerateLevel(levelNumber = lvl, explicitSeed = seed)
                            viewModel.selectTab(GameTab.PLAY)
                        }
                    )
                }

                GameTab.BIOMES -> {
                    BiomesCodexScreen(
                        onPlayBiomeLevel = { biome ->
                            val currentLvl = engineState.level?.levelNumber ?: 1
                            viewModel.loadOrGenerateLevel(
                                levelNumber = currentLvl,
                                explicitBiome = biome
                            )
                            viewModel.selectTab(GameTab.PLAY)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFF0E131F),
    selectedTextColor = Color(0xFFFFB800),
    indicatorColor = Color(0xFFFFB800),
    unselectedIconColor = Color(0xFF94A3B8),
    unselectedTextColor = Color(0xFF94A3B8)
)
