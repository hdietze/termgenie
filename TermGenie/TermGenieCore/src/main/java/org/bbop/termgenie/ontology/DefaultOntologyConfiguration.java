package org.bbop.termgenie.ontology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.bbop.termgenie.core.OntologyAware.Ontology;
import org.bbop.termgenie.tools.Pair;
import org.bbop.termgenie.tools.ResourceLoader;

import owltools.graph.OWLGraphWrapper;

public class DefaultOntologyConfiguration extends ResourceLoader {
	
	private static final Logger logger = Logger.getLogger(DefaultOntologyConfiguration.class);
	
	private static final String SETTINGS_FILE = "default-ontology-configuration.settings";

	private static volatile Pair<IRIMapper, Map<String, ConfiguredOntology>> configuration = null;
	
	public static Map<String, ConfiguredOntology> getOntologies() {
		return getConfiguration().getTwo();
	}
	
	public static IRIMapper getIRIMapper() {
		return getConfiguration().getOne();
	}
	
	private static synchronized Pair<IRIMapper, Map<String, ConfiguredOntology>> getConfiguration() {
		if (configuration == null) {
			DefaultOntologyConfiguration c = new DefaultOntologyConfiguration();
			configuration = c.loadOntologyConfiguration();
		}
		return configuration;
	}
	
	public static class ConfiguredOntology extends Ontology {
		
		String source = null;
		List<String> supports = null;
		List<String> requires = null;
		
		ConfiguredOntology(String name) {
			super(null, name, null, null);
		}
		
		
		void addSupport(String support) {
			if (support == null) {
				return;
			}
			if (supports == null) {
				supports = new ArrayList<String>(6);
			}
			supports.add(support);
		}
		
		void addRequires(String requires) {
			if (requires == null) {
				return;
			}
			if (this.requires == null) {
				this.requires = new ArrayList<String>(3);
			}
			this.requires.add(requires);
		}

		/**
		 * @return the supports
		 */
		List<String> getSupports() {
			if (supports == null) {
				return Collections.emptyList();
			}
			return supports;
		}


		/**
		 * @return the requires
		 */
		List<String> getRequires() {
			if (requires == null) {
				return Collections.emptyList();
			}
			return requires;
		}
		
		protected ConfiguredOntology createBranch(String subOntologyName, String subOntologyParentId) {
			ConfiguredOntology branch = new ConfiguredOntology(name);
			branch.requires = requires;
			branch.source = source;
			branch.supports = supports;
			branch.setBranch(subOntologyName, subOntologyParentId);
			return branch;
		}
		
		public Ontology createOntology(OWLGraphWrapper realOntology) {
			realInstance = realOntology;
			return this;
		}


		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ConfiguredOntology [");
			if (name != null) {
				builder.append("name=");
				builder.append(name);
				builder.append(", ");
			}
			if (subOntologyName != null) {
				builder.append("subOntologyName=");
				builder.append(subOntologyName);
				builder.append(", ");
			}
			if (subOntologyParentId != null) {
				builder.append("subOntologyParentId=");
				builder.append(subOntologyParentId);
				builder.append(", ");
			}
			if (realInstance != null) {
				builder.append("realInstance=");
				builder.append(realInstance);
				builder.append(", ");
			}
			if (source != null) {
				builder.append("source=");
				builder.append(source);
				builder.append(", ");
			}
			if (supports != null) {
				builder.append("supports=");
				builder.append(supports);
				builder.append(", ");
			}
			if (requires != null) {
				builder.append("requires=");
				builder.append(requires);
			}
			builder.append("]");
			return builder.toString();
		}
	}
	
	Pair<IRIMapper, Map<String, ConfiguredOntology>> loadOntologyConfiguration() {
		BufferedReader reader = null;
		try {
			InputStream inputStream = loadResource(SETTINGS_FILE);
			reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
			Map<String, ConfiguredOntology> map = new LinkedHashMap<String, ConfiguredOntology>();
			IRIMapper iriMapper = null;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.length() > 0 && !line.startsWith("!")) {
					if(line.startsWith("[Ontology]")) {
						parseOntology(reader, map);
					}
					else if (line.startsWith("[OntologyBranch]")) {
						parseOntologyBranch(reader, map);
					}
					else if (line.startsWith("[IRIMapping]")) {
						iriMapper = parseIRIMapperConfig(reader);
					}
				}
			}
			return new Pair<IRIMapper, Map<String, ConfiguredOntology>>(iriMapper, map);
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException exception) {
					logger.error("Could not close reader.",exception);
				}
			}
		}
	}
	
	private IRIMapper parseIRIMapperConfig(BufferedReader reader) throws IOException {
		String line = reader.readLine();
		if (line.startsWith("localfile:")) {
			String resource = line.substring("localfile:".length()).trim();
			return new LocalFileIRIMapper(resource);
		}
		return null;
	}
	
	private void parseOntology(BufferedReader reader, Map<String, ConfiguredOntology> ontologies) throws IOException {
		String line;
		ConfiguredOntology current = null;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() <= 1) {
				if (current != null) {
					ontologies.put(current.getUniqueName(), current);
					current = null;
				}
				// empty lines as end marker
				return;
			}
			if (!line.startsWith("!")) {
				if (line.startsWith("name: ")) {
					current = new ConfiguredOntology(getValue(line, "name: "));
				}
				else if (current != null && line.startsWith("source: ")) {
					current.source = getValue(line, "source: ");
				}
				else if (current != null && line.startsWith("support: ")) {
					current.addSupport(getValue(line, "support: "));
				}
				else if (current != null && line.startsWith("requires: ")) {
					current.addRequires(getValue(line, "requires: "));
				}
			}
		}
		if (current != null) {
			ontologies.put(current.getUniqueName(), current);
		}
	}
	
	private void parseOntologyBranch(BufferedReader reader, Map<String, ConfiguredOntology> ontologies) throws IOException  {
		String line;
		String name = null;
		String ontology = null;
		String parent = null;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() <= 1) {
				if (name != null && ontology != null && parent != null) {
					ConfiguredOntology full = ontologies.get(ontology);
					if (full != null) {
						ontologies.put(name, full.createBranch(name, parent));
					}
				}
				// empty lines as end marker
				return;
			}
			if (!line.startsWith("!")) {
				if (line.startsWith("name: ")) {
					name = getValue(line, "name: ");
				}
				else if (line.startsWith("ontology: ")) {
					ontology = getValue(line, "ontology: ");
				}
				else if (line.startsWith("parent: ")) {
					parent = getValue(line, "parent: ");
				}
			}
		}
	}
	
	private String getValue(String line, String prefix) {
		String value = line.substring(prefix.length());
		int comment = value.indexOf(" !");
		if (comment > 0) {
			value = value.substring(0, comment);
		}
		value = value.trim();
		return value;
	}
	
	public static void main(String[] args) {
		Map<String, ConfiguredOntology> ontologies = getOntologies();
		for (String key : ontologies.keySet()) {
			ConfiguredOntology ontology = ontologies.get(key);
			System.out.print(ontology.getUniqueName());
			if (ontology.getBranch() != null) {
				System.out.print(" - ");
				System.out.print(ontology.getBranch());
				System.out.print(" ! ");
				System.out.print(ontology.getBranchId());
			}
			System.out.println();
			System.out.println(ontology.source);
			for (String support : ontology.getSupports()) {
				System.out.println("Support: "+support);
			}
			for (String requires : ontology.getRequires()) {
				System.out.println("Requires: "+requires);
			}
			System.out.println();
		}
	}
}
