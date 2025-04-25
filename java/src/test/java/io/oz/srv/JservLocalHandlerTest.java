package io.oz.srv;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;

class JservLocalHandlerTest {

	@Test
	void testOnStartup() throws IOException {
		String html_json = "{"
				+ "\"type\": \"io.oz.srv.WebConfig\", \"port\": 8901,"
				+ "\"paths\": [{\"path\": \"/\", \"resource\": \"test-dist\"}],"
				+ "\"startHandler\": [\"io.oz.srv.JservLocalHandler\", \"private/host.json\", \"X29\", \"http://%s:8964/jserv-album\"]"
				+ "}";
		WebConfig wcfg = (WebConfig) Anson.fromJson(html_json);
		JservLocalHandler h = new JservLocalHandler();
		h.onStartup(wcfg);
		
		/*
		String json = "test-dist/private/host.json";
		try (FileInputStream fis = new FileInputStream(json)) {
			List<String> doc = IOUtils.readLines(fis, StandardCharsets.UTF_8);
			String s = doc.stream().collect(Collectors.joining("\n"));
			assertEquals(f("{ \"type\": \"io.oz.srv.ExternalHosts\",\n"
					+ "  \"host\": \"http://%1$s:8964/jserv-album\",\n"
					+ "  \"localip\": \"%1$s\",\n"
					+ "  \"syndomx\": {\"Y201\": \"http://example.com/jserv-album\",\n" // from host.json, the hole point of using ExternalHosts type.
					+ "      \"domain\": \"zsu\",\n"
					+ "      \"X29\": \"http://%1$s:8964/jserv-album\"}\n"
					+ "}", JservLocalHandler.getLocalIp()), s, "jserv in host.json");
		}
		*/
	}

}
