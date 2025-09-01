package etc.soap.paperDiscord;

/**
 * Temporarily stores part 1 of the ban appeal while the user completes part 2
 */
public class BanAppealData {
    public final String minecraftUsername;
    public final String dateOfBan;
    public final String serverName;
    public final String banReason;
    public final String whoBanned;

    public BanAppealData(String minecraftUsername, String dateOfBan, String serverName, String banReason, String whoBanned) {
        this.minecraftUsername = minecraftUsername;
        this.dateOfBan = dateOfBan;
        this.serverName = serverName;
        this.banReason = banReason;
        this.whoBanned = whoBanned;
    }
}
