//package io.oz.srv;
//
//import org.eclipse.jetty.server.Server;
//
///**
// * Handler of dynamic resource paths, i. e. paths published / removed while the server is running.
// * The implementation is configured in html-service.json/dynHandler[0], and only loaded if configured,
// * e. g. {@link ResPathsTier}, which requires semantic.jserv.
// * 
// * @since 0.2.1
// */
//public interface IDynPaths {
//
//	/**
//	 * Called before the server is started.
//	 * @param wcfg
//	 * @param server the not started server, created by {@link HtmlServer#multiReserver()}
//	 */
//	void start(WebConfig wcfg, Server server) throws Exception;
//
//	void stop();
//}
