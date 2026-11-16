package io.oz.srv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Static resources ({@link HtmlServer#staticHolder(String, String)}) are served in gzip whenever possible.
 */
class StaticGzipTest {
	@TempDir
	static Path tmp;

	static Server server;
	static String root;

	static final String js = "var album = 'portfolio'; // ".repeat(64);

	@BeforeAll
	static void startServer() throws Exception {
		Files.writeString(tmp.resolve("portfolio.min.js"), js);
		ByteArrayOutputStream gz = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
			out.write(js.getBytes(StandardCharsets.UTF_8));
		}
		Files.write(tmp.resolve("portfolio.min.js.gz"), gz.toByteArray());

		// not compressed by the build
		Files.writeString(tmp.resolve("index.html"), "home");

		server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setHost("127.0.0.1");
		connector.setPort(0);
		server.addConnector(connector);

		ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
		ctx.setContextPath("/");
		ctx.addServlet(HtmlServer.staticHolder("servlet0", tmp.toString()), "/");
		server.setHandler(ctx);
		server.start();

		root = "http://127.0.0.1:" + connector.getLocalPort();
	}

	@AfterAll
	static void stopServer() throws Exception {
		server.stop();
	}

	static HttpResponse<byte[]> get(String path, boolean gzip) throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(root + path));
		if (gzip)
			req.header("Accept-Encoding", "gzip");
		return HttpClient.newHttpClient().send(req.build(), BodyHandlers.ofByteArray());
	}

	@Test
	void testGzip() throws Exception {
		// 1. the .gz is served to a client accepting gzip
		HttpResponse<byte[]> resp = get("/portfolio.min.js", true);
		assertEquals(200, resp.statusCode());
		assertEquals("gzip", resp.headers().firstValue("Content-Encoding").orElse(null));
		assertArrayEquals(Files.readAllBytes(tmp.resolve("portfolio.min.js.gz")), resp.body());
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(resp.body()))) {
			assertEquals(js, new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}

		// 2. the file itself to the others
		resp = get("/portfolio.min.js", false);
		assertEquals(200, resp.statusCode());
		assertFalse(resp.headers().firstValue("Content-Encoding").isPresent());
		assertEquals(js, new String(resp.body(), StandardCharsets.UTF_8));

		// 3. and a file without .gz as it is
		resp = get("/index.html", true);
		assertEquals(200, resp.statusCode());
		assertFalse(resp.headers().firstValue("Content-Encoding").isPresent());
		assertEquals("home", new String(resp.body(), StandardCharsets.UTF_8));
	}
}
