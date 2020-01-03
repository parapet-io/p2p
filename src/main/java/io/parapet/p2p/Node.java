package io.parapet.p2p;


import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;
import scala.Tuple2;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.parapet.p2p.utils.Throwables.suppressError;

public class Node implements Interface {

    private ZContext ctx; //  Our context wrapper
    private ZMQ.Socket pipe;


    public Node(Config config) {
        ctx = new ZContext();
        pipe = ZThread.fork(ctx, new InterfaceAgent(config));
    }

    @Override
    public void send(String peerId, byte[] data) {

    }

    @Override
    public String receive() {
        return pipe.recvStr();
    }

    private static class InterfaceAgent implements ZThread.IAttachedRunnable {
        private Udplib udplib;
        private int port;
        private int version;
        private String id;
        private ZMQ.Socket pipe;
        private String multicastIp;
        private int multicastPort;
        private Map<String, Peer> peers = new HashMap<>();

        private static final int PING_INTERVAL = 1000; //  Once per second

        InterfaceAgent(Config config) {
            this.port = config.nodePort;
            this.version = config.protocolVer;
            this.id = UUID.randomUUID().toString();
            this.multicastIp = config.multicastIp;
            this.multicastPort = config.multicastPort;
        }

        void init(ZMQ.Socket pipe) {
            //  todo create ROUTER socket to receive messages from peers
            this.pipe = pipe;
            this.udplib = new Udplib(multicastIp, multicastPort);
        }

        private void handleBeacon() {
            Optional<Tuple2<Protocol.Beacon, SocketAddress>> beaconOpt = udplib.receive();
            assert beaconOpt.isPresent();

            if (!id.equals(beaconOpt.get()._1.getPeerId())) {
                Tuple2<Protocol.Beacon, SocketAddress> beacon = beaconOpt.get();
                InetSocketAddress sourceAddr = (InetSocketAddress) beacon._2;
                if (addPeer(sourceAddr.getHostString(), beacon._1)) {
                    // todo notify frontend
                    System.out.printf("Found peer %s, %s:%d/%d\n", beacon._1.getPeerId(),
                            sourceAddr.getHostString(), sourceAddr.getPort(), beacon._1.getPort());
                }
            }
        }

        private boolean addPeer(String peerIp, Protocol.Beacon beacon) {
            if (!peers.containsKey(beacon.getPeerId())) {
                Peer peer = new Peer(beacon.getPeerId(), peerIp, beacon.getPort());
                // todo connect
                peers.put(peer.id, peer);
                return true;
            } //todo else update expiration date
            return false;
        }


        private void handleControlMsg() {
            // read from `pipe`
        }

        @Override
        public void run(Object[] args, ZContext ctx, ZMQ.Socket pipe) {
            Selector selector = null;
            try {
                init(pipe);
                selector = Selector.open();
                // Send first beacon immediately
                long pingAt = System.currentTimeMillis();
                ZMQ.PollItem[] pollItems = new ZMQ.PollItem[]{
                        udplib.createPollItem(),
                        new ZMQ.PollItem(pipe, ZMQ.Poller.POLLIN),

                };

                while (!Thread.currentThread().isInterrupted()) {
                    long timeout = pingAt - System.currentTimeMillis();
                    if (timeout < 0)
                        timeout = 0;

                    if (ZMQ.poll(selector, pollItems, 2, timeout) == -1)
                        break;              //  Interrupted

                    //  If we had input on the UDP socket, go process that
                    if (pollItems[0].isReadable())
                        handleBeacon();

                    // messages sent by frontend via `pipe`
                    if (pollItems[1].isReadable())
                        handleControlMsg();

                    if (System.currentTimeMillis() >= pingAt) {
                        //  Broadcast our beacon
                        System.out.println("Pinging peers…");
                        udplib.send(Protocol.Beacon.newBuilder()
                                .setVersion(version)
                                .setPort(port)
                                .setPeerId(id)
                                .build());
                        pingAt = System.currentTimeMillis() + PING_INTERVAL;
                    }

                    // todo check peers
                }


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (udplib != null) udplib.close();
                if (selector != null) suppressError(selector::close);

            }

        }
    }
}
