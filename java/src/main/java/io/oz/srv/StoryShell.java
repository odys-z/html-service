package io.oz.srv;

import java.nio.file.Path;

import io.oz.sdoc.DocInf;

/**
 * STUB (media story, WP4): the index.html of a published story, a shell the same for every
 * story but its title and cover, written with j2html (as {@link ResIndex}):
 *
 * <pre>
 * &lt;title&gt;{title}&lt;/title&gt;
 * &lt;meta property="og:title" content="{title}"&gt; &lt;meta property="og:image" content="m/{cover pid}"&gt;
 * &lt;link rel="stylesheet" href="/{pubroot}/_story/viewer.css"&gt;
 * &lt;div id="story"&gt;&lt;/div&gt;
 * &lt;script src="/{pubroot}/_story/viewer.js" data-page="page.json"&gt;&lt;/script&gt;
 * </pre>
 *
 * The viewer (viewer.js, built by semantic-doc/src/viewer) renders page.json, its media files
 * m/&lt;synuid&gt; served by {@link StoryMedia}, from the story's json map.
 *
 * @since media story (stub)
 */
public class StoryShell {

	/** the viewer's folder under the url root of published docs, e.g. /pub/_story/ */
	public static final String viewer = "_story";

	/**
	 * STUB: write the story's index.html into its folder.
	 * TODO WP4.
	 *
	 * @param dir the story's folder, before it's served
	 * @param doc the story, title etc.
	 * @param pubroot url root of the published docs, e.g. "pub"
	 */
	public static void write(Path dir, DocInf doc, String pubroot) throws Exception {
		throw new UnsupportedOperationException("TODO: StoryShell.write()");
	}
}
