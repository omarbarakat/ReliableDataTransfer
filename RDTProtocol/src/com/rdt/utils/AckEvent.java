package com.rdt.utils;

import com.rdt.AckPacket;
import java.net.DatagramPacket;

public class AckEvent implements Event {

    private AckPacket pkt;
    private long timestamp;

    public AckEvent(AckPacket ackPkt, long ts) {
        this.pkt = ackPkt;
        this.timestamp = ts;
    }

    public long getAckNo() {
        return pkt.getAckNo();
    }
}
