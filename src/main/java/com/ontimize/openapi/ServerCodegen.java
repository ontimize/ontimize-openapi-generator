package com.ontimize.openapi;

import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.escape;
import static org.openapitools.codegen.utils.StringUtils.underscore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jknack.handlebars.internal.antlr.misc.MultiMap;
import com.github.jknack.handlebars.internal.lang3.ArrayUtils;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.util.SchemaTypeUtil;

public class ServerCodegen extends AbstractJavaCodegen {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerCodegen.class);
	private static final String DEPENDENCIES = "dependencies";

	private static final String X_BODY_NAME = "x-codegen-request-body-name";
	private static final String X_EXTENDS = "x-extends";
	private static final String X_HAS_PARENT_PATH = "x-hasparentpath";
	private static final String X_IS_WILDCARD = "x-iswildcard";
	private static final String X_IGNORE = "x-ignore";
	private static final String X_REST_CONTROLLER = "x-restcontroller";
	private static final String X_SUPER = "x-super";
	private static final String X_THROWS = "x-throws";

	protected String apiVersion = "1.0.0";
	protected Map<String, Object> dependencies = new HashMap<>();

	/**
	 * Configures the type of generator.
	 * 
	 * @return  the CodegenType for this generator
	 * @see     org.openapitools.codegen.CodegenType
	 */
	public CodegenType getTag() {
		return CodegenType.OTHER;
	}

	/**
	 * Configures a friendly name for the generator.  This will be used by the generator
	 * to select the library with the -g flag.
	 * 
	 * @return the friendly name for the generator
	 */
	public String getName() {
		return "ontimize-server";
	}

	/**
	 * Returns human-friendly help for the generator.  Provide the consumer with help
	 * tips, parameters here
	 * 
	 * @return A string value for the help message
	 */
	public String getHelp() {
		return "Generates a Ontimize server library.";
	}

	public ServerCodegen() {
		super();

		this.fullJavaUtil = true;

		// set the output folder here
		this.outputFolder = "generated-code/ontimize-server";

		/**
		 * Models.  You can write model files using the modelTemplateFiles map.
		 * if you want to create one template for file, you can do so here.
		 * for multiple files for model, just put another entry in the `modelTemplateFiles` with
		 * a different extension
		 */
		this.modelTemplateFiles.put(
				"model.mustache", // the template to use
				".java");       // the extension for each file to write

		/**
		 * Api classes.  You can write classes for each Api file with the apiTemplateFiles map.
		 * as with models, add multiple entries with different extensions for multiple files per
		 * class
		 */
		this.apiTemplateFiles.put(
				"api.mustache",   // the template to use
				".java");       // the extension for each file to write

		this.apiNamePrefix = "I";

		/**
		 * Template Location.  This is the location which templates will be read from.  The generator
		 * will use the resource stream to attempt to read the templates.
		 */
		this.templateDir = "ontimize-server";
		this.embeddedTemplateDir = this.templateDir;

		/**
		 * Api Package.  Optional, if needed, this can be used in templates
		 */
		this.apiPackage = "com.ontimize.api";

		/**
		 * Model Package.  Optional, if needed, this can be used in templates
		 */
		this.modelPackage = "com.ontimize.model";

		/**
		 * Additional Properties.  These values can be passed to the templates and
		 * are available in models, apis, and supporting files
		 */
		this.additionalProperties.put("apiVersion", this.apiVersion);

		/**
		 * Supporting Files.  You can write single files for the generator with the
		 * entire object tree available.  If the input file has a suffix of `.mustache
		 * it will be processed by the template engine.  Otherwise, it will be copied
		 */

		this.supportingFiles.add(new SupportingFile("pom.mustache",  // the input template or file
				"",  // the destination folder, relative `outputFolder`
				"pom.xml")  // the output file
				);

		this.cliOptions.add(new CliOption(DEPENDENCIES, "Additional dependencies for pom.xml"));
	}

	@Override
	public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation
			co, Map<String, List<CodegenOperation>> operations) {
		List<CodegenOperation> opList = operations.get(tag);
		if (opList == null) {
			opList = new ArrayList<CodegenOperation>();
			operations.put(tag, opList);
		}
		co.operationIdLowerCase = co.operationId.toLowerCase(Locale.ROOT);
		co.operationIdCamelCase = camelize(co.operationId);
		co.operationIdSnakeCase = underscore(co.operationId);

		opList.add(co);
		co.baseName = tag;

		if (this.searchItemValue(co.vendorExtensions, X_HAS_PARENT_PATH, true)) {
			int pos = StringUtils.ordinalIndexOf(co.path, "/", 2);

			if (pos >= 0) {
				co.path = co.path.substring(pos);
			}
		}
	}

	@Override
	public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
		Object extension = this.getItemValue(operation.getExtensions(), X_REST_CONTROLLER);

		if (extension != null) {
			java.util.Map<String, Object> extensions = operation.getExtensions();

			if (extension.equals("orestcontroller")) {
				this.addDependency("com.ontimize.jee", "ontimize-jee-server-rest");
				this.addInternalParam(operation, "name", "string", "path");

				if ("GET".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("query");
				} else if (path.endsWith("/search")) {
					operation.setOperationId("query");
					this.addItem(extensions, X_BODY_NAME, "queryParameter");
					this.addItem(extensions, X_THROWS, "Exception");
				} else if (path.endsWith("/advancedsearch")) {
					operation.setOperationId("query");
					this.addItem(extensions, X_BODY_NAME, "queryParameter");
					this.addItem(extensions, X_THROWS, "Exception");
				} else if ("DELETE".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("delete");
					this.addItem(extensions, X_BODY_NAME, "deleteParameter");
				} else if ("POST".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("insert");
					this.addItem(extensions, X_BODY_NAME, "insertParameter");
				} else if ("PUT".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("update");
					this.addItem(extensions, X_BODY_NAME, "updateParameter");
				}
			} else if (extension.equals("dmsrestcontroller")) {
				this.addDependency("com.ontimize.jee.dms", "ontimize-jee-dms-rest");

				if (path.endsWith("/queryFiles/{workspaceId}")) {
					operation.setOperationId("documentGetFiles");
					this.addItem(extensions, X_BODY_NAME, "queryParameter");
				} else if (path.endsWith("/getFile/{fileId}")) {
					operation.setOperationId("fileGetContent");
					this.addInternalParam(operation, "response", "HttpServletResponse");
					this.addDependency("javax.servlet", "javax.servlet-api");
				} else if (path.endsWith("/getFiles/{workspaceId}")) {
					operation.setOperationId("fileGetContent");
					this.addItem(extensions, X_BODY_NAME, "file");
				} else if (path.endsWith("/getZipFile/{file}")) {
					operation.setOperationId("fileGetZip");
					this.addInternalParam(operation, "response", "HttpServletResponse");
					this.addDependency("javax.servlet", "javax.servlet-api");
				} else if (path.endsWith("/insertFile/{workspaceId}")) {
					operation.setOperationId("fileInsert");
				} else if (path.endsWith("/deleteFiles/{workspaceId}")) {
					operation.setOperationId("delete");
					this.addItem(extensions, X_BODY_NAME, "deleteParameter");
					this.addItem(extensions, X_THROWS, "com.ontimize.jee.common.exceptions.DmsException");
				} else if (path.endsWith("/insertFolder/{workspaceId}/{name}")) {
					operation.setOperationId("folderInsert");
					this.addItem(extensions, X_BODY_NAME, "insertParameter");
				} else if (path.endsWith("/fileUpdate")) {
					operation.setOperationId("fileUpdate");
					this.addItem(extensions, X_BODY_NAME, "updateParameter");
				} else if (path.endsWith("/createDocument/{name}")) {
					operation.setOperationId("createDocument");
				}
			} else if (extension.equals("oexportrestcontroller")) {
				this.addDependency("com.ontimize.jee", "ontimize-jee-server-rest");

				if (path.endsWith("/{extension}/{id}")) {
					operation.setOperationId("downloadFile");
					this.addInternalParam(operation, "response", "HttpServletResponse");
					this.addDependency("javax.servlet", "javax.servlet-api");
				} else if (path.endsWith("/{name}/{extension}")) {
					operation.setOperationId("export");
					this.addItem(extensions, X_BODY_NAME, "exportParameter");
					this.addItem(extensions, X_THROWS, "Exception");
				}
			} else if (extension.equals("remoteconfigurationrestcontroller")) {
				this.addDependency("com.ontimize.jee", "ontimize-jee-webclient-addons");

				if (path.endsWith("/config") && "POST".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("createUserConfiguration");
				} else if (path.endsWith("/config") && "DELETE".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("delete");
				} else if (path.endsWith("/config/search") && "POST".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("getUserConfiguration");
				} else if (path.endsWith("/config") && "PUT".equalsIgnoreCase(httpMethod)) {
					operation.setOperationId("updateUserConfiguration");
					this.addItem(extensions, X_BODY_NAME, "updateParameter");
				}
			}
		}

		final List<Parameter> parameters = operation.getParameters();
		if (parameters != null) {
			operation.setParameters(parameters.stream().filter((final Parameter parameter) ->
					!this.searchItemValue(parameter.getExtensions(), X_IGNORE, true)).collect(Collectors.toList()));
		}

		return super.fromOperation(path, httpMethod, operation, servers);
	}

	@Override
	public String getSchemaType(Schema p) {
		String openAPIType = super.getSchemaType(p);

		// don't apply renaming on types from the typeMapping
		if (this.modelPackage != null && !this.modelPackage.isEmpty()
				&& !this.modelPackage.equals(this.apiPackage)
				&& !this.typeMapping.containsValue(openAPIType)
				&& !this.importMapping.containsValue(openAPIType)
				&& !this.schemaMapping.containsValue(openAPIType)) {

			openAPIType = this.modelPackage + "." + openAPIType;
		}

		return openAPIType;
	}

	@Override
	public String getTypeDeclaration(String name) {
		String typeDeclaration = super.getTypeDeclaration(name);

		// don't apply renaming on types from the typeMapping
		if (this.modelPackage != null && !this.modelPackage.isEmpty()
				&& !this.modelPackage.equals(this.apiPackage)
				&& !this.typeMapping.containsValue(typeDeclaration)
				&& !this.importMapping.containsValue(typeDeclaration)
				&& !this.schemaMapping.containsValue(typeDeclaration)) {

			typeDeclaration = this.modelPackage + "." + typeDeclaration;
		}

		return typeDeclaration;
	}

	@Override
	public String getTypeDeclaration(Schema p) {
		if (p == null) {
			LOGGER.warn("Null schema found. Default type to `NULL_SCHEMA_ERR`");
			return "NULL_SCHEMA_ERR";
		}

		String typeDeclaration = this.getSchemaType(p);

		if (ModelUtils.isArraySchema(p) || (ModelUtils.isMapSchema(p) && !ModelUtils.isComposedSchema(p))) {
			String format = "%s";
			if (this.searchItemValue(p.getExtensions(), X_EXTENDS, true)) {
				format = "? extends %s";
			} else if (this.searchItemValue(p.getExtensions(), X_SUPER, true)) {
				format = "? super %s";
			}

			if (ModelUtils.isArraySchema(p)) {
				Schema<?> items = this.getSchemaItems((ArraySchema) p);
				typeDeclaration = typeDeclaration + "<" + String.format(format, this.getTypeDeclaration(ModelUtils.unaliasSchema(this.openAPI, items))) + ">";
			} else if (ModelUtils.isMapSchema(p)) {
				// Note: ModelUtils.isMapSchema(p) returns true when p is a composed schema that also defines
				// additionalproperties: true
				Schema<?> inner = this.getSchemaAdditionalProperties(p);
				if (this.searchItemValue(p.getExtensions(), X_IS_WILDCARD, true)) {
					typeDeclaration = typeDeclaration + "<?, ";
				} else {
					typeDeclaration = typeDeclaration + "<String, ";
				}
				typeDeclaration = typeDeclaration + String.format(format, this.getTypeDeclaration(ModelUtils.unaliasSchema(this.openAPI, inner))) + ">";
			}
		} else if (this.searchItemValue(p.getExtensions(), X_IS_WILDCARD, true)) {
			typeDeclaration = "?";
		}

		return typeDeclaration;
	}

	/**
	 * Provides an opportunity to inspect and modify operation data before the code is generated.
	 */
	/*
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {

		// to try debugging your code generator:
		// set a break point on the next line.
		// then debug the JUnit test called LaunchGeneratorInDebugger

		Map<String, Object> results = super.postProcessOperationsWithModels(objs, allModels);

		Map<String, Object> ops = (Map<String, Object>)results.get("operations");
		ArrayList<CodegenOperation> opList = (ArrayList<CodegenOperation>)ops.get("operation");

		// iterate over the operation and perhaps modify something
		for(CodegenOperation co : opList){
			// example:
			// co.httpMethod = co.httpMethod.toLowerCase();
		}

		return results;
	}
	*/

	@Override
	public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
		super.postProcessSupportingFileData(objs);

		Object value = this.getItemValue(this.additionalProperties, DEPENDENCIES);

		if (value != null) {
			String[] items = value.toString().split(",");

			for (String item: items) {
				String[] words = item.split("[.]");

				if (words.length > 1) {
					String groupId = String.join(".", ArrayUtils.remove(words, words.length - 1));
					String artifactId = words[words.length - 1];

					this.addDependency(groupId, artifactId);
				}
			}
		}

		objs.put(DEPENDENCIES, this.dependencies.values());

		return objs;
	}

	@Override
	public void processOpts() {
		super.processOpts();

		this.apiTestTemplateFiles.remove("api_test.mustache");
		this.modelDocTemplateFiles.remove("model_doc.mustache");
		this.apiDocTemplateFiles.remove("api_doc.mustache");

		this.typeMapping.remove("file");
		this.typeMapping.put("file", "org.springframework.web.multipart.MultipartFile");

		this.importMapping.put("HttpServletResponse", "javax.servlet.http.HttpServletResponse");

		this.importMapping.put("Number", "java.lang.Number");
		this.importMapping.put("Object", "java.lang.Object");
		this.importMapping.put("String", "java.lang.String");
		this.importMapping.put("Void", "java.lang.Void");

		/*
		this.schemaMapping.put("AdvancedEntityResult", "com.ontimize.db.AdvancedEntityResult");
		this.schemaMapping.put("EntityResult", "com.ontimize.db.EntityResult");
		*/
		this.schemaMapping.put("AdvancedEntityResult", "com.ontimize.jee.common.db.AdvancedEntityResult");
		this.schemaMapping.put("EntityResult", "com.ontimize.jee.common.dto.EntityResult");

		this.schemaMapping.put("AdvancedQueryParameter", "com.ontimize.jee.server.rest.AdvancedQueryParameter");
		this.schemaMapping.put("DeleteParameter", "com.ontimize.jee.server.rest.DeleteParameter");
		this.schemaMapping.put("FileListParameter", "com.ontimize.jee.server.rest.FileListParameter");
		this.schemaMapping.put("InsertParameter", "com.ontimize.jee.server.rest.InsertParameter");
		this.schemaMapping.put("QueryParameter", "com.ontimize.jee.server.rest.QueryParameter");
		this.schemaMapping.put("UpdateFileParameter", "com.ontimize.jee.server.rest.UpdateFileParameter");
		this.schemaMapping.put("UpdateParameter", "com.ontimize.jee.server.rest.UpdateParameter");

		this.schemaMapping.put("ExportParameter", "com.ontimize.jee.webclient.export.ExportParameter");

		this.schemaMapping.put("OFile", "com.ontimize.jee.server.dms.model.OFile");
		this.schemaMapping.put("DocumentIdentifier", "com.ontimize.jee.common.services.dms.DocumentIdentifier");

		this.schemaMapping.put("SQLOrder", "com.ontimize.db.SQLStatementBuilder.SQLOrder");
	}

	/*
	@Override
	public String toDefaultValue(Schema schema) {
		if (schema.getDefault() != null) {
			return super.toDefaultValue(schema);
		}

		return null;
	}
	 */

	public void addDependency(String groupId, String artifactId) {
		Map<String, Object> dependency = new HashMap<>();
		String key = groupId + "." + artifactId;

		if (!this.dependencies.containsKey(key)) {
			dependency.put(CodegenConstants.GROUP_ID, groupId);
			dependency.put(CodegenConstants.ARTIFACT_ID, artifactId);

			this.dependencies.put(key, dependency);
		}
	}

	private void addInternalParam(Operation co, String name, String type) {
		this.addInternalParam(co,name, type, "query");
	}

	private void addInternalParam(Operation co, String name, String type, String in) {
		Schema schema = new Schema();
		schema.setName(name);
		schema.setType(type);

		Parameter parameter = new Parameter();
		parameter.setName(name);
		parameter.setSchema(schema);
		parameter.setIn(in);

		co.addParametersItem(parameter);
	}

	private void addItem(java.util.Map<String, Object> map, String key, Object value) {
		if (map != null) {
			Object item = map.get(key);

			if (item == null || !item.equals(value)) {
				map.put(key, value);
			}
		}
	}

	private Object getItemValue(java.util.Map<String, Object> map, String key) {
		Object value = null;

		if (map != null) {
			value = map.get(key);
		}

		return value;
	}

	private boolean searchItemValue(java.util.Map<String, Object> map, String key, Object value) {
		boolean found = false;

		if (map != null) {
			Object item = map.get(key);

			if (item != null && item.equals(value)) {
				found = true;
			}
		}

		return found;
	}
}
