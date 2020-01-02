package io.parapet.p2p;

public interface Interface {

    void send(String peerId, byte[] data);

    //todo void sendToGroup(String group, byte[] data);

}