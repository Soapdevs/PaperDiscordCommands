package etc.soap.paperDiscord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class DiscordCommandListener extends ListenerAdapter {
    private JDA jda; // JDA instance for the bot
    private final JavaPlugin plugin;
    private final Map<String, Boolean> usedBoostPerksMap = new HashMap<>();
    private final Map<String, Boolean> usedBalancedPerksMap = new HashMap<>();
    private final Map<String, Boolean> usedSteadyPerksMap = new HashMap<>();
    private final Map<String, BanAppealData> pendingBanAppeals = new HashMap<>();

    private final DatabaseManager db;              // <-- store DB manager
    public DiscordCommandListener(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
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
                        Commands.slash("banformat", "Start the ban appeal process")
                                .addOption(OptionType.USER, "user", "The Discord user to invite to fill out the ban appeal form"),
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
            case "banformat":
                handleBanFormatCommand(event);
                break;
            default:
                event.reply("Unknown command").setEphemeral(true).queue();
                break;
        }
    }


    // /banformat command: only staff can use; they specify the target user as an option.
    // /banformat command: only staff can use; specify the target user
    private void handleBanFormatCommand(SlashCommandInteractionEvent event) {
        String staffRoleId = plugin.getConfig().getString("discord.staffRole");
        if (staffRoleId == null || staffRoleId.isEmpty() ||
                event.getMember().getRoles().stream().noneMatch(role -> role.getId().equals(staffRoleId))) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        if (event.getOption("user") == null) {
            event.reply("Please specify a user.").setEphemeral(true).queue();
            return;
        }
        var targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply("Invalid user specified.").setEphemeral(true).queue();
            return;
        }
        // Inform the staff that the invitation was sent
        event.reply("Invitation sent to " + targetMember.getAsMention() + " to fill out their ban appeal form.")
                .setEphemeral(true)
                .queue();
        // Send a public message in the channel pinging the target user with a button.
        // The button's custom ID includes the target user's ID so only they can click it.
        TextChannel channel = event.getChannel().asTextChannel();
        String buttonId = "banAppealButton_" + targetMember.getId();
        channel.sendMessage(targetMember.getAsMention() + ", please click the button below to fill out your ban appeal form:")
                .addActionRow(Button.primary(buttonId, "Fill Appeal Form"))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String compId = event.getComponentId();
        // Handle the "Fill Appeal Form" button from /banformat
        if (compId.startsWith("banAppealButton_")) {
            String targetUserId = compId.substring("banAppealButton_".length());
            if (!event.getUser().getId().equals(targetUserId)) {
                event.reply("This button is not for you.").setEphemeral(true).queue();
                return;
            }
            // Disable the button so it can only be used once.
            event.getMessage().editMessageComponents().queue();
            // Open Modal 1 (Questions 1-5)
            TextInput usernameInput = TextInput.create("ban_username", "Your Minecraft Username", TextInputStyle.SHORT)
                    .setPlaceholder("Enter your in-game name")
                    .setRequired(true)
                    .build();
            TextInput banDateInput = TextInput.create("ban_date", "Date of Ban", TextInputStyle.SHORT)
                    .setPlaceholder("e.g., 2025-03-07")
                    .setRequired(false)
                    .build();
            TextInput serverNameInput = TextInput.create("server_name", "Server Name (if applicable)", TextInputStyle.SHORT)
                    .setPlaceholder("Enter server or game mode")
                    .setRequired(false)
                    .build();
            TextInput banReasonInput = TextInput.create("ban_reason", "Ban Reason", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Reason given when you tried to log in")
                    .setRequired(true)
                    .build();
            TextInput whoBannedInput = TextInput.create("who_banned", "Who Banned You (if known)", TextInputStyle.SHORT)
                    .setPlaceholder("Admin name or system")
                    .setRequired(false)
                    .build();

            Modal modal1 = Modal.create("banAppealModal1", "Ban Appeal Form (Part 1)")
                    .addActionRow(usernameInput)
                    .addActionRow(banDateInput)
                    .addActionRow(serverNameInput)
                    .addActionRow(banReasonInput)
                    .addActionRow(whoBannedInput)
                    .build();
            event.replyModal(modal1).queue();
        }
        // If there is a separate continuation button for Modal 2, check that too.
        else if (compId.equals("banAppealContinue")) {
            // Ensure that the user already submitted Modal 1.
            if (!pendingBanAppeals.containsKey(event.getUser().getId())) {
                event.reply("You have not submitted Part 1 of the appeal.").setEphemeral(true).queue();
                return;
            }
            // Open Modal 2 (Questions 6-10)
            TextInput whyBannedInput = TextInput.create("why_banned", "Why Do You Think You Were Banned?", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Explain your perspective")
                    .setRequired(true)
                    .build();
            TextInput admitInput = TextInput.create("admit_rule", "Do You Admit to the Rule Violation? (Yes/No)", TextInputStyle.SHORT)
                    .setPlaceholder("Yes or No")
                    .setRequired(true)
                    .build();
            TextInput whyUnbanInput = TextInput.create("why_unban", "Why Should You Be Unbanned?", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Explain why you deserve another chance")
                    .setRequired(true)
                    .build();
            TextInput bannedBeforeInput = TextInput.create("banned_before", "Have You Been Banned Before? (Yes/No)", TextInputStyle.SHORT)
                    .setPlaceholder("Yes or No")
                    .setRequired(false)
                    .build();
            TextInput otherCommentsInput = TextInput.create("other_comments", "Any Other Comments?", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Additional information")
                    .setRequired(false)
                    .build();

            Modal modal2 = Modal.create("banAppealModal2", "Ban Appeal Form (Part 2)")
                    .addActionRow(whyBannedInput)
                    .addActionRow(admitInput)
                    .addActionRow(whyUnbanInput)
                    .addActionRow(bannedBeforeInput)
                    .addActionRow(otherCommentsInput)
                    .build();
            event.replyModal(modal2).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().equals("banAppealModal1")) {
            // Process Modal 1 responses and store them temporarily
            String username = event.getValue("ban_username").getAsString();
            String banDate = event.getValue("ban_date") != null ? event.getValue("ban_date").getAsString() : "Unknown";
            String serverName = event.getValue("server_name") != null ? event.getValue("server_name").getAsString() : "N/A";
            String banReason = event.getValue("ban_reason").getAsString();
            String whoBanned = event.getValue("who_banned") != null ? event.getValue("who_banned").getAsString() : "Unknown";

            BanAppealData data = new BanAppealData(username, banDate, serverName, banReason, whoBanned);
            pendingBanAppeals.put(event.getUser().getId(), data);

            event.reply("Part 1 received. Click the button below to continue to Part 2.")
                    .addActionRow(Button.primary("banAppealContinue", "Continue to Part 2"))
                    .setEphemeral(true)
                    .queue();
        } else if (event.getModalId().equals("banAppealModal2")) {
            // Process Modal 2 responses
            String whyBanned = event.getValue("why_banned").getAsString();
            String admit = event.getValue("admit_rule").getAsString();
            String whyUnban = event.getValue("why_unban").getAsString();
            String bannedBefore = event.getValue("banned_before") != null ? event.getValue("banned_before").getAsString() : "No";
            String otherComments = event.getValue("other_comments") != null ? event.getValue("other_comments").getAsString() : "None";

            // Retrieve stored data from Modal 1
            String userId = event.getUser().getId();
            BanAppealData data = pendingBanAppeals.remove(userId);
            if (data == null) {
                event.reply("An error occurred retrieving your previous responses.").setEphemeral(true).queue();
                return;
            }

            // Build the final embed with all ban appeal information
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Ban Appeal Submitted")
                    .setColor(Color.CYAN)
                    .addField("Minecraft Username", data.minecraftUsername, false)
                    .addField("Date of Ban", data.dateOfBan, false)
                    .addField("Server Name", data.serverName, false)
                    .addField("Ban Reason", data.banReason, false)
                    .addField("Who Banned You", data.whoBanned, false)
                    .addField("Why Do You Think You Were Banned?", whyBanned, false)
                    .addField("Do You Admit to the Rule Violation?", admit, false)
                    .addField("Why Should You Be Unbanned?", whyUnban, false)
                    .addField("Have You Been Banned Before?", bannedBefore, false)
                    .addField("Any Other Comments", otherComments, false)
                    .setFooter("Please wait patiently while your appeal is reviewed.", null);
            event.replyEmbeds(embed.build()).queue();
        }
    }
    //endregion

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

        String discordOwnerId = plugin.getConfig().getString("discord.ownerID");
        boolean isOwner = event.getUser().getId().equals(discordOwnerId);

        if (!isOwner) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
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
    private void handleStatsCommand(SlashCommandInteractionEvent event) {
        String player = event.getOption("player").getAsString().trim();
        event.deferReply(false).queue();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<PlayerStats> opt = db.getPlayerStatsByName(player, 6);
                if (opt.isEmpty()) {
                    event.getHook().sendMessage("No player found with name `" + player + "`.").queue();
                    return;
                }

                PlayerStats p = opt.get();

                long firstJoin = p.firstJoin;
                if (firstJoin > 0 && firstJoin < 1_000_000_000_000L) firstJoin *= 1000L;
                Instant joinInstant = Instant.ofEpochMilli(firstJoin);
                String joinFormatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(joinInstant);

                long seconds = p.playtime;
                long days = TimeUnit.SECONDS.toDays(seconds);
                long hours = TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days);
                long minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(seconds));
                String playtimeStr = (days > 0 ? (days + "d ") : "") + hours + "h " + minutes + "m";

                double kdr = p.totalDeaths == 0 ? p.totalKills : ((double) p.totalKills / (double) p.totalDeaths);
                String kdrStr = String.format("%.2f", kdr);

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Stats — " + p.name)
                        .setColor(Color.CYAN)
                        .addField("UUID", p.uuid, true)
                        .addField("Rank", p.rank == null ? "None" : p.rank, true)
                        .addField("Joined", joinFormatted, false)
                        .addField("Playtime", playtimeStr, true)
                        .addField("Duels — K/D/W/L", p.totalKills + "/" + p.totalDeaths + "/" + p.totalWins + "/" + p.totalLosses, true)
                        .addField("K/D Ratio", kdrStr, true)
                        .addField("Best Streak", String.valueOf(p.bestStreak), true)
                        .setFooter("Requested by " + event.getUser().getName(), event.getUser().getAvatarUrl())
                        .setTimestamp(Instant.now());

                if (!p.kits.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (PlayerStats.DuelsKit k : p.kits) {
                        sb.append("**").append(k.kit).append("**: ")
                                .append(k.kills).append("k / ").append(k.deaths).append("d / ")
                                .append(k.wins).append("w\n");
                    }
                    embed.addField("Top kits", sb.toString(), false);
                }

                event.getHook().sendMessageEmbeds(embed.build()).queue();
            } catch (Exception ex) {
                ex.printStackTrace();
                event.getHook().sendMessage("An error occurred while fetching stats for `" + player + "`.").queue();
            }
        });
    }
}
