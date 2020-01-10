package io.parapet.p2p;

public class InetAddress {
    final String ip;
    final int port;

    public InetAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}