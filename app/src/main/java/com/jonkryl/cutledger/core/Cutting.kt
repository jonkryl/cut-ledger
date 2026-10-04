package com.jonkryl.cutledger.core

import java.math.BigDecimal

data class Draft(val name: String = "", val stock: String = "", val parts: String = "", val kerf: String = "3", val trim: String = "0")
data class Piece(val id: Int, val label: String, val length: Long)
data class Stock(val id: Int, val length: Long)
data class Job(val name: String, val stocks: List<Stock>, val pieces: List<Piece>, val kerf: Long, val trim: Long)
enum class Field { STOCK, PARTS, KERF, TRIM }
class InputIssue(val field: Field, val line: Int = 0) : IllegalArgumentException()

/** Integer micrometres avoid floating point rounding at saw-width boundaries. */
object Lengths {
    fun parse(text: String, zeroAllowed: Boolean = false): Long {
        val value = text.trim()
        require(Regex("[0-9]{1,7}(?:[.,][0-9]{1,3})?").matches(value))
        val number = BigDecimal(value.replace(',', '.')).movePointRight(3).longValueExact()
        require(number in (if (zeroAllowed) 0L else 1L)..1_000_000_000L)
        return number
    }
    fun format(length: Long): String = BigDecimal.valueOf(length, 3).stripTrailingZeros().toPlainString()
}

object JobParser {
    fun parse(draft: Draft): Job {
        fun setting(text: String, field: Field) = try { Lengths.parse(text, true) } catch (_: IllegalArgumentException) { throw InputIssue(field) }
        val kerf = setting(draft.kerf, Field.KERF)
        val trim = setting(draft.trim, Field.TRIM)
        val stocks = mutableListOf<Stock>()
        val pieces = mutableListOf<Piece>()
        fun rows(text: String, field: Field): List<String> {
            if (text.length > 20_000) throw InputIssue(field)
            val rows = text.lineSequence().filter { it.isNotBlank() }.toList()
            if (rows.isEmpty() || rows.size > 60) throw InputIssue(field)
            return rows
        }
        rows(draft.stock, Field.STOCK).forEachIndexed { i, row ->
            try {
                val values = row.split(';').map { it.trim() }
                require(values.size == 2)
                val length = Lengths.parse(values[0])
                require(length > 2 * trim)
                val count = values[1].toInt()
                require(count in 1..100 && stocks.size + count <= 100)
                repeat(count) { stocks.add(Stock(stocks.size, length)) }
            } catch (_: IllegalArgumentException) { throw InputIssue(Field.STOCK, i + 1) }
        }
        rows(draft.parts, Field.PARTS).forEachIndexed { i, row ->
            try {
                val values = row.split(';').map { it.trim() }
                require(values.size == 3 && values[0].length in 1..40)
                val length = Lengths.parse(values[1])
                val count = values[2].toInt()
                require(count in 1..120 && pieces.size + count <= 120)
                repeat(count) { pieces.add(Piece(pieces.size, values[0], length)) }
            } catch (_: IllegalArgumentException) { throw InputIssue(Field.PARTS, i + 1) }
        }
        return Job(draft.name.trim().take(60), stocks.toList(), pieces.toList(), kerf, trim)
    }
}

data class Bar(val stock: Stock, val pieces: List<Piece>, val kerf: Long, val trim: Long) {
    val usable: Long get() = stock.length - 2 * trim
    val partLength: Long get() = pieces.sumOf { it.length }
    val interPieceLoss: Long get() = kerf * (pieces.size - 1).coerceAtLeast(0)
    val finalCut: Boolean get() = pieces.isNotEmpty() && partLength + interPieceLoss != usable
    val sawLoss: Long get() = interPieceLoss + if (finalCut) kerf else 0
    val remainder: Long get() = usable - partLength - sawLoss
}
data class Plan(val job: Job, val bars: List<Bar>, val unplaced: List<Piece>, val exhaustive: Boolean, val visited: Int) {
    val placedCount: Int get() = bars.sumOf { it.pieces.size }
    val placedLength: Long get() = bars.sumOf { it.partLength }
    val usedStockLength: Long get() = bars.sumOf { it.stock.length }
    val remainder: Long get() = bars.sumOf { it.remainder }
    val sawLoss: Long get() = bars.sumOf { it.sawLoss }
}

