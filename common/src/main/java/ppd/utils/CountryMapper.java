package ppd.utils;

import java.util.HashMap;
import java.util.Map;

public class CountryMapper {
    private static final Map<Integer, String> countryMap = new HashMap<>();
    private static final int NOT_FOUND = -1;

    static {
        initializeMappings();
    }

    private static void initializeMappings() {
        countryMap.put(0, "Portugal");
        countryMap.put(1, "Spain");
        countryMap.put(2, "France");
        countryMap.put(3, "Germany");
        countryMap.put(4, "Italy");
        countryMap.put(5, "United Kingdom");
        countryMap.put(6, "Poland");
        countryMap.put(7, "Netherlands");
        countryMap.put(8, "Belgium");
        countryMap.put(9, "Romania");
    }

    public static String getCountryName(int countryId) {
        return countryMap.get(countryId);
    }

    public static int getCountryId(String countryName) {
        for (Map.Entry<Integer, String> entry : countryMap.entrySet()) {
            if (entry.getValue().equals(countryName)) {
                return entry.getKey();
            }
        }
        return NOT_FOUND;
    }
}
