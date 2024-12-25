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
        countryMap.put(1, "Portugal");
        countryMap.put(2, "Spain");
        countryMap.put(3, "France");
        countryMap.put(4, "Germany");
        countryMap.put(5, "Italy");
        countryMap.put(6, "United Kingdom");
        countryMap.put(7, "Poland");
        countryMap.put(8, "Netherlands");
        countryMap.put(9, "Belgium");
        countryMap.put(10, "Romania");
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
