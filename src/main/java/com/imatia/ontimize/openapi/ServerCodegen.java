package com.imatia.ontimize.openapi;

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

import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jknack.handlebars.internal.antlr.misc.MultiMap;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;

public class ServerCodegen extends AbstractJavaCodegen {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerCodegen.class);
	
	// source folder where to write the files
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
		this.apiPackage = "com.imatia.ontimize.api";

		/**
		 * Model Package.  Optional, if needed, this can be used in templates
		 */
		this.modelPackage = "com.imatia.ontimize.model";

		/**
		 * Reserved words.  Override this with reserved words specific to your language
		 */
		this.reservedWords = new HashSet<String> (
				Arrays.asList(
						"sample1",  // replace with static values
						"sample2")
				);

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

        if (this.searchExtension(co.vendorExtensions, "x-hasparentpath", true)) {
			String path = "/" + tag.toLowerCase() + "/"; 
			int pos = co.path.indexOf(path);
			
			if (pos >= 0) {
				co.path = co.path.substring(pos + path.length() - 1);
			}
		}
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        Object extension = this.getExtension(operation.getExtensions(), "x-restcontroller");

        if (extension != null) {
        	java.util.Map<String, Object> extensions = operation.getExtensions();

        	if (extension.equals("orestcontroller")) {
        		this.addDependency("com.ontimize.jee", "ontimize-jee-server-rest");

        		if (path.endsWith("/{name}")) {
        			operation.setOperationId("query");
        		} else if (path.endsWith("/{name}/search")) {
        			operation.setOperationId("query");
	   		        this.addExtension(extensions, "x-codegen-request-body-name", "queryParameter");
	   		        this.addExtension(extensions, "x-throws", "Exception");
        		} else if (path.endsWith("/{name}/advancedsearch")) {
        			operation.setOperationId("query");
        			this.addExtension(extensions, "x-codegen-request-body-name", "queryParameter");
	   		        this.addExtension(extensions, "x-throws", "Exception");
        		} else if (path.endsWith("/{name}/delete")) {
        			operation.setOperationId("delete");
        			this.addExtension(extensions, "x-codegen-request-body-name", "deleteParameter");
        		} else if (path.endsWith("/{name}/insert")) {
        			operation.setOperationId("insert");
        			this.addExtension(extensions, "x-codegen-request-body-name", "insertParameter");
        		} else if (path.endsWith("/{name}/update")) {
        			operation.setOperationId("update");
        			this.addExtension(extensions, "x-codegen-request-body-name", "updateParameter");
        		}
        	} else if (extension.equals("dmsrestcontroller")) {
        		this.addDependency("com.ontimize.jee.dms", "ontimize-jee-dms-rest");

        		if (path.endsWith("/queryFiles/{workspaceId}")) {
        			operation.setOperationId("documentGetFiles");
        			this.addExtension(extensions, "x-codegen-request-body-name", "queryParameter");
        		} else if (path.endsWith("/getFile/{fileId}")) {
        			operation.setOperationId("fileGetContent");
        			this.addInternalParam(operation, "response", "HttpServletResponse");
        			this.addDependency("javax.servlet", "javax.servlet-api");
        		} else if (path.endsWith("/getFiles/{workspaceId}")) {
        			operation.setOperationId("fileGetContent");
	   		        this.addExtension(extensions, "x-codegen-request-body-name", "file");
        		} else if (path.endsWith("/getZipFile/{file}")) {
        			operation.setOperationId("fileGetZip");	
        			this.addInternalParam(operation, "response", "HttpServletResponse");
        			this.addDependency("javax.servlet", "javax.servlet-api");
        		} else if (path.endsWith("/insertFile/{workspaceId}")) {
        			operation.setOperationId("fileInsert");	
        		} else if (path.endsWith("/deleteFiles/{workspaceId}")) {
        			operation.setOperationId("delete");	
	   		        this.addExtension(extensions, "x-codegen-request-body-name", "deleteParameter");
					this.addExtension(extensions, "x-throws", "com.ontimize.jee.common.exceptions.DmsException");
        		} else if (path.endsWith("/insertFolder/{workspaceId}/{name}")) {
        			operation.setOperationId("folderInsert");	
	   		        this.addExtension(extensions, "x-codegen-request-body-name", "insertParameter");
        		} else if (path.endsWith("/fileUpdate")) {
        			operation.setOperationId("fileUpdate");	
	   		        this.addExtension(extensions, "x-codegen-request-body-name", "updateParameter");
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
	   		        this.addExtension(extensions, "x-codegen-request-body-name", "exportParameter");
	   		        this.addExtension(extensions, "x-throws", "Exception");
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
	   		        this.addExtension(extensions, "x-codegen-request-body-name", "updateParameter");
       			}
			}
        }

    	return super.fromOperation(path, httpMethod, operation, servers);
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
			if (this.searchExtension(p.getExtensions(), "x-extends", true)) {
				format = "? extends %s";
			} else if (this.searchExtension(p.getExtensions(), "x-super", true)) {
				format = "? super %s";
			}

			if (ModelUtils.isArraySchema(p)) {
				Schema<?> items = this.getSchemaItems((ArraySchema) p);
				typeDeclaration = typeDeclaration + "<" + String.format(format, this.getTypeDeclaration(ModelUtils.unaliasSchema(this.openAPI, items))) + ">";
			} else if (ModelUtils.isMapSchema(p)) {
				// Note: ModelUtils.isMapSchema(p) returns true when p is a composed schema that also defines
				// additionalproperties: true
				Schema<?> inner = this.getSchemaAdditionalProperties(p);
				if (this.searchExtension(p.getExtensions(), "x-iswildcard", true)) {
					typeDeclaration = typeDeclaration + "<?, ";
				} else {
					typeDeclaration = typeDeclaration + "<String, ";
				}
				typeDeclaration = typeDeclaration + String.format(format, this.getTypeDeclaration(ModelUtils.unaliasSchema(this.openAPI, inner))) + ">";
			}
		} else if (this.searchExtension(p.getExtensions(), "x-iswildcard", true)) {
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
    	
        objs.put("dependencies", this.dependencies.values());
    	
        return objs;
    }

    @Override
	public void processOpts() {
		super.processOpts();

		this.apiTestTemplateFiles.remove("api_test.mustache");
		this.modelDocTemplateFiles.remove("model_doc.mustache");
		this.apiDocTemplateFiles.remove("api_doc.mustache");

		this.importMapping.put("Number", "java.lang.Number");
		this.importMapping.put("Void", "java.lang.Void");

		this.typeMapping.remove("file");
		this.typeMapping.put("file", "org.springframework.web.multipart.MultipartFile");
		
		this.importMapping.put("HttpServletResponse", "javax.servlet.http.HttpServletResponse");

		this.importMapping.put("AdvancedEntityResult", "com.ontimize.db.AdvancedEntityResult");
		this.importMapping.put("EntityResult", "com.ontimize.db.EntityResult");

		this.importMapping.put("AdvancedQueryParameter", "com.ontimize.jee.server.rest.AdvancedQueryParameter");
		this.importMapping.put("DeleteParameter", "com.ontimize.jee.server.rest.DeleteParameter");
		this.importMapping.put("FileListParameter", "com.ontimize.jee.server.rest.FileListParameter");
		this.importMapping.put("InsertParameter", "com.ontimize.jee.server.rest.InsertParameter");
		this.importMapping.put("QueryParameter", "com.ontimize.jee.server.rest.QueryParameter");
		this.importMapping.put("UpdateFileParameter", "com.ontimize.jee.server.rest.UpdateFileParameter");
		this.importMapping.put("UpdateParameter", "com.ontimize.jee.server.rest.UpdateParameter");

		this.importMapping.put("ExportParameter", "com.ontimize.jee.webclient.export.ExportParameter");

		this.importMapping.put("OFile", "com.ontimize.jee.server.dms.model.OFile");
		this.importMapping.put("DocumentIdentifier", "com.ontimize.jee.common.services.dms.DocumentIdentifier");

		this.importMapping.put("SQLOrder", "com.ontimize.db.SQLStatementBuilder.SQLOrder");
	}

	@Override
	public String toDefaultValue(Schema schema) {
		if (schema.getDefault() != null) {
			return super.toDefaultValue(schema);
		}

		return null;
	}

    public void addDependency(String groupId, String artifactId) {
    	Map<String, Object> dependency = new HashMap<>();
    	String key = groupId + "." + artifactId; 
    	
    	if (!this.dependencies.containsKey(key)) {
	    	dependency.put("groupId", groupId);
	    	dependency.put("artifactId", artifactId);
	    	
	    	this.dependencies.put(key, dependency);
    	}
    }

    private void addExtension(java.util.Map<String, Object> extensions, String name, Object value) {
		if (extensions != null) {
			Object extension = extensions.get(name);

			if (extension == null || !extension.equals(value)) {
				extensions.put(name, value);
			}
		}
	}

	private void addInternalParam(Operation co, String name, String type) {
		Schema schema = new Schema();
		schema.setName(name);
		schema.setType(type);
		
		Parameter parameter = new Parameter();
		Set<String> imports = new HashSet<String>();
		parameter.setName(name);
		parameter.setSchema(schema);
		
		co.getParameters().add(parameter);
    }

	private Object getExtension(java.util.Map<String, Object> extensions, String name) {
		Object value = null;

		if (extensions != null) {
			value = extensions.get(name);
		}

		return value;
	}

	private boolean searchExtension(java.util.Map<String, Object> extensions, String name, Object value) {
		boolean found = false;

		if (extensions != null) {
			Object extension = extensions.get(name);

			if (extension != null && extension.equals(value)) {
				found = true;
			}
		}

		return found;
	}
}
