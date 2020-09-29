package com.imatia.ontimize.openapi;

import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.models.properties.*;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.*;
import java.io.File;

public class ServerCodegen extends AbstractJavaCodegen {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServerCodegen.class);
	
	// source folder where to write the files
	protected String apiVersion = "1.0.0";

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
	 * Provides an opportunity to inspect and modify operation data before the code is generated.
	 */
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

	@Override
	public void processOpts() {
		super.processOpts();

		apiTestTemplateFiles.remove("api_test.mustache");
		modelDocTemplateFiles.remove("model_doc.mustache");
		apiDocTemplateFiles.remove("api_doc.mustache");

		importMapping.put("Number", "java.lang.Number");
		importMapping.put("Void", "java.lang.Void");

		typeMapping.remove("file");
		typeMapping.put("file", "org.springframework.web.multipart.MultipartFile");

		importMapping.put("AdvancedEntityResult", "com.ontimize.db.AdvancedEntityResult");
		importMapping.put("EntityResult", "com.ontimize.db.EntityResult");

		importMapping.put("AdvancedQueryParameter", "com.ontimize.jee.server.rest.AdvancedQueryParameter");
		importMapping.put("DeleteParameter", "com.ontimize.jee.server.rest.DeleteParameter");
		importMapping.put("FileListParameter", "com.ontimize.jee.server.rest.FileListParameter");
		importMapping.put("InsertParameter", "com.ontimize.jee.server.rest.InsertParameter");
		importMapping.put("QueryParameter", "com.ontimize.jee.server.rest.QueryParameter");
		importMapping.put("UpdateFileParameter", "com.ontimize.jee.server.rest.UpdateFileParameter");
		importMapping.put("UpdateParameter", "com.ontimize.jee.server.rest.UpdateParameter");

		importMapping.put("OFile", "com.ontimize.jee.server.dms.model.OFile");
		importMapping.put("DocumentIdentifier", "com.ontimize.jee.common.services.dms.DocumentIdentifier");

		importMapping.put("SQLOrder", "com.ontimize.db.SQLStatementBuilder.SQLOrder");
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

		fullJavaUtil = true;

		// set the output folder here
		outputFolder = "generated-code/ontimize-server";

		/**
		 * Models.  You can write model files using the modelTemplateFiles map.
		 * if you want to create one template for file, you can do so here.
		 * for multiple files for model, just put another entry in the `modelTemplateFiles` with
		 * a different extension
		 */
		modelTemplateFiles.put(
				"model.mustache", // the template to use
				".java");       // the extension for each file to write

		/**
		 * Api classes.  You can write classes for each Api file with the apiTemplateFiles map.
		 * as with models, add multiple entries with different extensions for multiple files per
		 * class
		 */
		apiTemplateFiles.put(
				"api.mustache",   // the template to use
				".java");       // the extension for each file to write

		apiNamePrefix = "I";

		/**
		 * Template Location.  This is the location which templates will be read from.  The generator
		 * will use the resource stream to attempt to read the templates.
		 */
		templateDir = "ontimize-server";
		embeddedTemplateDir = templateDir;

		/**
		 * Api Package.  Optional, if needed, this can be used in templates
		 */
		apiPackage = "com.imatia.ontimize.api";

		/**
		 * Model Package.  Optional, if needed, this can be used in templates
		 */
		modelPackage = "com.imatia.ontimize.model";

		/**
		 * Reserved words.  Override this with reserved words specific to your language
		 */
		reservedWords = new HashSet<String> (
				Arrays.asList(
						"sample1",  // replace with static values
						"sample2")
				);

		/**
		 * Additional Properties.  These values can be passed to the templates and
		 * are available in models, apis, and supporting files
		 */
		additionalProperties.put("apiVersion", apiVersion);

		/**
		 * Supporting Files.  You can write single files for the generator with the
		 * entire object tree available.  If the input file has a suffix of `.mustache
		 * it will be processed by the template engine.  Otherwise, it will be copied
		 */

		supportingFiles.add(new SupportingFile("pom.mustache",  // the input template or file
				"",  // the destination folder, relative `outputFolder`
				"pom.xml")  // the output file
				);
	}

	@Override
	public String toDefaultValue(Schema schema) {
		if (schema.getDefault() != null) {
			return super.toDefaultValue(schema);
		}

		return null;
	}

	@Override
	public String getTypeDeclaration(Schema p) {
		if (p == null) {
			LOGGER.warn("Null schema found. Default type to `NULL_SCHEMA_ERR`");
			return "NULL_SCHEMA_ERR";
		}

		String typeDeclaration = getSchemaType(p);

		if (ModelUtils.isArraySchema(p) || (ModelUtils.isMapSchema(p) && !ModelUtils.isComposedSchema(p))) {
			String format = "%s";
			if (searchExtension(p, "x-extends", true)) {
				format = "? extends %s";
			} else if (searchExtension(p, "x-super", true)) {
				format = "? super %s";
			}

			if (ModelUtils.isArraySchema(p)) {
				Schema<?> items = getSchemaItems((ArraySchema) p);
				return typeDeclaration + "<" + String.format(format, getTypeDeclaration(ModelUtils.unaliasSchema(this.openAPI, items))) + ">";
			} else if (ModelUtils.isMapSchema(p)) {
				// Note: ModelUtils.isMapSchema(p) returns true when p is a composed schema that also defines
				// additionalproperties: true
				Schema<?> inner = getSchemaAdditionalProperties(p);
				return typeDeclaration + "<String, " + String.format(format, getTypeDeclaration(ModelUtils.unaliasSchema(this.openAPI, inner))) + ">";
			}
		}

		return typeDeclaration;
	}

	private boolean searchExtension(Schema p, String name, Object value) {
		boolean found = false;

		if (p.getExtensions() != null) {
			Object extension = p.getExtensions().get(name);

			if (extension != null && extension.equals(value)) {
				found = true;
			}
		}

		return found;
	}
}