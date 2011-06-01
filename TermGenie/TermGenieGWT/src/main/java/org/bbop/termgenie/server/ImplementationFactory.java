package org.bbop.termgenie.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bbop.termgenie.core.OntologyTermSuggestor;
import org.bbop.termgenie.core.rules.TermGenerationEngine;
import org.bbop.termgenie.ontology.DefaultOntologyConfiguration;
import org.bbop.termgenie.ontology.DefaultOntologyConfiguration.ConfiguredOntology;
import org.bbop.termgenie.rules.HardCodedTermGenerationEngine;
import org.bbop.termgenie.server.ValidateUserCredentialServiceImpl.UserCredentialValidator;
import org.bbop.termgenie.solr.LuceneOnlyClient;

public class ImplementationFactory {

	private final static ImplementationFactory instance = new ImplementationFactory();
	
	private final TermGenerationEngine engine;
	private final OntologyTools ontologyTools;
	private final OntologyTermSuggestor suggestor;
	
	private ImplementationFactory() {
		List<ConfiguredOntology> ontologies = new ArrayList<ConfiguredOntology>();
		Map<String, ConfiguredOntology> ontologyMap = DefaultOntologyConfiguration.getOntologies();
		for(String name : ontologyMap.keySet()) {
			ConfiguredOntology ontology = ontologyMap.get(name);
			if (ontology != null) {
				ontologies.add(ontology);
			}
		}
		engine = new HardCodedTermGenerationEngine(ontologies);
		ontologyTools = new OntologyTools(engine);
		suggestor = new LuceneOnlyClient(ontologies);
	}
	
	public static TermGenerationEngine getTermGenerationEngine() {
		return instance.engine;
	}
	
	public static OntologyCommitTool getOntologyCommitTool() {
		return OntologyCommitTool.getInstance();
	}
	
	public static OntologyTools getOntologyTools() {
		return instance.ontologyTools;
	}
	
	public static UserCredentialValidator getUserCredentialValidator() {
		return UserCredentialValidator.getInstance();
	}
	
	public static OntologyTermSuggestor getOntologyTermSuggestor() {
		return instance.suggestor;
	}
}
