package etc.soap.paperDiscord;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseManager {
    private final HikariDataSource ds;

    public DatabaseManager(org.bukkit.plugin.java.JavaPlugin plugin) {
        HikariConfig cfg = new HikariConfig();
        String host = plugin.getConfig().getString("mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String db = plugin.getConfig().getString("mysql.database", "minecraft");
        String user = plugin.getConfig().getString("mysql.user", "root");
        String pass = plugin.getConfig().getString("mysql.password", "");
        int pool = plugin.getConfig().getInt("mysql.pool-size", 4);

        cfg.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC",
                host, port, db
        ));
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(pool);
        cfg.setMinimumIdle(1);
        cfg.setPoolName("PaperDiscordPool");
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        ds = new HikariDataSource(cfg);
    }

    public void shutdown() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    // Player table
    public Optional<PlayerInfo> getPlayerInfoByName(String name) {
        String sql = "SELECT uuid, name, rank, first_join, playtime FROM player WHERE name = ? LIMIT 1";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PlayerInfo(
                            rs.getString("uuid"),
                            rs.getString("name"),
                            rs.getString("rank"),
                            rs.getLong("first_join"),
                            rs.getInt("playtime")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<PlayerInfo> getPlayerInfoByUUID(java.util.UUID uuid) {
        String sql = "SELECT uuid, name, rank, first_join, playtime FROM player WHERE uuid = ? LIMIT 1";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PlayerInfo(
                            rs.getString("uuid"),
                            rs.getString("name"),
                            rs.getString("rank"),
                            rs.getLong("first_join"),
                            rs.getInt("playtime")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // Aggregated duels totals
    public Optional<DuelsAggregated> getDuelsAggregatedByName(String name) {
        String sql = "SELECT COALESCE(SUM(kills),0) AS total_kills, COALESCE(SUM(deaths),0) AS total_deaths, " +
                "COALESCE(SUM(wins),0) AS total_wins, COALESCE(SUM(losses),0) AS total_losses, COALESCE(MAX(best_streak),0) AS best_streak " +
                "FROM duels_kit_stats WHERE name = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new DuelsAggregated(
                            rs.getInt("total_kills"),
                            rs.getInt("total_deaths"),
                            rs.getInt("total_wins"),
                            rs.getInt("total_losses"),
                            rs.getInt("best_streak")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<DuelsAggregated> getDuelsAggregatedByUUID(java.util.UUID uuid) {
        String sql = "SELECT COALESCE(SUM(kills),0) AS total_kills, COALESCE(SUM(deaths),0) AS total_deaths, " +
                "COALESCE(SUM(wins),0) AS total_wins, COALESCE(SUM(losses),0) AS total_losses, COALESCE(MAX(best_streak),0) AS best_streak " +
                "FROM duels_kit_stats WHERE uuid = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new DuelsAggregated(
                            rs.getInt("total_kills"),
                            rs.getInt("total_deaths"),
                            rs.getInt("total_wins"),
                            rs.getInt("total_losses"),
                            rs.getInt("best_streak")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    // Per-kit breakdown (limit parameter)
    public List<DuelsKitStats> getDuelsPerKitByName(String name, int limit) {
        String sql = "SELECT kit, kills, deaths, wins, losses, streak, best_streak FROM duels_kit_stats WHERE name = ? ORDER BY kills DESC LIMIT ?";
        List<DuelsKitStats> list = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DuelsKitStats(
                            rs.getString("kit"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("streak"),
                            rs.getInt("best_streak")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<DuelsKitStats> getDuelsPerKitByUUID(java.util.UUID uuid, int limit) {
        String sql = "SELECT kit, kills, deaths, wins, losses, streak, best_streak FROM duels_kit_stats WHERE uuid = ? ORDER BY kills DESC LIMIT ?";
        List<DuelsKitStats> list = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DuelsKitStats(
                            rs.getString("kit"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("streak"),
                            rs.getInt("best_streak")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Fetch combined player info and duels statistics for the specified player.
     */
    public Optional<PlayerStats> getPlayerStatsByName(String name, int kitLimit) {
        Optional<PlayerInfo> infoOpt = getPlayerInfoByName(name);
        if (infoOpt.isEmpty()) {
            return Optional.empty();
        }

        PlayerInfo info = infoOpt.get();
        DuelsAggregated agg = getDuelsAggregatedByName(name)
                .orElse(new DuelsAggregated(0, 0, 0, 0, 0));
        List<DuelsKitStats> rawKits = getDuelsPerKitByName(name, kitLimit);

        List<PlayerStats.DuelsKit> kits = new ArrayList<>();
        for (DuelsKitStats k : rawKits) {
            kits.add(new PlayerStats.DuelsKit(k.kit, k.kills, k.deaths, k.wins, k.losses, k.streak, k.bestStreak));
        }

        PlayerStats stats = new PlayerStats(
                info.uuid,
                info.name,
                info.rank,
                info.firstJoin,
                info.playtime,
                agg.totalKills,
                agg.totalDeaths,
                agg.totalWins,
                agg.totalLosses,
                agg.bestStreak,
                kits
        );
        return Optional.of(stats);
    }

    public Optional<PlayerStats> getPlayerStatsByUUID(java.util.UUID uuid, int kitLimit) {
        Optional<PlayerInfo> infoOpt = getPlayerInfoByUUID(uuid);
        if (infoOpt.isEmpty()) {
            return Optional.empty();
        }

        PlayerInfo info = infoOpt.get();
        DuelsAggregated agg = getDuelsAggregatedByUUID(uuid)
                .orElse(new DuelsAggregated(0, 0, 0, 0, 0));
        List<DuelsKitStats> rawKits = getDuelsPerKitByUUID(uuid, kitLimit);

        List<PlayerStats.DuelsKit> kits = new ArrayList<>();
        for (DuelsKitStats k : rawKits) {
            kits.add(new PlayerStats.DuelsKit(k.kit, k.kills, k.deaths, k.wins, k.losses, k.streak, k.bestStreak));
        }

        PlayerStats stats = new PlayerStats(
                info.uuid,
                info.name,
                info.rank,
                info.firstJoin,
                info.playtime,
                agg.totalKills,
                agg.totalDeaths,
                agg.totalWins,
                agg.totalLosses,
                agg.bestStreak,
                kits
        );
        return Optional.of(stats);
    }

    // Simple container classes for internal mapping
    public static class PlayerInfo {
        public final String uuid;
        public final String name;
        public final String rank;
        public final long firstJoin;
        public final int playtime;

        public PlayerInfo(String uuid, String name, String rank, long firstJoin, int playtime) {
            this.uuid = uuid;
            this.name = name;
            this.rank = rank;
            this.firstJoin = firstJoin;
            this.playtime = playtime;
        }
    }

    public static class DuelsAggregated {
        public final int totalKills;
        public final int totalDeaths;
        public final int totalWins;
        public final int totalLosses;
        public final int bestStreak;

        public DuelsAggregated(int totalKills, int totalDeaths, int totalWins, int totalLosses, int bestStreak) {
            this.totalKills = totalKills;
            this.totalDeaths = totalDeaths;
            this.totalWins = totalWins;
            this.totalLosses = totalLosses;
            this.bestStreak = bestStreak;
        }
    }

    public static class DuelsKitStats {
        public final String kit;
        public final int kills;
        public final int deaths;
        public final int wins;
        public final int losses;
        public final int streak;
        public final int bestStreak;

        public DuelsKitStats(String kit, int kills, int deaths, int wins, int losses, int streak, int bestStreak) {
            this.kit = kit;
            this.kills = kills;
            this.deaths = deaths;
            this.wins = wins;
            this.losses = losses;
            this.streak = streak;
            this.bestStreak = bestStreak;
        }
    }

    // expose DataSource if needed
    public DataSource getDataSource() { return ds; }
}
