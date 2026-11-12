package io.oz.srv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.oz.sdoc.publish.ResPublishReq;
import io.oz.srv.respaths.HtmlPort;

/**
 * Publish, replace and remove a context path on a running server.
 */
class ResPathsTierTest {
	@TempDir
	static Path tmp;

	static Server server;
	static String root;
	static ResPathsTier tier;

	/** The synode's published records, changed by the test. */
	static AnResultset records;

	@BeforeAll
	static void startServer() throws Exception {
		// a configured static path, as HtmlServer.multiReserver() does
		Path home = Files.createDirectories(tmp.resolve("home"));
		Files.writeString(home.resolve("index.html"), "home");

		server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setHost("127.0.0.1");
		connector.setPort(0);
		server.addConnector(connector);

		ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
		ctx.setContextPath("/");
		ctx.setWelcomeFiles(new String[] {"index.html"});
		ServletHolder holder = new ServletHolder("servlet0", DefaultServlet.class);
		holder.setInitParameter("resourceBase", home.toString());
		holder.setInitParameter("pathInfoOnly", "true");
		ctx.addServlet(holder, "/");
		server.setHandler(ctx);

		WebConfig wcfg = (WebConfig) Anson.fromJson("{"
				+ "\"type\": \"io.oz.srv.WebConfig\", \"port\": 8901,"
				+ "\"paths\": [{\"path\": \"/album-web/*\", \"resource\": \"home\"}],"
				+ "\"dynHandler\": [\"io.oz.srv.ResPathsTier\", \"\", \"3600\", \""
				+ tmp.resolve("unzip").toString().replace("\\", "/") + "\"]"
				+ "}");

		records = records();
		tier = new ResPathsTier().pubs(() -> records);
		tier.start(wcfg, server);
		server.start();

		root = "http://127.0.0.1:" + connector.getLocalPort();
	}

	@AfterAll
	static void stopServer() throws Exception {
		tier.stop();
		server.stop();
	}

	@Test
	void testPublishReplaceRemove() throws Exception {
		assertEquals("home", get("/index.html").body());
		assertEquals(404, get("/x7k2/index.html").statusCode());

		// publish
		records = records(row("x7k2", zip("doc-1.zip", "edition 1"), "1"));
		assertEquals("x7k2", refresh());
		assertEquals("edition 1", get("/x7k2/index.html").body());
		assertEquals("edition 1", get("/x7k2/").body());
		assertEquals("home", get("/index.html").body());

		// replace, with a changed stamp
		records = records(row("x7k2", zip("doc-2.zip", "edition 2"), "2"));
		assertEquals("x7k2", refresh());
		assertEquals("edition 2", get("/x7k2/index.html").body());

		// a configured path can't be replaced by a published one
		records = records(
				row("x7k2", zip("doc-2.zip", "edition 2"), "2"),
				row("album-web", zip("doc-3.zip", "edition 3"), "1"));
		assertEquals("x7k2", refresh());

		// remove
		records = records();
		assertEquals("", refresh());
		assertEquals(404, get("/x7k2/index.html").statusCode());
		assertEquals("home", get("/index.html").body());
	}

	/** Post a refresh request to the port, as a synode does. */
	static String refresh() throws Exception {
		AnsonMsg<ResPublishReq> req = new AnsonMsg<ResPublishReq>(HtmlPort.respaths)
				.body(new ResPublishReq().a(A.refresh));
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		req.toBlock(bos);

		String json = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + "/" + HtmlPort.respaths.url()))
					.POST(BodyPublishers.ofByteArray(bos.toByteArray())).build(),
				BodyHandlers.ofString()).body();

		@SuppressWarnings("unchecked")
		AnsonMsg<AnsonResp> resp = (AnsonMsg<AnsonResp>) Anson.fromJson(new ByteArrayInputStream(json.getBytes()));
		assertEquals(MsgCode.ok, resp.code(), json);
		return resp.body(0).msg();
	}

	static HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + path)).GET().build(),
				BodyHandlers.ofString());
	}

	static String zip(String name, String indexHtml) throws Exception {
		Path z = tmp.resolve(name);
		try (OutputStream out = Files.newOutputStream(z);
			 ZipOutputStream zout = new ZipOutputStream(out)) {
			zout.putNextEntry(new ZipEntry("index.html"));
			zout.write(indexHtml.getBytes());
			zout.closeEntry();
		}
		return z.toString();
	}

	static ArrayList<Object> row(String pubpath, String zip, String stamp) {
		return new ArrayList<Object>(Arrays.asList(pubpath, zip, stamp));
	}

	@SafeVarargs
	static AnResultset records(ArrayList<Object>... rows) {
		HashMap<String, Object[]> cols = new HashMap<String, Object[]>();
		cols.put(Cols.pubpath, new Object[] {1, Cols.pubpath});
		cols.put(Cols.zip,     new Object[] {2, Cols.zip});
		cols.put(Cols.stamp,   new Object[] {3, Cols.stamp});
		AnResultset rs = new AnResultset(cols, true);
		for (ArrayList<Object> r : rows)
			rs.append(r);
		return rs;
	}
}
