package etc.soap.paperDiscord;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class ServerStatusFetcher {

    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final Gson gson = new Gson();

    public static ServerStatus fetchStatus(String serverIp) {
        String apiUrl = "https://api.mcsrvstat.us/2/" + serverIp;
        Request request = new Request.Builder().url(apiUrl).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return new ServerStatus(false, 0, 0, "Unknown", "Unknown");
            }

            String jsonResponse = response.body().string();
            JsonObject json = gson.fromJson(jsonResponse, JsonObject.class);

            boolean online = json.has("online") && json.get("online").getAsBoolean();
            if (!online) {
                return new ServerStatus(false, 0, 0, "Unknown", "Unknown");
            }

            int onlinePlayers = 0;
            int maxPlayers = 0;
            if (json.has("players") && json.get("players").isJsonObject()) {
                JsonObject playersJson = json.getAsJsonObject("players");
                onlinePlayers = playersJson.has("online") ? playersJson.get("online").getAsInt() : 0;
                maxPlayers = playersJson.has("max") ? playersJson.get("max").getAsInt() : 0;
            }
            String version = json.has("version") ? json.get("version").getAsString() : "Unknown";
            String software = json.has("software") ? json.get("software").getAsString() : "Unknown";

            return new ServerStatus(true, onlinePlayers, maxPlayers, version, software);
        } catch (IOException e) {
            e.printStackTrace();
            return new ServerStatus(false, 0, 0, "Unknown", "Unknown");
        }
    }
}
