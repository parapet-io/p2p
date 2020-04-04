package io.parapet.p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MulticastDiscovery {

    public static void main(String[] args) throws Exception {
        String multicastAddr = System.getenv("MULTICAST_ADDR");
        int multicastPort = Integer.parseInt(System.getenv("MULTICAST_PORT"));

        ExecutorService service = Executors.newFixedThreadPool(2);
        service.submit(new MulticastPublisher(multicastAddr, multicastPort));
        service.submit(new MulticastReceiver(multicastAddr, multicastPort)).get();
    }

    static class MulticastReceiver implements Runnable {

        private final byte[] buf = new byte[256];

        private final String addr;
        private final int port;

        MulticastReceiver(String addr, int port) {
            this.addr = addr;
            this.port = port;
        }


        @Override
        public void run() {
            try (MulticastSocket socket = new MulticastSocket(port)) {
                String selfIp = getSelfIP();
                InetAddress group = InetAddress.getByName(addr);
                socket.joinGroup(group);
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = new String(
                            packet.getData(), 0, packet.getLength());

                    if (!packet.getAddress().getHostAddress().equals(selfIp)) {
                        /*System.out.println(String.format("[%s] received '%s' from %s:%d",
                                selfIp,
                                received,
                                packet.getAddress(),
                                packet.getPort()));*/

                        if ("end".equals(received)) {
                            break;
                        }
                    }


                }
                socket.leaveGroup(group);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class MulticastPublisher implements Runnable {

        private final static byte[] MESSAGE_BUF = "ping".getBytes();
        private final String addr;
        private final int port;

        MulticastPublisher(String addr, int port) {
            this.addr = addr;
            this.port = port;
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress group = InetAddress.getByName(addr);
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket packet = new DatagramPacket(MESSAGE_BUF, MESSAGE_BUF.length, group, port);
                    socket.send(packet);
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static String getSelfIP() throws UnknownHostException {
        InetAddress inetAddress = InetAddress.getLocalHost();
        return inetAddress.getHostAddress();
    }

}
