package org.bbop.termgenie.services;

import static org.bbop.termgenie.tools.ErrorMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.bbop.termgenie.core.Ontology;
import org.bbop.termgenie.core.TemplateField;
import org.bbop.termgenie.core.TemplateField.Cardinality;
import org.bbop.termgenie.core.TermTemplate;
import org.bbop.termgenie.core.process.ProcessState;
import org.bbop.termgenie.core.rules.TermGenerationEngine;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationInput;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationOutput;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationParameters;
import org.bbop.termgenie.data.JsonGenerationResponse;
import org.bbop.termgenie.data.JsonOntologyTerm;
import org.bbop.termgenie.data.JsonTermGenerationInput;
import org.bbop.termgenie.data.JsonTermGenerationParameter;
import org.bbop.termgenie.data.JsonTermGenerationParameter.JsonOntologyTermIdentifier;
import org.bbop.termgenie.data.JsonTermTemplate;
import org.bbop.termgenie.data.JsonTermTemplate.JsonCardinality;
import org.bbop.termgenie.data.JsonTermTemplate.JsonTemplateField;
import org.bbop.termgenie.data.JsonValidationHint;
import org.bbop.termgenie.ontology.OntologyTaskManager;
import org.bbop.termgenie.ontology.OntologyTaskManager.OntologyTask;
import org.bbop.termgenie.tools.FieldValidatorTool;
import org.bbop.termgenie.tools.OntologyTools;
import org.bbop.termgenie.user.OrcidUserData;
import org.bbop.termgenie.user.UserDataProvider;
import org.bbop.termgenie.user.XrefUserData;

