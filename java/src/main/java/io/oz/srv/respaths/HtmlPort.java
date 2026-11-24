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
	echo("echo.less"),
	/** port provided by doctree.jserv's io.oz.sdoc.htmlsrv.ResPathsTier, localhost only, no session;
	 * the url is checked by it, ResPathsTier.url */
	respaths("respaths.serv"),
	/**
	 * The local synode's doc-tree port (doctree.jserv's io.oz.sdoc.tier.DoctreeTier, in the synode's own ports
	 * as "doctree"), queried by io.oz.sdoc.htmlsrv.ResPathsTier for the pages published, r/published,
	 * without a session. Its url is checked by ResPathsTier, DoctreeTier.url.
	 */
	doctree("doctree.serv");

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
