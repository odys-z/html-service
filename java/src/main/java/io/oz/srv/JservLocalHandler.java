package io.oz.srv;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import io.odysz.common.FilenameUtils;

import static io.odysz.common.Utils.warn;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;

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
		String ip = getLocalIp();
		String host_json = wcfg.startHandler[1];
		host_json = FilenameUtils.concat(isNull(wcfg.paths) ? "." : new File(wcfg.paths[0].resource).getAbsolutePath(), host_json);
		logi("Updating %s ...", host_json);

		String jserv = f(wcfg.startHandler[2], ip);

		String json = f("{\"host\": \"%s\"}", jserv);
		logi(json);
		
		File file = new File(host_json);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs(); // Create parent directories if they don't exist
        }
        
		try (FileOutputStream fos = new FileOutputStream(host_json, false)) {
			fos.write(json.getBytes(StandardCharsets.UTF_8));
		}
	}
}
