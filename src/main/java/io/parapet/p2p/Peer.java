package io.parapet.p2p;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class Peer {
    final String id;
    final String ip;
    final int port;
    private static final int PEER_EXPIRY = 5000;
    long expiresAt;
    ZMQ.Socket socket;


    public Peer(String id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        updateExpire();
    }

    public void connect(String identity, ZContext ctx) {
        socket = ctx.createSocket(SocketType.DEALER);
        socket.setIdentity(identity.getBytes());
        socket.connect(String.format("tcp://%s:%d", ip, port));
    }

    public void updateExpire() {
        expiresAt = System.currentTimeMillis() + PEER_EXPIRY;
    }

    @Override
    public String toString() {
        return String.format("[id=%s,ip=%s,port=%d]", id, ip, port);
    }


    // todo builder
}
