package com.example.model

import androidx.compose.ui.graphics.Color

enum class GraphicsQuality(
    val label: String,
    val description: String,
    val resolutionScale: Float,
    val textureMipResolution: Int,
    val enableAmbientOcclusion: Boolean,
    val enableDynamicPointLights: Boolean,
    val enableSoftShadows: Boolean,
    val enableSurfaceDetailPass: Boolean,
    val enableAtmosphericFogAndWeather: Boolean,
    val maxWeatherParticles: Int,
    val maxCombatParticles: Int,
    val cullRadiusTiles: Int
) {
    ULTRA(
        label = "ULTRA 4K",
        description = "Maximum resolution, multi-layer soft shadows, contact ambient occlusion, dynamic point lights, and dense biome weather.",
        resolutionScale = 1.0f,
        textureMipResolution = 128,
        enableAmbientOcclusion = true,
        enableDynamicPointLights = true,
        enableSoftShadows = true,
        enableSurfaceDetailPass = true,
        enableAtmosphericFogAndWeather = true,
        maxWeatherParticles = 64,
        maxCombatParticles = 96,
        cullRadiusTiles = 11
    ),
    HIGH(
        label = "HIGH",
        description = "High-definition rendering with balanced dynamic lighting, ambient occlusion, and moderate particle budgets.",
        resolutionScale = 0.88f,
        textureMipResolution = 64,
        enableAmbientOcclusion = true,
        enableDynamicPointLights = true,
        enableSoftShadows = false,
        enableSurfaceDetailPass = true,
        enableAtmosphericFogAndWeather = true,
        maxWeatherParticles = 36,
        maxCombatParticles = 64,
        cullRadiusTiles = 10
    ),
    PERFORMANCE(
        label = "PERFORMANCE",
        description = "Optimized 60 FPS pipeline for mid-range devices with fast single-pass shadows and lightweight effects.",
        resolutionScale = 0.75f,
        textureMipResolution = 32,
        enableAmbientOcclusion = false,
        enableDynamicPointLights = false,
        enableSoftShadows = false,
        enableSurfaceDetailPass = false,
        enableAtmosphericFogAndWeather = false,
        maxWeatherParticles = 12,
        maxCombatParticles = 32,
        cullRadiusTiles = 8
    );

    fun next(): GraphicsQuality = when (this) {
        ULTRA -> HIGH
        HIGH -> PERFORMANCE
        PERFORMANCE -> ULTRA
    }
}

enum class WeatherEffectType(val label: String) {
    JUNGLE_SPORES("Floating Spores & Canopy Leaves"),
    DUNGEON_EMBERS("Torch Embers & Dust Motes"),
    DESERT_SANDSTORM("Swirling Golden Sand"),
    BLIZZARD_SNOW("Falling Glacial Snowflakes"),
    SPECTRAL_MIST("Drifting Haunted Wraith Fog"),
    VOLCANIC_ASH("Rising Magma Sparks & Ash"),
    SOLAR_RAYS("Gilded Sunbeams & Gold Dust"),
    BIOLUMINESCENT_ORBS("Rising Arcane Energy Motes")
}

enum class PropType(
    val displayName: String,
    val baseHp: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val glowColor: Color,
    val isGlowingCrystal: Boolean = false,
    val isSwayingVegetation: Boolean = false
) {
    JUNGLE_VINE_TOTEM("Overgrown Totem & Vines", 30, Color(0xFF15803D), Color(0xFF854D0E), Color(0xFFA3E635), isSwayingVegetation = true),
    DUNGEON_IRON_CRATE("Ironbound Supply Crate", 35, Color(0xFF78350F), Color(0xFF475569), Color(0xFFF59E0B)),
    SANDSTONE_RELIC_URN("Ancient Sandstone Urn", 25, Color(0xFFD97706), Color(0xFF92400E), Color(0xFFFDE047)),
    FROST_CRYSTAL_CLUSTER("Glacial Mana Crystal", 40, Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFFE0F2FE), isGlowingCrystal = true),
    HAUNTED_SOUL_LANTERN("Cursed Soul Reliquary", 35, Color(0xFF6D28D9), Color(0xFF312E81), Color(0xFFC084FC), isGlowingCrystal = true),
    VOLCANIC_MAGMA_GEODE("Volatile Magma Geode", 45, Color(0xFF991B1B), Color(0xFF451A03), Color(0xFFFB923C), isGlowingCrystal = true),
    GOLDEN_SUN_OBELISK("Gilded Sun Vessel", 40, Color(0xFFEAB308), Color(0xFFB45309), Color(0xFFFFFBEB), isGlowingCrystal = true),
    BIOLUM_ENERGY_PYLON("Arcane Conduit Crystal", 45, Color(0xFF06B6D4), Color(0xFF164E63), Color(0xFF67E8F9), isGlowingCrystal = true)
}

