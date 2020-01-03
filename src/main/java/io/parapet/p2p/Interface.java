package io.parapet.p2p;

public interface Interface {

    // send to concrete peer
    void send(String peerId, byte[] data);

    // broadcast
    void send(byte[] data);

    // receive message
    Protocol.Command receive();

    //void sendToGroup(String group, byte[] data);

}