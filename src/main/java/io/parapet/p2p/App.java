package io.parapet.p2p;

import java.io.IOException;
import java.net.ServerSocket;

public class App {

    public static void main(String[] args) throws IOException, InterruptedException {
        Config config = new Config.Builder().multicastIp("230.0.0.0")
                .protocolVer(1)
                .multicastPort(4446)
                .nodePort(new ServerSocket(0).getLocalPort()).build();


        Node node = new Node(config);

        Protocol.Command command = node.receive();

        System.out.println(command.getCmdType());
        System.out.println(command.getPeerId());

        node.send("PING".getBytes());

        while (true) {
            Protocol.Command cmd = node.receive();
            if (cmd.getCmdType() == Protocol.CmdType.DELIVER) {
                String data = new String(cmd.getData().toByteArray());
                System.out.println(String.format("RECEIVED, data: %s, peerId: %s", data, cmd.getPeerId()));
                if ("PING".equals(data)) {
                    node.send("PONG".getBytes());
                } else if ("PONG".equals(data)) {
                    node.send("PING".getBytes());
                } else {
                    System.out.println("unknown message");
                }
            }

            Thread.sleep(1000);
        }
    }
}