data class DestructiblePropEntity(
    val id: Int,
    val type: PropType,
    val gx: Int,
    val gy: Int,
    val hp: Int = type.baseHp,
    val maxHp: Int = type.baseHp,
    val isDestroyed: Boolean = false,
    val destroyAnimProgress: Float = 0f,
    val hitFlashTimer: Float = 0f
)

enum class CharacterAnimState {
    IDLE,
    WALK,
    RUN,
    JUMP_ASCEND,
    JUMP_LAND,
    DASH,
    ATTACK,
    HIT_REACT,
    DEATH
}

enum class BiomeType(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val mechanicDescription: String,
    val enemyName: String,
    val weatherType: WeatherEffectType,
    val signatureProp: PropType,
    val floorTop: Color,
    val floorLeft: Color,
    val floorRight: Color,
    val wallTop: Color,
    val wallLeft: Color,
    val wallRight: Color,
    val liquidColor: Color,
    val liquidGlow: Color,
    val ambientLight: Color,
    val accentColor: Color,
    val fogColor: Color,
    val frictionMultiplier: Float = 1.0f
) {
    ANCIENT_JUNGLE_RUINS(
        id = "jungle_ruins",
        displayName = "Ancient Jungle Ruins",
        subtitle = "Overgrown Temple & Poison Totems",
        mechanicDescription = "Lush mossy stone temples with flowing emerald rivers, swaying vines, and poison dart totems.",
        enemyName = "Vine Stalker",
        weatherType = WeatherEffectType.JUNGLE_SPORES,
        signatureProp = PropType.JUNGLE_VINE_TOTEM,
        floorTop = Color(0xFF4D7C5D),
        floorLeft = Color(0xFF385E45),
        floorRight = Color(0xFF284532),
        wallTop = Color(0xFF6B8E63),
        wallLeft = Color(0xFF4F6B49),
        wallRight = Color(0xFF394F34),
        liquidColor = Color(0xFF0EA5E9),
        liquidGlow = Color(0xFF38BDF8),
        ambientLight = Color(0xFF34D399),
        accentColor = Color(0xFFA3E635),
        fogColor = Color(0x2610B981)
    ),
    UNDERGROUND_DUNGEON(
        id = "underground_dungeon",
        displayName = "Underground Dungeon",
        subtitle = "Torchlit Catacombs & Iron Spikes",
        mechanicDescription = "Deep medieval stonework with retracting iron floor spikes, destructible crates, and skeleton sentries.",
        enemyName = "Iron Warden",
        weatherType = WeatherEffectType.DUNGEON_EMBERS,
        signatureProp = PropType.DUNGEON_IRON_CRATE,
        floorTop = Color(0xFF475569),
        floorLeft = Color(0xFF334155),
        floorRight = Color(0xFF1E293B),
        wallTop = Color(0xFF64748B),
        wallLeft = Color(0xFF475569),
        wallRight = Color(0xFF273449),
        liquidColor = Color(0xFF1E3A8A),
        liquidGlow = Color(0xFF3B82F6),
        ambientLight = Color(0xFFF59E0B),
        accentColor = Color(0xFFFBBF24),
        fogColor = Color(0x260F172A)
    ),
    DESERT_TEMPLE(
        id = "desert_temple",
        displayName = "Desert Temple",
        subtitle = "Sunlit Sandstone & Flame Traps",
        mechanicDescription = "Golden sandstone obelisks with rhythmic fire jets, relic urns, and ancient guardians.",
        enemyName = "Anubis Sentinel",
        weatherType = WeatherEffectType.DESERT_SANDSTORM,
        signatureProp = PropType.SANDSTONE_RELIC_URN,
        floorTop = Color(0xFFD99B52),
        floorLeft = Color(0xFFB87D38),
        floorRight = Color(0xFF8F5E24),
        wallTop = Color(0xFFEAB368),
        wallLeft = Color(0xFFC78C44),
        wallRight = Color(0xFF9C692B),
        liquidColor = Color(0xFFD97706),
        liquidGlow = Color(0xFFFBBF24),
        ambientLight = Color(0xFFFDE047),
        accentColor = Color(0xFFF59E0B),
        fogColor = Color(0x26F59E0B)
    ),
    FROZEN_KINGDOM(
        id = "frozen_kingdom",
        displayName = "Frozen Kingdom",
        subtitle = "Glacial Citadel & Slippery Ice",
        mechanicDescription = "Crystalline ice fortresses with low-friction skating tiles, glowing mana crystals, and frost geysers.",
        enemyName = "Frost Golem",
        weatherType = WeatherEffectType.BLIZZARD_SNOW,
        signatureProp = PropType.FROST_CRYSTAL_CLUSTER,
        floorTop = Color(0xFF67E8F9),
        floorLeft = Color(0xFF22D3EE),
        floorRight = Color(0xFF0891B2),
        wallTop = Color(0xFFA5F3FC),
        wallLeft = Color(0xFF38BDF8),
        wallRight = Color(0xFF0284C7),
        liquidColor = Color(0xFF0284C7),
        liquidGlow = Color(0xFF7DD3FC),
        ambientLight = Color(0xFF38BDF8),
        accentColor = Color(0xFFE0F2FE),
        fogColor = Color(0x2838BDF8),
        frictionMultiplier = 0.88f
    ),
    HAUNTED_CASTLE(
        id = "haunted_castle",
        displayName = "Haunted Castle",
        subtitle = "Spectral Keep & Cursed Grounds",
        mechanicDescription = "Gothic obsidian halls illuminated by eerie violet lanterns, spectral fog, and phantom knights.",
        enemyName = "Phantom Knight",
        weatherType = WeatherEffectType.SPECTRAL_MIST,
        signatureProp = PropType.HAUNTED_SOUL_LANTERN,
        floorTop = Color(0xFF5B4B8A),
        floorLeft = Color(0xFF44356B),
        floorRight = Color(0xFF2E224F),
        wallTop = Color(0xFF7866AD),
        wallLeft = Color(0xFF58478A),
        wallRight = Color(0xFF3E3066),
        liquidColor = Color(0xFF7C3AED),
        liquidGlow = Color(0xFFA78BFA),
        ambientLight = Color(0xFFC084FC),
        accentColor = Color(0xFFD8B4FE),
        fogColor = Color(0x2E7C3AED)
    ),
    LAVA_CAVERNS(
        id = "lava_caverns",
        displayName = "Lava Caverns",
        subtitle = "Magma Rivers & Volcanic Vents",
        mechanicDescription = "Charred basalt bridges suspended over bubbling magma streams, volatile geodes, and fire geysers.",
        enemyName = "Magma Brute",
        weatherType = WeatherEffectType.VOLCANIC_ASH,
        signatureProp = PropType.VOLCANIC_MAGMA_GEODE,
        floorTop = Color(0xFF57413D),
        floorLeft = Color(0xFF3F2E2B),
        floorRight = Color(0xFF291D1B),
        wallTop = Color(0xFF784B42),
        wallLeft = Color(0xFF59352E),
        wallRight = Color(0xFF3D221D),
        liquidColor = Color(0xFFEA580C),
        liquidGlow = Color(0xFFF97316),
        ambientLight = Color(0xFFFB923C),
        accentColor = Color(0xFFEF4444),
        fogColor = Color(0x28EA580C)
    ),
    LOST_GOLDEN_CITY(
        id = "lost_golden_city",
        displayName = "Lost Golden City",
        subtitle = "Gilded Ziggurats & Solar Beams",
        mechanicDescription = "Gleaming citadels of solid gold guarded by clockwork solar automatons and sun obelisks.",
        enemyName = "Solar Automaton",
        weatherType = WeatherEffectType.SOLAR_RAYS,
        signatureProp = PropType.GOLDEN_SUN_OBELISK,
        floorTop = Color(0xFFEAB308),
        floorLeft = Color(0xFFCA8A04),
        floorRight = Color(0xFFA16207),
        wallTop = Color(0xFFFACC15),
        wallLeft = Color(0xFFD97706),
        wallRight = Color(0xFFB45309),
        liquidColor = Color(0xFF0D9488),
        liquidGlow = Color(0xFF2DD4BF),
        ambientLight = Color(0xFFFDE047),
        accentColor = Color(0xFFFFFBEB),
        fogColor = Color(0x24FACC15)
    ),
    UNDERGROUND_CIVILIZATION(
        id = "underground_civilization",
        displayName = "Ancient Underground Civilization",
        subtitle = "Bioluminescent Sanctum & Energy Cores",
        mechanicDescription = "Subterranean crystal cities powered by arcane conduits, glowing pylons, and pulse constructs.",
        enemyName = "Crystal Construct",
        weatherType = WeatherEffectType.BIOLUMINESCENT_ORBS,
        signatureProp = PropType.BIOLUM_ENERGY_PYLON,
        floorTop = Color(0xFF1E4E5F),
        floorLeft = Color(0xFF153947),
        floorRight = Color(0xFF0D2630),
        wallTop = Color(0xFF2A6B82),
        wallLeft = Color(0xFF1D4D5E),
        wallRight = Color(0xFF123440),
        liquidColor = Color(0xFF06B6D4),
        liquidGlow = Color(0xFF67E8F9),
        ambientLight = Color(0xFF22D3EE),
        accentColor = Color(0xFF2DD4BF),
        fogColor = Color(0x2806B6D4)
    );

    companion object {
        fun forLevel(level: Int): BiomeType {
            val index = ((level - 1) / 3) % entries.size
            return entries[index]
        }
    }
}

