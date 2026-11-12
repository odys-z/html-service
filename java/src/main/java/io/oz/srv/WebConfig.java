package io.oz.srv;

import org.eclipse.jetty.server.Server;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;

public class WebConfig extends Anson {

	StaticResource[] paths;
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
	 * Configuration of {@link #dynHandler}, e. g. for {@link io.oz.srv.ResPathsTier}<pre>
	 * { "synode": "http://127.0.0.1:8964/jserv-album", "pollInterval_snd": 3600,
	 *   "resource": "respaths.unzip", "danyroot": "pub", "allowDir": false }</pre>
	 * @since 0.2.2
	 */
	DynaResource dynaResource;
	
	public WebConfig() {}

	void error(String name, String message) {
		this.error = new String[] {name, message};
	}
}
