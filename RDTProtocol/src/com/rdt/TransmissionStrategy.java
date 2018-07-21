package com.rdt;

import java.io.*;

public abstract class TransmissionStrategy {

    // To check if done ...
    protected int numOfPackets;
    protected long initSeqNo;

    // Running variables
    protected int windowSize;
    protected long base; // first not acked.
    protected long nextSeqNum;

    private int ssthreshold = 10;
    private double cwnd = 0.0;      // used to save info about current window size
    private int acksToCompleteWindow;

    // Invariants:
    // first pkt in window = base
    // last  pkt in window = base + windowSize - 1

    public static final String STOP_AND_WAIT = "StopAndWait";
    public static final String GO_BACK_N = "GoBackN";
    public static final String SELECTIVE_REPEAT = "SelectiveRepeat";

    private PrintWriter congestionTracingFile;

    public TransmissionStrategy(int numOfPackets, long initSeqNo, int initWindowSize) {
        this.numOfPackets = numOfPackets;
        this.initSeqNo = initSeqNo;
        this.windowSize = initWindowSize;
        this.cwnd = (double)initWindowSize;
        this.acksToCompleteWindow = initWindowSize;

        this.nextSeqNum = initSeqNo;
        this.base = initSeqNo;

        try {
            this.congestionTracingFile = new PrintWriter("CongestionTrace", "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }

    protected void updateWinSize_timeout(){
        this.ssthreshold /= 2;
        this.windowSize = (int)Math.ceil(this.windowSize/2.0);       // if packet is lost: window size is halved
        this.cwnd = this.windowSize*1.0;
        this.congestionTracingFile.println(this.windowSize);
        this.acksToCompleteWindow = this.windowSize;

        System.out.println(" *********** "+this.windowSize);

    }

    protected void updateWinSize_ackRecv(){
        if( this.windowSize >= ssthreshold ) {
            cwnd += 1.0/this.windowSize;
            this.windowSize = (int)Math.floor(cwnd);
        } else {
            this.windowSize ++;
            this.cwnd ++;
        }

        System.out.println(" *********** "+this.windowSize);

        if(this.acksToCompleteWindow == 0) {
            this.acksToCompleteWindow = this.windowSize;
            this.congestionTracingFile.println(this.cwnd);
            this.congestionTracingFile.flush();
        } else {
            this.acksToCompleteWindow --;
        }

    }

    public abstract boolean isDone();

    public abstract void sent(long seqNo);

    public abstract void acknowledged(long seqNo);

    public abstract void timedout(long seqNo);

    public abstract long getNextSeqNo();

    public long[] getWindow() {
        long[] w = { base, base + windowSize };
        return w;
    }

    public int getNumOfPackets() {
        return numOfPackets;
    }

    public long getInitSeqNo() {
        return initSeqNo;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public long getBase() {
        return base;
    }

    public long getNextSeqNum() {
        return nextSeqNum;
    }
}