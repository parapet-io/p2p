package io.parapet.p2p.utils;

final public class Proto {


    /**
     * version - 4 bytes + 1 byte to encode key (field_number, type), total: 5 bytes
     * peerId - 36 bytes + 2 bytes to encode key (field_number, type, length), total: 38 bytes
     * port - 4 bytes + 1 byte to encode key (field_number, type), total: 5 bytes
     * <p>
     * version + peerId + port
     * 5 + 38 + 5 = 48
     */
    public static final int BEACON_SIZE = 48;

}
