package io.oz.srv;

import org.eclipse.jetty.server.Server;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;

public class WebConfig extends Anson {

	ResPath[] paths;
	int port;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	Server server;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	String[] error;

	/**
	 * Resource handler's class name and its args,
	 * which implements {@link io.oz.srv.IResUpdater}.
	 */
	String[] startHandler;

	String[] welcomepages;

	/**
	 * Optional. Dynamic resource paths' handler: the class name implementing {@link io.oz.srv.IDynPaths}
	 * and its args, e. g. for {@link io.oz.srv.ResPathsTier}<pre>
	 * ["io.oz.srv.ResPathsTier", "http://127.0.0.1:8964/jserv-album", "3600", "respaths.unzip"]</pre>
	 * args: synode's jserv root, polling interval in seconds, the folder for unzipped packages.
	 * @since 0.2.1
	 */
	String[] dynHandler;

	// @AnsonField(ignoreTo=true, ignoreFrom=true)
	// IDynPaths dynpaths;
	
	public WebConfig() {}

	void error(String name, String message) {
		this.error = new String[] {name, message};
	}
}