enum class DifficultyTier(
    val title: String,
    val levelRangeLabel: String,
    val description: String,
    val baseGridSize: Int,
    val roomCount: Int,
    val requiredKeys: Int,
    val baseEnemies: Int,
    val trapDensity: Float,
    val secretRooms: Int
) {
    TUTORIAL_1_10(
        title = "Initiate's Trial",
        levelRangeLabel = "Levels 1–10",
        description = "Easy tutorial challenges with clear escape routes and basic traps.",
        baseGridSize = 18,
        roomCount = 4,
        requiredKeys = 1,
        baseEnemies = 2,
        trapDensity = 0.06f,
        secretRooms = 1
    ),
    TRAPS_ENEMIES_11_30(
        title = "Warden's Gauntlet",
        levelRangeLabel = "Levels 11–30",
        description = "Introduces roaming biome guards, fire jets, and multi-room key hunts.",
        baseGridSize = 22,
        roomCount = 5,
        requiredKeys = 2,
        baseEnemies = 4,
        trapDensity = 0.10f,
        secretRooms = 1
    ),
    ADVANCED_31_60(
        title = "Labyrinth of Shadows",
        levelRangeLabel = "Levels 31–60",
        description = "Advanced switch puzzles, branching corridors, and guarded treasure chambers.",
        baseGridSize = 25,
        roomCount = 6,
        requiredKeys = 2,
        baseEnemies = 6,
        trapDensity = 0.14f,
        secretRooms = 2
    ),
    COMPLEX_61_100(
        title = "Catacomb Sovereign",
        levelRangeLabel = "Levels 61–100",
        description = "Complex combinations of hazards, elite sentinels, and dual escape routes.",
        baseGridSize = 28,
        roomCount = 7,
        requiredKeys = 3,
        baseEnemies = 8,
        trapDensity = 0.17f,
        secretRooms = 2
    ),
    EXPERT_101_500(
        title = "Grandmaster's Abyss",
        levelRangeLabel = "Levels 101–500",
        description = "High-density tactical layouts requiring precise dashes, jumps, and combat.",
        baseGridSize = 31,
        roomCount = 8,
        requiredKeys = 3,
        baseEnemies = 10,
        trapDensity = 0.20f,
        secretRooms = 3
    ),
    ENDLESS_501_PLUS(
        title = "Eternal Legend",
        levelRangeLabel = "Levels 501+",
        description = "Endless mastery challenges with boss sentinels and rich relic vaults.",
        baseGridSize = 34,
        roomCount = 9,
        requiredKeys = 4,
        baseEnemies = 12,
        trapDensity = 0.23f,
        secretRooms = 3
    );

    companion object {
        fun forLevel(level: Int): DifficultyTier = when {
            level <= 10 -> TUTORIAL_1_10
            level <= 30 -> TRAPS_ENEMIES_11_30
            level <= 60 -> ADVANCED_31_60
            level <= 100 -> COMPLEX_61_100
            level <= 500 -> EXPERT_101_500
            else -> ENDLESS_501_PLUS
        }
    }
}

