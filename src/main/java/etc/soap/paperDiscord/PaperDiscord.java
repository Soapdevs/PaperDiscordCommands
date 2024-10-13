package etc.soap.paperDiscord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.bukkit.plugin.java.JavaPlugin;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.List;

public class PaperDiscord extends JavaPlugin {
    private DiscordCommandListener discordCommandListener;
    private JDA jda; // JDA instance for the bot
    private final OkHttpClient httpClient = new OkHttpClient(); // HTTP client for server status

    @Override
    public void onEnable() {
        // Load the configuration file
        saveDefaultConfig();

        // Initialize and start the Discord bot
        discordCommandListener = new DiscordCommandListener(this);
        discordCommandListener.startBot(); // Assign JDA instance

        // Ensure JDA is initialized before starting the status updater
        if (jda != null) {
            cleanUpOldCommands();
            startStatusUpdater();
        } else {
            getLogger().severe("Failed to initialize JDA. Status updater will not start.");
        }
    }



    private void cleanUpOldCommands() {
        if (jda == null) {
            getLogger().severe("JDA is not initialized. Cannot clean up old commands.");
            return;
        }

        String guildId = getConfig().getString("discord.guild-id");
        if (guildId == null) {
            getLogger().warning("Guild ID not configured. Skipping old command cleanup.");
            return;
        }

        // Fetch all existing commands and remove unwanted ones
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

    @Override
    public void onDisable() {
        System.out.println("Removing Discord Link.");
        if (jda != null) {
            jda.shutdown();
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

                fetchServerStatus((onlinePlayers, maxPlayers, serverOnline) -> {
                    if (jda == null) {
                        getLogger().warning("JDA is not initialized yet. Skipping status update.");
                        return;
                    }

                    Guild guild = jda.getGuildById(guildId);
                    if (guild != null) {
                        // Update the bot's status based on server data
                        if (!serverOnline) {
                            jda.getPresence().setActivity(Activity.watching("Server Offline"));
                            jda.getPresence().setStatus(OnlineStatus.IDLE); // Idle when the server is offline
                        } else if (maxPlayers > 0) {
                            jda.getPresence().setActivity(Activity.watching(onlinePlayers + "/" + maxPlayers + " Players"));
                            jda.getPresence().setStatus(OnlineStatus.ONLINE);
                        } else {
                            jda.getPresence().setActivity(Activity.watching("Checking server status..."));
                            jda.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
                        }
                    } else {
                        getLogger().severe("Guild not found.");
                    }
                });
            }
        }.runTaskTimer(this, 0L, 600L); // 600 ticks = 30 seconds
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
                    getLogger().warning("API request failed with status code: " + response.code());
                    callback.onResult(0, 0, false);
                    return;
                }

                // Parse the response
                String jsonResponse = response.body().string();
                JsonObject json = new Gson().fromJson(jsonResponse, JsonObject.class);

                // Check if the server is online
                boolean online = json.get("online").getAsBoolean();
                if (!online) {
                    getLogger().info("Server is offline according to API.");
                    callback.onResult(0, 0, false);
                    return;
                }

                // Get player counts
                JsonObject playersJson = json.getAsJsonObject("players");
                int onlinePlayers = playersJson.has("online") ? playersJson.get("online").getAsInt() : 0;
                int maxPlayers = playersJson.has("max") ? playersJson.get("max").getAsInt() : 0;

                // Pass results back to the callback
                callback.onResult(onlinePlayers, maxPlayers, true);

            } catch (IOException e) {
                e.printStackTrace();
                getLogger().severe("Error while fetching server status: " + e.getMessage());
                callback.onResult(0, 0, false); // Error case, assume server is offline
            }
        });
    }

    // Functional interface for passing server status results
    private interface ServerStatusCallback {
        void onResult(int onlinePlayers, int maxPlayers, boolean serverOnline);
    }
}