package server.detection;

/** More specific classification used when the detector reports an attack. */
public enum AttackType {
    NONE("NORMAL"),
    DOS("DoS ATTACK"),
    DDOS("DDoS ATTACK");

    private final String displayName;

    AttackType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
