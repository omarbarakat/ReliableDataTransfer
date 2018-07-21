package com.rdt.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.TimerTask;

public class TimeoutTimerTask extends TimerTask implements Publisher {

    private Set<Subscriber> subscribers = new HashSet<>();
    private long seqNo;
    private long timestamp;
    private long delay;

    public TimeoutTimerTask(long seqNo, long timestamp, long delay){
        this.seqNo = seqNo;
        this.timestamp = timestamp;
        this.delay = delay;
    }

    @Override
    public void publish(Event e) {
        for(Subscriber s : subscribers) {
            s.update(e);
        }
    }

    @Override
    public void subscribe(Subscriber s) {
        subscribers.add(s);
    }

    @Override
    public void unsubscribe(Subscriber s) {
        subscribers.remove(s);
    }

    @Override
    public void run() {
        publish(new TimeoutEvent(seqNo));
        System.out.println(seqNo + " timedout from ttt");
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDelay() {
        return delay;
    }

    public long getSeqNo() {
        return seqNo;
    }
}
