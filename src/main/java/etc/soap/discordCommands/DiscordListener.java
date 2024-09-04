package etc.soap.discordCommands;

import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;

public class DiscordListener extends ListenerAdapter {

    private final ProxyServer server;
    private final Map<String, String> commandMap;

    public DiscordListener(ProxyServer server, Map<String, String> commandMap) {
        this.server = server;
        this.commandMap = commandMap;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String discordCommand = event.getName();
        if (commandMap.containsKey(discordCommand)) {
            String minecraftCommand = commandMap.get(discordCommand);
            sendCommandToServer(minecraftCommand);
            event.reply("Executed command: " + minecraftCommand).queue();
        } else {
            event.reply("No matching command found for: " + discordCommand).setEphemeral(true).queue();
        }
    }

    private void sendCommandToServer(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }
}