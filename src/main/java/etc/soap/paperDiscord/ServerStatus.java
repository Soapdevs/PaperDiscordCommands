package etc.soap.paperDiscord;

public class ServerStatus {
    private final boolean online;
    private final int onlinePlayers;
    private final int maxPlayers;
    private final String version;
    private final String software;
    private final String icon; // Server icon (data URI) fetched from the API

    public ServerStatus(boolean online, int onlinePlayers, int maxPlayers, String version, String software, String icon) {
        this.online = online;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.version = version;
        this.software = software;
        this.icon = icon;
    }

    public boolean isOnline() {
        return online;
    }

    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getVersion() {
        return version;
    }

    public String getSoftware() {
        return software;
    }

    public String getIcon() {
        return icon;
    }
}
