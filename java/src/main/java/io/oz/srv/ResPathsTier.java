package io.oz.srv;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort6;
import io.odysz.semantics.x.SemanticException;
import io.oz.sdoc.DocInf;
import io.oz.sdoc.publish.ResPublishReq;
import io.oz.srv.respaths.HtmlPort;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dynamic resource paths: publishes zip packages registered at a synode as context paths
 * of this (running) server, and removes those no longer registered.
 * 
 * <p>The synode's records are the single source of truth. This tier only makes the context
 * paths the same as the records ({@link #reconcile()}), triggered<ol>
 * <li>when the server started, then every dynHandler[2] seconds,</li>
 * <li>by a refresh request to {@link ResPaths}, sent by the synode after it changed the records.</li>
 * </ol>
 * 
 * Requires semantic.jserv, see {@link WebConfig#dynHandler}.
 * 
 * @since 0.2.1
 */
public class ResPathsTier extends ServPort6<ResPublishReq> implements IResUpdater {
	private static final long serialVersionUID = 1L;

	/** The source of published paths' records, see {@link Cols} for columns. */
	public class DynaPublish extends DocInf {

		ContextHandler ctx;
		String urlpath;

		public DynaPublish(DocInf doc) {
			// TODO Auto-generated constructor stub
		}
		/** @return records, null for unknown (nothing will be changed) */
//		AnResultset query() throws Exception;
	}

//	static class Published {
//	}

//	IPubRecords pubs;

	Server server;
	String[] welcomepages;
	Path unzipRoot;
	long seconds;

	ContextHandlerCollection contexts;
	HashSet<String> reserved = new HashSet<String>();
	HashMap<Path, DynaPublish> published = new HashMap<Path, DynaPublish>();

	ScheduledExecutorService scheduler;

	public ResPathsTier() {
		super(HtmlPort.respaths);
//		pubs = () -> {
//			Utils.warn("[ResPathsTier] Querying the synode is not implemented yet.");
//			return null;
//		};
	}

//	/**
//	 * @param pubs the records' source
//	 * @return this
//	 */
//	public ResPathsTier pubs(IPubRecords pubs) {
//		this.pubs = pubs;
//		return this;
//	}

	@Override
	public void onUpdate(WebConfig wcfg) throws IOException {
		req2synode(null); // ResPathsReq.A.refresh
	}

	public void start(WebConfig wcfg, Server server) throws Exception {
		this.server = server;
		welcomepages = isNull(wcfg.welcomepages) ? new String[] {"index.html"} : wcfg.welcomepages;

		String[] args = wcfg.dynHandler;
		seconds   = len(args) > 2 && !isblank(args[2]) ? Long.valueOf(args[2]) : 3600;
		unzipRoot = Paths.get(len(args) > 3 && !isblank(args[3]) ? args[3] : "respaths.unzip").toAbsolutePath();
		clean(unzipRoot);
		Files.createDirectories(unzipRoot);

		AnsonMsg.understandPorts(HtmlPort.respaths);

		// 1. the reverse direction: a port for synodes' refresh requests, in the configured context ("/")
		ServletContextHandler ctx0 = (ServletContextHandler) server.getHandler();
		// ctx0.addServlet(new ServletHolder(new ResPaths(this)), "/" + HtmlPort.respaths.url());
		ctx0.addServlet(new ServletHolder(this), "/" + HtmlPort.respaths.url());

		reserved.add(HtmlPort.respaths.url());
		if (wcfg.paths != null)
			for (ResPath p : wcfg.paths)
				// reserved.add(p.path.replaceAll("^/+|/\\*$|/+$", ""));
				reserved.add(p.path);

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
					try {
						// reconcile();
					}
					catch (Exception e) {
						Utils.warn("[ResPathsTier] Updating paths failed. Will try again in %s seconds. %s: %s",
								seconds, e.getClass().getName(), e.getMessage());
					}
				}, 0, seconds, TimeUnit.SECONDS);
			}
		});

		Utils.logi("[ResPathsTier] dynamic paths: polling per %s seconds, unzip to %s", seconds, unzipRoot);
	}

	public void stop() {
		if (scheduler != null)
			scheduler.shutdownNow();
	}

