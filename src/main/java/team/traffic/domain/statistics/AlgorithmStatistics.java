package team.traffic.domain.statistics;

import java.util.List;

public class AlgorithmStatistics {
    private String algorithmName;
    private List<String> supportedPreferences;

    public AlgorithmStatistics() {}

    public AlgorithmStatistics(String algorithmName, List<String> supportedPreferences) {
        this.algorithmName = algorithmName;
        this.supportedPreferences = supportedPreferences;
    }

    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName = algorithmName; }

    public List<String> getSupportedPreferences() { return supportedPreferences; }
    public void setSupportedPreferences(List<String> supportedPreferences) { this.supportedPreferences = supportedPreferences; }
}
