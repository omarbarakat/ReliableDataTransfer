package com.rdt;

import java.net.DatagramPacket;
import java.net.InetAddress;

public class FileNotFoundPacket extends Packet {

    private String fileName;

    public FileNotFoundPacket(String requestedFilePath, int port, InetAddress ip) {
        this.fileName = requestedFilePath;

        this.chunkData = getBytes(fileName);
        this.chunkLength = this.chunkData.length;

        this.seqNo = 0;
        this.packetType = T_FILE_NOT_FND;
        this.port = port;
        this.ip = ip;
        fillPacketDataFromAtt();
    }

    public FileNotFoundPacket(DatagramPacket pkt){
        if(pkt == null)
            throw new IllegalArgumentException("Null Datagram");
        this.packetData = pkt.getData();
        this.port = pkt.getPort();
        this.ip = pkt.getAddress();
        fillAttsFromPacketData();
        this.fileName = getFileName();
    }

    public String getFileName() {
        byte[] exactChunk = new byte[chunkLength];
        System.arraycopy(chunkData, 0, exactChunk, 0, chunkLength);
        return getString(exactChunk);
    }

}
