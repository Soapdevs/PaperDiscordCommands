package etc.soap.paperDiscord;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Small wrapper around HikariCP for querying duel statistics.
 */
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
 beta/fix-code-errors
                host, port, db));

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
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    /**
     * Fetch duel statistics for the given player UUID from the `duels` table.
     */
    public Optional<PlayerStats> getPlayerStatsByUUID(java.util.UUID uuid) {
        String sql = "SELECT uuid, name, COALESCE(kills,0) AS kills, COALESCE(deaths,0) AS deaths, " +
                     "COALESCE(wins,0) AS wins, COALESCE(losses,0) AS losses, " +
                     "COALESCE(streak,0) AS streak, COALESCE(best_streak,0) AS best_streak " +
                     "FROM duels WHERE uuid = ? LIMIT 1";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PlayerStats(
                            rs.getString("uuid"),
                            rs.getString("name"),
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
        return Optional.empty();
    }


    public DataSource getDataSource() {
        return ds;
    }
}

