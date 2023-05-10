package com.ontimize.openapi;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.openapitools.codegen.config.CodegenConfiguratorUtils.applyImportMappingsKvpList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.auth.AuthParser;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.util.ClasspathHelper;

/**
 * Goal which generates client/server code from a OpenAPI json/yaml definition.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class CodeGenMojo extends AbstractMojo {
	/**
	 * The build context is only avail when running from within eclipse. It is used
	 * to update the eclipse-m2e-layer when the plugin is executed inside the IDE.
	 */
	@Component
	private BuildContext buildContext = new DefaultBuildContext();

	/**
	 * The project being built.
	 */
	@Parameter(readonly = true, required = true, defaultValue = "${project}")
	private MavenProject mavenProject;

	/**
	 * Adds authorization headers when fetching the swagger definitions remotely. " Pass in a
	 * URL-encoded string of name:header with a comma separating multiple values
	 */
	@Parameter(name = "auth", property = "ontimize.openapi.generator.maven.plugin.auth")
	private String auth;

	/**
	 * A map of classes and the import that should be used for that class
	 */
	@Parameter(name = "importMappings", property = "ontimize.openapi.generator.maven.plugin.importMappings")
	private List<String> importMappings;

	/**
	 * Location of the OpenAPI spec, as URL or file.
	 */
	@Parameter(name = "inputSpec", property = "ontimize.openapi.generator.maven.plugin.inputSpec")
	private String inputSpec;

	/**
	 * The default package to use for the generated objects
	 */
	@Parameter(name = "packageName", property = "ontimize.openapi.generator.maven.plugin.packageName")
	private String packageName;

	/**
	 * The default package to use for the generated objects
	 */
	@Parameter(name = "modelPackageName", property = "ontimize.openapi.generator.maven.plugin.modelPackageName")
	private String modelPackageName;

	/**
	 * Skip the execution if the source file is older than the output folder.
	 */
	@Parameter(name = "skipIfSpecIsUnchanged", property = "ontimize.openapi.generator.maven.plugin.skipIfSpecIsUnchanged", defaultValue = "false")
	private Boolean skipIfSpecIsUnchanged;

	/**
	 * Adds the Swagger UI 
	 */
	@Parameter(name = "swaggerUI", property = "ontimize.openapi.swagger-ui.enabled", defaultValue = "false")
	private Boolean swaggerUI;

	/**
	 * The the Swagger UI path
	 */
	@Parameter(name = "swaggerUIPath", property = "ontimize.openapi.swagger-ui.path")
	private String swaggerUIPath;

	/**
	 * The API URL 
	 */
	@Parameter(name = "swaggerUIAPIUrl", property = "ontimize.openapi.swagger-ui.apiUrl")
	private String swaggerUIAPIUrl;

	@Parameter(name = "verbose", defaultValue = "false")
	private boolean verbose;

	public void setBuildContext(BuildContext buildContext) {
		this.buildContext = buildContext;
	}

	@Override
	public void execute() throws MojoExecutionException {
		final File sourceDir = new File(this.mavenProject.getBuild().getSourceDirectory()).getParentFile();
		final File targetDir = new File(this.mavenProject.getBuild().getDirectory());
		final String inputSpecPath;

		if (isEmpty(this.inputSpec)) {
			inputSpecPath = Paths.get(sourceDir.getAbsolutePath(), "rest", "openapi-rest.yml").toString();
		} else {
			inputSpecPath = this.inputSpec;
		}

		final File inputSpecFile = new File(inputSpecPath);

		if (!inputSpecFile.exists()) {
			this.getLog().info("Code generation is skipped in delta-build because source-json does not exists.");
			return;
		}

		try {
			if (this.buildContext != null && this.buildContext.isIncremental()
					&& !this.buildContext.hasDelta(inputSpecFile)) {

				this.getLog()
						.info("Code generation is skipped in delta-build because source-json was not modified.");
				return;
			}

			final String inputSpecHash = this.calculateInputSpecHash(inputSpecPath);
			final File targetHashDir = Paths.get(targetDir.getAbsolutePath(), ".openapi-generator").toFile();
			final File inputSpecHashFile = this.getHashFile(inputSpecPath, targetHashDir);

			if (Boolean.TRUE.equals(skipIfSpecIsUnchanged) && inputSpecHashFile.exists()) {
				@SuppressWarnings("UnstableApiUsage")
				String storedInputSpecHash = Files.asCharSource(inputSpecHashFile, StandardCharsets.UTF_8).read();
				if (storedInputSpecHash.equals(inputSpecHash)) {
					this.getLog().info(
							"Code generation is skipped because input was unchanged");
					return;
				}
			}

			final File output = Paths.get(targetDir.getAbsolutePath(), "generated-sources").toFile();

			final CodegenConfigurator configurator = new CodegenConfigurator();

			configurator.setVerbose(verbose)
				.setInputSpec(inputSpecFile.getAbsolutePath())
				.setGeneratorName("ontimize-server")
				.setOutputDir(output.getAbsolutePath())
				.addAdditionalProperty(CodegenConstants.SOURCE_FOLDER, "java")
				.addSystemProperty(CodegenConstants.MODELS, "") // Empty means "All models"
				.addSystemProperty(CodegenConstants.APIS, ""); // Empty means "All APIs" 

			if (isNotEmpty(this.auth)) {
				configurator.setAuth(this.auth);
			}

			if (this.importMappings != null) {
				applyImportMappingsKvpList(this.importMappings, configurator);
			}

			if (isNotEmpty(this.packageName)) {
				configurator.setApiPackage(this.packageName);
			}

			if (isNotEmpty(this.modelPackageName)) {
				configurator.setModelPackage(this.modelPackageName);
			} else if (isNotEmpty(this.packageName)) {
				configurator.setModelPackage(this.packageName + ".model");
			}

			final ClientOptInput input = configurator.toClientOptInput();

			new DefaultGenerator().opts(input).generate();

			if (this.buildContext != null) {
				this.buildContext.refresh(output);
			}

			// Store a checksum of the input spec
			if (!targetHashDir.exists()) {
				targetHashDir.mkdirs();
			}

			Files.asCharSink(inputSpecHashFile, StandardCharsets.UTF_8).write(inputSpecHash);

			if (this.swaggerUI) {
				this.includeSwaggerUI();
			}
		} catch (Exception e) {
			// Maven logs exceptions thrown by plugins only if invoked with -e
			// I find it annoying to jump through hoops to get basic diagnostic information,
			// so let's log it in any case:
			if (this.buildContext != null) {
				this.buildContext.addMessage(inputSpecFile, 0, 0, "unexpected error in Open-API generation",
						BuildContext.SEVERITY_WARNING, e);
			}
			this.getLog().error(e);
			throw new MojoExecutionException("Code generation failed. See above for the full exception.");
		}
	}

	/**
	 * Calculate openapi specification file hash. If specification is hosted on remote resource it is downloaded first
	 *
	 * @param inputSpec     - Openapi specification input file to calculate it's hash.
	 *                        Does not taken into account if input spec is hosted on remote resource
	 * @return openapi specification file hash
	 * @throws IOException
	 */
	private String calculateInputSpecHash(String inputSpec) throws IOException {
		final URL inputSpecRemoteUrl = this.inputSpecRemoteUrl(inputSpec);
		final File inputSpecTempFile;

		if (inputSpecRemoteUrl == null) {
			inputSpecTempFile = new File(inputSpec);
		} else {
			inputSpecTempFile = File.createTempFile("openapi-spec", ".tmp");

			URLConnection conn = inputSpecRemoteUrl.openConnection();

			if (isNotEmpty(auth)) {
				List<AuthorizationValue> authList = AuthParser.parse(auth);
				for (AuthorizationValue a : authList) {
					conn.setRequestProperty(a.getKeyName(), a.getValue());
				}
			}

			try (ReadableByteChannel readableByteChannel = Channels.newChannel(conn.getInputStream())) {
				try (FileOutputStream fileOutputStream = new FileOutputStream(inputSpecTempFile)) {
					final FileChannel fileChannel = fileOutputStream.getChannel();
					fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
				}
			}
		}

		ByteSource inputSpecByteSource = inputSpecTempFile.exists()
			? Files.asByteSource(inputSpecTempFile)
			: CharSource.wrap(ClasspathHelper.loadFileFromClasspath(inputSpecTempFile.toString().replaceAll("\\\\","/")))
			.asByteSource(StandardCharsets.UTF_8);

		return inputSpecByteSource.hash(Hashing.sha256()).toString();
	}

	/**
	 * Get specification hash file
	 * @param inputSpec     - Openapi specification input file to calculate it's hash.
	 *                        Does not taken into account if input spec is hosted on remote resource
	 * @param targetDir     - Target directory
     * @return a file with previously calculated hash
	 */
	private File getHashFile(String inputSpec, File targetDir) {
		final File inputSpecFile = new File(inputSpec);
		final String name;
		final URL url = this.inputSpecRemoteUrl(inputSpec);

		if (url == null) {
			name = inputSpecFile.getName();
		} else {
			final String[] segments = url.getPath().split("/");
			name = Files.getNameWithoutExtension(segments[segments.length - 1]);
		}

		return Paths.get(targetDir.getAbsolutePath(), name + ".sha256").toFile();
	}

	/**
	 * Try to parse inputSpec setting string into URL
	 * @return A valid URL or null if inputSpec is not a valid URL
	 */
	private URL inputSpecRemoteUrl(String inputSpec){
		try {
			return new URI(inputSpec).toURL();
		} catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
			return null;
		}
	}

	private void includeSwaggerUI() throws URISyntaxException, IOException {
		if (this.swaggerUIPath != null) {
			ResourceUtil.extractResources("/swagger-ui", this.swaggerUIPath);

			if (this.swaggerUIAPIUrl != null) {
				String content = null;
				final File file = new File(this.swaggerUIPath, "/swagger-initializer.js");
				try (final InputStream is = new FileInputStream(file)) {
					if (is != null) {
						content = new BufferedReader(new InputStreamReader(is))
							.lines()
							.collect(Collectors.joining("\n"))
							.replace("https://petstore.swagger.io/v2/swagger.json", this.swaggerUIAPIUrl);
					}
				}

				if (content != null) {
					System.out.println(content);
					try (FileOutputStream os = new FileOutputStream(file)) {
						os.write(content.getBytes());
					}
				}
			}
		}
	}
}