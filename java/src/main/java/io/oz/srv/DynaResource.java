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
	 * OPTIONAL
	 * Published resources of synode.
	 */
	String synode;
	
	/** resource root path */
	String resource;
	
	/** 0 for no polling back to the synode */
	int pollInterval_snd;

	/** Allow show path's directory content */
	boolean allowDir;
	
	/** No null, and it's the user's resonsibility to checke the root is not used by other static resources */
	String ziproot;
	
	/** No null, and it's the user's resonsibility to checke the root is not used by other static resources */
	String danyroot;
	
	public DynaResource() {}
}
