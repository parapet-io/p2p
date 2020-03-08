package io.parapet.p2p;

import java.io.IOException;
import java.net.ServerSocket;

public class App {

    public static void main(String[] args) throws IOException, InterruptedException {
        Config config = Config.builder().multicastIp("230.0.0.0")
                .protocolVer(1)
                .multicastPort(4446)
                .nodePort(new ServerSocket(0).getLocalPort()).build();

        Node node = new Node(config);

        while (true) {
            Protocol.Command cmd = node.receive();
            Peer peer = node.getPeer(cmd.getPeerId());
            switch (cmd.getCmdType()) {
                case JOINED:
                    System.out.println(String.format("peer %s has joined", peer));
                    node.send("PING".getBytes());
                    break;
                case DELIVER:
                    String data = new String(cmd.getData().toByteArray());

                    System.out.println(String.format("received '%s' from: %s", data, peer));
                    if ("PING".equals(data)) {
                        node.send("PONG".getBytes());
                    } else if ("PONG".equals(data)) {
                        node.send("PING".getBytes());
                    } else {
                        System.out.println("unknown message");
                    }
                    break;
                case LEFT:
                    System.out.println(String.format("peer with id=%s left", cmd.getPeerId()));
                    break;
            }

            Thread.sleep(3000);
        }
    }
}