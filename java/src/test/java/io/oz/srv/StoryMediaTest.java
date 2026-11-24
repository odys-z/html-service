package io.oz.srv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

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
import io.odysz.semantic.jserv.WebConfig;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.oz.sdoc.docx.DocxInf;
import io.oz.sdoc.publish.PubMedia;
import io.oz.sdoc.publish.PuckPubReq;
import io.oz.sdoc.publish.PuckPubReq.A;
import io.oz.sdoc.publish.PuckPubResp;
import io.oz.srv.respaths.HtmlPort;

/**
 * Stories published by the synode: the shell and page.json, the media served by the story's map
 * (with ranges), refreshed and removed with the story.
 */
class StoryMediaTest {
	@TempDir
	static Path tmp;

	static Server server;
	static String root;
	static ResPathsTier tier;
	static Path vol;

	/** the synode's answer to a refresh */
	static PuckPubResp synodeAnswer = new PuckPubResp().respubs(new ArrayList<DocxInf>());

	@BeforeAll
	static void startServer() throws Exception {
		Path home = Files.createDirectories(tmp.resolve("home"));
		vol = Files.createDirectories(tmp.resolve("vol"));

		server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setHost("127.0.0.1");
		connector.setPort(0);
		server.addConnector(connector);

		ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
		ctx.setContextPath("/");
		ServletHolder holder = new ServletHolder("servlet0", DefaultServlet.class);
		holder.setInitParameter("resourceBase", home.toString());
		ctx.addServlet(holder, "/");
		server.setHandler(ctx);

		String tmpdir = tmp.toString().replace("\\", "/");
		WebConfig wcfg = (WebConfig) Anson.fromJson("{"
				+ "\"type\": \"io.oz.srv.WebConfig\", \"port\": 8901,"
				+ "\"dynaResource\": {\"handler\": \"io.oz.srv.ResPathsTier\", \"syn_jserv\": \"http://127.0.0.1:8964/jserv-album\", \"pollInterval_snd\": 3600,"
				+ "                   \"resource\": \"" + tmpdir + "/unzip\", \"danyroot\": \"pub\"}"
				+ "}");
		wcfg.server = server;

		tier = new ResPathsTier() {
			private static final long serialVersionUID = 1L;
			@Override
			PuckPubResp req2synode() { return synodeAnswer; }
		};
		tier.onUpdate(wcfg);
		server.start();

		root = "http://127.0.0.1:" + connector.getLocalPort();
	}

	@AfterAll
	static void stopServer() throws Exception {
		server.stop();
	}