enum class TileType(val isWalkable: Boolean, val isJumpable: Boolean, val baseHeight: Float) {
    VOID(isWalkable = false, isJumpable = false, baseHeight = 0f),
    FLOOR(isWalkable = true, isJumpable = true, baseHeight = 0.25f),
    ICE_FLOOR(isWalkable = true, isJumpable = true, baseHeight = 0.25f),
    ELEVATED_WALL(isWalkable = false, isJumpable = false, baseHeight = 1.45f),
    PILLAR_STRUCTURE(isWalkable = false, isJumpable = false, baseHeight = 1.85f),
    TORCH_BRAZIER(isWalkable = false, isJumpable = true, baseHeight = 0.95f),
    LIQUID_HAZARD(isWalkable = false, isJumpable = true, baseHeight = 0.08f),
    SPIKE_TRAP(isWalkable = true, isJumpable = true, baseHeight = 0.25f),
    FIRE_JET_TRAP(isWalkable = true, isJumpable = true, baseHeight = 0.28f),
    CRACKED_SECRET_WALL(isWalkable = false, isJumpable = false, baseHeight = 1.35f),
    SECRET_FLOOR(isWalkable = true, isJumpable = true, baseHeight = 0.25f),
    LOCKED_DOOR(isWalkable = false, isJumpable = false, baseHeight = 1.30f),
    UNLOCKED_DOOR(isWalkable = true, isJumpable = true, baseHeight = 0.26f),
    SWITCH_PEDESTAL(isWalkable = true, isJumpable = true, baseHeight = 0.45f),
    START_PAD(isWalkable = true, isJumpable = true, baseHeight = 0.30f),
    EXIT_PORTAL(isWalkable = true, isJumpable = true, baseHeight = 0.35f)
}

