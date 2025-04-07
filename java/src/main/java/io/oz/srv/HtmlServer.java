package io.oz.srv;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.mustgt;
import static io.odysz.common.LangExt.str;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.AliasCheck;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.resource.Resources;

import io.odysz.anson.Anson;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;

/**
 * Using a {@link ServletContextHandler} serve static file content from single location
 */
public class HtmlServer {
	static final String web_inf = "WEB-INF";
	static final String config_json = "html-service.json";
	
	static WebConfig wcfg;

	public static void jvmStart(String[] args) {
		try {
			wcfg = _main(null);
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
        Server server = HtmlServer.newServer();
        server.start();
        return wcfg;
	}

	public static Server newServer() throws IOException {
    	wcfg = Anson.fromPath(FilenameUtils.concat(web_inf, config_json));
    	valid(wcfg);

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(wcfg.port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.addAliasCheck(new AliasCheck() {
        	public boolean checkAlias(String pctx, Resource r) { return true; }
        });

        context.setWelcomeFiles(new String[]{"index.html", "index.htm"});

        // default path
        ResPath pth0 = wcfg.paths[0];
        context.setContextPath(pth0.path);
        ResourceFactory resourceFactory = ResourceFactory.of(context);
        Resource baseResource = resourceFactory.newResource(pth0.resource);

        if (!Resources.isReadableDirectory(baseResource))
        	// debug at src/java/main/webapp ?
            throw new FileNotFoundException(f("Unable to find base-resource for [%s]", pth0.resource));

        context.setBaseResource(baseResource);
        server.setHandler(context);

        ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
        holderPwd.setInitParameter("dirAllowed", pth0.allowDir ? "true" : "false");
        context.addServlet(holderPwd, pth0.path);

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
