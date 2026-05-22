package etc.soap.paperDiscord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.time.Instant;

public class DiscordCommandListener extends ListenerAdapter {
    private JDA jda;
    private final JavaPlugin plugin;

    public DiscordCommandListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JDA getJDA() {
        return jda;
    }

    public void startBot() {
        String token = plugin.getConfig().getString("discord.token");

        try {
            JDABuilder builder = JDABuilder.createDefault(token)
                    .setActivity(net.dv8tion.jda.api.entities.Activity.watching(plugin.getConfig().getString("discord.status")))
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(this);
            jda = builder.build();
            jda.awaitReady();

            Guild guild = jda.getGuildById(plugin.getConfig().getString("discord.guild-id"));
            if (guild != null) {
                guild.updateCommands().addCommands(
                        Commands.slash("serverstatus", "Check the status of a Minecraft server")
                                .addOption(OptionType.STRING, "server_ip", "The IP of the server you want to check", false),
                        Commands.slash("serverstatusembed", "Send a server status embed that updates every 30 seconds")
                ).queue();
            } else {
                System.err.println("Guild not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to initialize JDA: " + e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "serverstatus":
                handleServerStatusCommand(event);
                break;
            case "serverstatusembed":
                handleServerStatusEmbedCommand(event);
                break;
            default:
                event.reply("Unknown command").setEphemeral(true).queue();
                break;
        }
    }

    private void handleServerStatusCommand(SlashCommandInteractionEvent event) {
        String fallbackIp = plugin.getConfig().getString("server-status.ip");
        String serverIp = event.getOption("server_ip") != null
                ? event.getOption("server_ip").getAsString()
                : fallbackIp;

        ServerStatus status = ServerStatusFetcher.fetchStatus(serverIp);
        EmbedBuilder embed = buildServerStatusEmbed(status, serverIp);

        event.replyEmbeds(embed.build()).queue(hook -> {
            long epochSeconds = Instant.now().getEpochSecond();
            String userLocalTime = "Last Updated: <t:" + epochSeconds + ":R>";
            event.getChannel().sendMessage(userLocalTime).queue();
        });
    }

    private void handleServerStatusEmbedCommand(SlashCommandInteractionEvent event) {
        String discordOwnerId = plugin.getConfig().getString("discord.ownerID");
        boolean isOwner = event.getUser().getId().equals(discordOwnerId);

        if (!isOwner) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String serverIp = plugin.getConfig().getString("server-status.ip");
        TextChannel channel = event.getChannel().asTextChannel();

        ServerStatus initialStatus = ServerStatusFetcher.fetchStatus(serverIp);
        EmbedBuilder embed = buildServerStatusEmbed(initialStatus, serverIp);
        Message[] messages = new Message[2];

        channel.sendMessageEmbeds(embed.build()).queue(embedMsg -> {
            long epochSeconds = Instant.now().getEpochSecond();
            String initialTimestamp = "Last Updated: <t:" + epochSeconds + ":R>";

            channel.sendMessage(initialTimestamp).queue(timestampMsg -> {
                messages[0] = embedMsg;
                messages[1] = timestampMsg;

                event.reply("Server status embed started in " + channel.getAsMention()).setEphemeral(true).queue();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ServerStatus updatedStatus = ServerStatusFetcher.fetchStatus(serverIp);
                        EmbedBuilder updatedEmbed = buildServerStatusEmbed(updatedStatus, serverIp);

                        messages[0].editMessageEmbeds(updatedEmbed.build()).queue();

                        long newEpoch = Instant.now().getEpochSecond();
                        String newTimestamp = "Last Updated: <t:" + newEpoch + ":R>";
                        messages[1].editMessage(newTimestamp).queue();
                    }
                }.runTaskTimer(plugin, 30 * 20L, 30 * 20L);
            });
        });
    }

    public EmbedBuilder buildServerStatusEmbed(ServerStatus status, String serverIp) {
        String iconUrl = "https://eu.mc-api.net/v3/server/favicon/" + serverIp;
        Color onlineColor = parseColor(plugin.getConfig().getString("server-status.embed-colors.online"), Color.GREEN);
        Color offlineColor = parseColor(plugin.getConfig().getString("server-status.embed-colors.offline"), Color.RED);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Minecraft Server Status")
                .setThumbnail(iconUrl)
                .setColor(status.isOnline() ? onlineColor : offlineColor)
                .addField("Server IP", serverIp, true);

        if (status.isOnline()) {
            embed.addField("Version", status.getVersion(), true)
                    .addField("Players", status.getOnlinePlayers() + "/" + status.getMaxPlayers(), false)
                    .addField("Software", status.getSoftware(), true);
        } else {
            embed.addField("Status", "Offline", true);
        }
        return embed;
    }

    private Color parseColor(String rawColor, Color fallback) {
        if (rawColor == null || rawColor.isBlank()) {
            return fallback;
        }
        String normalized = rawColor.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        try {
            return Color.decode(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