data class GridPoint(val x: Int, val y: Int)

data class IsoTile(
    val gx: Int,
    val gy: Int,
    val type: TileType,
    val height: Float = type.baseHeight,
    val isSecret: Boolean = false,
    val isRevealed: Boolean = !isSecret,
    val isSwitchActivated: Boolean = false,
    val variant: Int = 0
)

enum class RewardType(val label: String, val coinValue: Int, val gemValue: Int) {
    COIN("Gold Coin", 15, 0),
    GEM("Arcane Gem", 0, 2),
    TREASURE_CHEST("Royal Chest", 75, 5),
    RELIC_KEY("Portal Key", 25, 1),
    RARE_ARTEFACT("Ancient Relic", 150, 10),
    HEALTH_ELIXIR("Vitality Orb", 10, 0)
}

data class RewardItemEntity(
    val id: Int,
    val type: RewardType,
    val gx: Int,
    val gy: Int,
    val isSecret: Boolean = false,
    val isCollected: Boolean = false,
    val openAnimProgress: Float = 0f
)

data class EnemyEntity(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val startX: Float,
    val startY: Float,
    val patrolEndX: Float,
    val patrolEndY: Float,
    val hp: Int,
    val maxHp: Int,
    val speed: Float,
    val damage: Int,
    val isBoss: Boolean = false,
    val facingAngle: Float = 0f,
    val hurtFlashTimer: Float = 0f,
    val movingToEnd: Boolean = true,
    val isAlerted: Boolean = false,
    val attackAnimTimer: Float = 0f,
    val knockbackVx: Float = 0f,
    val knockbackVy: Float = 0f,
    val isDying: Boolean = false,
    val deathAnimProgress: Float = 0f
)

