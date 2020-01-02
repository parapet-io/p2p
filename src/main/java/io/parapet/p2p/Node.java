package io.parapet.p2p;


import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import scala.Tuple2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.UUID;

public class Node implements Interface {

    private final Udplib udplib;
    private final int port;
    private final int version;
    private final String id;
    private final ZContext ctx;

    private static final int PING_INTERVAL = 1000; //  Once per second

    public Node(Config config) {
        this.udplib = new Udplib(config.multicastIp, config.multicastPort);
        this.port = config.nodePort;
        this.version = config.protocolVer;
        this.id = UUID.randomUUID().toString();
        this.ctx = new ZContext();
    }

    @Override
    public void send(String peerId, byte[] data) {

    }

    void start() throws IOException {
        Selector selector = Selector.open();
        //  We use zmq_poll to wait for activity on the UDP socket, because
        //  this function works on non-0MQ file handles. We send a beacon
        //  once a second, and we collect and report beacons that come in
        //  from other nodes:
        PollItem[] pollItems = new PollItem[]{new PollItem(udplib.getSocket(), ZMQ.Poller.POLLIN)};
        //  Send first ping right away
        long pingAt = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            long timeout = pingAt - System.currentTimeMillis();
            if (timeout < 0)
                timeout = 0;
            if (ZMQ.poll(selector, pollItems, 1, timeout) == -1)
                break;              //  Interrupted

            //  Someone answered our ping
            if (pollItems[0].isReadable()) {
                Optional<Tuple2<Protocol.Beacon, SocketAddress>> beaconOpt = udplib.receive();
                assert beaconOpt.isPresent();

                if (!id.equals(beaconOpt.get()._1.getPeerId())) {
                    Tuple2<Protocol.Beacon, SocketAddress> beacon = beaconOpt.get();
                    InetSocketAddress sourceAddr = (InetSocketAddress) beacon._2;
                    System.out.printf("Found peer %s, %s:%d/%d\n", beacon._1.getPeerId(),
                            sourceAddr.getHostString(), sourceAddr.getPort(), beacon._1.getPort());
                }

            }
            if (System.currentTimeMillis() >= pingAt) {
                //  Broadcast our beacon
                System.out.println("Pinging peers…");
                udplib.send(Protocol.Beacon.newBuilder().setVersion(version).setPort(port).setPeerId(id).build());
                pingAt = System.currentTimeMillis() + PING_INTERVAL;
            }
        }
        udplib.close();
        ctx.close();
    }


    static class Peer {
        final String id;
        final String ip;
        final int port;

        Peer(String id, String ip, int port) {
            this.id = id;
            this.ip = ip;
            this.port = port;
        }
    }
}
