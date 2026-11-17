package io.oz.srv;

import java.io.IOException;

/** 
 * Resources updater, e. g. update private/host.json, as
 * the service can start up on a very different IP address.
 */
public interface IResUpdater {

	void onUpdate(WebConfig wcfg) throws IOException;

	/**
	 * Called for a start handler of {@link WebConfig#startHandlers}, with its own configuration.
	 * Default: {@link #onUpdate(WebConfig)}.
	 * 
	 * @param wcfg
	 * @param handler the handler's configuration, [class name, args...]
	 * @since 0.2.2
	 */
	default void onUpdate(WebConfig wcfg, String[] handler) throws IOException {
		onUpdate(wcfg);
	}
}
