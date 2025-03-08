package etc.soap.paperDiscord;

public class ServerStatus {
    private final boolean online;
    private final int onlinePlayers;
    private final int maxPlayers;
    private final String version;
    private final String software;

    public ServerStatus(boolean online, int onlinePlayers, int maxPlayers, String version, String software) {
        this.online = online;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.version = version;
        this.software = software;
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
}
