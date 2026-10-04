package com.jonkryl.cutledger.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CuttingTest {
    private fun job(stock: List<Long>, parts: List<Long>, kerf: Long = 0, trim: Long = 0) = Job("test", stock.mapIndexed { i, l -> Stock(i, l) }, parts.mapIndexed { i, l -> Piece(i, "P$i", l) }, kerf, trim)
    private fun score(p: Plan) = listOf(p.placedLength, p.placedCount.toLong(), -p.usedStockLength, -p.bars.size.toLong())
    private fun invariant(p: Plan) {
        val all = p.bars.flatMap { it.pieces } + p.unplaced
        assertEquals(p.job.pieces.map { it.id }.sorted(), all.map { it.id }.sorted())
        assertEquals(p.bars.size, p.bars.map { it.stock.id }.distinct().size)
        assertTrue(p.bars.all { it.stock in p.job.stocks && it.remainder >= 0 })
        p.bars.forEach { b -> assertEquals(b.stock.length, 2 * b.trim + b.partLength + b.sawLoss + b.remainder) }
    }
    @Test fun exactFillNeedsNoFinalSawCut() {
        val p = CuttingPlanner.solve(job(listOf(1000), listOf(1000), 3))
        assertEquals(1, p.placedCount); assertEquals(0, p.sawLoss); assertEquals(0, p.remainder); invariant(p)
    }
    @Test fun gapSmallerThanBladeDoesNotFit() {
        val p = CuttingPlanner.solve(job(listOf(1001), listOf(1000), 3))
        assertEquals(0, p.placedCount); assertEquals(1, p.unplaced.size)
    }
    @Test fun exactFillStillIncludesIntermediateKerf() {
        val p = CuttingPlanner.solve(job(listOf(1003), listOf(500, 500), 3))
        assertEquals(2, p.placedCount); assertEquals(3, p.sawLoss); assertEquals(0, p.remainder); invariant(p)
    }
    @Test fun cutBeforeLeftoverIsCounted() {
        val p = CuttingPlanner.solve(job(listOf(1100), listOf(500, 500), 3))
        assertEquals(6, p.sawLoss); assertEquals(94, p.remainder); invariant(p)
    }
    @Test fun bothEndTrimsAreRemoved() {
        val p = CuttingPlanner.solve(job(listOf(1200), listOf(1000), 3, 20))
        assertEquals(157, p.remainder); invariant(p)
    }
    @Test fun exactSearchRepairsGreedyCounterexample() {
        val p = CuttingPlanner.solve(job(listOf(10, 10), listOf(6, 5, 3, 2, 2, 2)))
        assertEquals(6, p.placedCount); assertTrue(p.exhaustive); assertEquals(0, p.remainder); invariant(p)
    }
    @Test fun finiteInventoryAndOversizeRemainVisible() {
        val p = CuttingPlanner.solve(job(listOf(1000), listOf(1100, 600, 600)))
        assertEquals(1, p.placedCount); assertEquals(2, p.unplaced.size); invariant(p)
    }
    @Test fun exactSearchChoosesLessRawStockWhenAllPartsFit() {
        val p = CuttingPlanner.solve(job(listOf(2000, 1000, 1000), listOf(600, 300)))
        assertEquals(1000, p.usedStockLength); assertEquals(1, p.bars.size); invariant(p)
    }
    @Test fun largerJobsAreHonestlyHeuristic() {
        val p = CuttingPlanner.solve(job(List(30) { 1000 }, List(50) { 250 }))
        assertEquals(50, p.placedCount); assertFalse(p.exhaustive); invariant(p)
    }
    @Test fun decimalsAndCommaUseExactMicrometres() {
        assertEquals(123456, Lengths.parse("123,456")); assertEquals("123.456", Lengths.format(123456)); assertEquals(0, Lengths.parse("0", true))
    }
    @Test fun invalidNumbersAndZeroPartsAreRejected() {
        for (s in listOf("NaN", "-1", "0", "1e6", "0.0001", "1000001", "1.2.3")) {
            try { Lengths.parse(s); fail(s) } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun parserEnforcesQuantitiesAndPositiveUsableStock() {
        val p = JobParser.parse(Draft("Shelf", "2400;2\n1200;1", "Long;900;3\nShort;300;2", "3", "10"))
        assertEquals(3, p.stocks.size); assertEquals(5, p.pieces.size)
        for (d in listOf(Draft(stock = "10;1", parts = "P;1;1", trim = "5"), Draft(stock = "10;101", parts = "P;1;1"), Draft(stock = "10;1", parts = "P;1;121"))) {
            try { JobParser.parse(d); fail() } catch (_: InputIssue) { }
        }
    }
    @Test fun draftRoundTripKeepsUnfinishedUserInput() {
        val d = Draft("Shelf", "2400;", "Shelf;900;", "3,2", "0")
        assertEquals(d, DraftCodec.decode(DraftCodec.encode(d))); assertNull(DraftCodec.decode("broken")); assertNull(DraftCodec.decode("{\"schema\":9}"))
    }
    @Test fun csvRetainsQuantitiesPositionsAndEscapesLabels() {
        val j = job(listOf(1000), listOf(300, 300), 3).copy(name = "=SUM(1;2)", pieces = listOf(Piece(0, "\"A\"", 300), Piece(1, "=2+2", 300)))
        val csv = PlanCsv.export(CuttingPlanner.solve(j))
        assertTrue(csv.contains("\"'=SUM(1;2)\"")); assertTrue(csv.contains("\"\"\"A\"\"\"")); assertTrue(csv.contains("\"'=2+2\"")); assertTrue(csv.contains(";303;603"))
    }
    @Test fun independentExhaustiveOracleMatchesFortyTinyFiniteCases() {
        val random = Random(23041)
        repeat(40) {
            val j = job(List(3) { random.nextLong(10, 23) }, List(6) { random.nextLong(2, 13) }, random.nextLong(0, 3), random.nextLong(0, 2))
            var optimum = listOf(0L, 0L, 0L, 0L)
            val assignments = IntArray(j.pieces.size)
            fun greater(a: List<Long>, b: List<Long>): Boolean {
                for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
                return false
            }
            fun enumerate(i: Int) {
                if (i < assignments.size) {
                    for (s in -1 until j.stocks.size) { assignments[i] = s; enumerate(i + 1) }
                    return
                }
                var length = 0L; var count = 0L; var raw = 0L; var bars = 0L
                for (s in j.stocks.indices) {
                    val indices = assignments.indices.filter { assignments[it] == s }
                    if (indices.isEmpty()) continue
                    val partSum = indices.sumOf { j.pieces[it].length }
                    val available = j.stocks[s].length - 2 * j.trim
                    val withoutLast = partSum + j.kerf * (indices.size - 1)
                    val loss = if (withoutLast == available) 0 else j.kerf
                    if (withoutLast + loss > available) return
                    length += partSum; count += indices.size; raw += j.stocks[s].length; bars++
                }
                val value = listOf(length, count, -raw, -bars)
                if (greater(value, optimum)) optimum = value
            }
            enumerate(0)
            val plan = CuttingPlanner.solve(j)
            assertTrue(plan.exhaustive); assertEquals(optimum, score(plan)); invariant(plan)
        }
    }
}
