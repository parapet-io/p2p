package io.parapet.p2p;

import com.google.protobuf.InvalidProtocolBufferException;
import io.parapet.p2p.utils.Proto;
import org.zeromq.ZMQ;
import scala.Tuple2;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Optional;

import static io.parapet.p2p.utils.Throwables.suppressError;

public class Udplib {

    private final String multicastIp;
    private final int multicastPort;
    private DatagramChannel dc;
    private MembershipKey membershipKey;
    private InetSocketAddress target;
    private ByteBuffer _buf = ByteBuffer.allocate(Proto.BEACON_SIZE);

    public Udplib(InetAddress address) {
        this.multicastIp = address.ip;
        this.multicastPort = address.port;

        try {
            init();
        } catch (Exception e) {
            close();
            throw new RuntimeException(e);
        }
    }

    public ZMQ.PollItem createPollItem() {
        return new ZMQ.PollItem(dc, ZMQ.Poller.POLLIN);
    }

    public void send(Protocol.Beacon beacon) {
        send(beacon.toByteArray());
    }

    public void send(byte[] data) {
        try {
            dc.send(ByteBuffer.wrap(data), target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Optional<Tuple2<Protocol.Beacon, SocketAddress>> receive() {
        Optional<SocketAddress> res = receive(_buf);

        if (!res.isPresent()) return Optional.empty();


        try {
            Protocol.Beacon b = Protocol.Beacon.parseFrom(_buf.array());
            return Optional.of(Tuple2.apply(b, res.get()));
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return Optional.empty();
        }

    }

    public Optional<SocketAddress> receive(ByteBuffer buf) {
        try {
            buf.clear();
            return Optional.ofNullable(dc.receive(buf));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void init() throws Exception {
        NetworkInterface ni = getNetworkInterface();

        dc = DatagramChannel.open(StandardProtocolFamily.INET)
                .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                .bind(new InetSocketAddress(multicastPort))
                .setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);

        java.net.InetAddress group = java.net.InetAddress.getByName(multicastIp);
        dc.configureBlocking(false);
        membershipKey = dc.join(group, ni);
        target = new InetSocketAddress(group, multicastPort);
    }

    public void close() {
        if (dc != null) {
            suppressError(() -> dc.close());
        }
    }

    private NetworkInterface getNetworkInterface() {
        try (MulticastSocket temp = new MulticastSocket()) {
            return temp.getNetworkInterface();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
