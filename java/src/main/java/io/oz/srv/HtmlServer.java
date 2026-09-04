package io.oz.srv;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.mustgt;
import static io.odysz.common.LangExt.str;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import io.odysz.anson.Anson;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;

/**
 * Using a {@link ServletContextHandler} serve static file content from single location
 */
public class HtmlServer {
	static final String webinf = "WEB-INF";
	static final String config_json = "html-service.json";
	
	static WebConfig wcfg;

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
		if (wcfg != null && wcfg.server != null)
			try {
				wcfg.server.stop();
				wcfg.server.join();
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
        wcfg.server.join();
    }

    private static WebConfig _main(String[] args) throws Exception {
        Server server = HtmlServer.multiReserver();
        
        if (!isNull(wcfg.startHandler))
			((IResUpdater) Class.forName(wcfg.startHandler[0])
				.getDeclaredConstructor()
				.newInstance())
				.onStartup(wcfg);

        server.start();
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
		 ServletHolder aHolder = new ServletHolder("servlet0", DefaultServlet.class);

		 Utils.logi("root path: %s", absdir);
		 Utils.logi("port     : %s", wcfg.port);

		 aHolder.setInitParameter("resourceBase", absdir);
		 aHolder.setInitParameter("pathInfoOnly", "true");
		 servletHandler.addServlet(aHolder, "/");

		 if (!isNull(wcfg.paths))
		 for (ResPath pth : wcfg.paths) {
			 aHolder = new ServletHolder("servlet" + Math.random(), DefaultServlet.class);
			 String res = EnvPath.decodeUri(absdir, pth.resource);
			 Utils.logi("%s: %s -> %s", pth.path, pth.resource, res);
			 aHolder.setInitParameter("resourceBase", res);
			 aHolder.setInitParameter("pathInfoOnly", "true");
			 servletHandler.addServlet(aHolder, pth.path);
		 }

		 server.setHandler(servletHandler);
        
        wcfg.server = server;
        return server;
	}
	
	static void valid(WebConfig wcfg) {
		mustnonull(wcfg.paths);
		mustgt(wcfg.port, 1024);
		for (ResPath pth : wcfg.paths) {
			mustnonull(pth);
			mustnonull(pth.path);
			mustnonull(pth.resource);
		}
	}
}
