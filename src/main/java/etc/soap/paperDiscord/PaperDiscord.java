package etc.soap.paperDiscord;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PaperDiscord extends JavaPlugin {

    private DiscordCommandListener discordCommandListener;

    @Override
    public void onEnable() {
        // Load the configuration file
        saveDefaultConfig();

        // Initialize and start the Discord bot
        discordCommandListener = new DiscordCommandListener(this);
        discordCommandListener.startBot();

        // Clean up old commands
        cleanUpOldCommands();
    }

    @Override
    public void onDisable() {
        cleanUpOldCommands();
    }

    private void cleanUpOldCommands() {
        String guildId = getConfig().getString("discord.guild-id");
        if (guildId == null) {
            getLogger().warning("Guild ID not configured. Skipping old command cleanup.");
            return;
        }

        String token = getConfig().getString("discord.token");
        JDABuilder builder = JDABuilder.createDefault(token);
        builder.addEventListeners(new ListenerAdapter() {
            @Override
            public void onReady(ReadyEvent event) {
                Guild guild = event.getJDA().getGuildById(guildId);
                if (guild != null) {
                    // Fetch all existing commands
                    guild.retrieveCommands().queue(existingCommands -> {
                        event.getJDA().getPresence().setActivity(Activity.playing("Balanced Guild"));
                        // Define the commands you want to keep
                        List<String> commandsToKeep = List.of("boostperks", "reload");

                        // Remove commands not in the list
                        for (Command command : existingCommands) {
                            if (!commandsToKeep.contains(command.getName())) {
                                guild.deleteCommandById(command.getId()).queue();
                            }
                        }
                    });
                } else {
                    getLogger().severe("Guild not found.");
                }
            }
        }).build();
    }
}
