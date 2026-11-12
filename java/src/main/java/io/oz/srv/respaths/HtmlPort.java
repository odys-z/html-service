package io.oz.srv.respaths;

import java.io.IOException;
import java.io.OutputStream;

import io.odysz.anson.AnsonException;
import io.odysz.anson.IJsonable;
import io.odysz.anson.JSONAnsonListener;
import io.odysz.anson.JsonOpt;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantics.x.SemanticException;

/**
 * Ports of html-service. This type is also used by synodes for sending requests to html-service,
 * so no Jetty / servlet types here.
 * 
 * @since 0.2.1
 */
public enum HtmlPort implements IPort {
	/** port provided by {@link io.oz.srv.ResPaths}, localhost only, no session */
	echo("echo.less"), respaths("respaths.serv");

	static {
		JSONAnsonListener.registFactory(HtmlPort.class, 
			(s) -> {
				return HtmlPort.valueOf(s);
			});
	}

	private String url;
	HtmlPort(String v) { url = v; };
	public String url() { return url; }

	@Override
	public IPort valof(String pname) throws SemanticException {
		try {
			IPort p = Port.valueOf(pname);
			return p;
		} catch (Exception e) {
			try { return valueOf(pname); }
			catch (IllegalArgumentException ex) {
				throw new SemanticException(ex.getMessage());
			}
		}
	}

	@Override
	public IJsonable toBlock(OutputStream stream, JsonOpt... opts)
			throws AnsonException, IOException {
		stream.write('\"');
		stream.write(name().getBytes());
		stream.write('\"');
		return this;
	}

	@Override
	public IJsonable toJson(StringBuffer buf) throws IOException, AnsonException {
		buf.append('\"');
		buf.append(name());
		buf.append('\"');
		return this;
	}
}
