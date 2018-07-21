package com.rdt;

import com.rdt.utils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConnectionHandler implements Runnable, Subscriber {

    private String strategyName;
    private int windowSize;
    private TransmissionStrategy strategy;

    private DatagramSocket socket;

    private String fileName;
    private FileInputStream fileStream;

    private Map<Long, TimeoutTimerTask> timeoutMap;
    private Map<Long, DataPacket> packetMap;
    private Set<Long> timedoutNotAcked;
    private BlockingQueue<Event> mailbox;
    private Set<Long> acked;

    private int numOfChunx;
    private int initialSeqNo;

    private Random rng;

    private double plp;         // packet loss probability: from 0 to 1
    private double pep;         // packet error probability: from 0 to 1

    private double estimatedRtt = 1000.0d;
    private double devRtt = 0.0;
    private long timeoutInterval = 1000L; // In milliseconds

    private int destPort;
    private InetAddress destIp;


    private static final Timer TIMER = new Timer(true); // Thread safe.
    private static final long NICENESS = 1L; // milliseconds to sleep every iteration
    private static final int CHUNK_SIZE = 1024; // bytes
    private static final float ALPHA = 0.125f;
    private static final float BETA = 0.25f;
    private static final long MAX_PKT_TIMEOUT = 6000_000L; // one minute

    public ConnectionHandler(String strategyName, RequestPacket request, double plp, double pep, long seed, int windowSize) {
        this.strategyName = strategyName;
        this.fileName = request.getFileName();
        this.destPort = request.getPort();
        this.destIp = request.getIp();

        this.plp = plp;
        this.pep = 0.0;
        this.rng = new Random(seed);
        this.windowSize = windowSize;

        this.timedoutNotAcked = new HashSet<>();
        this.mailbox = new LinkedBlockingQueue<>();
        this.timeoutMap = new HashMap<>();
        this.packetMap = new HashMap<>();
        this.acked = new HashSet<>();
    }

    private boolean init() {

        try {
            socket = new DatagramSocket();
            socket.connect(destIp, destPort);
        } catch (SocketException e) {
          return false;
        }

        File file;
        try {
            file = new File(fileName);
            fileStream = new FileInputStream(file);
            socket.send(new AckPacket(file.length(), destPort, destIp).createDatagramPacket());

            TimerTask killIfNotConnected = new TimerTask() {
                @Override
                public void run() {
                    kill();
                }
            };
            TIMER.schedule(killIfNotConnected, timeoutInterval * 3);
            DatagramPacket pkt = new DatagramPacket(new byte[2048], 2048);
            socket.receive(pkt);
            AckPacket ack3 = new AckPacket(pkt);
            if(ack3.isCorrupted()) {
                return false;
            } else {
                killIfNotConnected.cancel();
                System.out.println("Connected!");
            }
        } catch(FileNotFoundException e) {
            sendNotFoundPacket();
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        numOfChunx = (int) Math.ceil(((double)file.length() / (double)CHUNK_SIZE));
        System.out.println("CHUNX: " + numOfChunx);

        initialSeqNo = 1;

        if (strategyName == null) {
            throw new IllegalArgumentException();
        } else if(strategyName.equalsIgnoreCase(TransmissionStrategy.STOP_AND_WAIT)){
            strategy = new StopAndWaitStrategy(numOfChunx, initialSeqNo);
        } else if (strategyName.equalsIgnoreCase(TransmissionStrategy.SELECTIVE_REPEAT)){
            strategy = new SelectiveRepeatStrategy(numOfChunx, initialSeqNo, windowSize);
        } else if (strategyName.equalsIgnoreCase(TransmissionStrategy.GO_BACK_N)) {
            strategy = new GoBackNStrategy(numOfChunx, initialSeqNo, windowSize);
        } else {
            throw new IllegalArgumentException();
        }
        SocketListener socketListener = new SocketListener(socket);
        socketListener.subscribe(this);
        new Thread(socketListener).start();
        return true;
    }

    @Override
    public void run() {
        if(!init()) return;

        long time1 = System.currentTimeMillis();
        int i = 0;
        while (!strategy.isDone() && timeoutInterval <= MAX_PKT_TIMEOUT) {
            long seqNo;
            while((seqNo = strategy.getNextSeqNo()) != -1L) {
                i = 0;
                DataPacket pkt;
                if(!packetMap.containsKey(seqNo)) {
                    pkt = makeDataPacket(seqNo);
                    packetMap.put(seqNo, pkt);
                } else {
                    pkt = packetMap.get(seqNo);
                }
                sendDataPacket(pkt);
                setTimer(seqNo);
            }

            consumeMailbox();

            i++;

            if(i >= 10000) {
                TimeoutTimerTask ttt = timeoutMap.get(strategy.getBase());

                System.out.println("\n\nBase: " + strategy.getBase());
                System.out.println("NextSeqNo: "+ strategy.getNextSeqNum());
                System.out.println("WindowSize: " + strategy.getWindowSize());
                System.out.println("timeout interval: " + timeoutInterval);
                System.out.println("size: " + mailbox.size());
                System.out.println(ttt.scheduledExecutionTime() > System.currentTimeMillis());

                System.out.println(ttt.scheduledExecutionTime());
                System.out.println(ttt.getTimestamp() + ttt.getDelay());
                System.out.println(System.currentTimeMillis());
                System.out.println("\n");
                i = 0;
            }

            try {
                Thread.sleep(NICENESS);
            } catch (InterruptedException e){}
        }

        System.out.println("Loop exited!!!!");
        if(strategy.isDone())
            System.out.println("File is done...");
        else if(timeoutInterval >= MAX_PKT_TIMEOUT)
            System.out.println("Max timeout for packet is reached...");
        clean();
        long time2 = System.currentTimeMillis();
        System.out.println("Time = " + (double) (time2 - time1) / 1000.0);
    }

    public void kill() {
        clean();
    }

    private void clean() {
        socket.close();
        if(!timeoutMap.isEmpty())
            for(Map.Entry<Long, TimeoutTimerTask> e : timeoutMap.entrySet()){
                e.getValue().cancel();
            }
        timeoutMap.clear();
        try {
            fileStream.close();
        } catch (IOException e) {}
        TIMER.purge();
    }

    private void sendNotFoundPacket() {
        try {
            socket.send(new FileNotFoundPacket(fileName, destPort, destIp).createDatagramPacket());
        } catch (IOException e) {
            // Do nothing ...
        }
    }

    private DataPacket makeDataPacket(long seqNo) {
        byte[] data = new byte[CHUNK_SIZE];
        int actualLen;
        try {
            actualLen = fileStream.read(data);
        } catch(IOException e) {
            return null;
        }
        DataPacket dp = new DataPacket(data, actualLen, seqNo, destPort, destIp);
        dp.createDatagramPacket();
        return dp;
    }

    private void sendDataPacket(DataPacket pkt) {
        try {

            if(rng.nextFloat() < pep) { // send corrupted
                pkt.setChecksum(pkt.getCheckSum() * 2 + 1);
                if(pkt.isCorrupted()) {
                    System.out.println("Corrupted data in " + pkt.getSeqNo());
                } else {
                    System.out.println("MISTAKE");
                }
            } else {
                pkt.refreshChecksum();
            }

            if(rng.nextFloat() >= plp) {
                socket.send(pkt.createDatagramPacket());
                System.out.println("Sent " + pkt.getSeqNo());
            } else {
                System.out.println("Dropped " + pkt.getSeqNo());
            }
            strategy.sent(pkt.getSeqNo());
        } catch (IOException e){ }
    }

    private void consumeMailbox() {
        Event e = null;
        while (mailbox.size() != 0) {
            try {
                e = mailbox.take();
            } catch (InterruptedException ex) { ex.printStackTrace(); }

            if(e instanceof TimeoutEvent) {
                handleTimeoutEvent((TimeoutEvent) e);
            } else if(e instanceof AckEvent) {
                handleAckEvent((AckEvent) e);
            }
        }
    }

    private void handleAckEvent(AckEvent e) {
        long seqNo = e.getAckNo();

        if(!timeoutMap.containsKey(seqNo)) // Already seen as a timeout
            return;

        TimeoutTimerTask ttt = timeoutMap.remove(seqNo);
        ttt.cancel();
        acked.add(seqNo);
        if(!timedoutNotAcked.contains(seqNo)) {
            long timeNow = System.currentTimeMillis();
            double sampleRtt = (double) (timeNow - ttt.getTimestamp());
            estimatedRtt = (1.0f - ALPHA) * estimatedRtt + ALPHA * sampleRtt;
            devRtt = (1.0f - BETA) * devRtt + BETA * Math.abs(sampleRtt - estimatedRtt);
            timeoutInterval = (long) Math.ceil(estimatedRtt + 4.0d * devRtt);
        } else {
            timedoutNotAcked.remove(seqNo);
        }
        strategy.acknowledged(seqNo);
        System.out.println("Acked: " + e.getAckNo());
    }

    private void handleTimeoutEvent(TimeoutEvent e) {
        long seqNo = e.getSeqNo();

        if(acked.contains(seqNo) ) {
            System.out.println("Timed out, neglected, Acked before");
            return; // Rescheduled and did not fire again!
        }

        if(timeoutMap.get(seqNo).scheduledExecutionTime() > System.currentTimeMillis()){
            System.out.println("Timed out, neglected, to be executed...");
           return;
        }

        System.out.println("Timed out, waiting for resend: " + e.getSeqNo());

        timeoutMap.remove(seqNo);
        timedoutNotAcked.add(seqNo);

        rescheduleAll();

        TIMER.purge();
        timeoutInterval *= 2L; // Exponential, must guard against!
        strategy.timedout(seqNo);
    }

    private void setTimer(long seqNo) {
        TimeoutTimerTask ttt = new TimeoutTimerTask(seqNo, System.currentTimeMillis(), timeoutInterval);
        ttt.subscribe(this);
        timeoutMap.put(seqNo, ttt);
        TIMER.schedule(ttt, timeoutInterval);
    }

    private void rescheduleAll() {
        Map<Long, TimeoutTimerTask> newMap = new HashMap<>();

        for(Map.Entry<Long, TimeoutTimerTask> entry: timeoutMap.entrySet()) {
            long seqNoE = entry.getKey();
            TimeoutTimerTask tttE = entry.getValue();
            tttE.cancel();
            long sysMillis = System.currentTimeMillis();
            long newDelay = tttE.getTimestamp() + 2L * tttE.getDelay() - sysMillis + NICENESS;
            while(newDelay <= 0) {
                newDelay += (3L * tttE.getDelay());
            }
            TimeoutTimerTask newTttE = new TimeoutTimerTask(seqNoE, sysMillis, newDelay);
            newTttE.subscribe(this);
            TIMER.schedule(newTttE, newDelay);
            newMap.put(seqNoE, newTttE);
        }

        timeoutMap.clear();
        timeoutMap = newMap;
    }

    @Override
    public void update(Event e) {
        if(e instanceof AckEvent || e instanceof TimeoutEvent) {
            try {
                mailbox.put(e);
            } catch (InterruptedException ex) { ex.printStackTrace(); }
        }
    }
}
