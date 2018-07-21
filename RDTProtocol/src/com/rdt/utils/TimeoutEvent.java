package com.rdt.utils;

public class TimeoutEvent implements Event {

    private long seqNo;

    public TimeoutEvent(long seqNo){
        this.seqNo = seqNo;
    }

    public long getSeqNo(){
        return seqNo;
    }
}
