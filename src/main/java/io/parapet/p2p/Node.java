package io.parapet.p2p;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.*;
import scala.Tuple2;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static io.parapet.p2p.Protocol.CmdType.*;
import static io.parapet.p2p.utils.Throwables.suppressError;

public class Node implements Interface {

    private final ZContext ctx; //  Our context wrapper
    private final ZMQ.Socket pipe;
    private final String id;
    private final InterfaceAgent agent;


    public Node(Config config) {
        ctx = new ZContext();
        agent = new InterfaceAgent(config);
        pipe = ZThread.fork(ctx, agent);
        id = config.nodeId;
    }

    public Peer getPeer(String id) {
        return agent.peers.get(id);
    }

    public String getInfo() {
        return String.format("Node[id=%s, ip=%s, port=%d]", id, agent.selfIp, agent.selfPort);
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
        private int selfPort = -1;
        private int version;
        private String selfId;
        private String selfIp;
        private ZMQ.Socket pipe;
        private ZContext ctx;
        private InetAddress multicastAddress;
        private final Map<String, Peer> peers = new HashMap<>();
        private ZMQ.Socket router;

        private static final int PING_INTERVAL = 1000; //  Once per second

        InterfaceAgent(Config config) {
            this.version = config.protocolVer;
            this.selfId = config.nodeId;
            this.multicastAddress = new InetAddress(config.multicastIp, config.multicastPort);
        }

        void init(ZMQ.Socket pipe, ZContext ctx) {
            this.pipe = pipe;
            this.ctx = ctx;
            this.udplib = new Udplib(multicastAddress);
            this.router = ctx.createSocket(SocketType.ROUTER);
            this.selfPort = bindRouter();
            this.selfIp = getSelfIP();
            System.out.println(String.format("node created [id=%s, ip=%s, port=%d]", selfId, selfIp, selfPort));
        }

        // todo add custom maxRetries
        // ports range must be configurable
        private int bindRouter() {
            return router.bindToRandomPort("tcp://*", 5555, 6666);
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

            if (!selfId.equals(beacon.getPeerId())) {
                addPeer(beacon.getPeerId(), peerAddr.getHostString(), beacon.getPort());
            }
        }

        private boolean addPeer(String id, String ip, int port) {
            if (!peers.containsKey(id)) {
                Peer peer = new Peer(id, ip, port);
                peer.connect(selfId, ctx);
                peers.put(id, peer);
                System.out.printf("Connected to peer %s\n", peer);
                sendToPeer(id, HELLO, Protocol.Hello.newBuilder()
                        .setPort(selfPort).setIp(selfIp).build().toByteString());
                sendJoin(id);
                return true;
            } else {
                //todo update peer expireAt
                peers.get(id).updateExpire();
                return false;
            }
        }

        private void handleControlMsg() {
            try {
                Protocol.Command cmd = Protocol.Command.parseFrom(pipe.recv());
                switch (cmd.getCmdType()) {
                    case SHOUT:
                        Protocol.Shout shout = Protocol.Shout.parseFrom(cmd.getData());
                        if (shout.getGroup().isEmpty()) {
                            // send to all peers
                            for (Peer peer : peers.values()) {
                                sendToPeer(peer.id, DELIVER, shout.getData());
                            }
                        }
                        break;
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

        }

        private void handleCmd() {
            ZMsg msg = ZMsg.recvMsg(router);
            msg.popString(); // identity

            try {
                Protocol.Command cmd = Protocol.Command.parseFrom(msg.pop().getData());
                switch (cmd.getCmdType()) {
                    case HELLO:
                        Protocol.Hello hello = Protocol.Hello.parseFrom(cmd.getData());
                        System.out.println("received hello from: " + hello.getIp());
                        addPeer(cmd.getPeerId(), hello.getIp(), hello.getPort());
                        break;
                    case DELIVER:
                        if (!peers.containsKey(cmd.getPeerId())) {
                            throw new IllegalStateException(String.format("peer with id=%s doesn't exist", cmd.getPeerId()));
                        }
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

        private void sendLeft(String peerId) {
            pipe.send(Protocol.Command.newBuilder()
                    .setPeerId(peerId)
                    .setCmdType(Protocol.CmdType.LEFT)
                    .build().toByteArray());
        }

        private void sendToPeer(String peerId, Protocol.CmdType cmdType, ByteString data) {
            if (!peers.containsKey(peerId)) {
                System.out.println(String.format("Error: peer[id=%s] doesn't exist", peerId));
            } else {
                byte[] msg = Protocol.Command.newBuilder()
                        .setPeerId(selfId)
                        .setCmdType(cmdType)
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
                                .setPort(selfPort)
                                .setPeerId(selfId)
                                .build());
                        pingAt = System.currentTimeMillis() + PING_INTERVAL;
                    }

                    //  Delete and report any expired peers
                    reapPeers();

                }


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (udplib != null) udplib.close();
                if (selector != null) suppressError(selector::close);

            }

        }

        private void reapPeers() {
            Iterator<String> iterator = peers.keySet().iterator();

            while (iterator.hasNext()) {
                String peerId = iterator.next();
                if (System.currentTimeMillis() >= peers.get(peerId).expiresAt) {
                    sendLeft(peerId);
                    iterator.remove();
                }
            }
        }
    }


    private static String getSelfIP() {
        java.net.InetAddress inetAddress;
        try {
            inetAddress = java.net.InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException("failed to retrieve self ip", e);
        }
        return inetAddress.getHostAddress();
    }
}
