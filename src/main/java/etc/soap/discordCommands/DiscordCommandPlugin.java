package etc.soap.discordCommands;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.google.inject.Inject;
import net.dv8tion.jda.api.JDABuilder;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Plugin(
        id = "discordCommands",
        name = "DiscordCommands",
        version = "1.0-SNAPSHOT",
        description = "A plugin to run Minecraft commands from Discord",
        authors = {"YourName"}
)
public class DiscordCommandPlugin {

    private final ProxyServer server;
    private Map<String, String> commandMap;

    @Inject
    public DiscordCommandPlugin(ProxyServer server) {
        this.server = server;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfiguration();
        initializeDiscordBot();
    }

    private void loadConfiguration() {
        File configFile = new File("plugins/discordCommands/config.yml");
        if (!configFile.exists()) {
            System.out.println("Configuration file not found!");
            return;
        }

        ConfigurationLoader<CommentedConfigurationNode> loader = YamlConfigurationLoader.builder()
                .path(Paths.get(configFile.getPath()))
                .build();

        try {
            ConfigurationNode rootNode = loader.load();
            commandMap = new HashMap<>();
            ConfigurationNode commandsNode = rootNode.node("commands");
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : commandsNode.childrenMap().entrySet()) {
                String discordCommand = entry.getKey().toString();
                String minecraftCommand = entry.getValue().getString();
                commandMap.put(discordCommand, minecraftCommand);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeDiscordBot() {
        try {
            JDABuilder.createDefault("YOUR_BOT_TOKEN")
                    .addEventListeners(new DiscordListener(server, commandMap))
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}