package com.rdt;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Server {

    private static class Worker {
        private Thread thread;
        private ConnectionHandler conn;
        private long timestamp;

        public Worker(Thread t, ConnectionHandler conn, long timestamp){
            this.thread = t;
            this.conn = conn;
            this.timestamp = timestamp;
        }

        public Thread getThread() {
            return thread;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public ConnectionHandler getConn() {
            return conn;
        }
    }

    private DatagramSocket welcomingSocket;
    private int maxN;
    private long rngSeed;
    private float plp;
    private float pep = 0.0f;
    private String strategy;
    private List<Worker> workers;

    private static final long WORKER_MAX_TIME = 10_000_000L; // In milliseconds (~3 hours)
    private static final int MAX_PAR_WORKERS = 1000;
    private static final int EXPECTED_REQ_SIZE = 2048;

    public Server (ServerConfig serverConfig) throws SocketException {

        this(serverConfig.getPort(), serverConfig.getMaxN(),
                serverConfig.getRngSeed(), serverConfig.getPlp(),
                serverConfig.getStrategy());

    }

    public Server(int port, int maxN, long rngSeed, float plp, String strategy) throws SocketException {
        try {
            this.welcomingSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            throw e;
        }

        this.maxN = maxN;
        this.rngSeed = rngSeed;
        this.plp = plp;
        this.strategy = strategy;
        this.workers = new LinkedList<>();
    }

    private void printWelcome() {
        System.out.print(
                "\n\n\n ██████╗██╗      ██████╗ ██╗   ██╗██████╗       ███████╗███████╗██████╗ ██╗   ██╗███████╗██████╗        ██████╗ ██████╗ ███╗   ███╗\n");
        System.out.print(
                "██╔════╝██║     ██╔═══██╗██║   ██║██╔══██╗      ██╔════╝██╔════╝██╔══██╗██║   ██║██╔════╝██╔══██╗      ██╔════╝██╔═══██╗████╗ ████║\n");
        System.out.print(
                "██║     ██║     ██║   ██║██║   ██║██║  ██║█████╗███████╗█████╗  ██████╔╝██║   ██║█████╗  ██████╔╝█████╗██║     ██║   ██║██╔████╔██║\n");
        System.out.print(
                "██║     ██║     ██║   ██║██║   ██║██║  ██║╚════╝╚════██║██╔══╝  ██╔══██╗╚██╗ ██╔╝██╔══╝  ██╔══██╗╚════╝██║     ██║   ██║██║╚██╔╝██║\n");
        System.out.print(
                "╚██████╗███████╗╚██████╔╝╚██████╔╝██████╔╝      ███████║███████╗██║  ██║ ╚████╔╝ ███████╗██║  ██║      ╚██████╗╚██████╔╝██║ ╚═╝ ██║\n");
        System.out.print(
                "╚═════╝╚══════╝ ╚═════╝  ╚═════╝ ╚═════╝       ╚══════╝╚══════╝╚═╝  ╚═╝  ╚═══╝  ╚══════╝╚═╝  ╚═╝       ╚═════╝ ╚═════╝ ╚═╝     ╚═╝\n");
        System.out.print("\n\n*************************************************************************\n");
        System.out.print("|       Welcome to Cloud-Server-Com HTTP server v2.0.0                  |\n");
        System.out.print("|       The server is now ready to accept connections ...               |\n");
        System.out.print("*************************************************************************\n\n");
    }

    public void run() {
        printWelcome();
        while(!welcomingSocket.isClosed()) {
            while (!canCreateNewWorker()) {
               try {
                   Thread.sleep(1000);
               } catch (InterruptedException e) {
                   continue;
               }
            }

            try {
                DatagramPacket pkt = new DatagramPacket(new byte[EXPECTED_REQ_SIZE], EXPECTED_REQ_SIZE);
                welcomingSocket.receive(pkt);  //Blocks!
                RequestPacket reqPkt = new RequestPacket(pkt);

                if(reqPkt.isCorrupted()) {
                    continue;
                }

                ConnectionHandler conn = new ConnectionHandler(strategy, reqPkt,
                        plp, pep, rngSeed, maxN);
                Thread connectionHandler = new Thread(conn);
                connectionHandler.start();
                workers.add(new Worker(connectionHandler, conn, System.currentTimeMillis()));
            } catch (IOException e) {
                continue;
            }
        }
    }

    private boolean canCreateNewWorker() {
        Iterator<Worker> it = workers.iterator();
        while (it.hasNext()) {
            Worker worker = it.next();
            if(!worker.getThread().isAlive() ||
                    (System.currentTimeMillis() - worker.getTimestamp() > WORKER_MAX_TIME)){
                worker.getConn().kill();
                it.remove();
            }
        }
        return workers.size() < MAX_PAR_WORKERS;
    }
}