	@Test
	void testStory() throws Exception {
		// the synode's volume: a picture, a video; the committed page
		Path jpg = Files.write(vol.resolve("IMG_0412.jpg"), "jpeg bytes".getBytes());
		Path mp4 = Files.write(vol.resolve("tea.mp4"), "0123456789abcdef".getBytes());
		Path page = Files.writeString(vol.resolve("X29.S.000A.page.json"),
				"{\"type\": \"io.oz.sdoc.story.PuckNode\", \"pucktype\": \"page\", \"id\": \"root\"}");

		DocxInf s = story("X29.S.000A", null, page, "2026-11-23 19:20:00");
		PubMedia m = new PubMedia(s.docId, null, new HashMap<String, String>() {{
			put("X29,00A3F1", jpg.toString());
			put("X29,00A3F7", mp4.toString());
		}});

		// published: shell, page.json, the map
		String path = post(A.deploy, m, s).get(0).pubpath;
		HttpResponse<String> shell = get("/" + path + "/");
		assertEquals(200, shell.statusCode());
		assertTrue(shell.body().contains("<title>Spring Trip</title>"), shell.body());
		assertTrue(shell.body().contains("/pub/_story/viewer.js"), shell.body());
		assertTrue(shell.body().contains("content=\"m/X29%2C00A3F1\""), shell.body());
		assertTrue(get("/" + path + "/page.json").body().contains("io.oz.sdoc.story.PuckNode"));

		// media by the map, the synuid encoded or not
		assertEquals("jpeg bytes", get("/" + path + "/m/X29%2C00A3F1").body());
		assertEquals("jpeg bytes", get("/" + path + "/m/X29,00A3F1").body());
		assertEquals("image/jpeg", get("/" + path + "/m/X29,00A3F1").headers().firstValue("Content-Type").orElse(""));

		// a range of the video
		HttpResponse<String> r = range("/" + path + "/m/X29,00A3F7", "bytes=4-7");
		assertEquals(206, r.statusCode());
		assertEquals("4567", r.body());
		assertEquals("bytes 4-7/16", r.headers().firstValue("Content-Range").orElse(""));
		assertEquals(416, range("/" + path + "/m/X29,00A3F7", "bytes=99-").statusCode());

		// not mapped, or of another story: 404; never a volume path in the served folder
		assertEquals(404, get("/" + path + "/m/X29,00B000").statusCode());
		assertEquals(404, get("/pub/nosuch/m/X29,00A3F1").statusCode());
		// an encoded slash: refused by Jetty (400), never served
		assertTrue(get("/" + path + "/m/..%2Fpage.json").statusCode() >= 400);
		assertFalse(Files.list(tmp.resolve("unzip").resolve(path.substring(4))).anyMatch(
				(f) -> f.getFileName().toString().endsWith(".media.json")));

		// refreshed: the file of a media synchronized since gets mapped
		Path jpg2 = Files.write(vol.resolve("IMG_0415.jpg"), "second".getBytes());
		s.pubpath = path;
		PubMedia m2 = new PubMedia(s.docId, path, new HashMap<String, String>(m.paths) {{
			put("X29,00A3F2", jpg2.toString());
		}});
		synodeAnswer = new PuckPubResp().respubs(docs(s)).media(medias(m2));
		post(A.refresh, null);
		assertEquals("second", get("/" + path + "/m/X29,00A3F2").body());

		// the viewer's folder is never taken as a doc no longer published
		Files.writeString(tmp.resolve("unzip/_story/viewer.js"), "viewer");
		post(A.refresh, null);
		assertEquals("viewer", get("/pub/_story/viewer.js").body());

		// removed: the map too
		synodeAnswer = new PuckPubResp().respubs(docs()).media(medias());
		post(A.refresh, null);
		assertEquals(404, get("/" + path + "/").statusCode());
		assertEquals(404, get("/" + path + "/m/X29,00A3F1").statusCode());
		assertEquals("viewer", get("/pub/_story/viewer.js").body());
	}

	static DocxInf story(String docId, String pubpath, Path page, String builtAt) {
		DocxInf d = new DocxInf(docId, "Spring Trip", "ody");
		d.kind = DocxInf.Kind.story;
		d.pubpath = pubpath;
		d.htmlUri = page.toString();
		d.builtAt = builtAt;
		d.cover = "X29,00A3F1";
		return d;
	}

	static ArrayList<DocxInf> docs(DocxInf... ds) {
		ArrayList<DocxInf> l = new ArrayList<DocxInf>();
		for (DocxInf d : ds) l.add(d);
		return l;
	}

	static ArrayList<PubMedia> medias(PubMedia... ms) {
		ArrayList<PubMedia> l = new ArrayList<PubMedia>();
		for (PubMedia m : ms) l.add(m);
		return l;
	}

	/** Post a request to the port, as the synode does. */
	static ArrayList<DocxInf> post(String a, PubMedia m, DocxInf... docs) throws Exception {
		AnsonMsg<PuckPubReq> req = new AnsonMsg<PuckPubReq>(HtmlPort.respaths)
				.body(new PuckPubReq().respubs(docs).media(m).a(a));
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		req.toBlock(bos);

		String json = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + "/" + HtmlPort.respaths.url()))
					.POST(BodyPublishers.ofByteArray(bos.toByteArray())).build(),
				BodyHandlers.ofString()).body();

		@SuppressWarnings("unchecked")
		AnsonMsg<AnsonResp> resp = (AnsonMsg<AnsonResp>) Anson.fromJson(new ByteArrayInputStream(json.getBytes()));
		assertEquals(MsgCode.ok, resp.code(), json);
		return ((PuckPubResp) resp.body(0)).respubs;
	}

	static HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + path)).GET().build(),
				BodyHandlers.ofString());
	}

	static HttpResponse<String> range(String path, String range) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(root + path)).header("Range", range).GET().build(),
				BodyHandlers.ofString());
	}
}
