package ma.ensat.hirelink.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteOkClientTest {

    @Test
    void unPortFermeNeRenvoiePasDannonces() {
        RemoteOkClient client = new RemoteOkClient("http://127.0.0.1:9/api");
        IllegalStateException error = assertThrows(IllegalStateException.class, client::fetch);
        assertTrue(error.getMessage().contains("injoignable"));
    }

    @Test
    void unStatutHttpInvalideEstSignale() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            byte[] body = "indisponible".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RemoteOkClient client = new RemoteOkClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            IllegalStateException error = assertThrows(IllegalStateException.class, client::fetch);
            assertTrue(error.getMessage().contains("503"));
        } finally {
            server.stop(0);
        }
    }
}
