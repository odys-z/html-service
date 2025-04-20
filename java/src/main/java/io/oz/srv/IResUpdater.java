package io.oz.srv;

import java.io.IOException;

/** 
 * Resources updater, e. g. update private/host.json, as
 * the service can start up on a very different IP address.
 */
public interface IResUpdater {

	void onStartup(WebConfig wcfg) throws IOException;
}
