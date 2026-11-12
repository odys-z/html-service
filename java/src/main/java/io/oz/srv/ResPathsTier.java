package io.oz.srv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.prefixIfnull;

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

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.ResourceFactory;

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
 * Dynamic resource paths: publishes docs' html packages (zip) as context paths of this
 * (running) server, and removes them. It is the port {@link HtmlPort#respaths}, for the local synode,
 * no session, localhost only:<ol>
 * <li>{@link A#deploy}, {@link A#remove}: publish / replace, or remove the docs of the request.
 *     A doc first published gets its context path here ({@link DocInf#pubpath}), replied
 *     for the synode to keep;</li>
 * <li>{@link A#refresh}: query the synode for all the published docs ({@link #req2synode()}),
 *     and make context paths the same ({@link #reconcile(ArrayList)}).</li>
 * </ol>
 * The refresh is also triggered when the server started, then every dynHandler[2] seconds
 * (sooner after a failure, e.g. the synode isn't started yet): the paths are restored with
 * the synode's records.
 * 
 * <p>Requires semantic.jserv, doctree.jserv and anclient.java, see {@link WebConfig#dynHandler}.</p>
 * 
 * @since 0.2.1
 */
public class ResPathsTier extends ServPort6<ResPublishReq> implements IResUpdater {
	private static final long serialVersionUID = 1L;

	/** A published doc and its context. */
	static class DynaPublish extends DocInf {
		private final ContextHandler ctx;
		/** the unzipped folder */
		Path dir;

		DynaPublish(DocInf doc, Path unzipRoot, String... welcomepages) throws IOException {
			docId   = doc.docId;
			title   = doc.title;
			pubpath = doc.pubpath;
			htmlUri = doc.htmlUri;
			builtAt = doc.builtAt;

			ctx = new ContextHandler(prefixIfnull("/", pubpath));
			ResourceHandler res = new ResourceHandler();
			res.setBaseResource(ResourceFactory.of(ctx).newResource(dir));
			res.setWelcomeFiles(welcomepages);
			res.setDirAllowed(false);
			ctx.setHandler(res);
		}
		
		DynaPublish setDir(Path d) {
			dir = d;
			return this;
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

	Server server;
	String[] welcomepages;
	Path unzipRoot;
	/** the local synode's jserv root, dynHandler[1] */
	String synode;
	/** polling interval */
	long seconds;
	/** polling again after a failure */
	static final long retry = 60;
	/** time of the next polling, ms */
	long nextpoll;

	ContextHandlerCollection contexts;
	/** configured paths' names, e.g. "album-web" for "/album-web/*" */
	HashSet<String> reserved = new HashSet<String>();
	/** root folders of the configured context "/" */
	ArrayList<String> webroots = new ArrayList<String>();
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
		this.server = server;
		welcomepages = isNull(wcfg.welcomepages) ? new String[] {"index.html"} : wcfg.welcomepages;

		String[] args = wcfg.dynHandler;
		synode    = len(args) > 1 ? args[1] : null;
		seconds   = len(args) > 2 && !isblank(args[2]) ? Long.valueOf(args[2]) : 3600;
		unzipRoot = Paths.get(len(args) > 3 && !isblank(args[3]) ? args[3] : "respaths.unzip").toAbsolutePath();
		clean(unzipRoot);
		Files.createDirectories(unzipRoot);

		AnsonMsg.understandPorts(HtmlPort.respaths);

		// 1. this port, in the configured context ("/")
		ServletContextHandler ctx0 = (ServletContextHandler) server.getHandler();
		ctx0.addServlet(new ServletHolder(this), "/" + HtmlPort.respaths.url());

		// existing resources, for verifying published paths
		String absdir = Paths.get(".").toAbsolutePath().toString();
		webroots.add(absdir);
		reserved.add(HtmlPort.respaths.url());
		if (wcfg.paths != null)
			for (ResPath p : wcfg.paths) {
				// "/*", "/" -> "";  "/album-web/*" -> "album-web"
				String name = p.path.replaceAll("\\*$", "").replaceAll("^/+|/+$", "");
				if (isblank(name))
					webroots.add(EnvPath.decodeUri(absdir, p.resource));
				else
					reserved.add(name);
			}

		// 2. the dynamic paths
		contexts = new ContextHandlerCollection(true);
		contexts.addHandler(ctx0);
		server.setHandler(contexts);

		// 3. polling
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

		Utils.logi("[ResPathsTier] dynamic paths: polling per %s seconds, unzip to %s", seconds, unzipRoot);
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
			throw new SemanticException("The synode's jserv root, dynHandler[1], is not configured.");

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
	 * Publish the doc's html package ({@link DocInf#htmlUri}) at /{@link DocInf#pubpath}.
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

		verifyPath(doc.pubpath);

		// unzip first: a broken package must not take the published one down

		Path zip_dir = Files.createTempDirectory(unzipRoot, doc.pubpath + "-");
		try {
			if (isblank(doc.htmlUri))
				throw new SemanticException("No html package (htmlUri) of doc %s.", doc.docId);
			unzip(Paths.get(doc.htmlUri), zip_dir);
		} catch (Exception e) {
			clean(zip_dir);
			throw e;
		}

		if (old != null)
			undeploy(doc.pubpath);

//		p.ctx = new ContextHandler("/" + p.pubpath);
//		ResourceHandler res = new ResourceHandler();
//		res.setBaseResource(ResourceFactory.of(p.ctx).newResource(p.dir));
//		res.setWelcomeFiles(welcomepages);
//		res.setDirAllowed(false);
//		p.ctx.setHandler(res);
		DynaPublish p = new DynaPublish(doc, unzipRoot, welcomepages).setDir(zip_dir);

		Callback.Completable deployed = new Callback.Completable();
		contexts.deployHandler(p.ctx, deployed);
		deployed.get(30, TimeUnit.SECONDS); // ExecutionException if the deploy failed and TimeoutException if nothing came back
		
		// a handler deployed to a running server is not started by Jetty (12.0.18)
		if (!p.ctx.isStarted())
			p.ctx.start();

		published.put(p.pubpath, p);
		Utils.logi("[ResPathsTier] published /%s <- %s", p.pubpath, p.htmlUri);
	}

	void undeploy(String pubpath) throws Exception {
		DynaPublish p = published.remove(pubpath);
		if (p == null) return;

		Callback.Completable undeployed = new Callback.Completable();
		contexts.undeployHandler(p.ctx, undeployed);
		undeployed.get(30, TimeUnit.SECONDS);
		if (!p.ctx.isStopped())
			p.ctx.stop();

		clean(p.dir);
		Utils.logi("[ResPathsTier] removed /%s", pubpath);
	}

	/**
	 * Get the context (URL) path: short, random, radix 32 (as file names are case insensitive on Windows),
	 * If a doc already published, e.g. the synode missed the reply, keeps its path.
	 */
	String getCtxUrlPath(DocInf doc) {
		for (DynaPublish p : published.values())
			if (!isblank(doc.docId) && eq(p.docId, doc.docId))
				return p.pubpath;

		while (true) {
			String path = Radix32.toString(ThreadLocalRandom.current().nextLong(1L << 30), 6).toLowerCase();
			try {
				verifyPath(path);
				if (!published.containsKey(path))
					return path;
			} catch (SemanticException e) { }
		}
	}

	/**
	 * A published path must be a single path segment, and must not hide existing resources:
	 * a configured path, this port, or a file / folder in the web roots.
	 */
	void verifyPath(String pubpath) throws SemanticException {
		if (isblank(pubpath) || pubpath.matches(".*[/\\\\].*|\\.+"))
			throw new SemanticException("The path is not a path segment.");
		if (reserved.contains(pubpath))
			throw new SemanticException("The path is a configured resource path.");
		for (String root : webroots)
			if (new File(root, pubpath).exists())
				throw new SemanticException("The path is an existing resource.");
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
