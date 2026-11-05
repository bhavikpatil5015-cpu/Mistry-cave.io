package com.example

import com.example.engine.ProceduralLevelGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun verifyProceduralEscapeEngineIntegration() {
        val report = ProceduralLevelGenerator.verifyConsecutiveLevels(startLevel = 1, count = 10)
        assertTrue(report.allSolvable)
    }
}
