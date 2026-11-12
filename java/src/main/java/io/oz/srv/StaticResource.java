package io.oz.srv;

import io.odysz.anson.Anson;

/**
 * @since 0.2.2
 */
public class StaticResource extends Anson {
	String path;
	String resource;

	/** Allow show path's directory content */
	boolean allowDir;
	
	public StaticResource() {}
}
