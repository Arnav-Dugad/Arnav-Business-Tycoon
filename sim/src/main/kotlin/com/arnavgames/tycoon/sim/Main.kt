package com.arnavgames.tycoon.sim

import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.GameEngine
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Headless balance simulator (docs/ECONOMY.md §10).
 *
 * ./gradlew :sim:run --args="--seeds 200 --days 2 --bots active,idle,cautious,poor_choices --out docs/balance/auto"
 */
fun main(args: Array<String>) {
    val opts = args.toList().chunked(2).filter { it.size == 2 }.associate { it[0].removePrefix("--") to it[1] }
    val seeds = opts["seeds"]?.toInt() ?: 100
    val days = opts["days"]?.toInt() ?: 2
    // Default to a quarter of the cores so a batch never makes the laptop unusable; raise with --threads when idle.
    val threads = opts["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() / 4).coerceIn(1, 6)
    val botNames = (opts["bots"] ?: "active,idle,cautious,poor_choices").split(",").map { it.trim() }
    val outDir = File(opts["out"] ?: "docs/balance/${LocalDate.now()}")
    val gitSha = opts["sha"] ?: "local"

    val loaded = ContentLoader.loadFromRepo()
    require(loaded.issues.isEmpty()) { "content has issues: ${loaded.issues}" }
    val engine = GameEngine(loaded.pack)
    val sim = Simulation(engine, loaded.hash, trace = opts["trace"] == "1")
    val bots = botNames.map { botByName(it) }

    println("Simulating ${bots.size} bots × $seeds seeds × $days days on $threads threads…")
    val started = System.currentTimeMillis()
    val pool = Executors.newFixedThreadPool(threads)
    val done = AtomicInteger()
    val total = bots.size * seeds
    val futures = bots.flatMap { bot ->
        (1..seeds).map { seed ->
            pool.submit(Callable {
                sim.run(bot, seed.toLong(), days).also {
                    val n = done.incrementAndGet()
                    if (n % maxOf(1, total / 10) == 0) println("  $n / $total")
                }
            })
        }
    }
    val results = futures.map { it.get() }
    pool.shutdown()
    val secs = (System.currentTimeMillis() - started) / 1000.0
    val config = "${bots.size} bots × $seeds seeds × $days days · ${"%.1f".format(secs)} s on $threads threads"
    val (pass, fail) = Report.write(outDir, results, config, gitSha)
    println("Report: ${File(outDir, "summary.md").path}")
    println("Targets: $pass passed, $fail failed · errors: ${results.count { it.error != null }} · $config")
}

fun botByName(name: String): Bot = when (name) {
    "active" -> ActiveBot()
    "idle" -> IdleBot()
    "cautious" -> cautiousBot()
    "poor_choices" -> PoorChoicesBot()
    "risky_trader" -> RiskyTraderBot()
    else -> error("unknown bot '$name' (available: active, idle, cautious, poor_choices)")
}
