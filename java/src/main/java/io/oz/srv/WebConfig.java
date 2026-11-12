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
	 * Optional. Dynamic resources: paths published / removed while the server is running, by
	 * {@link DynaResource#handler}, an {@link io.oz.srv.IResUpdater} only loaded if this is configured,
	 * e. g. for {@link io.oz.srv.ResPathsTier}<pre>
	 * { "handler": "io.oz.srv.ResPathsTier",
	 *   "synode": "http://127.0.0.1:8964/jserv-album", "pollInterval_snd": 3600,
	 *   "resource": "respaths", "danyroot": "pub", "allowDir": false }</pre>
	 * @since 0.2.2
	 */
	DynaResource dynaResource;
	
	public WebConfig() {}

	void error(String name, String message) {
		this.error = new String[] {name, message};
	}
}
