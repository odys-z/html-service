package io.oz.srv;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.mustgt;
import static io.odysz.common.LangExt.str;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import com.google.zxing.WriterException;

import io.odysz.anson.Anson;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.IResUpdater;
import io.odysz.semantic.jserv.StaticResource;
import io.odysz.semantic.jserv.WebConfig;
import io.odysz.semantic.jserv.helper.QrProps;
import io.odysz.semantic.jserv.helper.QrTerminal;
import io.oz.srv.respaths.HtmlPort;

/**
 * Using a {@link ServletContextHandler} serve static file content from single location
 */
public class HtmlServer {
	static final String webinf = "WEB-INF";
	static final String config_json = "html-service.json";
	
	static WebConfig wcfg;
	static Server wcfg_server;
	/** the web-dist root, the folder served at /* (or /), the working folder if not configured */
	static String webroot;

	public static void jvmStart(String[] args) {
		try {
			wcfg = _main(args);
			Utils.logi("Html-web service started on port %s,\tpaths: %s", wcfg.port, str(wcfg.paths));
		} catch (Exception e) {
			e.printStackTrace();
			wcfg.error(e.getClass().getName(), e.getMessage());
		}
	}
	
	/**
	 * Response to SCM stop commands. This is a stub and won't work as
	 * the method is called in different process than the main process.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void jvmStop(String[] args) {
		if (wcfg != null && wcfg_server != null)
			try {
				wcfg_server.stop();
				wcfg_server.join();
				// Thread.sleep(1000);
				Utils.logi("Service stopped. %s", wcfg.error == null ? "" : str(wcfg.error));
			} catch (Exception e) {
				e.printStackTrace();
				if (wcfg == null)
					wcfg = new WebConfig();
				wcfg.error(e.getClass().getName(), e.getMessage());
			}
	}

    public static void main(String[] args) throws Exception {
    	wcfg = _main(args);
        wcfg_server.join();
    }

    private static WebConfig _main(String[] args) throws Exception {
        Server server = HtmlServer.multiReserver();

        // the page of every 404, e.g. /404.html (status 404 kept)
        if (!isblank(wcfg.notfound)) {
        	ErrorPageErrorHandler errs = new ErrorPageErrorHandler();
        	errs.addErrorPage(404, wcfg.notfound);
        	((ServletContextHandler) server.getHandler()).setErrorHandler(errs);
        }

        // before the server starts, e.g. WebAssetsHandler: the pages' files must be there
        for (String[] handler : wcfg.startHandlers())
			((IResUpdater) Class.forName(handler[0])
				.getDeclaredConstructor()
				.newInstance())
				.onUpdate(wcfg, handler);

        IResUpdater dynaRes = null;
        if (wcfg.dynaResource != null) {
        	mustnonull(wcfg.dynaResource.handler, "html-service.json/dynaResource.handler is required.");
        	// loaded only if configured, as this requires optional jars, e.g. doctree.jserv's
        	// io.oz.sdoc.htmlsrv.ResPathsTier, which checks its port's url
			dynaRes = (IResUpdater) Class.forName(wcfg.dynaResource.handler)
				.getDeclaredConstructor(IPort.class, IPort.class, Server.class)
				.newInstance(HtmlPort.respaths, HtmlPort.doctree, server);
			dynaRes.onUpdate(wcfg);
        }

        server.start();

        Utils.logi("[HtmlServer] started on port %s, web-dist root: %s", wcfg.port, webroot);
        Utils.logi("[HtmlServer] dynaResource handler: %s",
        		dynaRes == null ? "not configured" : dynaRes.getClass().getName());
        if (dynaRes != null)
        	Utils.logi("%s", dynaRes); // the handler's resource report, e.g. ResPathsTier.toString()

        // the pages' link, local ip only (a reverse proxy's ip / port is not known here yet)
        String link = f("http://%s:%s/", JservLocalHandler.getLocalIp(), wcfg.port);
        Utils.logi("[HtmlServer] %s", link);
        try {
        	QrTerminal.print(link, new QrProps(true));
        } catch (WriterException e) {
        	Utils.warn("[HtmlServer] Can't print the QR code of %s: %s", link, e.getMessage());
        }
        return wcfg;
	}

	public static Server multiReserver() throws FileNotFoundException, IOException {
    	wcfg = Anson.fromPath(FilenameUtils.concat(webinf, config_json));
    	valid(wcfg);

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(wcfg.port);
        connector.setHost("0.0.0.0");
        server.addConnector(connector);

		 ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		 servletHandler.setContextPath("/");
		 servletHandler.setWelcomeFiles(isNull(wcfg.welcomepages) ? new String[]{"index.html"} : wcfg.welcomepages);

		 String absdir = Paths.get(".").toAbsolutePath().toString();
		 Utils.logi("root path: %s", absdir);
		 Utils.logi("port     : %s", wcfg.port);

		 ServletHolder aHolder = staticHolder("servlet0", absdir);
		 servletHandler.addServlet(aHolder, "/");
		 webroot = absdir;

		 if (!isNull(wcfg.paths))
		 for (StaticResource pth : wcfg.paths) {
			 String res = EnvPath.decodeUri(absdir, pth.resource);
			 Utils.logi("%s: %s -> %s", pth.path, pth.resource, res);
			 aHolder = staticHolder("servlet" + Math.random(), res);
			 servletHandler.addServlet(aHolder, pth.path);
			 if ("/*".equals(pth.path) || "/".equals(pth.path))
				 webroot = res;
		 }

		 server.setHandler(servletHandler);
        
		 wcfg_server = server;
		 return server;
	}
	
	/**
	 * A static resource's servlet, serving the gzip version whenever possible: a file with a
	 * [file].gz beside it (e.g. portfolio.min.js.gz, made by the web pages' build) is answered
	 * with the .gz, Content-Encoding: gzip, to the clients accepting gzip; others get the file.
	 * A .gz without its file isn't served (404): the files packed as .gz only are unzipped when the
	 * service starts, see {@link WebAssetsHandler}.
	 * 
	 * @param name servlet name
	 * @param resourceBase the resource's folder
	 * @return the holder, to be added to a context
	 */
	static ServletHolder staticHolder(String name, String resourceBase) {
		ServletHolder holder = new ServletHolder(name, DefaultServlet.class);
		holder.setInitParameter("resourceBase", resourceBase);
		holder.setInitParameter("pathInfoOnly", "true");
		holder.setInitParameter("precompressed", "gzip=.gz");
		return holder;
	}

	static void valid(WebConfig wcfg) {
		mustnonull(wcfg.paths);
		mustgt(wcfg.port, 1024);
		for (StaticResource pth : wcfg.paths) {
			mustnonull(pth);
			mustnonull(pth.path);
			mustnonull(pth.resource);
		}
	}
}
