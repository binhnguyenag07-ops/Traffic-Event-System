package team.traffic.config;

public class ApplicationConfig {
    private String host = "localhost";
    private int port = 8080;
    private long simulationTickMillis = 1000L;
    private String dataDirectory = "data";

    public static ApplicationConfig defaultConfig() {
        return new ApplicationConfig();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getSimulationTickMillis() {
        return simulationTickMillis;
    }

    public void setSimulationTickMillis(long simulationTickMillis) {
        this.simulationTickMillis = simulationTickMillis;
    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    public void setDataDirectory(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }
}