import owltools.graph.OWLGraphWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GenerateTermsServiceImpl implements GenerateTermsService {

	private static final Logger logger = Logger.getLogger(GenerateTermsServiceImpl.class);

	private final TemplateCache TEMPLATE_CACHE = TemplateCache.getInstance();
	private final OntologyTools ontologyTools;
	private final TermGenerationEngine termGeneration;
	private final JsonTemplateTools jsonTools;
	private final UserDataProvider userDataProvider;

	/**
	 * @param ontologyTools
	 * @param termGeneration
	 * @param userDataProvider
	 */
	@Inject
	GenerateTermsServiceImpl(OntologyTools ontologyTools,
			TermGenerationEngine termGeneration,
			UserDataProvider userDataProvider)
	{
		super();
		this.ontologyTools = ontologyTools;
		this.termGeneration = termGeneration;
		this.userDataProvider = userDataProvider;
		this.jsonTools = new JsonTemplateTools();
	}

	
	@Override
	public AutoCompleteEntry[] getAutoCompleteResource(String sessionId,
			String resource,
			HttpSession session)
	{
		if ("xref".equals(resource)) {
			List<XrefUserData> userData = userDataProvider.getXrefUserData();
			if (userData != null && !userData.isEmpty()) {
				List<AutoCompleteEntry> xrefStrings = new ArrayList<AutoCompleteEntry>(userData.size());
				for (XrefUserData xrefUserData : userData) {
					String name = xrefUserData.getScreenname();
					String value = xrefUserData.getXref();
					if (value != null) {
						AutoCompleteEntry entry = new AutoCompleteEntry();
						entry.setName(name);
						entry.setValue(value);
						xrefStrings.add(entry);
					}
				}
				if (!xrefStrings.isEmpty()) {
					AutoCompleteEntry[] array = xrefStrings.toArray(new AutoCompleteEntry[xrefStrings.size()]);
					return array;
				}
			}
		}
		else if ("orcid".equals(resource)) {
			List<OrcidUserData> userData = userDataProvider.getOrcIdUserData();
			if (userData != null && !userData.isEmpty()) {
				List<AutoCompleteEntry> xrefStrings = new ArrayList<AutoCompleteEntry>(userData.size());
				for (OrcidUserData orcid : userData) {
					String name = orcid.getScreenname();
					String value = orcid.getOrcid();
					if (value != null) {
						AutoCompleteEntry entry = new AutoCompleteEntry();
						entry.setName(name);
						entry.setValue(value);
						xrefStrings.add(entry);
					}
				}
				if (!xrefStrings.isEmpty()) {
					AutoCompleteEntry[] array = xrefStrings.toArray(new AutoCompleteEntry[xrefStrings.size()]);
					return array;
				}
			}
		}
		return null;
	}
	
	@Override
	public List<JsonTermTemplate> availableTermTemplates(String sessionId, String ontologyName) {
		// sanity check
		if (ontologyName == null) {
			// silently ignore this
			return Collections.emptyList();
		}
		Collection<TermTemplate> templates = getTermTemplates(ontologyName);
		if (templates.isEmpty()) {
			// short cut for empty results.
			return Collections.emptyList();
		}

		// encode the templates for JSON
		List<JsonTermTemplate> jsonTemplates = new ArrayList<JsonTermTemplate>();
		for (TermTemplate template : templates) {
			jsonTemplates.add(jsonTools.createJsonTermTemplate(template));
		}
		Collections.sort(jsonTemplates, JsonTermTempleSorter.instance);
		return jsonTemplates;
	}

	/*
	 * Do not trust any input here. Do not assume that this is well formed, as
	 * the request could be generated by a different client!
	 */
	@Override
	public JsonGenerationResponse generateTerms(String sessionId,
			String ontologyName,
			List<JsonTermGenerationInput> allParameters,
			ProcessState processState)
	{
		// sanity checks
		if (ontologyName == null || ontologyName.isEmpty()) {
			return error(NO_ONTOLOGY);
		}
		if (allParameters == null) {
			return error(NO_TERM_GENERATION_PARAMETERS);
		}

		// retrieve target ontology
		OntologyTaskManager manager = ontologyTools.getManager(ontologyName);
		if (manager == null) {
			return error(NO_ONTOLOGY);
		}

		// term generation parameter validation
		List<JsonValidationHint> allErrors = new ArrayList<JsonValidationHint>();
		for (JsonTermGenerationInput input : allParameters) {
			if (input == null) {
				return error(UNEXPECTED_NULL_VALUE);
			}
			JsonTermTemplate one = input.getTermTemplate();
			JsonTermGenerationParameter parameter = input.getTermGenerationParameter();
			if (one == null || parameter == null) {
				return error(UNEXPECTED_NULL_VALUE);
			}
			// retrieve the template from the server, do not trust the submitted
			// one.
			TermTemplate template = getTermTemplate(ontologyName, one.getName());
			if (template == null) {
				return error("Unknow template specified: " + one.getName());
			}
			JsonTermTemplate jsonTermTemplate = jsonTools.createJsonTermTemplate(template);

			List<JsonValidationHint> simpleErrors = FieldValidatorTool.validateParameters(template,
					jsonTermTemplate,
					parameter);
			if (!simpleErrors.isEmpty()) {
				allErrors.addAll(simpleErrors);
			}
		}
		// return validation errors
		if (!allErrors.isEmpty()) {
			return errors(allErrors);
		}

		try {
			// generate term candidates
			List<TermGenerationInput> generationTasks = createGenerationTasks(ontologyName,
					allParameters);
			// this the place for a future hook to make this requirement user specific
			// at the moment this is a warning.
			boolean requireLiteratureReference = false; 
			List<TermGenerationOutput> candidates = termGeneration.generateTerms(manager.getOntology(), generationTasks, requireLiteratureReference, processState);

			// validate candidates
			if (candidates == null || candidates.isEmpty()) {
				return error(NO_TERMS_GENERATED);
			}

			GenerateJsonResponse task = new GenerateJsonResponse(candidates);
			manager.runManagedTask(task);
			JsonGenerationResponse generationResponse = task.generationResponse;

			// return response
			return generationResponse;
		} catch (Exception exception) {
			logger.warn("An error occured during the term generation for the parameters: {ontologyName: " + ontologyName + ", allParameters: " + allParameters + "}",
					exception);
			return error("An internal error occured on the server. Please contact the developers if the problem persists.");
		}
	}

	protected JsonGenerationResponse errors(List<JsonValidationHint> allErrors) {
		return new JsonGenerationResponse(null, allErrors, null, null, null);
	}
	
	private JsonGenerationResponse error(String msg) {
		return new JsonGenerationResponse(msg, null, null, null, null);
	}
	
	private class GenerateJsonResponse extends OntologyTask {

		private final List<TermGenerationOutput> candidates;
		private JsonGenerationResponse generationResponse;
		
		/**
		 * @param candidates
		 */
		GenerateJsonResponse(List<TermGenerationOutput> candidates) {
			super();
			this.candidates = candidates;
		}

		@Override
		protected void runCatching(OWLGraphWrapper managed) throws TaskException, Exception {
			List<JsonOntologyTerm> jsonCandidates = new ArrayList<JsonOntologyTerm>();
			List<JsonValidationHint> jsonErrors = new ArrayList<JsonValidationHint>();
			List<JsonValidationHint> jsonWarnings = new ArrayList<JsonValidationHint>();
			List<JsonTermTemplate> jsonTermTemplates = new ArrayList<JsonTermTemplate>();
			
			for (TermGenerationOutput candidate : candidates) {
				final String error = candidate.getError();
				final TermTemplate termTemplate = candidate.getInput().getTermTemplate();
				final JsonTermTemplate template = jsonTools.createJsonTermTemplate(termTemplate);
				if (error == null) {
					// no error -> success
					JsonOntologyTerm jsonCandidate = JsonOntologyTerm.createJson(candidate.getTerm(), candidate.getOwlAxioms(), candidate.getChangedTermRelations(), managed, termTemplate.getName());
					jsonCandidates.add(jsonCandidate);
					jsonTermTemplates.add(template);
					
					// check warnings
					List<String> warnings = candidate.getWarnings();
					if (warnings != null && warnings.isEmpty() == false) {
						for (String warning : warnings) {
							jsonWarnings.add(new JsonValidationHint(template, -1, warning));
						}
					}
				}
				else {
					jsonErrors.add(new JsonValidationHint(template, -1, error));
				}
			}
			generationResponse = new JsonGenerationResponse(null, jsonErrors, jsonWarnings, jsonCandidates, jsonTermTemplates);
		}
		
	}

	private List<TermGenerationInput> createGenerationTasks(String ontologyName,
			List<JsonTermGenerationInput> allParameters)
	{
		List<TermGenerationInput> result = new ArrayList<TermGenerationInput>();
		for (JsonTermGenerationInput jsonInput : allParameters) {
			JsonTermTemplate jsonTemplate = jsonInput.getTermTemplate();
			TermTemplate template = getTermTemplate(ontologyName, jsonTemplate.getName());
			TermGenerationParameters parameters = jsonTools.createTermGenerationParameters(jsonInput.getTermGenerationParameter());
			TermGenerationInput input = new TermGenerationInput(template, parameters);
			result.add(input);
		}
		return result;
	}

	private Collection<TermTemplate> getTermTemplates(String ontology) {
		Collection<TermTemplate> templates;
		synchronized (TEMPLATE_CACHE) {
			templates = TEMPLATE_CACHE.getTemplates(ontology);
			if (templates == null) {
				templates = requestTemplates(ontology);
				TEMPLATE_CACHE.put(ontology, templates);
			}
		}
		return templates;
	}

	private TermTemplate getTermTemplate(String ontology, String name) {
		TermTemplate template;
		synchronized (TEMPLATE_CACHE) {
			template = TEMPLATE_CACHE.getTemplate(ontology, name);
			if (template == null) {
				Collection<TermTemplate> templates = TEMPLATE_CACHE.getTemplates(ontology);
				if (templates == null) {
					templates = requestTemplates(ontology);
					TEMPLATE_CACHE.put(ontology, templates);
				}
				template = TEMPLATE_CACHE.getTemplate(ontology, name);
			}
		}
		return template;
	}

	/**
	 * Request the templates for a given ontology.
	 * 
	 * @param ontology
	 * @return templates, never null
	 */
	protected Collection<TermTemplate> requestTemplates(String ontology) {
		List<TermTemplate> templates = ontologyTools.getTermTemplates(ontology);
		if (templates == null) {
			templates = Collections.emptyList();
		}
		return templates;
	}

	private static final class JsonTermTempleSorter implements Comparator<JsonTermTemplate> {

		private static final JsonTermTempleSorter instance = new JsonTermTempleSorter();
		
		@Override
		public int compare(JsonTermTemplate o1, JsonTermTemplate o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}

	/**
	 * Tools for converting term generation details into the JSON enabled
	 * (transfer) objects.
	 */
	class JsonTemplateTools {

		/**
		 * Convert a single template into a JSON specific data structure.
		 * 
		 * @param template
		 * @return internal format
		 */
		JsonTermTemplate createJsonTermTemplate(TermTemplate template) {
			JsonTermTemplate jsonTermTemplate = new JsonTermTemplate();
			jsonTermTemplate.setName(template.getName());
			jsonTermTemplate.setDisplay(template.getDisplayName());
			jsonTermTemplate.setDescription(template.getDescription());
			jsonTermTemplate.setHint(template.getHint());
			jsonTermTemplate.setCategories(template.getCategories());
			List<TemplateField> fields = template.getFields();
			int size = fields.size();
			List<JsonTemplateField> jsonFields = new ArrayList<JsonTemplateField>(size);
			for (TemplateField field : fields) {
				jsonFields.add(createJsonTemplateField(field));
			}
			jsonTermTemplate.setFields(jsonFields);
			return jsonTermTemplate;
		}

		private JsonTemplateField createJsonTemplateField(TemplateField field) {
			JsonTemplateField jsonField = new JsonTemplateField();
			jsonField.setName(field.getName());
			jsonField.setLabel(field.getLabel());
			jsonField.setRequired(field.isRequired());
			jsonField.setRemoteResource(field.getRemoteResource());
			Cardinality c = field.getCardinality();
			jsonField.setCardinality(new JsonCardinality(c.getMinimum(), c.getMaximum()));
			jsonField.setFunctionalPrefixes(field.getFunctionalPrefixes().toArray(new String[0]));
			final List<String> ids = field.getFunctionalPrefixesIds();
			if (ids != null && !ids.isEmpty()) {
				jsonField.setFunctionalPrefixesIds(ids.toArray(new String[0]));
			}
			jsonField.setPreSelected(field.isPreSelected());
			if (field.hasCorrespondingOntologies()) {
				List<Ontology> ontologies = field.getCorrespondingOntologies();
				String[] ontologyNames = new String[ontologies.size()];
				for (int i = 0; i < ontologyNames.length; i++) {
					Ontology ontology = ontologies.get(i);
					ontologyNames[i] = ontologyTools.getOntologyName(ontology);
				}
				jsonField.setOntologies(ontologyNames);
			}
			return jsonField;
		}

		TermGenerationParameters createTermGenerationParameters(JsonTermGenerationParameter json)
		{
			TermGenerationParameters result = new TermGenerationParameters();
			Map<String, List<JsonOntologyTermIdentifier>> terms = json.getTerms();
			for (String field : terms.keySet()) {
				result.setTermValues(field, getTerms(terms.get(field)));
			}
			Map<String, List<String>> strings = json.getStrings();
			if (strings != null && !strings.isEmpty()) {
				for (String field : strings.keySet()) {
					result.setStringValues(field, strings.get(field));
				}
			}
			return result;
		}

		private List<String> getTerms(List<JsonOntologyTermIdentifier> jsonTerms)
		{
			List<String> terms = new ArrayList<String>();
			for (JsonOntologyTermIdentifier jsonTerm : jsonTerms) {
				terms.add(jsonTerm.getTermId());
			}
			return terms;
		}

	}

	static class TemplateCache {

		private static volatile TemplateCache instance = null;
		private final Map<String, Map<String, TermTemplate>> templates;

		private TemplateCache() {
			templates = new HashMap<String, Map<String, TermTemplate>>();
		}

		public synchronized static TemplateCache getInstance() {
			if (instance == null) {
				instance = new TemplateCache();
			}
			return instance;
		}

		void put(String ontology, Collection<TermTemplate> templates) {
			Map<String, TermTemplate> namedValues = new HashMap<String, TermTemplate>();
			for (TermTemplate template : templates) {
				namedValues.put(template.getName(), template);
			}
			if (namedValues.isEmpty()) {
				namedValues = Collections.emptyMap();
			}
			this.templates.put(ontology, namedValues);
		}

		boolean hasOntology(String ontology) {
			return templates.containsKey(ontology);
		}

		Collection<TermTemplate> getTemplates(String ontology) {
			Map<String, TermTemplate> namedValues = templates.get(ontology);
			if (namedValues == null) {
				return null;
			}
			return namedValues.values();
		}

		TermTemplate getTemplate(String ontology, String templateName) {
			Map<String, TermTemplate> namedValues = templates.get(ontology);
			if (namedValues == null) {
				return null;
			}
			return namedValues.get(templateName);
		}
	}
}
