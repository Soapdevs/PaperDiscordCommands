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

public class PaperDiscord extends JavaPlugin {
    private DiscordCommandListener discordCommandListener;
    private JDA jda;
    // We'll store references to our auto-posted messages so we can delete them on shutdown
    private Message embedMessage;
    private Message lastUpdatedMessage;
    private DatabaseManager dbManager;

    @Override
    public void onEnable() {
        // Ensure config exists before reading values
        saveDefaultConfig();

        dbManager = new DatabaseManager(this);
        discordCommandListener = new DiscordCommandListener(this, dbManager);
        discordCommandListener.startBot();
        jda = discordCommandListener.getJDA();

        // Delay starting of status updaters slightly to ensure bot is ready
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (jda == null) {
                getLogger().severe("Failed to initialize JDA. Status updater will not start.");
                return;
            }
            cleanUpOldCommands();
            startStatusUpdater();
            // Auto-start the server status embed if enabled in config
            if (getConfig().getBoolean("server-status.auto-embed", false)) {
                startAutoServerStatusEmbedUpdater();
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
                List<String> commandsToKeep = List.of("boostperks", "reload", "balancedperks", "steadyperks", "resetperk", "serverstatus", "stats", "serverstatusembed", "banformat");
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
        // Delete auto-posted messages if they exist
        if (dbManager != null) dbManager.shutdown();

        if (embedMessage != null) {
            embedMessage.delete().queue();
        }
        if (lastUpdatedMessage != null) {
            lastUpdatedMessage.delete().queue();
        }
        if (jda != null) {
            jda.shutdown();
            try {
                jda.awaitShutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startStatusUpdater() {
        new org.bukkit.scheduler.BukkitRunnable() {
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
        }.runTaskTimer(this, 0L, 600L);
    }
    // Modified auto-embed updater: Deletes old auto-post messages before posting new ones.
    private void startAutoServerStatusEmbedUpdater() {
        String channelId = getConfig().getString("server-status.channel-id");
        if (channelId == null || channelId.isEmpty()) {
            getLogger().warning("No channel id configured for auto server status embed update.");
            return;
        }
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            getLogger().warning("Auto server status embed channel not found.");
            return;
        }
        // Clear previous auto-embed messages from this bot (if any)
        channel.getHistory().retrievePast(100).queue(messages -> {
            for (Message msg : messages) {
                if (msg.getAuthor().equals(jda.getSelfUser())) {
                    // Check if this message is our auto-post (by embed title or content)
                    if (!msg.getEmbeds().isEmpty() && msg.getEmbeds().get(0).getTitle() != null &&
                            msg.getEmbeds().get(0).getTitle().equals("Minecraft Server Status")) {
                        msg.delete().queue();
                    } else if (msg.getContentRaw().startsWith("Last Updated:")) {
                        msg.delete().queue();
                    }
                }
            }
            // Now post fresh messages
            postAutoEmbedMessages(channel);
        });
    }

    // Helper to post auto-embed messages and schedule updates
    private void postAutoEmbedMessages(TextChannel channel) {
        String serverIp = getConfig().getString("server-status.ip");
        ServerStatus initialStatus = ServerStatusFetcher.fetchStatus(serverIp);
        // Use the existing helper method in DiscordCommandListener to build the embed
        EmbedBuilder embed = discordCommandListener.buildServerStatusEmbed(initialStatus, serverIp);
        channel.sendMessageEmbeds(embed.build()).queue(embedMsg -> {
            getLogger().info("Auto server status embed posted. Message ID: " + embedMsg.getId());
            // Send separate "Last Updated" message using Discord timestamp formatting
            long epochSeconds = System.currentTimeMillis() / 1000L;
            String timestamp = "Last Updated: <t:" + epochSeconds + ":R>";
            channel.sendMessage(timestamp).queue(timestampMsg -> {
                embedMessage = embedMsg;
                lastUpdatedMessage = timestampMsg;
                // Schedule auto-updates every 30 seconds to update both messages
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ServerStatus updatedStatus = ServerStatusFetcher.fetchStatus(serverIp);
                        EmbedBuilder updatedEmbed = discordCommandListener.buildServerStatusEmbed(updatedStatus, serverIp);
                        embedMessage.editMessageEmbeds(updatedEmbed.build()).queue();
                        long newEpoch = System.currentTimeMillis() / 1000L;
                        String newTimestamp = "Last Updated: <t:" + newEpoch + ":R>";
                        lastUpdatedMessage.editMessage(newTimestamp).queue();
                    }
                }.runTaskTimer(PaperDiscord.this, 30 * 20L, 30 * 20L);
            });
        });
    }
}
