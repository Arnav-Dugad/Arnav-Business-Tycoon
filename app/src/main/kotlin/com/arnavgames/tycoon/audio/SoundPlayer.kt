package com.arnavgames.tycoon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.arnavgames.tycoon.R
import com.arnavgames.tycoon.core.engine.GameEvent

/** Short sound effects (synthesized for this game by tools/audio/gen_sfx.py) at the player's chosen volume. */
class SoundPlayer(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = mapOf(
        "tap" to pool.load(context, R.raw.sfx_tap, 1), "coin" to pool.load(context, R.raw.sfx_coin, 1),
        "level" to pool.load(context, R.raw.sfx_level, 1), "success" to pool.load(context, R.raw.sfx_success, 1),
        "error" to pool.load(context, R.raw.sfx_error, 1), "bid" to pool.load(context, R.raw.sfx_bid, 1),
        "gavel" to pool.load(context, R.raw.sfx_gavel, 1),
    )
    private var lastTap = 0L

    fun play(name: String, volume: Float) {
        if (volume <= 0f) return
        val id = ids[name] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    /** Maps game events to sounds (taps are throttled so rapid tapping doesn't become noise). */
    fun onEvent(e: GameEvent, volume: Float) {
        val name = when (e) {
            is GameEvent.HustleEarned -> { val now = System.currentTimeMillis(); if (now - lastTap < 70) return; lastTap = now; "tap" }
            is GameEvent.Collected, is GameEvent.MarketPayout -> "coin"
            is GameEvent.LevelsBought, is GameEvent.TierReached -> "level"
            is GameEvent.ManagerHired, is GameEvent.BusinessOpened, is GameEvent.MissionClaimed, is GameEvent.ChapterCompleted, is GameEvent.ExpansionCompleted,
            is GameEvent.ConglomerateFormed, is GameEvent.HoldingFormed, is GameEvent.PropertyBought, is GameEvent.LuxuryBought, is GameEvent.CrownPassed,
            is GameEvent.IpoLaunched, is GameEvent.ProductGranted, is GameEvent.TaxFiled -> "success"
            is GameEvent.AuctionBid -> "bid"
            is GameEvent.AuctionWon, is GameEvent.AuctionLost -> "gavel"
            is GameEvent.PaymentMissed, is GameEvent.Foreclosure -> "error"
            else -> return
        }
        play(name, volume)
    }
}
