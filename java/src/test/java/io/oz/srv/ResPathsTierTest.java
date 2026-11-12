package io.oz.srv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.publish.ResPublishReq;
import io.oz.sdoc.publish.ResPublishReq.A;
import io.oz.sdoc.publish.ResPublishResp;
import io.oz.srv.respaths.HtmlPort;

/**
 * Publish, replace and remove context paths on a running server.
 * The synode's published docs, answered to the tier's query, are {@link #synodocs}.
 */
class ResPathsTierTest {
	@TempDir
	static Path tmp;

	static Server server;
	static String root;
	static ResPathsTier tier;

	/** The docs published, as the synode keeps them. */
	static ArrayList<DocInf> synodocs = new ArrayList<DocInf>();

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

		String tmpdir = tmp.toString().replace("\\", "/");
		WebConfig wcfg = (WebConfig) Anson.fromJson("{"
				+ "\"type\": \"io.oz.srv.WebConfig\", \"port\": 8901,"
				+ "\"paths\": [{\"path\": \"/*\", \"resource\": \"" + tmpdir + "/home\"}],"
				+ "\"dynHandler\": [\"io.oz.srv.ResPathsTier\", \"\", \"3600\", \"" + tmpdir + "/unzip\", \"pub\"]"
				+ "}");
		wcfg.server = server;

		tier = new ResPathsTier() {
			private static final long serialVersionUID = 1L;
			@Override
			ArrayList<DocInf> req2synode() { return synodocs; }
		};
		tier.onUpdate(wcfg);
		server.start();

		root = "http://127.0.0.1:" + connector.getLocalPort();
	}

	@AfterAll
	static void stopServer() throws Exception {
		server.stop();
	}

	/** Requests of the local synode: deploy, replace, remove. */
	@Test
	void testDeployReplaceRemove() throws Exception {
		assertEquals("home", get("/index.html").body());

		// first published: the path is assigned here
		String zip1 = zip("doc-1.zip", "edition 1");
		String path = post(A.deploy, doc("d1", null, zip1, "2026-11-12 01:00:00")).get(0).pubpath;
		assertTrue(path.matches("pub/[0-9a-v]{6}"), path);
		assertEquals("edition 1", get("/" + path + "/index.html").body());
		assertEquals("edition 1", get("/" + path + "/").body());
		assertEquals("home", get("/index.html").body());

		// the synode missed the reply and asks again: the same path
		assertEquals(path, post(A.deploy, doc("d1", null, zip1, "2026-11-12 01:00:00")).get(0).pubpath);

		// replace, as rebuilt
		assertEquals(path, post(A.deploy, doc("d1", path, zip("doc-2.zip", "edition 2"), "2026-11-12 02:00:00")).get(0).pubpath);
		assertEquals("edition 2", get("/" + path + "/index.html").body());

		// a broken package doesn't take the published one down
		post(MsgCode.exGeneral, A.deploy, doc("d1", path, tmp.resolve("no-such.zip").toString(), "2026-11-12 03:00:00"));
		assertEquals("edition 2", get("/" + path + "/index.html").body());

		// only one path segment in the root of published docs
		String zip3 = zip("doc-3.zip", "edition 3");
		post(MsgCode.exSemantic, A.deploy, doc("d3", "album-web", zip3, "2026-11-12 03:00:00"));
		post(MsgCode.exSemantic, A.deploy, doc("d3", "pub/a/b", zip3, "2026-11-12 03:00:00"));
		post(MsgCode.exSemantic, A.deploy, doc("d3", "pub/..", zip3, "2026-11-12 03:00:00"));

		// the docs can't be listed
		assertEquals(403, get("/pub/").statusCode());
		assertEquals(path, String.join(",", tier.paths()));

		// remove
		assertEquals(0, post(A.remove, doc("d1", path, null, null)).size());
		assertEquals(404, get("/" + path + "/index.html").statusCode());
		assertEquals("home", get("/index.html").body());
	}

	/**
	 * Refresh: the paths are the same as the synode's docs, e.g. restored after html-service restarted.
	 */
	@Test
	void testRefresh() throws Exception {
		DocInf a1 = doc("da", "pub/a001", zip("a-1.zip", "a 1"), "2026-11-12 01:00:00");
		DocInf b1 = doc("db", "pub/b001", zip("b-1.zip", "b 1"), "2026-11-12 01:00:00");
		DocInf b2 = doc("db", "pub/b001", zip("b-2.zip", "b 2"), "2026-11-12 02:00:00");

		synodocs = docs(a1, b1);
		assertEquals(2, post(A.refresh).size());
		assertEquals("a 1", get("/pub/a001/").body());
		assertEquals("b 1", get("/pub/b001/").body());

		// a001 removed, b001 rebuilt
		synodocs = docs(b2);
		ArrayList<DocInf> pubs = post(A.refresh);
		assertEquals(1, pubs.size());
		assertEquals("pub/b001", pubs.get(0).pubpath);
		assertEquals(404, get("/pub/a001/").statusCode());
		assertEquals("b 2", get("/pub/b001/").body());

		synodocs = docs();
		assertEquals(0, post(A.refresh).size());
		assertEquals(404, get("/pub/b001/").statusCode());
	}

	/** Post a request to the port, as a synode does. */
	static ArrayList<DocInf> post(String a, DocInf... docs) throws Exception {
		return post(MsgCode.ok, a, docs);
	}

	static ArrayList<DocInf> post(MsgCode expect, String a, DocInf... docs) throws Exception {
		AnsonMsg<ResPublishReq> req = new AnsonMsg<ResPublishReq>(HtmlPort.respaths)
				.body(new ResPublishReq().respubs(docs).a(a));
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		req.toBlock(bos);

		String json = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + "/" + HtmlPort.respaths.url()))
					.POST(BodyPublishers.ofByteArray(bos.toByteArray())).build(),
				BodyHandlers.ofString()).body();

		@SuppressWarnings("unchecked")
		AnsonMsg<AnsonResp> resp = (AnsonMsg<AnsonResp>) Anson.fromJson(new ByteArrayInputStream(json.getBytes()));
		assertEquals(expect, resp.code(), json);
		return expect == MsgCode.ok ? ((ResPublishResp) resp.body(0)).respubs : null;
	}

	static HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + path)).GET().build(),
				BodyHandlers.ofString());
	}

	static DocInf doc(String docId, String pubpath, String htmlUri, String builtAt) {
		DocInf d = new DocInf(docId, "Doc " + docId, "tester");
		d.pubpath = pubpath;
		d.htmlUri = htmlUri;
		d.builtAt = builtAt;
		return d;
	}

	static ArrayList<DocInf> docs(DocInf... docs) {
		return new ResPublishReq().respubs(docs).respubs;
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
}
