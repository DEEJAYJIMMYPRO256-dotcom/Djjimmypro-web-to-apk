package com.example.dj.model

enum class SamplePadBank(val label: String) {
  FX_SIRENS("FX & Sirens"),
  DRUMS_909("909 Drums"),
  SWEEPS_HYPE("Sweeps & Hype"),
  MC_DROPS("MC Drops"),
  DJ_TAGS("DJ Tags"),
  ALL("All (65)"),
}

data class SamplePadEffect(
  val id: String,
  val slot: Int,
  val title: String,
  val assetFileName: String,
  val bank: SamplePadBank,
  val colorHex: Long,
  val fallbackSynth: SamplerSound,
)

object SamplePadCatalog {
  val allPads: List<SamplePadEffect> =
    listOf(
      // Bank 1: FX & Sirens (8 pads)
      SamplePadEffect("fx_siren", 1, "Siren Drop", "Siren_30e05611.ogg", SamplePadBank.FX_SIRENS, 0xFF00B0FF, SamplerSound.SIRENI),
      SamplePadEffect("fx_police_siren", 2, "Police Siren", "Police_Siren_ef16b006.ogg", SamplePadBank.FX_SIRENS, 0xFFFF3B5C, SamplerSound.SIRENI),
      SamplePadEffect("fx_air_horn", 3, "Air Horn", "Horn_20a20b39.ogg", SamplePadBank.FX_SIRENS, 0xFFFF9100, SamplerSound.WHISTLE),
      SamplePadEffect("fx_lazer", 4, "Laser Zap", "lazer__0ee5d13f.ogg", SamplePadBank.FX_SIRENS, 0xFF00E676, SamplerSound.WHISTLE),
      SamplePadEffect("fx_long_lazer", 5, "Long Laser", "Long_lezer__f846a263.ogg", SamplePadBank.FX_SIRENS, 0xFFAA00FF, SamplerSound.SIRENI),
      SamplePadEffect("fx_gunshot", 6, "Gunshot FX", "Gunshot_6ea6bad9.ogg", SamplePadBank.FX_SIRENS, 0xFFFF5252, SamplerSound.APPLAUSE),
      SamplePadEffect("fx_gong", 7, "Gong Swell", "Gong_b9388598.ogg", SamplePadBank.FX_SIRENS, 0xFFFFD600, SamplerSound.CROWD),
      SamplePadEffect("fx_punch", 8, "Punch Drop", "Punch_3d4543b1.ogg", SamplePadBank.FX_SIRENS, 0xFF00E5FF, SamplerSound.APPLAUSE),

      // Bank 2: 909 Drums & Hits (8 pads)
      SamplePadEffect("drum_909_kick", 1, "909 Kick", "909_Kick_a88a8e8c.ogg", SamplePadBank.DRUMS_909, 0xFFFF3B5C, SamplerSound.APPLAUSE),
      SamplePadEffect("drum_909_clap", 2, "909 Clap", "909_Clap_7d581351.ogg", SamplePadBank.DRUMS_909, 0xFFFF9100, SamplerSound.APPLAUSE),
      SamplePadEffect("drum_909_chat", 3, "909 Closed Hat", "909_Closed_Hat_07a921ad.ogg", SamplePadBank.DRUMS_909, 0xFF00E5FF, SamplerSound.WHISTLE),
      SamplePadEffect("drum_909_ohat", 4, "909 Open Hat", "909_Open_Hat_62e63f54.ogg", SamplePadBank.DRUMS_909, 0xFF00E676, SamplerSound.WHISTLE),
      SamplePadEffect("drum_909_ride", 5, "909 Ride", "909_Ride_2181766e.ogg", SamplePadBank.DRUMS_909, 0xFFAA00FF, SamplerSound.WHISTLE),
      SamplePadEffect("drum_hit_hat", 6, "Hit Hat Crash", "hit_hat__f53b78e8.ogg", SamplePadBank.DRUMS_909, 0xFFFFD600, SamplerSound.APPLAUSE),
      SamplePadEffect("drum_owl", 7, "Owl Siren", "owl___49fdb4f3.ogg", SamplePadBank.DRUMS_909, 0xFF2979FF, SamplerSound.SIRENI),
      SamplePadEffect("drum_ready", 8, "Ready! Shout", "ready___fd3532b0.ogg", SamplePadBank.DRUMS_909, 0xFFFF4081, SamplerSound.CROWD),

      // Bank 3: Sweeps & Hype (20 pads)
      SamplePadEffect("sweep_chet_back", 1, "Rewind Back", "Chet_Back_By_dj_Jimmy_pro_256_Effects_fa9dab8f.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFF3B5C, SamplerSound.SIRENI),
      SamplePadEffect("sweep_crash", 2, "Crash Sweep", "AUD-20261120-WA0106_18294ca0.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF00E5FF, SamplerSound.APPLAUSE),
      SamplePadEffect("sweep_riser", 3, "Synth Riser", "AUD-20261120-WA0104_d9fbc2ce.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF00E676, SamplerSound.SIRENI),
      SamplePadEffect("sweep_boom_applause", 4, "Boom Applause", "AUD-20261120-WA0096_86708533.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFF9100, SamplerSound.APPLAUSE),
      SamplePadEffect("sweep_brrr_horn", 5, "Brrr Airhorn", "AUD-20261120-WA0095_95449412.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFFD600, SamplerSound.WHISTLE),
      SamplePadEffect("sweep_turnup_horn", 6, "Turn Up Horn", "AUD-20261120-WA0093_d10cea26.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFAA00FF, SamplerSound.CROWD),
      SamplePadEffect("sweep_glass", 7, "Glass Shatter", "AUD-20261120-WA0091_5272604b.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF2979FF, SamplerSound.APPLAUSE),
      SamplePadEffect("sweep_warp_laser", 8, "Warp Laser", "AUD-20261120-WA0088_8a8659ac.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF00BFA5, SamplerSound.WHISTLE),
      SamplePadEffect("sweep_spin_bomb", 9, "Spin Bomb", "AUD-20261120-WA0087_90254adb.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFF4081, SamplerSound.SIRENI),
      SamplePadEffect("sweep_sub_wobble", 10, "Sub Wobble", "AUD-20261120-WA0086_7c0492e2.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF7C4DFF, SamplerSound.SIRENI),
      SamplePadEffect("sweep_triple_horn", 11, "Triple Horn", "AUD-20261120-WA0085_50dd90d2.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFF6D00, SamplerSound.WHISTLE),
      SamplePadEffect("sweep_whip", 12, "Whip Impact", "AUD-20261120-WA0084_971e9bb2.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFF1744, SamplerSound.APPLAUSE),
      SamplePadEffect("sweep_deep_siren", 13, "Deep Siren", "AUD-20261120-WA0083_2c7cd0d4.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF00B0FF, SamplerSound.SIRENI),
      SamplePadEffect("sweep_cannon", 14, "Cannon Cheer", "AUD-20261120-WA0082_1484899a.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF00E676, SamplerSound.CROWD),
      SamplePadEffect("sweep_crowd_scream", 15, "Crowd Scream", "AUD-20261120-WA0081_13413adc.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFE040FB, SamplerSound.CROWD),
      SamplePadEffect("sweep_what_hype", 16, "Whaaaat Hype", "AUD-20261120-WA0103_11f81338.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFFAB00, SamplerSound.CROWD),
      SamplePadEffect("sweep_arena_scream", 17, "Arena Scream", "AUD-20261120-WA0112_3c756f1c.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFF5252, SamplerSound.CROWD),
      SamplePadEffect("sweep_hype_scream", 18, "Hype Scream", "AUD-20261120-WA0090_9a0c78ee.mp3", SamplePadBank.SWEEPS_HYPE, 0xFFFF6E40, SamplerSound.CROWD),
      SamplePadEffect("sweep_shot_echo", 19, "Shot Echo", "AUD-20261120-WA0097_2a734c57.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF18FFFF, SamplerSound.WHISTLE),
      SamplePadEffect("sweep_vocal_harm", 20, "Vocal Harm", "AUD-20261120-WA0100_21a8afb3.mp3", SamplePadBank.SWEEPS_HYPE, 0xFF69F0AE, SamplerSound.SIRENI),

      // Bank 4: MC Drops (14 pads)
      SamplePadEffect("mc_mama_warned", 1, "Mama Warned U", "AUD-20261120-WA0110_77efcb0a.mp3", SamplePadBank.MC_DROPS, 0xFFFF3B5C, SamplerSound.CROWD),
      SamplePadEffect("mc_turn_it_up", 2, "Turn It Up", "AUD-20261120-WA0109_c1bcf22e.mp3", SamplePadBank.MC_DROPS, 0xFF00E5FF, SamplerSound.CROWD),
      SamplePadEffect("mc_hiphop_rnb", 3, "Hip-Hop & R&B", "AUD-20261120-WA0107_d43bba13.mp3", SamplePadBank.MC_DROPS, 0xFFFF9100, SamplerSound.CROWD),
      SamplePadEffect("mc_okay_okay", 4, "Okay Okay!", "AUD-20261120-WA0105_a95790d1.mp3", SamplePadBank.MC_DROPS, 0xFF00E676, SamplerSound.WHISTLE),
      SamplePadEffect("mc_come_get_some", 5, "Come Get Some", "AUD-20261120-WA0101_61e61a25.mp3", SamplePadBank.MC_DROPS, 0xFFAA00FF, SamplerSound.CROWD),
      SamplePadEffect("mc_swahili_afro", 6, "Swahili Afro", "AUD-20261120-WA0102_ce78fb8b.mp3", SamplePadBank.MC_DROPS, 0xFFFFD600, SamplerSound.CROWD),
      SamplePadEffect("mc_turning_heat", 7, "Turn Up Heat", "AUD-20261120-WA0099_a81c2f50.mp3", SamplePadBank.MC_DROPS, 0xFFFF5252, SamplerSound.CROWD),
      SamplePadEffect("mc_baddest_crew", 8, "Baddest Crew", "AUD-20261120-WA0094_f4744ae6.mp3", SamplePadBank.MC_DROPS, 0xFF2979FF, SamplerSound.CROWD),
      SamplePadEffect("mc_pull_up_tune", 9, "Pull Up Tune!", "AUD-20261120-WA0079_d293bbd7.mp3", SamplePadBank.MC_DROPS, 0xFFFF4081, SamplerSound.SIRENI),
      SamplePadEffect("mc_back_in_time", 10, "Back In Time", "AUD-20261120-WA0078_4dfef549.mp3", SamplePadBank.MC_DROPS, 0xFF00BFA5, SamplerSound.SIRENI),
      SamplePadEffect("mc_dj_lisbon", 11, "Hold Up Pull", "AUD-20261120-WA0077_26a01ee8.mp3", SamplePadBank.MC_DROPS, 0xFF7C4DFF, SamplerSound.SIRENI),
      SamplePadEffect("mc_bring_back", 12, "Bring It Back", "AUD-20261120-WA0075_0fd86e2f.mp3", SamplePadBank.MC_DROPS, 0xFFFF6D00, SamplerSound.SIRENI),
      SamplePadEffect("mc_play_spin", 13, "Play Spin Cut", "AUD-20261120-WA0074_ba802703.mp3", SamplePadBank.MC_DROPS, 0xFF00B0FF, SamplerSound.WHISTLE),
      SamplePadEffect("mc_afro_promo", 14, "KYC Promo Drop", "AUD-20261120-WA0073_42a950a2.mp3", SamplePadBank.MC_DROPS, 0xFFFFAB00, SamplerSound.CROWD),

      // Bank 5: DJ Tags & Mixes (15 pads)
      SamplePadEffect("tag_dj_jimmy", 1, "DJ Jimmy", "DEEJAY-JIMMY-PRO-2026-11-16-16-07-DJ-JIMMY_56800d27.mp3", SamplePadBank.DJ_TAGS, 0xFF00E5FF, SamplerSound.CROWD),
      SamplePadEffect("tag_jimmy_pro_ug", 2, "Jimmy Pro UG", "DEEJAY-JIMMY-PRO-2026-11-16-16-06-JIMMY-PRO.-UG_708fe1c1.mp3", SamplePadBank.DJ_TAGS, 0xFFFF3B5C, SamplerSound.CROWD),
      SamplePadEffect("tag_jimmy_pro", 3, "Jimmy Pro", "DEEJAY-JIMMY-PRO-2026-11-16-16-06-JIMMY-PRO_00521137.mp3", SamplePadBank.DJ_TAGS, 0xFFFF9100, SamplerSound.CROWD),
      SamplePadEffect("tag_boosters_jimmy", 4, "Boosters 256", "Wigman-10-2026-11-16-16-04-booster_s-deejays-entertainment-it_s-dj-Jimmy-pr_a7bf0482.mp3", SamplePadBank.DJ_TAGS, 0xFF00E676, SamplerSound.CROWD),
      SamplePadEffect("tag_agwater_jimmy", 5, "Agwater 256", "Wigman-10-2026-11-16-16-02-agwater-booster-DJ_s_-entertainment-it_s-dj-Jimm_768c24ae.mp3", SamplePadBank.DJ_TAGS, 0xFFAA00FF, SamplerSound.CROWD),
      SamplePadEffect("tag_agwater", 6, "Agwater DJs", "Wigman-10-2026-11-16-15-58-agwater-booster-DJ_s__058ebb06.mp3", SamplePadBank.DJ_TAGS, 0xFFFFD600, SamplerSound.CROWD),
      SamplePadEffect("tag_old_school", 7, "Old School Mix", "Wigman-10-2026-11-16-15-55-You_re-listening-to-the-Old-School-Show_-droppin_aa72306c.mp3", SamplePadBank.DJ_TAGS, 0xFF2979FF, SamplerSound.CROWD),
      SamplePadEffect("tag_alvin_dj", 8, "DJ Alvin Pro", "Ian_pro-20260109174934480Z_a0e62c0a.mp3", SamplePadBank.DJ_TAGS, 0xFFFF4081, SamplerSound.CROWD),
      SamplePadEffect("tag_scratch_gen", 9, "Scratch General", "Ian_pro-20260109174730832Z_5704e8fd.mp3", SamplePadBank.DJ_TAGS, 0xFF00BFA5, SamplerSound.WHISTLE),
      SamplePadEffect("tag_zaga", 10, "DJ Zaga Mega", "AUD-20261113-WA0118_f3b5564b.mp3", SamplePadBank.DJ_TAGS, 0xFF7C4DFF, SamplerSound.CROWD),
      SamplePadEffect("tag_boy_pro", 11, "DJ Boy Pro 256", "AUD-20261113-WA0078_9a57626c.mp3", SamplePadBank.DJ_TAGS, 0xFFFF6D00, SamplerSound.CROWD),
      SamplePadEffect("tag_polycarp", 12, "DJ Polycarp UG", "AUD-20261107-WA0224_f2cd8b06.mp3", SamplePadBank.DJ_TAGS, 0xFF00B0FF, SamplerSound.CROWD),
      SamplePadEffect("tag_gasolina", 13, "Gasolina Drop", "AUD-20261107-WA0231_afd8b2ab.mp3", SamplePadBank.DJ_TAGS, 0xFFFF1744, SamplerSound.APPLAUSE),
      SamplePadEffect("tag_m4a_drop", 14, "Afro Drop M4A", "AUD-20261106-WA0060_b44b0499.m4a", SamplePadBank.DJ_TAGS, 0xFF69F0AE, SamplerSound.CROWD),
      SamplePadEffect("tag_tea_redrum", 15, "Tea Redrum Beat", "Tea_-_Rema__Redrum_Remix_Edit_OpdBeats___HD__128k__702d4e46.mp3", SamplePadBank.DJ_TAGS, 0xFFFFAB00, SamplerSound.APPLAUSE),
    )

  fun padsForBank(bank: SamplePadBank): List<SamplePadEffect> =
    if (bank == SamplePadBank.ALL) allPads else allPads.filter { it.bank == bank }
}
