package eu.topine.dumontbot.commons;

/**
 * Created by topine on 15/05/2017.
 */
public enum FlightStatusCode {

    EN_ROUTE("A","En-Route"),
    CANCELED("C", "Canceled"),
    DIVERTED("D", "Diverted"),
    DATA_SOURCE_NEEDED("DN", "Data Source Needed"),
    LANDED("L", "Landed"),
    NOT_OPERATIONAL("NO", "Not Operational"),
    REDIRECTED("R", "Redirected"),
    SCHEDULED("S", "Scheduled"),
    UNKNOWN("U", "Unknown");

    private final String shortCode;
    private final String label;


    FlightStatusCode(String shortCode, String label) {
        this.shortCode = shortCode;
        this.label = label;
    }

    public static FlightStatusCode findByShortCode(String shortCode) {

        for (FlightStatusCode v : values()) {
            if (v.shortCode.equals(shortCode)) {
                return v;
            }
        }
        return null;
    }

    public String getLabel() {
        return label;
    }
}