data class HeroCharacter(
    val id: String,
    val name: String,
    val title: String,
    val abilityName: String,
    val maxHp: Int,
    val speedMultiplier: Float,
    val attackDamage: Int,
    val costCoins: Int,
    val costGems: Int,
    val primaryColor: Color,
    val capeColor: Color
)

data class CharacterSkin(
    val id: String,
    val name: String,
    val armorColor: Color,
    val trimColor: Color,
    val glowColor: Color,
    val costGems: Int
)

data class GeneratedLevel(
    val levelNumber: Int,
    val seed: Long,
    val biome: BiomeType,
    val difficultyTier: DifficultyTier,
    val width: Int,
    val height: Int,
    val tiles: Array<Array<IsoTile>>,
    val startPoint: GridPoint,
    val exitPoint: GridPoint,
    val keyPoints: List<GridPoint>,
    val lockedDoorPoints: List<GridPoint> = emptyList(),
    val switchPoint: GridPoint?,
    val secretRoomPoints: List<GridPoint>,
    val initialEnemies: List<EnemyEntity>,
    val initialRewards: List<RewardItemEntity>,
    val initialProps: List<DestructiblePropEntity> = emptyList(),
    val verifiedSolutionPath: List<GridPoint>,
    val alternativeEscapePath: List<GridPoint>,
    val validationPasses: Int,
    val generationTimeMs: Long
)

object HeroCatalog {
    val heroes = listOf(
        HeroCharacter(
            id = "kaelen",
            name = "Kaelen the Pathfinder",
            title = "Balanced Relic Hunter",
            abilityName = "Whirlwind Slash",
            maxHp = 100,
            speedMultiplier = 1.0f,
            attackDamage = 35,
            costCoins = 0,
            costGems = 0,
            primaryColor = Color(0xFF38BDF8),
            capeColor = Color(0xFF0284C7)
        ),
        HeroCharacter(
            id = "lyra",
            name = "Lyra Shadowstep",
            title = "Agile Trap Runner",
            abilityName = "Phantom Blink",
            maxHp = 90,
            speedMultiplier = 1.22f,
            attackDamage = 32,
            costCoins = 450,
            costGems = 15,
            primaryColor = Color(0xFFA855F7),
            capeColor = Color(0xFF6B21A8)
        ),
        HeroCharacter(
            id = "torin",
            name = "Torin Ironshield",
            title = "Juggernaut Defender",
            abilityName = "Earthshatter Slam",
            maxHp = 150,
            speedMultiplier = 0.94f,
            attackDamage = 48,
            costCoins = 900,
            costGems = 30,
            primaryColor = Color(0xFFF59E0B),
            capeColor = Color(0xFFB45309)
        ),
        HeroCharacter(
            id = "seraphina",
            name = "Seraphina Sunweaver",
            title = "Solar Arcanist",
            abilityName = "Solar Nova",
            maxHp = 120,
            speedMultiplier = 1.12f,
            attackDamage = 55,
            costCoins = 1600,
            costGems = 55,
            primaryColor = Color(0xFF10B981),
            capeColor = Color(0xFF047857)
        ),
        HeroCharacter(
            id = "valkor",
            name = "Valkor Voidwalker",
            title = "Abyssal Sovereign",
            abilityName = "Obsidian Vortex",
            maxHp = 180,
            speedMultiplier = 1.25f,
            attackDamage = 70,
            costCoins = 3000,
            costGems = 95,
            primaryColor = Color(0xFFEF4444),
            capeColor = Color(0xFF991B1B)
        )
    )

    val skins = listOf(
        CharacterSkin("default_skin", "Explorer's Tunic", Color(0xFF38BDF8), Color(0xFFFFB800), Color(0xFF06B6D4), 0),
        CharacterSkin("emerald_jade", "Emerald Guardian", Color(0xFF10B981), Color(0xFFA3E635), Color(0xFF34D399), 15),
        CharacterSkin("crimson_drake", "Crimson Dragonscale", Color(0xFFEF4444), Color(0xFFFBBF24), Color(0xFFF97316), 30),
        CharacterSkin("royal_aurum", "Gilded Emperor", Color(0xFFFACC15), Color(0xFFFFFFFF), Color(0xFFFDE047), 50),
        CharacterSkin("astral_void", "Astral Celestia", Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFC084FC), 75)
    )
}
