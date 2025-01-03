package ppd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static ppd.utils.ContestConfig.COUNTRIES;

public class ContestClient {
    private static final Logger log = LogManager.getLogger(ContestClient.class);

    public static void main(String[] args) {
        List<Thread> threads = new ArrayList<>();
        for (int id = 1; id <= COUNTRIES; id++) {
            Thread handler = new Thread(new CountryHandler(id));
            threads.add(handler);
            log.info("Starting handler for country {}", id);
            handler.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                log.error(e);
            }
        }
    }
}
