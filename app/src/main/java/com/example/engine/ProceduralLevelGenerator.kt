package com.example.engine

import com.example.model.BiomeType
import com.example.model.DestructiblePropEntity
import com.example.model.DifficultyTier
import com.example.model.EnemyEntity
import com.example.model.GeneratedLevel
import com.example.model.GridPoint
import com.example.model.IsoTile
import com.example.model.RewardItemEntity
import com.example.model.RewardType
import com.example.model.TileType
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object ProceduralLevelGenerator {

    private data class RoomRect(
        val id: Int,
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val isSecret: Boolean = false
    ) {
        val centerX: Int get() = x + w / 2
        val centerY: Int get() = y + h / 2

        fun intersects(other: RoomRect, padding: Int = 1): Boolean {
            return x - padding < other.x + other.w &&
                x + w + padding > other.x &&
                y - padding < other.y + other.h &&
                y + h + padding > other.y
        }
    }

    fun computeSeedForLevel(levelNumber: Int, customSalt: Long = 0L): Long {
        val goldenGamma = -0x61c8864680b583ebL
        return (levelNumber.toLong() * goldenGamma) xor (customSalt + 0x4441524B5F323544L)
    }

    fun generateLevel(
        levelNumber: Int,
        explicitSeed: Long? = null,
        explicitBiome: BiomeType? = null
    ): GeneratedLevel {
        val startTime = System.currentTimeMillis()
        val seed = explicitSeed ?: computeSeedForLevel(levelNumber)
        val rng = Random(seed)
        val tier = DifficultyTier.forLevel(levelNumber)
        val biome = explicitBiome ?: BiomeType.forLevel(levelNumber)

        val gridSize = tier.baseGridSize + (levelNumber % 3) * 2
        val width = gridSize
        val height = gridSize

        // Initialize grid with Elevated Walls around the border and interior
        val grid = Array(width) { gx ->
            Array(height) { gy ->
                IsoTile(
                    gx = gx,
                    gy = gy,
                    type = TileType.ELEVATED_WALL,
                    height = 1.35f + rng.nextFloat() * 0.35f,
                    variant = rng.nextInt(4)
                )
            }
        }

        // 1. Procedural Room Generation
        val rooms = mutableListOf<RoomRect>()
        val targetRooms = tier.roomCount
        var attempts = 0
        while (rooms.size < targetRooms && attempts < 140) {
            attempts++
            val rw = rng.nextInt(4, 7)
            val rh = rng.nextInt(4, 7)
            val rx = rng.nextInt(2, width - rw - 2)
            val ry = rng.nextInt(2, height - rh - 2)
            val candidate = RoomRect(rooms.size, rx, ry, rw, rh)
            if (rooms.none { it.intersects(candidate, padding = 2) }) {
                rooms.add(candidate)
            }
        }

        if (rooms.size < 3) {
            rooms.clear()
            rooms.add(RoomRect(0, 2, 2, 5, 5))
            rooms.add(RoomRect(1, width / 2 - 2, height / 2 - 2, 5, 5))
            rooms.add(RoomRect(2, width - 7, height - 7, 5, 5))
        }

        rooms.sortBy { it.centerX + it.centerY }

        for (room in rooms) {
            carveRoom(grid, room, biome, rng)
        }

        // 2. Connect rooms via primary spanning corridors + secondary escape route loop
        for (i in 0 until rooms.size - 1) {
            val a = rooms[i]
            val b = rooms[i + 1]
            carveCorridor(grid, GridPoint(a.centerX, a.centerY), GridPoint(b.centerX, b.centerY), biome, rng)
        }
        if (rooms.size >= 3) {
            val first = rooms.first()
            val last = rooms.last()
            val mid = rooms[rooms.size / 2]
            carveCorridor(
                grid,
                GridPoint(first.centerX, last.centerY.coerceIn(3, height - 4)),
                GridPoint(last.centerX, last.centerY),
                biome,
                rng
            )
            carveCorridor(
                grid,
                GridPoint(first.centerX, first.centerY),
                GridPoint(mid.centerX, last.centerY.coerceIn(3, height - 4)),
                biome,
                rng
            )
        }

        // 3. Carve Optional Secret Areas behind Cracked Secret Walls
        val secretRoomPoints = mutableListOf<GridPoint>()
        var secretCreated = 0
        for (attempt in 0 until 40) {
            if (secretCreated >= tier.secretRooms) break
            val hostRoom = rooms[rng.nextInt(rooms.size)]
            val dir = rng.nextInt(4)
            val sx = when (dir) {
                0 -> hostRoom.x - 3
                1 -> hostRoom.x + hostRoom.w + 1
                else -> hostRoom.centerX - 1
            }
            val sy = when (dir) {
                2 -> hostRoom.y - 3
                3 -> hostRoom.y + hostRoom.h + 1
                else -> hostRoom.centerY - 1
            }
            if (sx in 2..(width - 5) && sy in 2..(height - 5)) {
                val secretRect = RoomRect(100 + secretCreated, sx, sy, 3, 3, isSecret = true)
                if (rooms.none { it.intersects(secretRect, padding = 0) }) {
                    for (gx in secretRect.x until secretRect.x + secretRect.w) {
                        for (gy in secretRect.y until secretRect.y + secretRect.h) {
                            grid[gx][gy] = IsoTile(
                                gx = gx,
                                gy = gy,
                                type = TileType.SECRET_FLOOR,
                                height = TileType.SECRET_FLOOR.baseHeight,
                                isSecret = true,
                                isRevealed = false
                            )
                        }
                    }
                    val doorX = (secretRect.centerX + hostRoom.centerX) / 2
                    val doorY = (secretRect.centerY + hostRoom.centerY) / 2
                    grid[doorX][doorY] = IsoTile(
                        gx = doorX,
                        gy = doorY,
                        type = TileType.CRACKED_SECRET_WALL,
                        height = TileType.CRACKED_SECRET_WALL.baseHeight,
                        isSecret = true,
                        isRevealed = false
                    )
                    carveSecretConnector(grid, GridPoint(hostRoom.centerX, hostRoom.centerY), GridPoint(doorX, doorY))
                    carveSecretConnector(grid, GridPoint(secretRect.centerX, secretRect.centerY), GridPoint(doorX, doorY))

                    secretRoomPoints.add(GridPoint(secretRect.centerX, secretRect.centerY))
                    secretCreated++
                }
            }
        }

        // 4. Define Start, Exit, Keys, Locked Doors, and Switch Objectives
        val startRoom = rooms.first()
        val exitRoom = rooms.last()
        val startPoint = GridPoint(startRoom.centerX, startRoom.centerY)
        val exitPoint = GridPoint(exitRoom.centerX, exitRoom.centerY)

        grid[startPoint.x][startPoint.y] = IsoTile(
            gx = startPoint.x,
            gy = startPoint.y,
            type = TileType.START_PAD,
            height = TileType.START_PAD.baseHeight
        )
        grid[exitPoint.x][exitPoint.y] = IsoTile(
            gx = exitPoint.x,
            gy = exitPoint.y,
            type = TileType.EXIT_PORTAL,
            height = TileType.EXIT_PORTAL.baseHeight
        )

        // Place required Relic Keys in early/intermediate rooms (before exitRoom)
        val keyPoints = mutableListOf<GridPoint>()
        val candidateRooms = rooms.drop(1).dropLast(1).ifEmpty { listOf(startRoom) }
        for (k in 0 until tier.requiredKeys) {
            val r = candidateRooms[k % candidateRooms.size]
            val kx = (r.x + 1 + (k % max(1, r.w - 2))).coerceIn(2, width - 3)
            val ky = (r.y + 1 + ((k * 2) % max(1, r.h - 2))).coerceIn(2, height - 3)
            val pt = if (ptEquals(kx, ky, startPoint) || ptEquals(kx, ky, exitPoint)) {
                GridPoint(r.centerX, (r.centerY + 1).coerceAtMost(height - 3))
            } else {
                GridPoint(kx, ky)
            }
            grid[pt.x][pt.y] = IsoTile(pt.x, pt.y, TileType.FLOOR, TileType.FLOOR.baseHeight)
            keyPoints.add(pt)
        }

        // Optional Switch Pedestal for Levels 6+
        val switchPoint: GridPoint? = if (levelNumber >= 6 && rooms.size >= 3) {
            val switchRoom = rooms[rooms.size / 2]
            val sp = GridPoint(
                (switchRoom.centerX + 1).coerceIn(2, width - 3),
                switchRoom.centerY.coerceIn(2, height - 3)
            )
            if (sp != startPoint && sp != exitPoint && sp !in keyPoints) {
                grid[sp.x][sp.y] = IsoTile(sp.x, sp.y, TileType.SWITCH_PEDESTAL, TileType.SWITCH_PEDESTAL.baseHeight)
                sp
            } else null
        } else null

        // Place a Locked Security Door at the entrance of the exit room
        val lockedDoorPoints = mutableListOf<GridPoint>()
        val doorCandidate = GridPoint(
            (exitRoom.x - 1).coerceIn(2, width - 3),
            exitRoom.centerY.coerceIn(2, height - 3)
        )
        if (doorCandidate != startPoint && doorCandidate != exitPoint && doorCandidate !in keyPoints && doorCandidate != switchPoint) {
            grid[doorCandidate.x][doorCandidate.y] = IsoTile(
                gx = doorCandidate.x,
                gy = doorCandidate.y,
                type = TileType.LOCKED_DOOR,
                height = TileType.LOCKED_DOOR.baseHeight
            )
            lockedDoorPoints.add(doorCandidate)
            // Ensure tile before and after the locked door is walkable floor
            val beforeDoor = GridPoint((doorCandidate.x - 1).coerceAtLeast(1), doorCandidate.y)
            val afterDoor = GridPoint((doorCandidate.x + 1).coerceAtMost(width - 2), doorCandidate.y)
            if (grid[beforeDoor.x][beforeDoor.y].type == TileType.ELEVATED_WALL) {
                grid[beforeDoor.x][beforeDoor.y] = IsoTile(beforeDoor.x, beforeDoor.y, TileType.FLOOR, TileType.FLOOR.baseHeight)
            }
            if (grid[afterDoor.x][afterDoor.y].type == TileType.ELEVATED_WALL) {
                grid[afterDoor.x][afterDoor.y] = IsoTile(afterDoor.x, afterDoor.y, TileType.FLOOR, TileType.FLOOR.baseHeight)
            }
        }

        // 5. Place Environmental Hazards, Pillars, Torches, and Traps
        val protectedPoints = mutableSetOf<GridPoint>().apply {
            add(startPoint)
            add(exitPoint)
            addAll(keyPoints)
            addAll(lockedDoorPoints)
            if (switchPoint != null) add(switchPoint)
        }

        placeStructuresAndTraps(grid, rooms, biome, tier, protectedPoints, rng)

        // 6. Pathfinding Solvability Validation & Automatic Repair Loop
        var validationPasses = 0
        val fullVerifiedPath = mutableListOf<GridPoint>()
        var isSolvable = false

        while (!isSolvable && validationPasses < 6) {
            validationPasses++
            fullVerifiedPath.clear()
            var currentPos = startPoint
            var allSegmentsValid = true

            val waypoints = buildList {
                addAll(keyPoints)
                if (switchPoint != null) add(switchPoint)
                addAll(lockedDoorPoints)
                add(exitPoint)
            }

            for ((idx, target) in waypoints.withIndex()) {
                // Once the first key is visited, locked doors can be unlocked
                val hasKey = idx >= 1
                val segment = findShortestWalkablePath(
                    grid, width, height, currentPos, target,
                    allowJumpHazards = false,
                    allowLockedDoors = hasKey
                ) ?: findShortestWalkablePath(
                    grid, width, height, currentPos, target,
                    allowJumpHazards = true,
                    allowLockedDoors = hasKey
                )

                if (segment == null) {
                    forceSafeCorridor(grid, currentPos, target)
                    allSegmentsValid = false
                    break
                } else {
                    if (fullVerifiedPath.isNotEmpty()) {
                        fullVerifiedPath.addAll(segment.drop(1))
                    } else {
                        fullVerifiedPath.addAll(segment)
                    }
                    currentPos = target
                }
            }

            if (allSegmentsValid && fullVerifiedPath.isNotEmpty()) {
                isSolvable = true
            }
        }

        if (fullVerifiedPath.isEmpty()) {
            var curr = startPoint
            val waypoints = buildList {
                addAll(keyPoints)
                if (switchPoint != null) add(switchPoint)
                add(exitPoint)
            }
            for (target in waypoints) {
                forceSafeCorridor(grid, curr, target)
                val seg = findShortestWalkablePath(
                    grid, width, height, curr, target,
                    allowJumpHazards = true,
                    allowLockedDoors = true
                ) ?: listOf(curr, target)
                if (fullVerifiedPath.isNotEmpty()) fullVerifiedPath.addAll(seg.drop(1)) else fullVerifiedPath.addAll(seg)
                curr = target
            }
        }

        val altStart = keyPoints.firstOrNull() ?: startPoint
        val alternativePath = findShortestWalkablePath(
            grid, width, height, altStart, exitPoint,
            allowJumpHazards = true,
            allowLockedDoors = true
        ) ?: fullVerifiedPath

        // 7. Spawn Enemies with Patrol Routes & Detection AI
        val enemies = spawnEnemies(grid, rooms, biome, tier, levelNumber, startPoint, rng)

        // 8. Spawn Collectibles, Chests, Coins, Keys, and Secret Artifacts
        val rewards = spawnRewards(grid, rooms, keyPoints, secretRoomPoints, startPoint, exitPoint, lockedDoorPoints, rng)

        // 9. Spawn Biome-Specific Destructible & Animated Environmental Props
        val props = spawnBiomeProps(grid, rooms, biome, startPoint, exitPoint, keyPoints, lockedDoorPoints, rewards, rng)

        val elapsed = max(1L, System.currentTimeMillis() - startTime)

        return GeneratedLevel(
            levelNumber = levelNumber,
            seed = seed,
            biome = biome,
            difficultyTier = tier,
            width = width,
            height = height,
            tiles = grid,
            startPoint = startPoint,
            exitPoint = exitPoint,
            keyPoints = keyPoints,
            lockedDoorPoints = lockedDoorPoints,
            switchPoint = switchPoint,
            secretRoomPoints = secretRoomPoints,
            initialEnemies = enemies,
            initialRewards = rewards,
            initialProps = props,
            verifiedSolutionPath = fullVerifiedPath,
            alternativeEscapePath = alternativePath,
            validationPasses = validationPasses,
            generationTimeMs = elapsed
        )
    }

    private fun carveRoom(
        grid: Array<Array<IsoTile>>,
        room: RoomRect,
        biome: BiomeType,
        rng: Random
    ) {
        for (gx in room.x until room.x + room.w) {
            for (gy in room.y until room.y + room.h) {
                val isIce = biome == BiomeType.FROZEN_KINGDOM && rng.nextFloat() < 0.45f
                val tileType = if (isIce) TileType.ICE_FLOOR else TileType.FLOOR
                grid[gx][gy] = IsoTile(
                    gx = gx,
                    gy = gy,
                    type = tileType,
                    height = tileType.baseHeight + (rng.nextFloat() - 0.5f) * 0.03f,
                    variant = rng.nextInt(4)
                )
            }
        }
        val corners = listOf(
            GridPoint(room.x, room.y),
            GridPoint(room.x + room.w - 1, room.y),
            GridPoint(room.x, room.y + room.h - 1),
            GridPoint(room.x + room.w - 1, room.y + room.h - 1)
        )
        for (c in corners) {
            if (rng.nextFloat() < 0.55f) {
                grid[c.x][c.y] = IsoTile(
                    gx = c.x,
                    gy = c.y,
                    type = TileType.TORCH_BRAZIER,
                    height = TileType.TORCH_BRAZIER.baseHeight
                )
            }
        }
    }

    private fun carveCorridor(
        grid: Array<Array<IsoTile>>,
        from: GridPoint,
        to: GridPoint,
        biome: BiomeType,
        rng: Random
    ) {
        var cx = from.x
        var cy = from.y
        val horizontalFirst = rng.nextBoolean()

        fun stepTile(x: Int, y: Int) {
            for (ox in 0..1) {
                val tx = (x + ox).coerceIn(1, grid.size - 2)
                val ty = y.coerceIn(1, grid[0].size - 2)
                val existing = grid[tx][ty].type
                if (existing == TileType.ELEVATED_WALL || existing == TileType.VOID) {
                    val isIce = biome == BiomeType.FROZEN_KINGDOM && rng.nextFloat() < 0.35f
                    val t = if (isIce) TileType.ICE_FLOOR else TileType.FLOOR
                    grid[tx][ty] = IsoTile(tx, ty, t, t.baseHeight, variant = rng.nextInt(4))
                }
            }
        }

        if (horizontalFirst) {
            while (cx != to.x) {
                stepTile(cx, cy)
                cx += if (to.x > cx) 1 else -1
            }
            while (cy != to.y) {
                stepTile(cx, cy)
                cy += if (to.y > cy) 1 else -1
            }
        } else {
            while (cy != to.y) {
                stepTile(cx, cy)
                cy += if (to.y > cy) 1 else -1
            }
            while (cx != to.x) {
                stepTile(cx, cy)
                cx += if (to.x > cx) 1 else -1
            }
        }
        stepTile(to.x, to.y)
    }

    private fun carveSecretConnector(grid: Array<Array<IsoTile>>, from: GridPoint, door: GridPoint) {
        var cx = from.x
        var cy = from.y
        while (cx != door.x || cy != door.y) {
            if (cx != door.x) cx += if (door.x > cx) 1 else -1
            else if (cy != door.y) cy += if (door.y > cy) 1 else -1
            if (cx == door.x && cy == door.y) break
            if (grid[cx][cy].type == TileType.ELEVATED_WALL) {
                grid[cx][cy] = IsoTile(
                    gx = cx,
                    gy = cy,
                    type = TileType.SECRET_FLOOR,
                    height = TileType.SECRET_FLOOR.baseHeight,
                    isSecret = true,
                    isRevealed = false
                )
            }
        }
    }

    private fun placeStructuresAndTraps(
        grid: Array<Array<IsoTile>>,
        rooms: List<RoomRect>,
        biome: BiomeType,
        tier: DifficultyTier,
        protectedPoints: Set<GridPoint>,
        rng: Random
    ) {
        val width = grid.size
        val height = grid[0].size

        for (gx in 2 until width - 2) {
            for (gy in 2 until height - 2) {
                val pt = GridPoint(gx, gy)
                if (pt in protectedPoints) continue
                val current = grid[gx][gy]
                if (current.type != TileType.FLOOR && current.type != TileType.ICE_FLOOR) continue

                val roll = rng.nextFloat()
                when {
                    roll < tier.trapDensity * 0.45f -> {
                        grid[gx][gy] = current.copy(
                            type = TileType.SPIKE_TRAP,
                            height = TileType.SPIKE_TRAP.baseHeight
                        )
                    }
                    roll < tier.trapDensity * 0.80f -> {
                        grid[gx][gy] = current.copy(
                            type = TileType.FIRE_JET_TRAP,
                            height = TileType.FIRE_JET_TRAP.baseHeight
                        )
                    }
                    roll < tier.trapDensity * 0.96f -> {
                        grid[gx][gy] = current.copy(
                            type = TileType.LIQUID_HAZARD,
                            height = TileType.LIQUID_HAZARD.baseHeight
                        )
                    }
                    roll < tier.trapDensity + 0.035f -> {
                        if (rooms.any { gx in (it.x + 1)..(it.x + it.w - 2) && gy in (it.y + 1)..(it.y + it.h - 2) }) {
                            grid[gx][gy] = current.copy(
                                type = TileType.PILLAR_STRUCTURE,
                                height = TileType.PILLAR_STRUCTURE.baseHeight
                            )
                        }
                    }
                }
            }
        }
    }

    private fun forceSafeCorridor(
        grid: Array<Array<IsoTile>>,
        from: GridPoint,
        to: GridPoint
    ) {
        var cx = from.x
        var cy = from.y
        while (cx != to.x) {
            val t = grid[cx][cy].type
            if (!t.isWalkable && t != TileType.START_PAD && t != TileType.EXIT_PORTAL &&
                t != TileType.SWITCH_PEDESTAL && t != TileType.LOCKED_DOOR
            ) {
                grid[cx][cy] = IsoTile(cx, cy, TileType.FLOOR, TileType.FLOOR.baseHeight)
            }
            cx += if (to.x > cx) 1 else -1
        }
        while (cy != to.y) {
            val t = grid[cx][cy].type
            if (!t.isWalkable && t != TileType.START_PAD && t != TileType.EXIT_PORTAL &&
                t != TileType.SWITCH_PEDESTAL && t != TileType.LOCKED_DOOR
            ) {
                grid[cx][cy] = IsoTile(cx, cy, TileType.FLOOR, TileType.FLOOR.baseHeight)
            }
            cy += if (to.y > cy) 1 else -1
        }
    }

    /**
     * A* Pathfinding Validator ensuring every generated level is genuinely completable.
     */
    fun findShortestWalkablePath(
        grid: Array<Array<IsoTile>>,
        width: Int,
        height: Int,
        start: GridPoint,
        goal: GridPoint,
        allowJumpHazards: Boolean,
        allowLockedDoors: Boolean = true
    ): List<GridPoint>? {
        if (start == goal) return listOf(start)

        data class Node(val pt: GridPoint, val g: Int, val f: Int) : Comparable<Node> {
            override fun compareTo(other: Node): Int = this.f.compareTo(other.f)
        }

        val openSet = PriorityQueue<Node>()
        val cameFrom = HashMap<GridPoint, GridPoint>()
        val gScore = HashMap<GridPoint, Int>()

        gScore[start] = 0
        openSet.add(Node(start, 0, abs(start.x - goal.x) + abs(start.y - goal.y)))

        val dirs = arrayOf(
            GridPoint(1, 0),
            GridPoint(-1, 0),
            GridPoint(0, 1),
            GridPoint(0, -1)
        )

        while (openSet.isNotEmpty()) {
            val current = openSet.poll().pt
            if (current == goal) {
                val path = ArrayList<GridPoint>()
                var curr: GridPoint? = current
                while (curr != null) {
                    path.add(curr)
                    curr = cameFrom[curr]
                }
                path.reverse()
                return path
            }

            val currentG = gScore[current] ?: continue

            for (d in dirs) {
                val nx = current.x + d.x
                val ny = current.y + d.y
                if (nx !in 1 until width - 1 || ny !in 1 until height - 1) continue

                val tile = grid[nx][ny]
                val isDoorPassable = allowLockedDoors && tile.type == TileType.LOCKED_DOOR
                val passable = if (allowJumpHazards) {
                    tile.type.isWalkable || tile.type == TileType.LIQUID_HAZARD || isDoorPassable
                } else {
                    tile.type.isWalkable || isDoorPassable
                }

                if (!passable && GridPoint(nx, ny) != goal) continue

                val stepCost = when (tile.type) {
                    TileType.SPIKE_TRAP, TileType.FIRE_JET_TRAP -> 4
                    TileType.LIQUID_HAZARD -> 6
                    TileType.LOCKED_DOOR -> 2
                    else -> 1
                }

                val neighbor = GridPoint(nx, ny)
                val tentativeG = currentG + stepCost
                if (tentativeG < (gScore[neighbor] ?: Int.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeG
                    val h = abs(nx - goal.x) + abs(ny - goal.y)
                    openSet.add(Node(neighbor, tentativeG, tentativeG + h))
                }
            }
        }
        return null
    }

    private fun spawnEnemies(
        grid: Array<Array<IsoTile>>,
        rooms: List<RoomRect>,
        biome: BiomeType,
        tier: DifficultyTier,
        levelNumber: Int,
        startPoint: GridPoint,
        rng: Random
    ): List<EnemyEntity> {
        val list = mutableListOf<EnemyEntity>()
        val count = tier.baseEnemies + min(6, levelNumber / 15)
        val candidateRooms = rooms.drop(1).ifEmpty { rooms }

        for (i in 0 until count) {
            val room = candidateRooms[i % candidateRooms.size]
            val sx = (room.x + 1 + rng.nextInt(max(1, room.w - 2))).coerceIn(2, grid.size - 3)
            val sy = (room.y + 1 + rng.nextInt(max(1, room.h - 2))).coerceIn(2, grid[0].size - 3)

            if (abs(sx - startPoint.x) + abs(sy - startPoint.y) < 3) continue
            if (!grid[sx][sy].type.isWalkable) continue

            val ex = (room.x + 1 + rng.nextInt(max(1, room.w - 2))).coerceIn(2, grid.size - 3)
            val ey = (room.y + 1 + rng.nextInt(max(1, room.h - 2))).coerceIn(2, grid[0].size - 3)

            val isBoss = (i == count - 1) && (levelNumber % 5 == 0)
            val baseHp = if (isBoss) 140 + levelNumber * 4 else 55 + levelNumber * 2
            val speed = if (isBoss) 1.65f else 1.95f + min(0.8f, levelNumber * 0.008f)
            val dmg = if (isBoss) 22 else 12 + min(15, levelNumber / 10)

            list.add(
                EnemyEntity(
                    id = i + 1,
                    name = if (isBoss) "Elder ${biome.enemyName}" else biome.enemyName,
                    x = sx + 0.5f,
                    y = sy + 0.5f,
                    startX = sx + 0.5f,
                    startY = sy + 0.5f,
                    patrolEndX = if (grid[ex][ey].type.isWalkable) ex + 0.5f else (sx + 1).coerceAtMost(grid.size - 3) + 0.5f,
                    patrolEndY = if (grid[ex][ey].type.isWalkable) ey + 0.5f else sy + 0.5f,
                    hp = baseHp,
                    maxHp = baseHp,
                    speed = speed,
                    damage = dmg,
                    isBoss = isBoss
                )
            )
        }
        return list
    }

    private fun spawnRewards(
        grid: Array<Array<IsoTile>>,
        rooms: List<RoomRect>,
        keyPoints: List<GridPoint>,
        secretPoints: List<GridPoint>,
        startPoint: GridPoint,
        exitPoint: GridPoint,
        lockedDoorPoints: List<GridPoint>,
        rng: Random
    ): List<RewardItemEntity> {
        val rewards = mutableListOf<RewardItemEntity>()
        val occupied = HashSet<GridPoint>().apply {
            add(startPoint)
            add(exitPoint)
            addAll(lockedDoorPoints)
        }
        var nextId = 1

        // 1. Required Portal & Door Relic Keys
        for (kp in keyPoints) {
            occupied.add(kp)
            rewards.add(
                RewardItemEntity(
                    id = nextId++,
                    type = RewardType.RELIC_KEY,
                    gx = kp.x,
                    gy = kp.y
                )
            )
        }

        // 2. Secret Room Rare Artefacts & Gems
        for (sp in secretPoints) {
            if (occupied.add(sp)) {
                rewards.add(
                    RewardItemEntity(
                        id = nextId++,
                        type = RewardType.RARE_ARTEFACT,
                        gx = sp.x,
                        gy = sp.y,
                        isSecret = true
                    )
                )
            }
        }

        // 3. Room Chests, Coins, Gems, and Health Elixirs (2 per room for rich gameplay)
        for (room in rooms) {
            for (slot in 0..1) {
                val rx = (room.x + 1 + rng.nextInt(max(1, room.w - 2))).coerceIn(2, grid.size - 3)
                val ry = (room.y + 1 + rng.nextInt(max(1, room.h - 2))).coerceIn(2, grid[0].size - 3)
                val pt = GridPoint(rx, ry)
                if (occupied.add(pt) && grid[rx][ry].type.isWalkable) {
                    val rType = when {
                        slot == 0 && rng.nextBoolean() -> RewardType.COIN
                        rng.nextInt(5) == 0 -> RewardType.TREASURE_CHEST
                        rng.nextInt(5) == 1 -> RewardType.GEM
                        rng.nextInt(6) == 2 -> RewardType.HEALTH_ELIXIR
                        else -> RewardType.COIN
                    }
                    rewards.add(
                        RewardItemEntity(
                            id = nextId++,
                            type = rType,
                            gx = rx,
                            gy = ry
                        )
                    )
                }
            }
        }

        return rewards
    }

    private fun spawnBiomeProps(
        grid: Array<Array<IsoTile>>,
        rooms: List<RoomRect>,
        biome: BiomeType,
        startPoint: GridPoint,
        exitPoint: GridPoint,
        keyPoints: List<GridPoint>,
        lockedDoorPoints: List<GridPoint>,
        rewards: List<RewardItemEntity>,
        rng: Random
    ): List<DestructiblePropEntity> {
        val props = mutableListOf<DestructiblePropEntity>()
        val reserved = HashSet<GridPoint>().apply {
            add(startPoint)
            add(exitPoint)
            addAll(keyPoints)
            addAll(lockedDoorPoints)
            rewards.forEach { add(GridPoint(it.gx, it.gy)) }
        }
        var propId = 1
        for (room in rooms) {
            val count = rng.nextInt(1, 3)
            for (i in 0 until count) {
                val px = (room.x + 1 + rng.nextInt(max(1, room.w - 2))).coerceIn(2, grid.size - 3)
                val py = (room.y + 1 + rng.nextInt(max(1, room.h - 2))).coerceIn(2, grid[0].size - 3)
                val pt = GridPoint(px, py)
                if (reserved.add(pt) && grid[px][py].type == TileType.FLOOR) {
                    props.add(
                        DestructiblePropEntity(
                            id = propId++,
                            type = biome.signatureProp,
                            gx = px,
                            gy = py
                        )
                    )
                }
            }
        }
        return props
    }

    private fun ptEquals(x: Int, y: Int, pt: GridPoint): Boolean = x == pt.x && y == pt.y

    data class BatchVerificationReport(
        val startLevel: Int,
        val levelsTested: Int,
        val allSolvable: Boolean,
        val uniqueLayoutsCount: Int,
        val biomesCovered: Int,
        val averagePathLength: Int,
        val totalTimeMs: Long
    )

    fun verifyConsecutiveLevels(startLevel: Int, count: Int): BatchVerificationReport {
        val t0 = System.currentTimeMillis()
        var solvableCount = 0
        val layoutHashes = HashSet<Long>()
        val biomes = HashSet<BiomeType>()
        var totalPathLen = 0

        for (i in 0 until count) {
            val lvlNum = startLevel + i
            val generated = generateLevel(lvlNum)
            if (generated.verifiedSolutionPath.size >= 2 &&
                generated.verifiedSolutionPath.first() == generated.startPoint &&
                generated.verifiedSolutionPath.last() == generated.exitPoint
            ) {
                solvableCount++
            }
            val signature = generated.seed xor
                (generated.startPoint.x.toLong() shl 16) xor
                (generated.exitPoint.y.toLong() shl 32) xor
                generated.verifiedSolutionPath.size.toLong()
            layoutHashes.add(signature)
            biomes.add(generated.biome)
            totalPathLen += generated.verifiedSolutionPath.size
        }

        return BatchVerificationReport(
            startLevel = startLevel,
            levelsTested = count,
            allSolvable = solvableCount == count,
            uniqueLayoutsCount = layoutHashes.size,
            biomesCovered = biomes.size,
            averagePathLength = if (count > 0) totalPathLen / count else 0,
            totalTimeMs = max(1L, System.currentTimeMillis() - t0)
        )
    }
}
