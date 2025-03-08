package etc.soap.paperDiscord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.OnlineStatus;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;
import java.util.List;

public class PaperDiscord extends JavaPlugin {
    private DiscordCommandListener discordCommandListener;
    private JDA jda;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        discordCommandListener = new DiscordCommandListener(this);
        discordCommandListener.startBot();

        // Delay to allow JDA to initialize before starting the updater
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (jda == null) {
                jda = discordCommandListener.getJDA();
            }
            if (jda != null) {
                cleanUpOldCommands();
                startStatusUpdater();
            } else {
                getLogger().severe("Failed to initialize JDA. Status updater will not start.");
            }
        }, 60L);
    }

    private void cleanUpOldCommands() {
        String guildId = getConfig().getString("discord.guild-id");
        if (guildId == null) {
            getLogger().warning("Guild ID not configured. Skipping old command cleanup.");
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            guild.retrieveCommands().queue(existingCommands -> {
                List<String> commandsToKeep = List.of("boostperks", "reload", "balancedperks", "steadyperks", "resetperk", "serverstatus", "serverstatusembed");
                for (net.dv8tion.jda.api.interactions.commands.Command command : existingCommands) {
                    if (!commandsToKeep.contains(command.getName())) {
                        guild.deleteCommandById(command.getId()).queue();
                    }
                }
            });
        } else {
            getLogger().severe("Guild not found.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Removing Discord Link.");
        if (jda != null) {
            jda.shutdown();
        }
    }

    private void startStatusUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String guildId = getConfig().getString("discord.guild-id");
                if (guildId == null) {
                    getLogger().warning("Guild ID not configured. Skipping status update.");
                    return;
                }
                String serverIp = getConfig().getString("server-status.ip");
                ServerStatus status = ServerStatusFetcher.fetchStatus(serverIp);
                if (jda == null) {
                    getLogger().warning("JDA is not initialized. Skipping status update.");
                    return;
                }
                Guild guild = jda.getGuildById(guildId);
                if (guild != null) {
                    if (!status.isOnline()) {
                        jda.getPresence().setActivity(Activity.watching("Server Offline"));
                        jda.getPresence().setStatus(OnlineStatus.IDLE);
                    } else if (status.getMaxPlayers() > 0) {
                        jda.getPresence().setActivity(Activity.watching(status.getOnlinePlayers() + "/" + status.getMaxPlayers() + " Players"));
                        jda.getPresence().setStatus(OnlineStatus.ONLINE);
                    } else {
                        jda.getPresence().setActivity(Activity.watching("Checking server status..."));
                        jda.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
                    }
                } else {
                    getLogger().severe("Guild not found.");
                }
            }
        }.runTaskTimer(this, 0L, 600L); // 600L = 30 seconds (20 ticks = 1 second)
    }
}
