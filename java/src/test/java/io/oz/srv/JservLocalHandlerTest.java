package io.oz.srv;

import static org.junit.jupiter.api.Assertions.*;

import static io.odysz.common.LangExt.f;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;

class JservLocalHandlerTest {

	@Test
	void testOnStartup() throws IOException {
		String html_json = "{"
				+ "\"type\": \"io.oz.srv.WebConfig\", \"port\": 8901,"
				+ "\"paths\": [{\"path\": \"/\", \"resource\": \"test-dist\"}],"
				+ "\"startHandler\": [\"io.oz.srv.JservLocalHandler\", \"private/host.json\", \"http://%s:8964/jserv-album\"]"
				+ "}";
		WebConfig wcfg = (WebConfig) Anson.fromJson(html_json);
		JservLocalHandler h = new JservLocalHandler();
		h.onStartup(wcfg);
		
		String json = "test-dist/private/host.json";
		try (FileInputStream fis = new FileInputStream(json)) {
			List<String> doc = IOUtils.readLines(fis, StandardCharsets.UTF_8);
			String s = doc.stream().collect(Collectors.joining());
			assertEquals(f("{\"host\": \"http://%s:8964/jserv-album\"}", JservLocalHandler.getLocalIp()), s, "jserv in host.json");
		}
	}

}
