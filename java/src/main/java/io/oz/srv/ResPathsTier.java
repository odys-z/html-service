package io.oz.srv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.mustnonull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;

import io.odysz.common.EnvPath;
import io.odysz.common.Radix32;
import io.odysz.common.Utils;
import io.odysz.jclient.HttpServClient;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort6;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.publish.ResPublishReq;
import io.oz.sdoc.publish.ResPublishReq.A;
import io.oz.sdoc.publish.ResPublishResp;
import io.oz.srv.respaths.HtmlPort;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dynamic resource paths: publishes docs' html packages (zip) at this (running) server, and
 * removes them. All the docs are under one url root, {@link DynaResource#danyroot}, e. g. /pub/er3cou,
 * served from one folder, {@link DynaResource#resource}: publishing a doc is unzipping its package into a sub folder,
 * removing is deleting the folder. It is the port {@link HtmlPort#respaths}, for the local synode,
 * no session, localhost only:<ol>
 * <li>{@link A#deploy}, {@link A#remove}: publish / replace, or remove the docs of the request.
 *     A doc first published gets its context path here ({@link DocInf#pubpath}), replied
 *     for the synode to keep;</li>
 * <li>{@link A#refresh}: query the synode for all the published docs ({@link #req2synode()}),
 *     and make context paths the same ({@link #reconcile(ArrayList)}).</li>
 * </ol>
 * The refresh is also triggered when the server started, then every {@link DynaResource#pollInterval_snd} seconds
 * (sooner after a failure, e.g. the synode isn't started yet): the paths are restored with
 * the synode's records.
 * 
 * <p>Requires semantic.jserv, doctree.jserv and anclient.java, see {@link WebConfig#dynHandler}, {@link WebConfig#dynaResource}.</p>
 * 
 * @since 0.2.1
 */
public class ResPathsTier extends ServPort6<ResPublishReq> implements IResUpdater {
	private static final long serialVersionUID = 1L;

	/** A published doc. */
	static class DynaPublish extends DocInf {
		DynaPublish(DocInf doc) {
			docId   = doc.docId;
			title   = doc.title;
			pubpath = doc.pubpath;
			htmlUri = doc.htmlUri;
			builtAt = doc.builtAt;
		}

		/** @return the doc's header, a type the synode knows */
		DocInf inf() {
			DocInf d = new DocInf(docId, title, null);
			d.pubpath = pubpath;
			d.htmlUri = htmlUri;
			d.builtAt = builtAt;
			return d;
		}
	}

	/** the folder of all the published docs, {@link DynaResource#resource}, served at /{@link #pubroot} */
//	Path unzipRoot;
	/** where packages are unzipped before published, and removed docs are moved to; not served */
	Path staging;

	/** url root of all the published docs, {@link DynaResource#danyroot}, e.g. "pub" */
	String pubroot;
	/** the local synode's jserv root, {@link DynaResource#synode} */
	String synode;
	/** polling interval, {@link DynaResource#pollInterval_snd}; 0: no polling */
	long seconds;
	/** polling again after a failure */
	static final long retry = 60;
	/** time of the next polling, ms */
	long nextpoll;

	/** [pubpath, published doc] */
	HashMap<String, DynaPublish> published = new HashMap<String, DynaPublish>();

	ScheduledExecutorService scheduler;

	public ResPathsTier() {
		super(HtmlPort.respaths);
	}

	/**
	 * Called once before the server is started, by {@link HtmlServer}.
	 */
	@Override
	public void onUpdate(WebConfig wcfg) throws IOException {
		start(wcfg, wcfg.server);
	}

	void start(WebConfig wcfg, Server server) throws IOException {
		DynaResource res = wcfg.dynaResource;
		mustnonull(res, "html-service.json/dynaResource is required by dynHandler ResPathsTier.");
		mustnonull(res.resource, "html-service.json/dynaResource.resource, the folder of published docs, is required.");
		mustnonull(res.danyroot, "html-service.json/dynaResource.danyroot, the url root of published docs, is required.");

		synode    = res.synode;
		seconds   = res.pollInterval_snd;
//		unzipRoot = Paths.get(EnvPath.decodeUri(Paths.get(".").toAbsolutePath().toString(), res.resource)).normalize();
		pubroot   = res.danyroot.replaceAll("^/+|/\\*$|/+$", "");
		if (!pubroot.matches("[0-9A-Za-z_\\-\\.]+"))
			throw new IOException("html-service.json/dynaResource.danyroot must be a folder name: " + res.danyroot);

		// start empty: the docs are restored with the synode's records
		Path unzipRoot = Paths.get(EnvPath.decodeUri(Paths.get(".").toAbsolutePath().toString(), res.resource)).normalize();
		staging = unzipRoot.resolveSibling(unzipRoot.getFileName() + ".staging");
		clean(staging);
		Files.createDirectories(staging);

		AnsonMsg.understandPorts(HtmlPort.respaths);

		// 1. this port, in the configured context ("/")
		ServletContextHandler ctx0 = (ServletContextHandler) server.getHandler();
		ctx0.addServlet(new ServletHolder(this), "/" + HtmlPort.respaths.url());

		// 2. the published docs, a static resource path as the configured ones (HtmlServer.multiReserver())
		ServletHolder holder = new ServletHolder("respaths-pub", DefaultServlet.class);
		holder.setInitParameter("resourceBase", unzipRoot.toString());
		holder.setInitParameter("pathInfoOnly", "true");
		holder.setInitParameter("dirAllowed", String.valueOf(res.allowDir));
		ctx0.addServlet(holder, "/" + pubroot + "/*");

		// 3. polling, also restores the docs when started
		if (seconds > 0 && !isblank(synode))
		server.addEventListener(new LifeCycle.Listener() {
			@Override
			public void lifeCycleStarted(LifeCycle event) {
				scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
					Thread t = new Thread(r, "respaths-polling");
					t.setDaemon(true);
					return t;
				});
				scheduler.scheduleWithFixedDelay(() -> {
					if (System.currentTimeMillis() < nextpoll)
						return;
					try {
						reconcile(req2synode());
						nextpoll = System.currentTimeMillis() + seconds * 1000;
					}
					catch (Exception e) {
						Utils.warn("[ResPathsTier] Updating paths failed. Will try again in %s seconds. %s: %s",
								Math.min(retry, seconds), e.getClass().getName(), e.getMessage());
					}
				}, 0, Math.min(retry, seconds), TimeUnit.SECONDS);
			}

			@Override
			public void lifeCycleStopping(LifeCycle event) {
				if (scheduler != null)
					scheduler.shutdownNow();
			}
		});

		Utils.logi("[ResPathsTier] dynamic paths: /%s/* -> %s, %s", pubroot, unzipRoot,
				seconds > 0 && !isblank(synode) ? f("polling %s per %s seconds", synode, seconds) : "no polling");
	}

	@Override
	protected void onGet(AnsonMsg<ResPublishReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {
		throw new SemanticException("Res-path-tier: only POST method available.");
	}

	@Override
	protected void onPost(AnsonMsg<ResPublishReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {

		if (!InetAddress.getByName(msg.addr()).isLoopbackAddress())
			throw new SemanticException("Hello, %s !", msg.addr());

		ResPublishReq req = msg.body(0);
		try {
			ArrayList<DocInf> docs;
			if (A.refresh.equals(req.a())) {
				reconcile(req2synode());
				docs = published();
			}
			else if (A.deploy.equals(req.a()))
				docs = deploy(req);
			else if (A.remove.equals(req.a())) {
				remove(req);
				docs = published();
			}
			else
				throw new SemanticException("Unknown request: a = %s", req.a());

			write(resp, ok(new ResPublishResp().respubs(docs)));
		} catch (SemanticException e) {
			throw e;
		} catch (Exception e) {
			write(resp, err(MsgCode.exGeneral, "%s: %s", e.getClass().getName(), e.getMessage()));
		}
	}

	/**
	 * Query the local synode ({@link HtmlPort#respub}) for all the published docs.
	 * @return docs
	 * @throws IOException the synode isn't reachable
	 * @throws SemanticException the synode refused
	 */
	ArrayList<DocInf> req2synode() throws Exception {
		if (isblank(synode))
			throw new SemanticException("The synode's jserv root, html-service.json/dynaResource.synode, is not configured.");

		AnsonMsg<ResPublishReq> req = new AnsonMsg<ResPublishReq>(HtmlPort.respub)
				.body(new ResPublishReq().a(A.refresh));
		ResPublishResp resp = (ResPublishResp) new HttpServClient()
				.post(HtmlPort.respub.url(synode), req)
				.body(0);
		return resp.respubs == null ? new ArrayList<DocInf>() : resp.respubs;
	}

	/** @return published paths, sorted */
	public synchronized String[] paths() {
		return published.keySet().stream().sorted().toArray(String[]::new);
	}

	/** @return published docs, sorted by paths */
	synchronized ArrayList<DocInf> published() {
		ArrayList<DocInf> docs = new ArrayList<DocInf>();
		for (String path : paths())
			docs.add(published.get(path).inf());
		return docs;
	}

	/**
	 * Make context paths the same as the published docs: deploy the new and the rebuilt, remove the others.
	 * @param docs all the published docs, each with its pubpath
	 */
	synchronized void reconcile(ArrayList<DocInf> docs) throws Exception {
		HashSet<String> pubs = new HashSet<String>();
		for (DocInf doc : docs) {
			if (isblank(doc.pubpath))
				continue;
			pubs.add(doc.pubpath);
			try { deploy(doc, false); }
			catch (Exception e) {
				// a bad record must not block the others
				Utils.warn("[ResPathsTier] Ignoring path '%s'. %s: %s", doc.pubpath, e.getClass().getName(), e.getMessage());
			}
		}

		for (String path : paths())
			if (!pubs.contains(path))
				undeploy(path);
	}

	/** @return the request's docs, with pubpaths */
	synchronized ArrayList<DocInf> deploy(ResPublishReq req) throws Exception {
		ArrayList<DocInf> docs = new ArrayList<DocInf>();
		if (req.respubs != null)
			for (DocInf doc : req.respubs) {
				deploy(doc, true);
				docs.add(doc);
			}
		return docs;
	}

	synchronized void remove(ResPublishReq req) throws Exception {
		if (req.respubs != null)
			for (DocInf doc : req.respubs)
				undeploy(doc.pubpath);
	}

	/**
	 * Publish the doc's html package ({@link DocInf#htmlUri}) at /{@link DocInf#pubpath}, e.g. /pub/er3cou.
	 * A doc without pubpath gets a new one, set to {@code doc}.
	 * 
	 * @param force false: a published doc is replaced only if rebuilt, i. e. {@link DocInf#builtAt}
	 * or {@link DocInf#htmlUri} changed; true: always, as the synode asked for it.
	 */
	void deploy(DocInf doc, boolean force) throws Exception {
		if (isblank(doc.pubpath))
			doc.pubpath = getCtxUrlPath(doc);

		DynaPublish old = published.get(doc.pubpath);
		if (!force && old != null && eq(old.builtAt, doc.builtAt) && eq(old.htmlUri, doc.htmlUri))
			return;

		Path dir = folder(doc.pubpath);

		// unzip first, where it's not served: a broken package must not take the published one down
		Path unzipped = Files.createTempDirectory(staging, dir.getFileName() + "-");
		try {
			if (isblank(doc.htmlUri))
				throw new SemanticException("No html package (htmlUri) of doc %s.", doc.docId);
			unzip(Paths.get(doc.htmlUri), unzipped);

			// then swap the folders
			retire(dir);
			Files.move(unzipped, dir);
		} catch (Exception e) {
			clean(unzipped);
			throw e;
		}

		published.put(doc.pubpath, new DynaPublish(doc));
		Utils.logi("[ResPathsTier] published /%s <- %s", doc.pubpath, doc.htmlUri);
	}

	void undeploy(String pubpath) throws Exception {
		if (published.remove(pubpath) == null)
			return;

		retire(folder(pubpath));
		Utils.logi("[ResPathsTier] removed /%s", pubpath);
	}

	/**
	 * Take a published folder away: moved out of the served folder at once, then deleted.
	 * If it can't be moved, e.g. a file is in use (Windows), nothing is changed.
	 */
	void retire(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		Path trash = Files.createTempDirectory(staging, "retired-");
		Files.move(dir, trash.resolve(dir.getFileName()));
		clean(trash);
	}

	/**
	 * @param pubpath e.g. "pub/er3cou"
	 * @return the doc's folder, e.g. respaths.unzip/er3cou
	 * @throws SemanticException the path is not the url root and one path segment
	 */
	Path folder(String pubpath) throws SemanticException {
		if (isblank(pubpath) || !pubpath.startsWith(pubroot + "/"))
			throw new SemanticException("The path is not in the root of published docs, /%s.", pubroot);
		String name = pubpath.substring(pubroot.length() + 1);
		if (isblank(name) || name.matches(".*[/\\\\].*|\\.+"))
			throw new SemanticException("The path is not a path segment in /%s.", pubroot);
		return pubroot.resolve(name);
	}

	/**
	 * Get the url path of a doc first published: the root and a short, random name, radix 32
	 * (as file names are case insensitive on Windows), e.g. "pub/er3cou".
	 * If a doc already published, e.g. the synode missed the reply, keeps its path.
	 */
	String getCtxUrlPath(DocInf doc) {
		for (DynaPublish p : published.values())
			if (!isblank(doc.docId) && eq(p.docId, doc.docId))
				return p.pubpath;

		String path;
		do {
			path = pubroot + "/" + Radix32.toString(ThreadLocalRandom.current().nextLong(1L << 30), 6).toLowerCase();
		} while (published.containsKey(path));
		return path;
	}

	static void unzip(Path zip, Path dir) throws IOException {
		try (InputStream fin = Files.newInputStream(zip);
			 ZipInputStream zin = new ZipInputStream(fin)) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				Path f = dir.resolve(e.getName()).normalize();
				if (!f.startsWith(dir))
					throw new IOException("Zip entry is out of the target folder: " + e.getName());
				if (e.isDirectory())
					Files.createDirectories(f);
				else {
					Files.createDirectories(f.getParent());
					Files.copy(zin, f, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	/** Delete the folder. Files still in use (Windows) are left to the next start. */
	static void clean(Path dir) {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		} catch (IOException e) {
			Utils.warn("[ResPathsTier] Can't clean %s: %s", dir, e.getMessage());
		}
	}
}
