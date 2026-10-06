package mockinterview;

/** Switches that distinguish the three development iterations (used by tests and experiments). */
public final class Config {
    public final int evaluatorVersion;      // 1, 2 or 3
    public final boolean uniqueSelection;   // draw questions without repeats
    public final boolean enforceDeadline;   // reject answers submitted after the time limit
    public final boolean atomicSubmit;      // exactly-once answer recording

    public Config(int evaluatorVersion, boolean uniqueSelection, boolean enforceDeadline, boolean atomicSubmit) {
        this.evaluatorVersion = evaluatorVersion;
        this.uniqueSelection = uniqueSelection;
        this.enforceDeadline = enforceDeadline;
        this.atomicSubmit = atomicSubmit;
    }

    public static Config iteration(int n) {
        return switch (n) {
            case 1 -> new Config(1, false, false, false);
            case 2 -> new Config(2, true, true, false);
            default -> new Config(3, true, true, true);
        };
    }

    public static Config finalConfig() { return iteration(3); }
}
