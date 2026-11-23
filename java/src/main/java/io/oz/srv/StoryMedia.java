package io.oz.srv;

import java.io.IOException;
import java.nio.file.Path;

import io.oz.sdoc.publish.PubMedia;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * STUB (media story, WP4): the media of published stories, /{pubroot}/{story}/m/{synuid}.
 *
 * <p>Nothing is copied when a story is published, and the synode is not asked per request: the
 * synode gives each published story's map, io_oz_synuid → the file's path on its volume
 * ({@link PubMedia}), at publish (ResPublishReq.A.deploy) and refresh (startup, polling). Each
 * map is kept as a json file, {@link #maps}/&lt;story&gt;.media.json, outside the served folder
 * (volume paths are never downloadable), replaced by a temp file moved over it, removed with
 * the story.</p>
 *
 * <p>A media request reads the story's json and streams the file, with ranges; 404 if the story
 * or the synuid isn't mapped (e.g. a file not synchronized when the map was given). Other paths
 * go on to the static holder of /{pubroot}/*. No cache: {@link DynaResource#storyMediaCache}
 * is reserved for a future LRU.</p>
 *
 * @since media story (stub)
 */
public class StoryMedia implements Filter {

	/** Warn at startup / refresh if all the maps have more media than this. */
	public static final int WARN_TOTAL = 100_000;
	/** Warn if a story has more media than this (a whole album dropped in by mistake?). */
	public static final int WARN_STORY = 5_000;

	final ResPathsTier tier;

	/** where the maps' json files are, e.g. &lt;resource&gt;.staging/maps; not served */
	final Path maps;

	public StoryMedia(ResPathsTier tier, Path maps) {
		this.tier = tier;
		this.maps = maps;
	}

	/**
	 * STUB: write (replace) the story's map, at deploy or refresh.
	 * TODO WP4: &lt;maps&gt;/&lt;story&gt;.media.json, by a temp file and a move.
	 */
	public void write(PubMedia media) throws IOException {
	}

	/** STUB: remove the story's map, with the story. TODO WP4. */
	public void remove(String pubpath) throws IOException {
	}

	/**
	 * STUB: the file of a story's media, read from the story's json file.
	 * TODO WP4.
	 * @return null if not mapped
	 */
	public Path path(String pubpath, String synuid) throws IOException {
		return null;
	}

	/**
	 * STUB: log the maps' size, at startup and after each refresh, e.g.
	 * "[StoryMedia] 12 stories, 1,840 media mapped (maps 0.4 MB on disk)";
	 * warn if more than {@link #WARN_TOTAL} media in all, or {@link #WARN_STORY} in a story.
	 * TODO WP4.
	 */
	public void report() {
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
			throws IOException, ServletException {
		// TODO WP4: match {story}/m/{synuid}; path(story, synuid); stream the file with ranges,
		// or 404; else chain.doFilter()
		chain.doFilter(req, resp);
	}
}
