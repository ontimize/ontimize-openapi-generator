package com.ontimize.openapi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

public class ResourceUtil {
	public static void extractResource(final String resource, final File output) throws IOException {
		try (final InputStream is = ResourceUtil.class.getResourceAsStream(resource)) {
			if (is != null) {
				try (final FileOutputStream fos = new FileOutputStream(output)) {
					int len;
					final byte[] buffer = new byte[1024];

					while ((len = is.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
				}
			}
		}
	}

	public static void extractResources(final String path, final String target) throws URISyntaxException, IOException {
		final URL url = ResourceUtil.class.getResource(path);

		if (url == null) {
			throw new FileNotFoundException();
		}

		final URI uri = url.toURI();
		if ("jar".equals(uri.getScheme())) {
			try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap())) {
				extractResources(fileSystem.getPath(path), Paths.get(target));
			}
		} else {
			extractResources(Paths.get(uri), Paths.get(target));
		}
	}

	private static void extractResources(final Path source, final Path target) throws IOException {
		try (final Stream<Path> walk = Files.walk(source, 1)) {
			for (final Iterator<Path> it = walk.iterator(); it.hasNext();) {
				final Path path = it.next();
				final String sourcePath = path.toString();
				final String targetPath = sourcePath.toString().substring(source.toString().length());
				if (targetPath.length() > 0) {
					final File targetFile = new File(target.toFile(), targetPath);
					final File targetDir = targetFile.getParentFile();
					if (!targetDir.exists()) {
						targetDir.mkdirs();
					}
					extractResource(sourcePath, targetFile);
				}
			}
		}
	}
}