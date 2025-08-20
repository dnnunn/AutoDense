package com.betterdairy.autodense.nl;

import java.io.IOException;
import java.net.ServerSocket;

public final class PortFinder {
    private PortFinder() {}
    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