/** Finite stock, homogeneous material, straight 1D cuts only. */
object CuttingPlanner {
    private const val NODE_LIMIT = 80_000
    fun solve(job: Job): Plan {
        require(job.stocks.size in 1..100 && job.pieces.size in 1..120)
        require(job.kerf in 0..1_000_000_000L && job.trim in 0..1_000_000_000L)
        require(job.stocks.all { it.length in 1..1_000_000_000L && it.length > 2 * job.trim })
        require(job.pieces.all { it.length in 1..1_000_000_000L })
        require(job.stocks.map { it.id }.distinct().size == job.stocks.size)
        require(job.pieces.map { it.id }.distinct().size == job.pieces.size)
        val ordered = job.pieces.sortedWith(compareByDescending<Piece> { it.length }.thenBy { it.id })
        fun fits(stock: Stock, assigned: List<Piece>, next: Piece): Boolean {
            val count = assigned.size + 1
            val usable = stock.length - 2 * job.trim
            val base = assigned.sumOf { it.length } + next.length + job.kerf * (count - 1)
            return base == usable || base + job.kerf <= usable
        }
        fun plan(bins: List<List<Piece>>, exhaustive: Boolean = false, visited: Int = 0): Plan {
            val bars = bins.mapIndexedNotNull { i, p -> if (p.isEmpty()) null else Bar(job.stocks[i], p.toList(), job.kerf, job.trim) }
            val assigned = bars.flatMap { it.pieces }.map { it.id }.toSet()
            return Plan(job, bars, job.pieces.filter { it.id !in assigned }, exhaustive, visited)
        }
        fun better(a: Plan, b: Plan): Boolean = when {
            a.placedLength != b.placedLength -> a.placedLength > b.placedLength
            a.placedCount != b.placedCount -> a.placedCount > b.placedCount
            a.usedStockLength != b.usedStockLength -> a.usedStockLength < b.usedStockLength
            else -> a.bars.size < b.bars.size
        }
        var best = plan(List(job.stocks.size) { emptyList() })
        for (ascending in listOf(false, true)) for (preferOpen in listOf(false, true)) {
            val bins = MutableList(job.stocks.size) { mutableListOf<Piece>() }
            for (piece in if (ascending) ordered.reversed() else ordered) {
                val eligible = job.stocks.indices.filter { fits(job.stocks[it], bins[it], piece) }
                val selected = eligible.minWithOrNull(compareBy<Int> {
                    if (preferOpen && bins[it].isEmpty()) 1 else 0
                }.thenBy {
                    val bar = Bar(job.stocks[it], bins[it] + piece, job.kerf, job.trim)
                    bar.remainder
                }.thenBy { job.stocks[it].length }.thenBy { it })
                if (selected != null) bins[selected].add(piece)
            }
            val candidate = plan(bins)
            if (better(candidate, best)) best = candidate
        }
        var visited = 0
        var limited = false
        val exactEligible = ordered.size <= 14 && job.stocks.size <= 20
        if (exactEligible) {
            val bins = MutableList(job.stocks.size) { mutableListOf<Piece>() }
            val suffix = LongArray(ordered.size + 1)
            for (i in ordered.indices.reversed()) suffix[i] = suffix[i + 1] + ordered[i].length
            fun search(index: Int, placed: Long, used: Long) {
                if (limited) return
                visited++
                if (visited > NODE_LIMIT) { limited = true; return }
                if (placed + suffix[index] < best.placedLength) return
                if (best.unplaced.isEmpty() && used > best.usedStockLength) return
                if (index == ordered.size) {
                    val candidate = plan(bins)
                    if (better(candidate, best)) best = candidate
                    return
                }
                val piece = ordered[index]
                val equivalent = HashSet<Triple<Long, Long, Int>>()
                for (s in job.stocks.indices.sortedBy { job.stocks[it].length }) {
                    val state = Triple(job.stocks[s].length, bins[s].sumOf { it.length }, bins[s].size)
                    if (!equivalent.add(state) || !fits(job.stocks[s], bins[s], piece)) continue
                    val wasEmpty = bins[s].isEmpty()
                    bins[s].add(piece)
                    search(index + 1, placed + piece.length, used + if (wasEmpty) job.stocks[s].length else 0)
                    bins[s].removeAt(bins[s].lastIndex)
                    if (limited) return
                }
                search(index + 1, placed, used)
            }
            search(0, 0, 0)
        }
        return best.copy(exhaustive = exactEligible && !limited, visited = visited)
    }
}

object PlanCsv {
    private fun cell(text: String): String {
        val clean = text.replace('\r', ' ').replace('\n', ' ')
        val safe = if (clean.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$clean" else clean
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    fun export(plan: Plan): String = buildString {
        append("record;bar;stock_length_mm;part;part_length_mm;start_mm;end_mm;remainder_mm\n")
        append("project;;;").append(cell(plan.job.name)).append(";;;;\n")
        plan.bars.forEachIndexed { index, bar ->
            var start = bar.trim
            for (part in bar.pieces) {
                append("part;").append(index + 1).append(';').append(Lengths.format(bar.stock.length)).append(';').append(cell(part.label)).append(';')
                append(Lengths.format(part.length)).append(';').append(Lengths.format(start)).append(';').append(Lengths.format(start + part.length)).append(";\n")
                start += part.length + bar.kerf
            }
            append("remainder;").append(index + 1).append(';').append(Lengths.format(bar.stock.length)).append(";;;;;").append(Lengths.format(bar.remainder)).append('\n')
        }
        for (part in plan.unplaced) append("unplaced;;;").append(cell(part.label)).append(';').append(Lengths.format(part.length)).append(";;;\n")
    }
}
