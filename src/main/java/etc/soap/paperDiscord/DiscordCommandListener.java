    package etc.soap.paperDiscord;

    import net.dv8tion.jda.api.JDABuilder;
    import net.dv8tion.jda.api.entities.Guild;
    import net.dv8tion.jda.api.entities.Member;
    import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
    import net.dv8tion.jda.api.events.session.ReadyEvent;
    import net.dv8tion.jda.api.hooks.ListenerAdapter;
    import net.dv8tion.jda.api.interactions.commands.OptionType;
    import net.dv8tion.jda.api.interactions.commands.build.Commands;
    import org.bukkit.Bukkit;
    import org.bukkit.plugin.java.JavaPlugin;

    import java.util.HashMap;
    import java.util.Map;

    public class DiscordCommandListener extends ListenerAdapter {

        private final JavaPlugin plugin;
        private final Map<String, Boolean> usedBoostPerksMap = new HashMap<>();
        private final Map<String, Boolean> usedBalancedPerksMap = new HashMap<>();
        private final Map<String, Boolean> usedSteadyPerksMap = new HashMap<>();


        public DiscordCommandListener(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        public void startBot() {
            String token = plugin.getConfig().getString("discord.token");

            JDABuilder builder = JDABuilder.createDefault(token);
            builder.addEventListeners(this);

            builder.build().addEventListener(new ListenerAdapter() {
                @Override
                public void onReady(ReadyEvent event) {
                    System.out.printf("Logged in as %s#%s%n", event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getDiscriminator());

                    // Register slash commands
                    Guild guild = event.getJDA().getGuildById(plugin.getConfig().getString("discord.guild-id"));
                    if (guild != null) {
                        guild.updateCommands().addCommands(
                                Commands.slash("boostperks", "Give perks to a Minecraft player.")
                                        .addOption(OptionType.STRING, "name", "The Minecraft player to receive the perks."),
                                Commands.slash("balancedperks", "Give balanced perks to a Minecraft player.")
                                        .addOption(OptionType.STRING, "name", "The Minecraft player to receive the balanced perks."),
                                Commands.slash("steadyperks", "Give steady perks to a Minecraft player.")
                                        .addOption(OptionType.STRING, "name", "The Minecraft player to receive the steady perks."),
                                Commands.slash("reload", "Reloads the bot's configuration."),
                                Commands.slash("resetperk", "Reset perks for a player.")
                                        .addOption(OptionType.STRING, "perk", "The type of perk to reset (booster/balanced/steady).")
                                        .addOption(OptionType.USER, "user", "The Discord user whose perk should be reset.")
                        ).queue();
                    } else {
                        System.err.println("Guild not found.");
                    }
                }
            });
        }

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
                default:
                    event.reply("Unknown command").setEphemeral(true).queue();
                    break;
            }
        }


        private void handleResetPerkCommand(SlashCommandInteractionEvent event) {
            // Retrieve the admin role ID from the config
            String discordRoleId = plugin.getConfig().getString("discord.adminRole");

            // Check if the admin role ID is configured
            if (discordRoleId == null || discordRoleId.isEmpty()) {
                event.reply("Admin role not configured properly. Please contact an admin.").setEphemeral(true).queue();
                System.err.println("Error: Admin role ID is null or empty.");
                return;
            }

            try {
                // Parse the admin role ID and check if the user has the required role
                long adminRoleId = Long.parseLong(discordRoleId);
                boolean hasAdminRole = event.getMember().getRoles().stream()
                        .anyMatch(role -> role.getIdLong() == adminRoleId);

                if (!hasAdminRole) {
                    event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
                    return;
                }
            } catch (NumberFormatException e) {
                event.reply("Admin role ID is invalid. Please contact an admin.").setEphemeral(true).queue();
                System.err.println("Error: Invalid admin role ID. Role ID: " + discordRoleId);
                e.printStackTrace();
                return;
            }

            // Get the subcommand (balanced, steady, booster)
            String perkType = event.getOption("perk").getAsString().toLowerCase();

            // Get the mentioned user (discord ping)
            Member mentionedMember = event.getOption("user").getAsMember();

            if (mentionedMember == null) {
                event.reply("Please mention a valid user.").setEphemeral(true).queue();
                return;
            }

            String userId = mentionedMember.getId();

            // Handle the different perk types
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
                    event.reply("Unknown perk type. Please use 'balanced', 'steady', or 'booster'.").setEphemeral(true).queue();
                    break;
            }
        }

        private synchronized void resetBoostPerks(String userId, SlashCommandInteractionEvent event) {
            if (usedBoostPerksMap.containsKey(userId)) {
                usedBoostPerksMap.remove(userId);
                event.reply("Successfully reset booster perks for user <@" + userId + ">").queue();
                System.out.println("Booster perks reset for user: " + userId);
            } else {
                event.reply("User has not used booster perks yet.").setEphemeral(true).queue();
            }
        }

        private synchronized void resetBalancedPerks(String userId, SlashCommandInteractionEvent event) {
            if (usedBalancedPerksMap.containsKey(userId)) {
                usedBalancedPerksMap.remove(userId);
                event.reply("Successfully reset balanced perks for user <@" + userId + ">").queue();
                System.out.println("Balanced perks reset for user: " + userId);
            } else {
                event.reply("User has not used balanced perks yet.").setEphemeral(true).queue();
            }
        }

        private synchronized void resetSteadyPerks(String userId, SlashCommandInteractionEvent event) {
            if (usedSteadyPerksMap.containsKey(userId)) {
                usedSteadyPerksMap.remove(userId);
                event.reply("Successfully reset steady perks for user <@" + userId + ">").queue();
                System.out.println("Steady perks reset for user: " + userId);
            } else {
                event.reply("User has not used steady perks yet.").setEphemeral(true).queue();
            }
        }

        private void handleBoostPerksCommand(SlashCommandInteractionEvent event) {
            String discordRoleId = plugin.getConfig().getString("discord.boostperks.allowedRole");
            String minecraftCommand = plugin.getConfig().getString("discord.boostperks.minecraftCommand");

            // Check if the user has the required role
            boolean hasRole = event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getIdLong() == Long.parseLong(discordRoleId));

            if (!hasRole) {
                event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
                return;
            }

            // Check if the user has already used the command
            String userId = event.getUser().getId();
            if (usedBoostPerksMap.containsKey(userId)) {
                event.reply("You have already claimed your perks!").setEphemeral(true).queue();
                return;
            }

            // Retrieve the player's name from the command option
            String playerName = event.getOption("name").getAsString();

            // Replace the {name} placeholder in the command with the actual player name
            String finalCommand = minecraftCommand.replace("{name}", playerName);

            // Run the Minecraft command on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            });

            // Mark the user as having used the command
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
                // Check if the user has the required role
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

            // Check if the user has already used the command
            String userId = event.getUser().getId();
            if (usedBalancedPerksMap.containsKey(userId)) {
                event.reply("You have already claimed your perks!").setEphemeral(true).queue();
                return;
            }

            // Retrieve the player's name from the command option
            String playerName = event.getOption("name").getAsString();

            // Replace the {name} placeholder in the command with the actual player name
            String finalCommand = minecraftCommand.replace("{name}", playerName);

            // Run the Minecraft command on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            });

            // Mark the user as having used the command
            usedBalancedPerksMap.put(userId, true);
            event.reply("Successfully executed the perks for player **" + playerName + "**!").queue();
        }

        private void handleSteadyPerksCommand(SlashCommandInteractionEvent event) {
            // Use the correct config key for Steady Perks
            String discordRoleId = plugin.getConfig().getString("discord.steadyperks.allowedRole");
            String minecraftCommand = plugin.getConfig().getString("discord.steadyperks.minecraftCommand");

            if (discordRoleId == null || discordRoleId.isEmpty()) {
                event.reply("Role not configured properly. Please contact an admin.").setEphemeral(true).queue();
                System.err.println("Error: Role ID for Steady Perks is null or empty.");
                return;
            }

            try {
                System.out.println("Steady Perks Role ID: " + discordRoleId);

                boolean hasRole = event.getMember().getRoles().stream()
                        .anyMatch(role -> role.getIdLong() == Long.parseLong(discordRoleId));

                if (!hasRole) {
                    event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
                    return;
                }
            } catch (NumberFormatException e) {
                event.reply("Role ID is invalid. Please contact an admin.").setEphemeral(true).queue();
                System.err.println("Error: Invalid role ID for Steady Perks. Role ID: " + discordRoleId);
                e.printStackTrace();
                return;
            }

            String userId = event.getUser().getId();
            if (usedSteadyPerksMap.containsKey(userId)) {
                event.reply("You have already claimed your perks!").setEphemeral(true).queue();
                return;
            }

            String playerName = event.getOption("name").getAsString();
            String finalCommand = minecraftCommand.replace("{name}", playerName);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            });

            usedSteadyPerksMap.put(userId, true);
            event.reply("Successfully executed the perks for player **" + playerName + "**!").queue();
        }

        private void handleReloadCommand(SlashCommandInteractionEvent event) {
            String discordOwnerId = plugin.getConfig().getString("discord.ownerID");

            // Check if the user is the owner (by comparing user ID)
            boolean isOwner = event.getUser().getId().equals(discordOwnerId);

            if (!isOwner) {
                event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
                return;
            }

            // Reload the configuration
            plugin.reloadConfig();
            event.reply("Configuration reloaded successfully!").queue();
        }

    }