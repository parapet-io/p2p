package io.parapet.p2p;

public class Peer {
    final String id;
    final String ip;
    final int port;

    public Peer(String id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    // todo builder
}
