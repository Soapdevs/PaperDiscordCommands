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
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class DiscordCommandListener extends ListenerAdapter {
    private JDA jda; // JDA instance for the bot
    private final JavaPlugin plugin;
    private final Map<String, Boolean> usedBoostPerksMap = new HashMap<>();
    private final Map<String, Boolean> usedBalancedPerksMap = new HashMap<>();
    private final Map<String, Boolean> usedSteadyPerksMap = new HashMap<>();

    public DiscordCommandListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Public getter for JDA instance
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

            System.out.printf("Logged in as %s#%s%n", jda.getSelfUser().getName(), jda.getSelfUser().getDiscriminator());

            Guild guild = jda.getGuildById(plugin.getConfig().getString("discord.guild-id"));
            if (guild != null) {
                guild.updateCommands().addCommands(
                        Commands.slash("boostperks", "Give perks to a Minecraft player.")
                                .addOption(OptionType.STRING, "name", "The Minecraft player to receive the perks."),
                        Commands.slash("balancedperks", "Give balanced perks to a Minecraft player.")
                                .addOption(OptionType.STRING, "name", "The Minecraft player to receive the balanced perks."),
                        Commands.slash("steadyperks", "Give steady perks to a Minecraft player.")
                                .addOption(OptionType.STRING, "name", "The Minecraft player to receive the steady perks."),
                        Commands.slash("resetperk", "Reset perks for a player.")
                                .addOption(OptionType.STRING, "perk", "The type of perk to reset (booster/balanced/steady).")
                                .addOption(OptionType.USER, "user", "The Discord user whose perk should be reset."),
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
            case "boostperks":
                handleBoostPerksCommand(event);
                break;
            case "balancedperks":
                handleBalancedPerksCommand(event);
                break;
            case "steadyperks":
                handleSteadyPerksCommand(event);
                break;
            case "resetperk":
                handleResetPerkCommand(event);
                break;
            case "reload":
                handleReloadCommand(event);
                break;
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

    //region Reset perk commands
    private void handleResetPerkCommand(SlashCommandInteractionEvent event) {
        String discordRoleId = plugin.getConfig().getString("discord.adminRole");
        if (discordRoleId == null || discordRoleId.isEmpty()) {
            event.reply("Admin role not configured properly. Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        try {
            long adminRoleId = Long.parseLong(discordRoleId);
            boolean hasAdminRole = event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getIdLong() == adminRoleId);
            if (!hasAdminRole) {
                event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
                return;
            }
        } catch (NumberFormatException e) {
            event.reply("Admin role ID is invalid. Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        String perkType = event.getOption("perk").getAsString().toLowerCase();
        if (event.getOption("user") == null) {
            event.reply("Please mention a valid user.").setEphemeral(true).queue();
            return;
        }
        var mentionedMember = event.getOption("user").getAsMember();
        if (mentionedMember == null) {
            event.reply("Please mention a valid user.").setEphemeral(true).queue();
            return;
        }
        String userId = mentionedMember.getId();
        switch (perkType) {
            case "booster":
                resetBoostPerks(userId, event);
                break;
            case "balanced":
                resetBalancedPerks(userId, event);
                break;
            case "steady":
                resetSteadyPerks(userId, event);
                break;
            default:
                event.reply("Unknown perk type. Use 'balanced', 'steady', or 'booster'.").setEphemeral(true).queue();
                break;
        }
    }

    private synchronized void resetBoostPerks(String userId, SlashCommandInteractionEvent event) {
        if (usedBoostPerksMap.containsKey(userId)) {
            usedBoostPerksMap.remove(userId);
            event.reply("Successfully reset booster perks for user <@" + userId + ">").queue();
        } else {
            event.reply("User has not used booster perks yet.").setEphemeral(true).queue();
        }
    }

    private synchronized void resetBalancedPerks(String userId, SlashCommandInteractionEvent event) {
        if (usedBalancedPerksMap.containsKey(userId)) {
            usedBalancedPerksMap.remove(userId);
            event.reply("Successfully reset balanced perks for user <@" + userId + ">").queue();
        } else {
            event.reply("User has not used balanced perks yet.").setEphemeral(true).queue();
        }
    }

    private synchronized void resetSteadyPerks(String userId, SlashCommandInteractionEvent event) {
        if (usedSteadyPerksMap.containsKey(userId)) {
            usedSteadyPerksMap.remove(userId);
            event.reply("Successfully reset steady perks for user <@" + userId + ">").queue();
        } else {
            event.reply("User has not used steady perks yet.").setEphemeral(true).queue();
        }
    }
    //endregion

    //region Boost/Balanced/Steady perks
    private void handleBoostPerksCommand(SlashCommandInteractionEvent event) {
        String discordRoleId = plugin.getConfig().getString("discord.boostperks.allowedRole");
        String minecraftCommand = plugin.getConfig().getString("discord.boostperks.minecraftCommand");
        boolean hasRole = event.getMember().getRoles().stream()
                .anyMatch(role -> role.getIdLong() == Long.parseLong(discordRoleId));
        if (!hasRole) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        String userId = event.getUser().getId();
        if (usedBoostPerksMap.containsKey(userId)) {
            event.reply("You have already claimed your perks!").setEphemeral(true).queue();
            return;
        }
        String playerName = event.getOption("name").getAsString();
        String finalCommand = minecraftCommand.replace("{name}", playerName);
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
        );
        usedBoostPerksMap.put(userId, true);
        event.reply("Successfully executed the perks for player **" + playerName + "**!").queue();
    }

    private void handleBalancedPerksCommand(SlashCommandInteractionEvent event) {
        String discordRoleId = plugin.getConfig().getString("discord.balancedperks.allowedRole");
        String minecraftCommand = plugin.getConfig().getString("discord.balancedperks.minecraftCommand");
        if (discordRoleId == null || discordRoleId.isEmpty()) {
            event.reply("Role not configured properly. Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        try {
            boolean hasRole = event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getIdLong() == Long.parseLong(discordRoleId));
            if (!hasRole) {
                event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
                return;
            }
        } catch (NumberFormatException e) {
            event.reply("Role ID is invalid. Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        String userId = event.getUser().getId();
        if (usedBalancedPerksMap.containsKey(userId)) {
            event.reply("You have already claimed your perks!").setEphemeral(true).queue();
            return;
        }
        String playerName = event.getOption("name").getAsString();
        String finalCommand = minecraftCommand.replace("{name}", playerName);
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
        );
        usedBalancedPerksMap.put(userId, true);
        event.reply("Successfully executed the perks for player **" + playerName + "**!").queue();
    }

    private void handleSteadyPerksCommand(SlashCommandInteractionEvent event) {
        String discordRoleId = plugin.getConfig().getString("discord.steadyperks.allowedRole");
        String minecraftCommand = plugin.getConfig().getString("discord.steadyperks.minecraftCommand");
        if (discordRoleId == null || discordRoleId.isEmpty()) {
            event.reply("Role not configured properly. Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        try {
            boolean hasRole = event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getIdLong() == Long.parseLong(discordRoleId));
            if (!hasRole) {
                event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
                return;
            }
        } catch (NumberFormatException e) {
            event.reply("Role ID is invalid. Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        String userId = event.getUser().getId();
        if (usedSteadyPerksMap.containsKey(userId)) {
            event.reply("You have already claimed your perks!").setEphemeral(true).queue();
            return;
        }
        String playerName = event.getOption("name").getAsString();
        String finalCommand = minecraftCommand.replace("{name}", playerName);
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
        );
        usedSteadyPerksMap.put(userId, true);
        event.reply("Successfully executed the perks for player **" + playerName + "**!").queue();
    }
    //endregion

    //region Reload
    private void handleReloadCommand(SlashCommandInteractionEvent event) {
        String discordOwnerId = plugin.getConfig().getString("discord.ownerID");
        boolean isOwner = event.getUser().getId().equals(discordOwnerId);
        if (!isOwner) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        plugin.reloadConfig();
        event.reply("Configuration reloaded successfully!").queue();
    }
    //endregion

    //region Server Status
    // /serverstatus: sends an embed + a separate "Last Updated" message
    private void handleServerStatusCommand(SlashCommandInteractionEvent event) {
        String serverIp = event.getOption("server_ip") != null
                ? event.getOption("server_ip").getAsString()
                : plugin.getConfig().getString("server-status.ip");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ServerStatus status = ServerStatusFetcher.fetchStatus(serverIp);

            // Build the embed (no "Last Updated" in the embed)
            String iconUrl = "https://eu.mc-api.net/v3/server/favicon/" + serverIp;
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Minecraft Server Status")
                    .setThumbnail(iconUrl)
                    .setColor(status.isOnline() ? Color.GREEN : Color.RED)
                    .addField("Server IP", serverIp, true);

            if (status.isOnline()) {
                embed.addField("Version", status.getVersion(), true)
                        .addField("Players", status.getOnlinePlayers() + "/" + status.getMaxPlayers(), false)
                        .addField("Software", status.getSoftware(), true);
            } else {
                embed.addField("Status", "Offline", true);
            }

            // 1) Send the embed
            event.replyEmbeds(embed.build()).queue();

            // 2) Send a separate "Last Updated" message using a user-local Discord timestamp
            //    For a user-local time, we can do e.g. <t:epochSeconds:f> or <t:epochSeconds:R>
            long epochSeconds = Instant.now().getEpochSecond();
            String userLocalTime = "Last Updated: <t:" + epochSeconds + ":R>";
            // This message is posted publicly (not ephemeral)
            event.getChannel().sendMessage(userLocalTime).queue();
        });
    }

    // /serverstatusembed: auto-updating embed + a separate "Last Updated" message
    private void handleServerStatusEmbedCommand(SlashCommandInteractionEvent event) {
        String serverIp = plugin.getConfig().getString("server-status.ip");
        TextChannel channel = event.getChannel().asTextChannel();

        // Fetch initial data
        ServerStatus initialStatus = ServerStatusFetcher.fetchStatus(serverIp);
        EmbedBuilder embed = buildServerStatusEmbed(initialStatus, serverIp);

        // We'll store references to both the embed message and the timestamp message
        Message[] messages = new Message[2];

        // 1) Send the embed
        channel.sendMessageEmbeds(embed.build()).queue(embedMsg -> {
            // 2) Send separate "Last Updated" with user-local Discord timestamp
            long epochSeconds = Instant.now().getEpochSecond();
            String initialTimestamp = "Last Updated: <t:" + epochSeconds + ":R>";

            channel.sendMessage(initialTimestamp).queue(timestampMsg -> {
                messages[0] = embedMsg;    // embed message
                messages[1] = timestampMsg; // last updated message

                // Confirm to user that we posted the embed
                event.reply("Server status embed started in " + channel.getAsMention()).setEphemeral(true).queue();

                // Schedule auto-updates every 30 seconds
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ServerStatus updatedStatus = ServerStatusFetcher.fetchStatus(serverIp);
                        EmbedBuilder updatedEmbed = buildServerStatusEmbed(updatedStatus, serverIp);

                        // Update the embed
                        messages[0].editMessageEmbeds(updatedEmbed.build()).queue();

                        // Update the "Last Updated" message with a new user-local timestamp
                        long newEpoch = Instant.now().getEpochSecond();
                        String newTimestamp = "Last Updated: <t:" + newEpoch + ":R>";
                        messages[1].editMessage(newTimestamp).queue();
                    }
                }.runTaskTimer(plugin, 30 * 20L, 30 * 20L);
            });
        });
    }

    // Helper method to build an embed without "Last Updated"
    public EmbedBuilder buildServerStatusEmbed(ServerStatus status, String serverIp) {
        String iconUrl = "https://eu.mc-api.net/v3/server/favicon/" + serverIp;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Minecraft Server Status")
                .setThumbnail(iconUrl)
                .setColor(status.isOnline() ? Color.GREEN : Color.RED)
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
}
