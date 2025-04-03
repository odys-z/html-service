package io.oz.srv.web;

import static io.odysz.common.LangExt.f;

import java.io.FileNotFoundException;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.AliasCheck;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.resource.Resources;

/**
 * Using a {@link ServletContextHandler} serve static file content from single location
 */
public class ServletFileServerSingleLocation {
	// static final String albumDist = "src/main/webapp/album-dist-0.4";
	// static final String albumDist = "src/main/webapp";
	static final String albumDist = ".";
	
    public static void main(String[] args) throws Exception {
        Server server = ServletFileServerSingleLocation.newServer(8080);
        server.start();
        server.join();
    }

    public static Server newServer(int port) throws FileNotFoundException {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.addAliasCheck(new AliasCheck() {
        	public boolean checkAlias(String pctx, Resource r) { return true; }
        });

        context.setContextPath("/");
        context.setWelcomeFiles(new String[]{"index.html", "index.htm"});
        ResourceFactory resourceFactory = ResourceFactory.of(context);
        String webappDir = "src/main/webapp/";
        Resource baseResource = resourceFactory.newResource(webappDir);

        if (!Resources.isReadableDirectory(baseResource))
            throw new FileNotFoundException(f("Unable to find base-resource for [%s]", webappDir));

        context.setBaseResource(baseResource);
        server.setHandler(context);

        ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
        holderPwd.setInitParameter("dirAllowed", "true");
        context.addServlet(holderPwd, "/");

        return server;
    }
}
