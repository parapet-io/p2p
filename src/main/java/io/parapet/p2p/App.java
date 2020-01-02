package io.parapet.p2p;

import java.io.IOException;
import java.net.ServerSocket;

public class App {

    public static void main(String[] args) throws IOException {
        Config config = new Config.Builder().multicastIp("230.0.0.0")
                .protocolVer(1)
                .multicastPort(4446)
                .nodePort(new ServerSocket(0).getLocalPort()).build();


        new Node(config).start();
    }
}