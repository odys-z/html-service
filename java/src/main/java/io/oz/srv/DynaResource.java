package io.oz.srv;

import io.odysz.anson.Anson;

/**
 * @since 0.2.2
 */
public class DynaResource extends Anson {
	/**
	 * Add a comment, the usage of each field to the json file.
	 */
	String help;
	
	/**
	 * Handler. For 0.2.2, always "io.oz.srv.ResPathsTier".
	 */
	String handler;
	
	/**
	 * OPTIONAL
	 * The local synode's jserv root, e.g. http://127.0.0.1:8964/jserv-album, for the docs it published.
	 * Named synode before 0.2.1 was released.
	 */
	String syn_jserv;
	
	/**
	 * Not null. The folder of the published docs: packages are unzipped here, each in a sub folder,
	 * and served as static htmls at {@link #danyroot}, the same as {@link StaticResource#resource}.
	 * It's emptied when the server starts, and it's the user's responsibility to check the folder
	 * is not used by other static resources.
	 */
	String resource;
	
	/** 0 for no polling back to the synode */
	int pollInterval_snd;

	/** Allow show path's directory content */
	boolean allowDir;
	
	/** No null, and it's the user's resonsibility to checke the root is not used by other static resources */
	String danyroot;
	
	public DynaResource() {}
}
