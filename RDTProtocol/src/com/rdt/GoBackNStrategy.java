package com.rdt;

public class GoBackNStrategy extends TransmissionStrategy {

    public GoBackNStrategy(int numOfPackets, long initSeqNo, int initWindowSize) {
        super(numOfPackets, initSeqNo, initWindowSize);
    }

    @Override
    public boolean isDone() {
        return base >= (numOfPackets + initSeqNo);
    }

    @Override
    public void sent(long seqNo) {
        if(seqNo == nextSeqNum)
            nextSeqNum++;
    }

    @Override
    public void acknowledged(long seqNo) {
        if(seqNo >= base && seqNo < base + windowSize) {
            base = seqNo + 1;  // Cumulative Ack!
        }
    }

    @Override
    public void timedout(long seqNo) { // base = 2313, pkts = 5, init = 2313
        if(seqNo >= base && seqNo < base + windowSize) // Paranoid much?
            nextSeqNum = base;
    }

    @Override
    public long getNextSeqNo() {
        if(nextSeqNum >= base && nextSeqNum < base + windowSize && nextSeqNum < initSeqNo+numOfPackets){
            return nextSeqNum;
        } else {
            return -1;
        }
    }

}
