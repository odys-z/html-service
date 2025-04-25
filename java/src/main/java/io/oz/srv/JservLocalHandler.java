package io.oz.srv;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.odysz.common.FilenameUtils;
import io.oz.syntier.srv.ExternalHosts;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.warn;
import static io.odysz.common.Utils.warnT;

public class JservLocalHandler implements IResUpdater {

	public static String getLocalIp() throws IOException {
	    try(final DatagramSocket socket = new DatagramSocket()) {
	    	boolean succeed = false;
	    	int tried = 0;
	    	while (!succeed && tried++ < 12)
	    		try {
	    			socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
	    			succeed = true;
	    		} catch (UncheckedIOException  e) {
	    			// starting service at network interface not ready yet
	    			warn("Network interface is not ready yet? Try again ...");
	    			try {
						Thread.sleep(3000);
					} catch (InterruptedException e1) { }
	    		}
		  return socket.getLocalAddress().getHostAddress();
		}
	}

	@Override
	public void onStartup(WebConfig wcfg) throws IOException {
		warnT(new Object() {}, "[Not Used] If this is not in a test, there must be some errors in html-service.json.");
		String ip = getLocalIp();
		String host_json = wcfg.startHandler[1];
		host_json = FilenameUtils.concat(
					isNull(wcfg.paths) ? "." : new File(wcfg.paths[0].resource).getAbsolutePath(),
					host_json);

		logi("Updating %s ...", host_json);

//		String jserv = f(wcfg.startHandler[2], ip);
//
//		File file = new File(host_json);
//        File parentDir = file.getParentFile();
//        if (parentDir != null && !parentDir.exists()) {
//            parentDir.mkdirs(); // Create parent directories if they don't exist
//        }

        ExternalHosts hosts;
        try {
        	hosts = Anson.fromPath(host_json);
        } catch (Exception e) {
        	e.printStackTrace();
        	hosts = new ExternalHosts();
        }
        hosts.localip = ip;

//        for (int argx = 2; wcfg.startHandler != null && argx < wcfg.startHandler.length; argx += 2)
//        	hosts.syndomx.put(wcfg.startHandler[argx], f(wcfg.startHandler[argx+1], ip));
//
        hosts.toFile(host_json, JsonOpt.beautify());
	}
}
