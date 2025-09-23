package etc.soap.paperDiscord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PaperDiscord extends JavaPlugin {
    private DiscordCommandListener discordCommandListener;
    private JDA jda;
    private Message embedMessage;
    private Message lastUpdatedMessage;
    private DatabaseManager dbManager;
    private BukkitRunnable statusUpdater;
    private BukkitRunnable embedUpdater;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        // Validate critical configuration
        if (!validateConfig()) {
            getLogger().severe("Invalid configuration. Plugin disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        dbManager = new DatabaseManager(this);
        discordCommandListener = new DiscordCommandListener(this, dbManager);
        
        try {
            discordCommandListener.startBot();
            jda = discordCommandListener.getJDA();
            
            // Wait for JDA to be ready with retry logic
            Bukkit.getScheduler().runTaskLater(this, this::initializeAfterBotReady, 40L);
            
        } catch (Exception e) {
            getLogger().severe("Failed to start Discord bot: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private boolean validateConfig() {
        String token = getConfig().getString("discord.token");
        String guildId = getConfig().getString("discord.guild-id");
        
        if (token == null || token.equals("YOUR_BOT_TOKEN")) {
            getLogger().severe("Discord bot token not configured! Check config.yml");
            return false;
        }
        
        if (guildId == null || guildId.equals("YOUR_SERVER_ID")) {
            getLogger().severe("Discord guild ID not configured! Check config.yml");
            return false;
        }
        
        return true;
    }

    private void initializeAfterBotReady() {
        if (jda == null) {
            getLogger().warning("JDA is null, retrying in 2 seconds...");
            Bukkit.getScheduler().runTaskLater(this, this::initializeAfterBotReady, 40L);
            return;
        }
        
        if (jda.getStatus() != JDA.Status.CONNECTED) {
            getLogger().warning("JDA not connected yet, retrying in 2 seconds... Status: " + jda.getStatus());
            Bukkit.getScheduler().runTaskLater(this, this::initializeAfterBotReady, 40L);
            return;
        }

        getLogger().info("Discord bot connected successfully! Starting features...");
        
        try {
            cleanUpOldCommands();
            startStatusUpdater();
            
            if (getConfig().getBoolean("server-status.auto-embed", false)) {
                startAutoServerStatusEmbedUpdater();
            }
        } catch (Exception e) {
            getLogger().severe("Error during initialization: " + e.getMessage());
        }
    }

    private void cleanUpOldCommands() {
        String guildId = getConfig().getString("discord.guild-id");
        if (guildId == null || jda == null) {
            getLogger().warning("Guild ID or JDA not available for command cleanup");
            return;
        }
        
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            getLogger().severe("Guild not found: " + guildId);
            return;
        }

        guild.retrieveCommands().queue(existingCommands -> {
            List<String> commandsToKeep = List.of(
                    "boostperks", "reload", "balancedperks", "steadyperks",
                    "resetperk", "serverstatus", "stats", "editstats",
                    "statsleaderboard", "serverstatusembed", "banformat");
            
            for (net.dv8tion.jda.api.interactions.commands.Command command : existingCommands) {
                if (!commandsToKeep.contains(command.getName())) {
                    guild.deleteCommandById(command.getId()).queue(
                        success -> getLogger().info("Deleted old command: " + command.getName()),
                        error -> getLogger().warning("Failed to delete command: " + command.getName())
                    );
                }
            }
        }, error -> {
            getLogger().warning("Failed to retrieve commands: " + error.getMessage());
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down PaperDiscord...");
        
        // Cancel all tasks
        if (statusUpdater != null) {
            statusUpdater.cancel();
            statusUpdater = null;
        }
        if (embedUpdater != null) {
            embedUpdater.cancel();
            embedUpdater = null;
        }
        
        // Safe message deletion
        safeDeleteMessage(embedMessage);
        safeDeleteMessage(lastUpdatedMessage);
        
        // Close resources
        if (dbManager != null) {
            dbManager.shutdown();
            dbManager = null;
        }
        
        // Shutdown Discord connection
        if (jda != null) {
            try {
                jda.shutdown();
                if (!jda.awaitShutdown(5, TimeUnit.SECONDS)) {
                    getLogger().warning("JDA shutdown timed out, forcing shutdown...");
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getLogger().warning("Error during JDA shutdown: " + e.getMessage());
            }
            jda = null;
        }
        
        getLogger().info("PaperDiscord shutdown complete.");
    }

    private void safeDeleteMessage(Message message) {
        if (message != null) {
            message.delete().queue(
                success -> getLogger().fine("Message deleted successfully"),
                error -> {
                    // Ignore "message already deleted" errors
                    if (!error.getMessage().contains("Unknown Message")) {
                        getLogger().warning("Failed to delete message: " + error.getMessage());
                    }
                }
            );
        }
    }

    private void startStatusUpdater() {
        // Cancel existing updater if any
        if (statusUpdater != null) {
            statusUpdater.cancel();
        }
        
        statusUpdater = new BukkitRunnable() {
            @Override
            public void run() {
                if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
                    return;
                }
                
                String guildId = getConfig().getString("discord.guild-id");
                if (guildId == null) {
                    getLogger().warning("Guild ID not configured. Skipping status update.");
                    return;
                }
                
                String serverIp = getConfig().getString("server-status.ip");
                ServerStatus status = ServerStatusFetcher.fetchStatus(serverIp);
                
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
        };
        statusUpdater.runTaskTimer(this, 0L, 600L);
    }

    private void startAutoServerStatusEmbedUpdater() {
        String channelId = getConfig().getString("server-status.channel-id");
        if (channelId == null || channelId.isEmpty()) {
            getLogger().warning("No channel id configured for auto server status embed update.");
            return;
        }
        
        if (jda == null) {
            getLogger().warning("JDA not initialized for auto embed update.");
            return;
        }
        
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            getLogger().warning("Auto server status embed channel not found: " + channelId);
            return;
        }
        
        // Clear previous auto-embed messages from this bot
        channel.getHistory().retrievePast(100).queue(messages -> {
            for (Message msg : messages) {
                if (msg.getAuthor().equals(jda.getSelfUser())) {
                    if (!msg.getEmbeds().isEmpty() && msg.getEmbeds().get(0).getTitle() != null &&
                            msg.getEmbeds().get(0).getTitle().equals("Minecraft Server Status")) {
                        msg.delete().queue();
                    } else if (msg.getContentRaw().startsWith("Last Updated:")) {
                        msg.delete().queue();
                    }
                }
            }
            postAutoEmbedMessages(channel);
        });
    }

    private void postAutoEmbedMessages(TextChannel channel) {
        String serverIp = getConfig().getString("server-status.ip");
        ServerStatus initialStatus = ServerStatusFetcher.fetchStatus(serverIp);
        EmbedBuilder embed = discordCommandListener.buildServerStatusEmbed(initialStatus, serverIp);
        
        channel.sendMessageEmbeds(embed.build()).queue(embedMsg -> {
            getLogger().info("Auto server status embed posted. Message ID: " + embedMsg.getId());
            embedMessage = embedMsg;
            
            long epochSeconds = System.currentTimeMillis() / 1000L;
            String timestamp = "Last Updated: <t:" + epochSeconds + ":R>";
            channel.sendMessage(timestamp).queue(timestampMsg -> {
                lastUpdatedMessage = timestampMsg;
                
                // Cancel existing updater if any
                if (embedUpdater != null) {
                    embedUpdater.cancel();
                }
                
                embedUpdater = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (embedMessage == null || lastUpdatedMessage == null) {
                            cancel();
                            return;
                        }
                        
                        ServerStatus updatedStatus = ServerStatusFetcher.fetchStatus(serverIp);
                        EmbedBuilder updatedEmbed = discordCommandListener.buildServerStatusEmbed(updatedStatus, serverIp);
                        
                        // Update embed message
                        embedMessage.editMessageEmbeds(updatedEmbed.build()).queue(
                            success -> {},
                            error -> getLogger().warning("Failed to update embed: " + error.getMessage())
                        );
                        
                        // Update timestamp
                        long newEpoch = System.currentTimeMillis() / 1000L;
                        String newTimestamp = "Last Updated: <t:" + newEpoch + ":R>";
                        lastUpdatedMessage.editMessage(newTimestamp).queue(
                            success -> {},
                            error -> getLogger().warning("Failed to update timestamp: " + error.getMessage())
                        );
                    }
                };
                embedUpdater.runTaskTimer(PaperDiscord.this, 30 * 20L, 30 * 20L);
            });
        });
    }
}