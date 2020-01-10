package io.parapet.p2p;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.*;
import scala.Tuple2;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.parapet.p2p.Protocol.CmdType.SHOUT;
import static io.parapet.p2p.utils.Throwables.suppressError;

public class Node implements Interface {

    private ZContext ctx; //  Our context wrapper
    private ZMQ.Socket pipe;
    private String id;


    public Node(Config config) {
        ctx = new ZContext();
        pipe = ZThread.fork(ctx, new InterfaceAgent(config));
        id = config.nodeId;
    }

    @Override
    public void send(String peerId, byte[] data) {

    }

    @Override
    public void send(byte[] data) {
        ByteString shoutMsg =
                Protocol.Shout.newBuilder()
                        .setGroup("")
                        .setData(ByteString.copyFrom(data))
                        .build().toByteString();


        byte[] cmd = Protocol.Command.newBuilder()
                .setPeerId(id)
                .setCmdType(SHOUT)
                .setData(shoutMsg)
                .build().toByteArray();

        pipe.send(cmd);
    }

    @Override
    public Protocol.Command receive() {
        try {
            return Protocol.Command.parseFrom(pipe.recv());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private static class InterfaceAgent implements ZThread.IAttachedRunnable {
        private Udplib udplib;
        private int port;
        private int version;
        private String id;
        private ZMQ.Socket pipe;
        private ZContext ctx;
        private InetAddress multicastAddress;
        private Map<String, Peer> peers = new HashMap<>(); // todo concurrent hash map

        private ZMQ.Socket router;

        private static final int PING_INTERVAL = 1000; //  Once per second

        InterfaceAgent(Config config) {
            this.port = config.nodePort;
            this.version = config.protocolVer;
            this.id = config.nodeId;
            this.multicastAddress = new InetAddress(config.multicastIp, config.multicastPort);
        }

        void init(ZMQ.Socket pipe, ZContext ctx) {
            this.pipe = pipe;
            this.ctx = ctx;
            this.udplib = new Udplib(multicastAddress);
            this.router = ctx.createSocket(SocketType.ROUTER);
            bindRouter();
            System.out.println("node created with id = " + id + ", port = " + port);
        }

        // todo add custom maxRetries
        // ports range must be configurable
        private void bindRouter() {
            router.bindToRandomPort("tcp://*", 5555, 6666);
        }

        private void handleBeacon() {
            Optional<Tuple2<Protocol.Beacon, SocketAddress>> beaconOpt = udplib.receive();
            if (!beaconOpt.isPresent()) {
                //  might happen sometimes
                return;
            }

            Tuple2<Protocol.Beacon, SocketAddress> beaconPair = beaconOpt.get();
            Protocol.Beacon beacon = beaconPair._1;
            InetSocketAddress peerAddr = (InetSocketAddress) beaconPair._2;

            if (!id.equals(beacon.getPeerId())) {
                if (addPeer(peerAddr.getHostString(), beacon)) {
                    // todo notify frontend
                    System.out.printf("Found peer %s, %s:%d/%d\n", beacon.getPeerId(),
                            peerAddr.getHostString(), peerAddr.getPort(), beacon.getPort());
                } else {
                    System.out.println("peer exists");
                }
            }
        }

        private boolean addPeer(String peerIp, Protocol.Beacon beacon) {
            if (!peers.containsKey(beacon.getPeerId())) {
                Peer peer = new Peer(beacon.getPeerId(), peerIp, beacon.getPort());
                peers.put(peer.id, peer);
                peer.connect(id, ctx);
                // send hello to the peer
                sendJoin(peer.id);
                return true;
            } //todo else update expiration date if peer exists
            return false;
        }


        private void handleControlMsg() {
            System.out.println("handleControlMsg");
            try {
                Protocol.Command cmd = Protocol.Command.parseFrom(pipe.recv());
                switch (cmd.getCmdType()) {
                    case SHOUT:
                        Protocol.Shout shout = Protocol.Shout.parseFrom(cmd.getData());
                        if (shout.getGroup().isEmpty()) {
                            // send to all peers
                            for (Peer peer : peers.values()) {
                                sendToPeer(peer.id, shout.getData());
                            }
                        }
                        break;
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }


        }

        private void handleCmd() {
            System.out.println("handleCmd");
            ZMsg msg = ZMsg.recvMsg(router);
            msg.popString(); // identity

            try {
                Protocol.Command cmd = Protocol.Command.parseFrom(msg.pop().getData());
                switch (cmd.getCmdType()) {
                    case DELIVER:
                        pipe.send(cmd.toByteArray());
                        break;
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        // notifies the client/frontend that new peer has joined
        // todo do we need ip:port ?
        private void sendJoin(String peerId) {
            pipe.send(Protocol.Command.newBuilder()
                    .setPeerId(peerId)
                    .setCmdType(Protocol.CmdType.JOINED)
                    .build().toByteArray());
        }

        public void sendToPeer(String peerId, ByteString data) {
            if (!peers.containsKey(peerId)) {
                System.out.println(String.format("Error: peer[id=%s] doesn't exist", peerId));
            } else {
                byte[] msg = Protocol.Command.newBuilder()
                        .setPeerId(id) // sender id is this node
                        .setCmdType(Protocol.CmdType.DELIVER)
                        .setData(data)
                        .build().toByteArray();
                peers.get(peerId).socket.send(msg);
            }
        }

        @Override
        public void run(Object[] args, ZContext ctx, ZMQ.Socket pipe) {
            Selector selector = null;
            try {
                init(pipe, ctx);
                selector = Selector.open();
                // Send first beacon immediately
                long pingAt = System.currentTimeMillis();
                ZMQ.PollItem[] pollItems = new ZMQ.PollItem[]{
                        udplib.createPollItem(),
                        new ZMQ.PollItem(pipe, ZMQ.Poller.POLLIN),
                        new ZMQ.PollItem(router, ZMQ.Poller.POLLIN)

                };

                while (!Thread.currentThread().isInterrupted()) {
                    long timeout = pingAt - System.currentTimeMillis();
                    if (timeout < 0)
                        timeout = 0;

                    if (ZMQ.poll(selector, pollItems, 3, timeout) == -1)
                        break;              //  Interrupted

                    //  If we had input on the UDP socket, go process that
                    if (pollItems[0].isReadable())
                        handleBeacon();

                    // messages sent by frontend via `pipe`
                    if (pollItems[1].isReadable())
                        handleControlMsg();

                    if (pollItems[2].isReadable())
                        handleCmd();


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
