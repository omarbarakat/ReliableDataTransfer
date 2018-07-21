package com.rdt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ServerConfig {
    private int port;
    private int maxN;
    private long rngSeed;
    private float plp;
    private String strategy;

    private ServerConfig() {

    }

    public static ServerConfig parseConfigFile(String fileName) throws IOException {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(fileName));
        } catch (IOException e) {
            throw e;
        }

        ServerConfig sc = new ServerConfig();

        String inn = in.readLine();
        sc.port = inn == null ? 3390 : Integer.parseInt(inn);

        inn = in.readLine();
        sc.maxN = inn == null ? 5: Integer.parseInt(inn);

        inn = in.readLine();
        sc.rngSeed = inn == null ? System.currentTimeMillis() : Long.parseLong(inn);

        inn = in.readLine();
        sc.plp = inn == null ? 0.2f: Float.parseFloat(inn);

        inn = in.readLine();
        sc.strategy = inn == null ? TransmissionStrategy.STOP_AND_WAIT : inn;

        in.close();
        return sc;
    }

    public int getPort() {
        return port;
    }

    public int getMaxN() {
        return maxN;
    }

    public long getRngSeed() {
        return rngSeed;
    }

    public float getPlp() {
        return plp;
    }

    public String getStrategy() {
        return strategy;
    }
}
