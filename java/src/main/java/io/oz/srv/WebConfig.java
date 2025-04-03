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
	
	public WebConfig() {}

	void error(String name, String message) {
		this.error = new String[] {name, message};
	}
}
