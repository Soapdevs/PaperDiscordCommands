package etc.soap.paperDiscord;

import java.util.List;
import java.util.Collections;

/**
 * Single container class for player / duels data.
 * - Contains basic player info (uuid, name, rank, firstJoin, playtime)
 * - Contains aggregated duels totals
 * - Contains a list of per-kit DuelsKit objects
 */
public class PlayerStats {
    // Basic player info (player table)
    public final String uuid;
    public final String name;
    public final String rank;
    public final long firstJoin; // as stored in DB (could be seconds or millis)
    public final int playtime;   // stored in DB (assumed seconds)

    // Aggregated duels totals (from duels_kit_stats)
    public final int totalKills;
    public final int totalDeaths;
    public final int totalWins;
    public final int totalLosses;
    public final int bestStreak;

    // Per-kit breakdown
    public final List<DuelsKit> kits;

    public PlayerStats(String uuid, String name, String rank, long firstJoin, int playtime,
                       int totalKills, int totalDeaths, int totalWins, int totalLosses, int bestStreak,
                       List<DuelsKit> kits) {
        this.uuid = uuid;
        this.name = name;
        this.rank = rank;
        this.firstJoin = firstJoin;
        this.playtime = playtime;
        this.totalKills = totalKills;
        this.totalDeaths = totalDeaths;
        this.totalWins = totalWins;
        this.totalLosses = totalLosses;
        this.bestStreak = bestStreak;
        this.kits = kits == null ? Collections.emptyList() : List.copyOf(kits);
    }

    // Small inner class representing per-kit stats
    public static class DuelsKit {
        public final String kit;
        public final int kills;
        public final int deaths;
        public final int wins;
        public final int losses;
        public final int streak;
        public final int bestStreak;

        public DuelsKit(String kit, int kills, int deaths, int wins, int losses, int streak, int bestStreak) {
            this.kit = kit;
            this.kills = kills;
            this.deaths = deaths;
            this.wins = wins;
            this.losses = losses;
            this.streak = streak;
            this.bestStreak = bestStreak;
        }
    }
}