//	/**
//	 * Make context paths the same as the published records.
//	 * @return paths currently published
//	 */
//	public synchronized String[] reconcile() throws Exception {
//		AnResultset rs = pubs.query();
//		if (rs == null)
//			return paths();
//
//		HashSet<String> recs = new HashSet<String>();
//		rs.beforeFirst();
//		while (rs.next()) {
//			String path = rs.getString(Cols.pubpath);
//			try {
//				verifyPath(path);
//				recs.add(path);
//
//				String stamp = rs.getString(Cols.stamp);
//				Published p = published.get(path);
//				if (p != null && eq(p.stamp, stamp))
//					continue;
//				if (p != null)
//					undeploy(path);
//				deploy(path, rs.getString(Cols.zip), stamp);
//			} catch (Exception e) {
//				// a bad record must not block the others
//				Utils.warn("[ResPathsTier] Ignoring path '%s'. %s: %s", path, e.getClass().getName(), e.getMessage());
//			}
//		}
//
//		for (String path : paths())
//			if (!recs.contains(path))
//				undeploy(path);
//
//		return paths();
//	}

	public synchronized String[] paths() {
		return published.keySet().toArray(new String[0]);
	}

	void verifyPath(String path) throws SemanticException {
		// FIXME not verify string literal, but the resources are existing

//		if (isblank(path) || !path.matches("[0-9A-Za-z_\\-]+"))
//			throw new SemanticException("Invalid context path.");
//		if (reserved.contains(path))
//			throw new SemanticException("The path is a configured resource path.");
	}

	void deploy(ResPublishReq req) throws Exception {
//		String path; String zip; String stamp = req.respubs.get(0).stamp;
		DocInf p = req.respubs.get(0);
		DynaPublish dynapub = new DynaPublish(p);

//		p.stamp = stamp;
		Path path = Files.createTempDirectory(unzipRoot, p.pubpath + "-");
		Path folder = path.getParent().toAbsolutePath();
		unzip(Paths.get(p.zip), folder);

		ContextHandler ctx = new ContextHandler("/" + path);
		ResourceHandler res = new ResourceHandler();
		res.setBaseResource(ResourceFactory.of(ctx).newResource(folder));
		res.setWelcomeFiles(welcomepages);
		res.setDirAllowed(false);
		ctx.setHandler(res);

		Callback.Completable deployed = new Callback.Completable();
		contexts.deployHandler(ctx, deployed);
		deployed.get(30, TimeUnit.SECONDS);
		// a handler deployed to a running server is not started by Jetty (12.0.18)
		if (!ctx.isStarted())
			ctx.start();

		published.put(path, dynapub);
		Utils.logi("[ResPathsTier] published /%s <- %s", path, dynapub.urlpath);
	}

	void undeploy(String path) throws Exception {
		DynaPublish p = published.remove(path);
		if (p == null) return;

		Callback.Completable undeployed = new Callback.Completable();
		contexts.undeployHandler(p.ctx, undeployed);
		undeployed.get(30, TimeUnit.SECONDS);
		if (!p.ctx.isStopped())
			p.ctx.stop();

		clean(p.dir);
		Utils.logi("[ResPathsTier] removed /%s", path);
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

	@Override
	protected void onGet(AnsonMsg<ResPublishReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {
		if (InetAddress.getByName(msg.addr()).isLoopbackAddress())
			throw new SemanticException("Res-path-tier: only POST method available.");
	}

	@Override
	protected void onPost(AnsonMsg<ResPublishReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, SemanticException {

		if (!InetAddress.getByName(msg.addr()).isLoopbackAddress())
			throw new SemanticException("Hello, %s !", msg.addr());

		ResPublishReq req = msg.body(0);
		if (A.refresh.equals(req.a()))
			try {
				write(resp, ok(String.join(",", req2synode(req))));
			} catch (Exception e) {
				write(resp, err(MsgCode.exGeneral, "%s: %s", e.getClass().getName(), e.getMessage()));
			}
		else
			throw new SemanticException("Unknown request: a = %s", req.a());
	}

	/**
	 * Request to synodes for update.
	 * @param req 
	 * @return
	 */
	private CharSequence req2synode(ResPublishReq req) {
		return null;
	}
}
