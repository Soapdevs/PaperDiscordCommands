package etc.soap.paperDiscord;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class DiscordCommandListener extends ListenerAdapter {

    private final JavaPlugin plugin;
    private final Map<String, Boolean> usedBoostPerksMap = new HashMap<>();

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
                            Commands.slash("reload", "Reloads the bot's configuration.")
                    ).queue();
                } else {
                    System.err.println("Guild not found.");
                }
            }
        });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "boostperks":
                handleBoostPerksCommand(event);
                break;
            case "reload":
                handleReloadCommand(event);
                break;
            default:
                event.reply("Unknown command").setEphemeral(true).queue();
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

    private void handleReloadCommand(SlashCommandInteractionEvent event) {
        String discordRoleId = plugin.getConfig().getString("discord.ownerID");
        // Reload the configuration
        boolean hasRole = event.getMember().getRoles().stream()
                .anyMatch(role -> role.getIdLong() == Long.parseLong(discordRoleId));

        if (!hasRole) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        plugin.reloadConfig();
        event.reply("Configuration has successfully reloaded!").setEphemeral(true).queue();
    }
}