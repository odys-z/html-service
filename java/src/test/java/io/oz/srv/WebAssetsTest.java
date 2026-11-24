package io.oz.srv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.nio.file.attribute.FileTime;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.odysz.anson.Anson;
import io.odysz.semantic.jserv.IResUpdater;
import io.odysz.semantic.jserv.WebConfig;

/**
 * The web pages' files packed as .gz only are unzipped by the list of the pages' build,
 * webpack-assets.json ({@link WebAssets}), when the service starts ({@link WebAssetsHandler}).
 */
class WebAssetsTest {
	@TempDir
	Path tmp;

	static final String js1 = "var album = 'portfolio 1'; // ".repeat(64);
	static final String js2 = "var album = 'portfolio 2'; // ".repeat(64);

	/** The list, as album-web's webpack.config.js (AssetsListPlugin) writes it. */
	static final String list = "{\n"
			+ "  \"type\": \"io.oz.srv.WebAssets\",\n"
			+ "  \"development\": [\n"
			+ "    \"fonts/KaTeX_Main-Regular.ttf\",\n"
			+ "    \"fonts/KaTeX_Main-Regular.ttf.gz\",\n"
			+ "    \"fonts/KaTeX_Main-Regular.woff2\",\n"
			+ "    \"portfolio.min.js\",\n"
			+ "    \"portfolio.min.js.gz\",\n"
			+ "    \"portfolio.min.js.map\"\n"
			+ "  ],\n"
			+ "  \"production\": [\n"
			+ "    \"../outside.js\",\n"
			+ "    \"../outside.js.gz\",\n"
			+ "    \"portfolio.prod.min.js\",\n"
			+ "    \"portfolio.prod.min.js.gz\"\n"
			+ "  ]\n"
			+ "}\n";

	@Test
	void testRestore() throws Exception {
		Path web = Files.createDirectories(tmp.resolve("web-dist"));

		// no list: nothing to do
		gz(web.resolve("portfolio.min.js.gz"), js1);
		assertEquals(0, WebAssetsHandler.restoreAsset(web.toString()));
		assertFalse(Files.exists(web.resolve("portfolio.min.js")));

		// as packed: the listed files with a .gz are .gz only
		Files.writeString(web.resolve(WebAssets.json), list);
		gz(web.resolve("portfolio.prod.min.js.gz"), js2);
		Files.createDirectories(web.resolve("fonts"));
		gz(web.resolve("fonts/KaTeX_Main-Regular.ttf.gz"), "ttf");
		Files.writeString(web.resolve("fonts/KaTeX_Main-Regular.woff2"), "woff2");
		Files.writeString(web.resolve("portfolio.min.js.map"), "map");
		// not listed, e.g. a package to download
		gz(web.resolve("synode.tar.gz"), "tar");
		// listed, but not in the web root
		gz(tmp.resolve("outside.js.gz"), "outside");

		assertEquals(3, WebAssetsHandler.restoreAsset(web.toString()));
		assertEquals(js1, Files.readString(web.resolve("portfolio.min.js")));
		assertEquals(js2, Files.readString(web.resolve("portfolio.prod.min.js")));
		assertEquals("ttf", Files.readString(web.resolve("fonts/KaTeX_Main-Regular.ttf")));
		assertEquals("woff2", Files.readString(web.resolve("fonts/KaTeX_Main-Regular.woff2")));
		assertEquals("map", Files.readString(web.resolve("portfolio.min.js.map")));
		assertFalse(Files.exists(web.resolve("synode.tar")));
		assertFalse(Files.exists(tmp.resolve("outside.js")));
		assertTrue(Files.exists(web.resolve("portfolio.min.js.gz")));
		assertFalse(Files.exists(web.resolve("portfolio.min.js.unzip")));

		// started again: the files are what the .gz files have, not written
		FileTime t0 = FileTime.fromMillis(1000000000000L);
		Files.setLastModifiedTime(web.resolve("portfolio.min.js"), t0);
		assertEquals(0, WebAssetsHandler.restoreAsset(web.toString()));
		assertEquals(t0, Files.getLastModifiedTime(web.resolve("portfolio.min.js")));

		// upgraded: a new .gz, older than the file unzipped of the last version
		gz(web.resolve("portfolio.min.js.gz"), js2);
		Files.setLastModifiedTime(web.resolve("portfolio.min.js.gz"), FileTime.fromMillis(900000000000L));
		assertEquals(1, WebAssetsHandler.restoreAsset(web.toString()));
		assertEquals(js2, Files.readString(web.resolve("portfolio.min.js")));

		// the same length, another content
		gz(web.resolve("portfolio.min.js.gz"), js1);
		assertEquals(1, WebAssetsHandler.restoreAsset(web.toString()));
		assertEquals(js1, Files.readString(web.resolve("portfolio.min.js")));
	}

