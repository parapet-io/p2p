package io.parapet.p2p;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class Peer {
    final String id;
    final String ip;
    final int port;
    public ZMQ.Socket socket;

    public Peer(String id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    public void connect(String identity, ZContext ctx) {
        socket = ctx.createSocket(SocketType.DEALER);
        socket.setIdentity(identity.getBytes());
        socket.connect(String.format("tcp://%s:%d", ip, port));
    }

    // todo builder
}
