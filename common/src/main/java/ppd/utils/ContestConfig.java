package ppd.utils;

public record ContestConfig() {
    public static final String HOME_DIR = System.getProperty("user.dir");
    public static final String SERVER_DATA_DIR = HOME_DIR + "/server/src/main/java/ppd/data";
    public static final String CLIENT_DATA_DIR = HOME_DIR + "/client/src/main/java/ppd/data";

    public static final int COUNTRIES = 5;
    public static final int PROBLEMS = 10;
    public static final int MIN_PARTICIPANTS = 80;
    public static final int MAX_PARTICIPANTS = 100;
    public static final double NON_SOLVE_PROBABILITY = 0.1;
    public static final double FRAUD_PROBABILITY = 0.02;
    public static final int MAX_QUEUE_CAPACITY = 100;

    public static final int PORT = 5555;
    public static final int READERS = 4;
    public static final int WRITERS = 8;
    public static final int DELTA_X = 1;
    public static final int DELTA_T = 1;
    public static final int CHUNK_SIZE = 20;

    public static final int SERVER_TIMEOUT = 2;
    public static final int MAX_RETRIES = 5;
    public static final int RETRY_DELAY = 10;
}
