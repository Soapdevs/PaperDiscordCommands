package etc.soap.paperDiscord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.List;

public class PaperDiscord extends JavaPlugin {
    private JDA jda; // JDA instance for the bot
    private final OkHttpClient httpClient = new OkHttpClient(); // HTTP client for server status

    @Override
    public void onEnable() {
        // Load the configuration file
        saveDefaultConfig();

        // Initialize and start the Discord bot
        startBot();

        // Clean up old commands
        cleanUpOldCommands();

        // Schedule the status updates every 30 seconds
        startStatusUpdater();
    }

    @Override
    public void onDisable() {
        cleanUpOldCommands();
        System.out.println("Removing Discord Link.");
    }

    private void startBot() {
        String token = getConfig().getString("discord.token");
        if (token == null) {
            getLogger().severe("Discord token not configured.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onReady(ReadyEvent event) {
                            getLogger().info("Bot is ready.");
                        }
                    })
                    .build();
            jda.awaitReady(); // Wait for the bot to be fully loaded
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Discord bot: " + e.getMessage());
        }
    }

    private void startStatusUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Ensure we have a valid guild ID
                String guildId = getConfig().getString("discord.guild-id");
                if (guildId == null) {
                    getLogger().warning("Guild ID not configured. Skipping status update.");
                    return;
                }

                fetchServerStatus((onlinePlayers, maxPlayers) -> {
                    Guild guild = jda.getGuildById(guildId);
                    if (guild != null) {
                        // Set the bot's status to display online players
                        if (maxPlayers > 0) {
                            jda.getPresence().setActivity(Activity.watching(onlinePlayers + " Players"));
                            jda.getPresence().setStatus(OnlineStatus.ONLINE);
                        } else {
                            jda.getPresence().setActivity(Activity.watching("the server"));
                            jda.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
                        }
                    } else {
                        getLogger().severe("Guild not found.");
                    }
                });
            }
        }.runTaskTimer(this, 0L, 600L); // 600 ticks = 30 seconds
    }

    private void cleanUpOldCommands() {
        String guildId = getConfig().getString("discord.guild-id");
        if (guildId == null) {
            getLogger().warning("Guild ID not configured. Skipping old command cleanup.");
            return;
        }

        // Fetch all existing commands and remove unwanted ones
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onReady(ReadyEvent event) {
                Guild guild = jda.getGuildById(guildId);
                if (guild != null) {
                    guild.retrieveCommands().queue(existingCommands -> {
                        // Define the commands you want to keep
                        List<String> commandsToKeep = List.of("boostperks", "reload", "balancedperks", "steadyperks", "resetperk", "serverstatus");

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
        });
    }

    private void fetchServerStatus(ServerStatusCallback callback) {
        String apiUrl = "https://api.mcsrvstat.us/2/balancedguild.com"; // Update with dynamic IP if needed

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // Build the request for the server status API
                Request request = new Request.Builder().url(apiUrl).build();
                Response response = httpClient.newCall(request).execute();

                // Check if the API response was successful
                if (!response.isSuccessful()) {
                    System.out.println("API request failed with status code: " + response.code());
                    return;
                }

                // Parse the response
                String jsonResponse = response.body().string();
                JsonObject json = new Gson().fromJson(jsonResponse, JsonObject.class);

                // Check if the server is online
                boolean online = json.get("online").getAsBoolean();
                if (!online) {
                    System.out.println("Server is offline according to API.");
                    callback.onResult(0, 0); // Server offline, so 0 players
                    return;
                }

                // Get player counts
                JsonObject playersJson = json.getAsJsonObject("players");
                int onlinePlayers = playersJson.has("online") ? playersJson.get("online").getAsInt() : 0;
                int maxPlayers = playersJson.has("max") ? playersJson.get("max").getAsInt() : 0;

                // Pass results back to the callback
                callback.onResult(onlinePlayers, maxPlayers);

            } catch (IOException e) {
                e.printStackTrace();
                callback.onResult(0, 0); // Error case, return 0 players
            }
        });
    }

    // Functional interface for passing server status results
    private interface ServerStatusCallback {
        void onResult(int onlinePlayers, int maxPlayers);
    }
}