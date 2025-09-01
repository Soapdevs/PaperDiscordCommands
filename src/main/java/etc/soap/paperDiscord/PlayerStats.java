package etc.soap.paperDiscord;

/**
 * Simple container for duel statistics.
 * Holds the player's UUID/name along with their overall duel totals.
 */
public class PlayerStats {
    public final String uuid;
    public final String name;

    public final int kills;
    public final int deaths;
    public final int wins;
    public final int losses;
    public final int streak;
    public final int bestStreak;

    public PlayerStats(String uuid, String name,
                       int kills, int deaths, int wins, int losses,
                       int streak, int bestStreak) {
        this.uuid = uuid;
        this.name = name;
        this.kills = kills;
        this.deaths = deaths;
        this.wins = wins;
        this.losses = losses;
        this.streak = streak;
        this.bestStreak = bestStreak;
    }
}

