package com.rdt;

public class StopAndWaitStrategy extends TransmissionStrategy {

    public StopAndWaitStrategy(int numOfPackets, long initSeqNo) {
        super(numOfPackets, initSeqNo, 1);
    }

    @Override
    public boolean isDone() {
        return (base == (numOfPackets + initSeqNo));
    }


    @Override
    public void acknowledged(long seqNo) {
        if (seqNo == base)
            base++;
    }

    @Override
    public void timedout(long seqNo) {
        if (seqNo == base){
            nextSeqNum--;
        }
    }

    @Override
    public void sent(long seqNo) {
        if(seqNo == nextSeqNum)
            nextSeqNum++;
    }

    @Override
    public long getNextSeqNo() {
        if(nextSeqNum == base && nextSeqNum < initSeqNo+numOfPackets){
            return nextSeqNum;
        }
        return -1L;
    }

}