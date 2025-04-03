package io.oz.srv;

import io.odysz.anson.Anson;

/**
 * @since 0.1.0
 */
public class ResPath extends Anson {
	String path;
	String resource;

	/** Allow show path's directory content */
	boolean allowDir;
	
	public ResPath() {}
}
