package io.parapet.p2p;

import java.util.UUID;

public class Config {

    public final String multicastIp;
    public final int multicastPort;
    public final int nodePort;
    public final int protocolVer;
    public final String nodeId = UUID.randomUUID().toString();
    public final int maxSocketBindRetries;

    public Config(Builder builder) {
        this.multicastIp = builder.multicastIp;
        this.multicastPort = builder.multicastPort;
        this.nodePort = builder.nodePort;
        this.protocolVer = builder.protocolVer;
        this.maxSocketBindRetries = builder.maxSocketBindRetries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String multicastIp;
        private int multicastPort;
        private int nodePort;
        private int protocolVer;
        private int maxSocketBindRetries = 100;

        public Builder multicastIp(String multicastIp) {
            this.multicastIp = multicastIp;
            return this;
        }

        public Builder multicastPort(int multicastPort) {
            this.multicastPort = multicastPort;
            return this;
        }

        public Builder nodePort(int nodePort) {
            this.nodePort = nodePort;
            return this;
        }

        public Builder protocolVer(int protocolVer) {
            this.protocolVer = protocolVer;
            return this;
        }

        public Builder maxSocketBindRetries(int value) {
            this.maxSocketBindRetries = value;
            return this;
        }

        public Config build() {
            return new Config(this);
        }

    }
}
