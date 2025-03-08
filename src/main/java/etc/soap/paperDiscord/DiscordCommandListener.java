package etc.soap.paperDiscord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.awt.*;
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
                        // New command to post an embed that updates periodically
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

    // --------------------------
    // Implementation of commands
    // --------------------------

    // /resetperk command implementation
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

    // /boostperks command implementation
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

    // /balancedperks command implementation
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

    // /steadyperks command implementation
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

    // /reload command implementation
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

    // /serverstatus command implementation (sends an ephemeral reply)
    private void handleServerStatusCommand(SlashCommandInteractionEvent event) {
        String serverIp = event.getOption("server_ip") != null
                ? event.getOption("server_ip").getAsString()
                : plugin.getConfig().getString("server-status.ip");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ServerStatus status = ServerStatusFetcher.fetchStatus(serverIp);
            if (!status.isOnline()) {
                event.reply("The server " + serverIp + " is currently offline.").queue();
                return;
            }
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Minecraft Server Status")
                    .setColor(Color.GREEN)
                    .addField("Server IP", serverIp, true)
                    .addField("Version", status.getVersion(), true)
                    .addField("Online Players", status.getOnlinePlayers() + "/" + status.getMaxPlayers(), false)
                    .addField("Software", status.getSoftware(), true)
                    .setFooter("Requested by " + event.getUser().getName(), event.getUser().getAvatarUrl());
            event.replyEmbeds(embed.build()).queue();
        });
    }

    // /serverstatusembed command implementation
    private void handleServerStatusEmbedCommand(SlashCommandInteractionEvent event) {
        String serverIp = plugin.getConfig().getString("server-status.ip");
        TextChannel channel = event.getChannel().asTextChannel();

        ServerStatus initialStatus = ServerStatusFetcher.fetchStatus(serverIp);
        EmbedBuilder embed = buildServerStatusEmbed(initialStatus, serverIp);
        channel.sendMessageEmbeds(embed.build()).queue(message -> {
            event.reply("Server status embed started in " + channel.getAsMention()).setEphemeral(true).queue();
            new BukkitRunnable() {
                @Override
                public void run() {
                    ServerStatus updatedStatus = ServerStatusFetcher.fetchStatus(serverIp);
                    EmbedBuilder updatedEmbed = buildServerStatusEmbed(updatedStatus, serverIp);
                    message.editMessageEmbeds(updatedEmbed.build()).queue();
                }
            }.runTaskTimer(plugin, 30 * 20L, 30 * 20L);
        });
    }

    private EmbedBuilder buildServerStatusEmbed(ServerStatus status, String serverIp) {
        EmbedBuilder embed = new EmbedBuilder();
        if (!status.isOnline()) {
            embed.setTitle("Minecraft Server Status")
                    .setColor(Color.RED)
                    .addField("Server IP", serverIp, true)
                    .addField("Status", "Offline", true)
                    .setFooter("Status update", null);
        } else {
            embed.setTitle("Minecraft Server Status")
                    .setColor(Color.GREEN)
                    .addField("Server IP", serverIp, true)
                    .addField("Version", status.getVersion(), true)
                    .addField("Players", status.getOnlinePlayers() + "/" + status.getMaxPlayers(), false)
                    .addField("Software", status.getSoftware(), true)
                    .setFooter("Status update", null);
        }
        return embed;
    }
}
