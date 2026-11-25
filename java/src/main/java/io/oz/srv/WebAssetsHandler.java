package io.oz.srv;

import static io.odysz.common.LangExt.isNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

import io.odysz.anson.Anson;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.semantic.jserv.IResUpdater;
import io.odysz.semantic.jserv.StaticResource;
import io.odysz.semantic.jserv.WebConfig;

/**
 * A start handler: the web pages' files packed as .gz only are unzipped, by the list of the pages'
 * build in each static path's folder ({@link WebAssetsHandler#restore(String)}), before the server starts,
 * as a .gz alone isn't served. No args, html-service.json:<pre>
 * "startHandlers": [["io.oz.srv.WebAssetsHandler"], ...]</pre>
 * A path that can't be unzipped is warned about, and the service starts.
 *
 * @since 0.2.2
 */
public class WebAssetsHandler implements IResUpdater {

	@Override
	public void onUpdate(WebConfig wcfg) throws IOException {
		if (isNull(wcfg.paths))
			return;

		String absdir = Paths.get(".").toAbsolutePath().toString();
		for (StaticResource pth : wcfg.paths)
			try {
				String res = EnvPath.decodeUri(absdir, pth.resource);
				Utils.logi("[WebAssetsHandler] %s -> %s", pth.path, res);
				WebAssetsHandler.restoreAsset(res);
			} catch (IOException e) {
				Utils.warn("[WebAssetsHandler] Files of %s are not unzipped, pages can be missing. %s",
						pth.resource, e.getMessage());
			}
	}

	/** @return the little-endian unsigned int at b[at] */
	static long uint32(byte[] b, int at) {
		return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
	}

	/**
	 * @return true if the file is what the .gz has: the length and CRC32 that a gzip file ends with
	 * (RFC 1952) are the file's.
	 */
	static boolean sameRes(Path file, Path gz) throws IOException {
		if (!Files.isRegularFile(file))
			return false;
	
		long crc, len;
		try (RandomAccessFile f = new RandomAccessFile(gz.toFile(), "r")) {
			if (f.length() < 18)
				return false;
			f.seek(f.length() - 8);
			byte[] tail = new byte[8];
			f.readFully(tail);
			crc = WebAssetsHandler.uint32(tail, 0);
			len = WebAssetsHandler.uint32(tail, 4);
		}
	
		if ((Files.size(file) & 0xffffffffL) != len)
			return false;
	
		CRC32 c = new CRC32();
		byte[] buf = new byte[65536];
		try (InputStream in = Files.newInputStream(file)) {
			for (int n; (n = in.read(buf)) > 0; )
				c.update(buf, 0, n);
		}
		return c.getValue() == crc;
	}

	/**
	 * Unzip the listed files of a web root, each with a listed .gz: [file].gz -&gt; [file], if the file
	 * isn't there, or isn't what the .gz has (the file of a version installed before, as an upgrade
	 * only brings the .gz). A file the same as its .gz is not written. A .gz that isn't listed, e.g.
	 * a package to download, is left alone.
	 *
	 * @param webroot a static path's folder, e.g. web-dist
	 * @return the number of the files unzipped; 0 if the folder has no list
	 * @throws IOException the list can't be read, or a file can't be written
	 */
	public static int restoreAsset(String webroot) throws IOException {
		Path root = Paths.get(webroot).toAbsolutePath().normalize();
		Path list = root.resolve(WebAssets.json);
		if (!Files.isRegularFile(list))
			return 0;
	
		WebAssets assets;
		try {
			assets = (WebAssets) Anson.fromPath(list.toString());
		} catch (Exception e) {
			throw new IOException(String.format("Can't read %s: %s", list, e.getMessage()), e);
		}
	
		HashSet<String> names = new HashSet<String>();
		for (String[] build : new String[][] {assets.development, assets.production})
			if (!isNull(build))
				for (String n : build)
					names.add(n);
	
		ArrayList<String> unzipped = new ArrayList<String>();
		int same = 0;
		for (String n : names) {
			if (n.endsWith(".gz") || !names.contains(n + ".gz"))
				continue;
	
			Path file = root.resolve(n).normalize();
			Path gz = root.resolve(n + ".gz").normalize();
			// the list is only for the files in the web root
			if (!file.startsWith(root) || !Files.isRegularFile(gz))
				continue;
			if (WebAssetsHandler.sameRes(file, gz)) {
				same++;
				continue;
			}
	
			Path tmp = file.resolveSibling(file.getFileName() + ".unzip");
			try (InputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
				Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			unzipped.add(n);
		}
	
		unzipped.sort(null);
		Utils.logi("[WebAssets] %s file(s) unzipped in %s, by %s, %s the same as their .gz already%s",
				unzipped.size(), root, WebAssets.json, same, unzipped.isEmpty() ? "." : ":");
		for (String n : unzipped)
			Utils.logi("\t%s <- %s.gz", n, n);
		return unzipped.size();
	}
}
