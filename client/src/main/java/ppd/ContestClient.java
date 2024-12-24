package ppd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.request.ScoreSubmission;
import ppd.utils.CountryMapper;

import java.util.ArrayList;
import java.util.List;

import static ppd.utils.ContestConfig.CLIENT_DATA_DIR;
import static ppd.utils.ContestConfig.PROBLEMS;

public class ContestClient {
    private static final List<String> fileNames = new ArrayList<>();

    private static final Logger log = LogManager.getLogger(ContestClient.class);

    private static void initializeFileNames(int countryId) {
        for(int problem = 1; problem <= PROBLEMS; problem++) {
            var fileName = String.format("%s/results_c%d_p%d.txt", CLIENT_DATA_DIR, countryId, problem);
            fileNames.add(fileName);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Please provide the country id of the client.");
            System.exit(1);
        }

        var countryId = Integer.parseInt(args[0]);
        var countryName = CountryMapper.getCountryName(countryId);
        if (countryName == null) {
            System.out.println("Invalid country id.");
            System.exit(1);
        }

        initializeFileNames(countryId);
        List<ScoreSubmission> buffer = new ArrayList<>();
        // TODO: implement request logic on the client side
//        for (var fileName: fileNames) {
//
//        }

    }
}
