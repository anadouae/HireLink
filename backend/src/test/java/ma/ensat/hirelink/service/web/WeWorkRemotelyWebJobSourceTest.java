package ma.ensat.hirelink.service.web;

import com.sun.net.httpserver.HttpServer;
import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeWorkRemotelyWebJobSourceTest {

    @Test
    void parseRssPublicFeedWithoutHtmlScraping() throws Exception {
        String rss = """
                <?xml version="1.0"?>
                <rss><channel>
                  <item>
                    <title>Acme: Java Developer</title>
                    <link>https://weworkremotely.com/remote-jobs/acme-java-developer</link>
                    <region>Anywhere in the World</region>
                    <category>Programming</category>
                    <description>&lt;p&gt;Build APIs&lt;/p&gt;</description>
                  </item>
                  <item>
                    <title>Bad Home</title>
                    <link>https://weworkremotely.com/</link>
                    <description>skip homepage</description>
                  </item>
                </channel></rss>
                """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed.rss", exchange -> {
            byte[] body = rss.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/rss+xml");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/feed.rss";
            WeWorkRemotelyWebJobSource src = new WeWorkRemotelyWebJobSource(
                    new JobGeoVerifier(), new WebJobFetchCache(), true, url, 600);
            List<DiscoveredJob> jobs = src.fetch("java", null, null, 10);
            assertEquals(1, jobs.size());
            assertEquals("Java Developer", jobs.get(0).title());
            assertEquals("Acme", jobs.get(0).companyName());
            assertTrue(jobs.get(0).originUrl().contains("/remote-jobs/acme-java-developer"));
            assertEquals("WE_WORK_REMOTELY", jobs.get(0).sourceCode());
        } finally {
            server.stop(0);
        }
    }
}