	/**
	 * The start handlers of html-service.json: WebAssetsHandler unzips the files of the paths,
	 * then JservLocalHandler updates host.json.
	 */
	@Test
	void testStartup() throws Exception {
		Path web = Files.createDirectories(tmp.resolve("web-dist"));
		Files.createDirectories(web.resolve("private"));
		Files.writeString(web.resolve(WebAssets.json), list);
		gz(web.resolve("portfolio.min.js.gz"), js1);

		Server server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setHost("127.0.0.1");
		connector.setPort(0);
		server.addConnector(connector);
		ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
		ctx.setContextPath("/");
		ctx.addServlet(HtmlServer.staticHolder("servlet0", web.toString()), "/");
		server.setHandler(ctx);
		server.start();

		try {
			String root = "http://127.0.0.1:" + connector.getLocalPort();

			// the .gz alone isn't served
			assertEquals(404, get(root + "/portfolio.min.js", true).statusCode());

			WebConfig wcfg = (WebConfig) Anson.fromJson("{"
				+ "\"type\": \"io.oz.srv.WebConfig\", \"port\": 8901,"
				+ "\"paths\": [{\"path\": \"/*\", \"resource\": \"" + web.toString().replace("\\", "/") + "\"}],"
				+ "\"startHandlers\": [[\"io.oz.srv.WebAssetsHandler\"], [\"io.oz.srv.JservLocalHandler\", \"private/host.json\"]]"
				+ "}");

			// as HtmlServer does when it starts: the handlers in their order, each with its own args
			assertEquals(2, wcfg.startHandlers().size());
			assertEquals("private/host.json", wcfg.startHandlers().get(1)[1]);
			for (String[] handler : wcfg.startHandlers())
				((IResUpdater) Class.forName(handler[0]).getDeclaredConstructor().newInstance())
					.onUpdate(wcfg, handler);
			assertTrue(Files.exists(web.resolve("private/host.json")));

			HttpResponse<byte[]> resp = get(root + "/portfolio.min.js", true);
			assertEquals(200, resp.statusCode());
			assertEquals("gzip", resp.headers().firstValue("Content-Encoding").orElse(null));
			try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(resp.body()))) {
				assertEquals(js1, new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}

			resp = get(root + "/portfolio.min.js", false);
			assertEquals(200, resp.statusCode());
			assertFalse(resp.headers().firstValue("Content-Encoding").isPresent());
			assertEquals(js1, new String(resp.body(), StandardCharsets.UTF_8));
		}
		finally {
			server.stop();
		}
	}

	static HttpResponse<byte[]> get(String url, boolean gzip) throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url));
		if (gzip)
			req.header("Accept-Encoding", "gzip");
		return HttpClient.newHttpClient().send(req.build(), BodyHandlers.ofByteArray());
	}

	static void gz(Path file, String content) throws Exception {
		ByteArrayOutputStream gz = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
			out.write(content.getBytes(StandardCharsets.UTF_8));
		}
		Files.write(file, gz.toByteArray());
	}
}
