package org.bbop.termgenie.solr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bbop.termgenie.core.Ontology;
import org.bbop.termgenie.core.Ontology.OntologyTerm;
import org.bbop.termgenie.core.OntologyTermSuggestor;
import org.bbop.termgenie.ontology.OntologyTaskManager;

public class LuceneOnlyClient implements OntologyTermSuggestor {
	
	private final Map<String, BasicLuceneClient> luceneIndices;

	public LuceneOnlyClient(Collection<? extends Ontology> ontologies, Collection<OntologyTaskManager> managers) {
		super();
		luceneIndices = createIndices(ontologies, managers);
	}
	
	private static Map<String, BasicLuceneClient> createIndices(Collection<? extends Ontology> ontologies, Collection<OntologyTaskManager> managers) {
		
		Map<String, List<Ontology>> groups = new HashMap<String, List<Ontology>>();
		Map<String, OntologyTaskManager> nameManagers = new HashMap<String, OntologyTaskManager>();
		for (OntologyTaskManager manager : managers) {
			nameManagers.put(manager.getOntology().getUniqueName(), manager);
		}
		for(Ontology ontology : ontologies) {
			String name = ontology.getUniqueName();
			List<Ontology> group = groups.get(name);
			if (group == null) {
				group = new ArrayList<Ontology>();
				groups.put(name, group);
			}
			group.add(ontology);
		}

		Map<String, BasicLuceneClient> indices = new HashMap<String, BasicLuceneClient>();
		for (String name : groups.keySet()) {
			OntologyTaskManager manager  = nameManagers.get(name);
			if (manager == null) {
				throw new RuntimeException("No OntologyTaskManager found for name: "+name);
			}
			indices.put(name, BasicLuceneClient.create(groups.get(name), manager));
		}
		return indices;
	}
	
	@Override
	public List<OntologyTerm> suggestTerms(String query, Ontology ontology, int maxCount) {
		BasicLuceneClient index = luceneIndices.get(ontology.getUniqueName());
		if (index != null) {
			return index.suggestTerms(query, ontology, maxCount);
		}
		return null;
	}
}
