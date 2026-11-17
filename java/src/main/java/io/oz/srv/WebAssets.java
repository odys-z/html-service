package io.oz.srv;

import io.odysz.anson.Anson;

/**
 * The list of the files the web pages' build emits, [web root]/webpack-assets.json, written by the
 * pages' webpack.config.js (AssetsListPlugin), one list for each build, e. g.<pre>
 * { "type": "io.oz.srv.WebAssets",
 *   "development": ["login.min.js", "login.min.js.gz", ...],
 *   "production" : ["login.prod.min.js", "login.prod.min.js.gz", ...] }</pre>
 *
 * <p>A package carries a listed file with a listed .gz as the .gz only (jserv-album's tasks.py).
 * {@link WebAssetsHandler#restore(String)} unzips these when the service starts ({@link WebAssetsHandler}): the
 * static servlet serves the .gz only to a file that exists ({@link HtmlServer#staticHolder(String, String)}).</p>
 *
 * @since 0.2.2
 */
public class WebAssets extends Anson {
	/** the list's file name, in a web root */
	public static final String json = "webpack-assets.json";

	String[] development;
	String[] production;

	public WebAssets() {}
}
