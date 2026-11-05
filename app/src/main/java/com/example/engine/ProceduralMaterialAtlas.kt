package com.example.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.example.model.BiomeType
import com.example.model.GraphicsQuality
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sin

/**
 * Synthesizes and caches GPU-backed procedural textures (with mipmap resolution levels
 * for ULTRA, HIGH, and PERFORMANCE) for all 8 biomes.
 */
object ProceduralMaterialAtlas {

    data class BiomeMaterialSet(
        val biome: BiomeType,
        val quality: GraphicsQuality,
        val floorTexture: ImageBitmap,
        val wallTexture: ImageBitmap,
        val liquidTexture: ImageBitmap
    )

    private val cache = ConcurrentHashMap<String, BiomeMaterialSet>()

    fun getMaterialSet(biome: BiomeType, quality: GraphicsQuality): BiomeMaterialSet {
        val key = "${biome.id}_${quality.name}"
        return cache.getOrPut(key) {
            generateBiomeMaterialSet(biome, quality)
        }
    }

    private fun generateBiomeMaterialSet(
        biome: BiomeType,
        quality: GraphicsQuality
    ): BiomeMaterialSet {
        val res = quality.textureMipResolution
        val floorBmp = createProceduralBitmap(
            size = res,
            baseColor = biome.floorTop,
            secondaryColor = biome.floorLeft,
            accentColor = biome.accentColor,
            patternType = when (biome) {
                BiomeType.ANCIENT_JUNGLE_RUINS -> 0
                BiomeType.UNDERGROUND_DUNGEON -> 1
                BiomeType.DESERT_TEMPLE -> 2
                BiomeType.FROZEN_KINGDOM -> 3
                BiomeType.HAUNTED_CASTLE -> 1
                BiomeType.LAVA_CAVERNS -> 4
                BiomeType.LOST_GOLDEN_CITY -> 5
                BiomeType.UNDERGROUND_CIVILIZATION -> 6
            }
        )
        val wallBmp = createProceduralBitmap(
            size = res,
            baseColor = biome.wallTop,
            secondaryColor = biome.wallRight,
            accentColor = biome.ambientLight,
            patternType = 1
        )
        val liquidBmp = createProceduralBitmap(
            size = res / 2,
            baseColor = biome.liquidColor,
            secondaryColor = biome.liquidGlow,
            accentColor = Color.White,
            patternType = 7
        )

        return BiomeMaterialSet(
            biome = biome,
            quality = quality,
            floorTexture = floorBmp,
            wallTexture = wallBmp,
            liquidTexture = liquidBmp
        )
    }

    private fun createProceduralBitmap(
        size: Int,
        baseColor: Color,
        secondaryColor: Color,
        accentColor: Color,
        patternType: Int
    ): ImageBitmap {
        val dim = size.coerceIn(16, 128)
        val androidBitmap = android.graphics.Bitmap.createBitmap(
            dim,
            dim,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val pixels = IntArray(dim * dim)

        val br = baseColor.red
        val bg = baseColor.green
        val bb = baseColor.blue

        val sr = secondaryColor.red
        val sg = secondaryColor.green
        val sb = secondaryColor.blue

        val ar = accentColor.red
        val ag = accentColor.green
        val ab = accentColor.blue

        for (y in 0 until dim) {
            for (x in 0 until dim) {
                val u = x.toFloat() / dim
                val v = y.toFloat() / dim

                // Deterministic hash noise for micro-surface grain
                val hash = ((x * 73856093) xor (y * 19349663)) and 0xFF
                val grain = (hash / 255f - 0.5f) * 0.08f

                var mixSecondary = 0f
                var mixAccent = 0f

                when (patternType) {
                    0 -> { // Mossy Jungle Stone & Vine veins
                        val brickSeam = (x % (dim / 4) == 0) || ((y + (x / (dim / 4)) * 7) % (dim / 4) == 0)
                        if (brickSeam) mixSecondary = 0.65f
                        val mossWave = sin(u * 12.56f) * sin(v * 12.56f)
                        if (mossWave > 0.55f) mixAccent = 0.35f
                    }
                    1 -> { // Chiseled Dungeon Cobble / Ashlar Masonry
                        val row = y / (dim / 4).coerceAtLeast(1)
                        val offsetX = if (row % 2 == 0) x else (x + dim / 8)
                        val isMortar = (y % (dim / 4).coerceAtLeast(1) <= 1) ||
                            (offsetX % (dim / 3).coerceAtLeast(1) <= 1)
                        if (isMortar) mixSecondary = 0.75f
                    }
                    2 -> { // Desert Sandstone Ripples & Hieroglyphic Bevel
                        val dune = sin((u + v * 0.5f) * 18.8f)
                        mixSecondary = (dune * 0.5f + 0.5f) * 0.3f
                    }
                    3 -> { // Glacial Faceted Crystal Ice
                        val facet = abs((u - 0.5f) + (v - 0.5f))
                        if (facet in 0.22f..0.26f || abs(u - v) < 0.04f) {
                            mixAccent = 0.55f
                        }
                    }
                    4 -> { // Cracked Volcanic Basalt & Magma Veins
                        val crack = abs(sin(u * 9.4f + sin(v * 6.2f)))
                        if (crack < 0.08f) mixAccent = 0.70f else mixSecondary = crack * 0.35f
                    }
                    5 -> { // Gilded Lost City Filigree
                        val border = (x < 2 || y < 2 || x >= dim - 2 || y >= dim - 2)
                        val diag = abs(x - y) <= 1 || abs(x - (dim - 1 - y)) <= 1
                        if (border) mixSecondary = 0.6f
                        if (diag) mixAccent = 0.45f
                    }
                    6 -> { // Ancient Underground Civilization Bioluminescent Circuitry
                        val gridLine = (x == dim / 2 && y in (dim / 4)..(dim * 3 / 4)) ||
                            (y == dim / 2 && x in (dim / 4)..(dim * 3 / 4))
                        if (gridLine) mixAccent = 0.75f
                    }
                    else -> { // Liquid Shimmer
                        val wave = sin((u * 2f + v) * 12.56f)
                        if (wave > 0.6f) mixAccent = 0.4f else mixSecondary = 0.3f
                    }
                }

                val r = (br * (1f - mixSecondary - mixAccent) + sr * mixSecondary + ar * mixAccent + grain)
                    .coerceIn(0f, 1f)
                val g = (bg * (1f - mixSecondary - mixAccent) + sg * mixSecondary + ag * mixAccent + grain)
                    .coerceIn(0f, 1f)
                val b = (bb * (1f - mixSecondary - mixAccent) + sb * mixSecondary + ab * mixAccent + grain)
                    .coerceIn(0f, 1f)

                pixels[y * dim + x] = Color(r, g, b, 1f).toArgb()
            }
        }

        androidBitmap.setPixels(pixels, 0, dim, 0, 0, dim, dim)
        return androidBitmap.asImageBitmap()
    }
}
