package org.bbop.termgenie.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bbop.termgenie.core.management.GenericTaskManager.InvalidManagedInstanceException;
import org.bbop.termgenie.core.process.ProcessState;
import org.bbop.termgenie.core.rules.ReasonerFactory;
import org.bbop.termgenie.core.rules.ReasonerTaskManager;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationInput;
import org.bbop.termgenie.owl.InferAllRelationshipsTask;
import org.bbop.termgenie.owl.InferredRelations;
import org.bbop.termgenie.owl.OWLChangeTracker;
import org.bbop.termgenie.rules.TermGenieScriptFunctionsMDef.MDef;
import org.bbop.termgenie.tools.Pair;
import org.obolibrary.macro.ManchesterSyntaxTool;
import org.semanticweb.owlapi.expression.ParserException;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import owltools.graph.OWLGraphWrapper;

public class TermCreationToolsMDef extends AbstractTermCreationTools<List<MDef>> {

	private final ManchesterSyntaxTool syntaxTool;
	private final String targetOntologyId;
	private final String tempIdPrefix;
	private final boolean useIsInferred;

	/**
	 * @param input
	 * @param targetOntology
	 * @param tempIdPrefix
	 * @param patternID
	 * @param factory
	 * @param syntaxTool
	 * @param state 
	 */
	TermCreationToolsMDef(TermGenerationInput input,
			OWLGraphWrapper targetOntology,
			String tempIdPrefix,
			String patternID,
			ReasonerFactory factory,
			ManchesterSyntaxTool syntaxTool,
			ProcessState state,
			boolean useIsInferred)
	{
		super(input, targetOntology, tempIdPrefix, patternID, factory, state);
		this.tempIdPrefix = tempIdPrefix;
		this.targetOntologyId = targetOntology.getOntologyId();
		this.syntaxTool = syntaxTool;
		this.useIsInferred = useIsInferred;
	}

	@Override
	protected String renderLogicalDefinition(List<MDef> logicalDefinitions) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < logicalDefinitions.size(); i++) {
			MDef mDef = logicalDefinitions.get(i);
			String expression = getFullExpression(mDef);
			if (i > 0) {
				sb.append(", ");
			}
			sb.append('"');
			sb.append(expression);
			sb.append('"');
		}
		return sb.toString();
	}

	private String getFullExpression(MDef mDef) {
		String expression = mDef.getExpression();
		Map<String, String> parameters = mDef.getParameters();
		for (Entry<String, String> parameter : parameters.entrySet()) {
			expression = expression.replaceAll("\\?" + parameter.getKey(), parameter.getValue());
		}
		return expression;
	}

	@Override
	protected InferredRelations createRelations(List<MDef> logicalDefinitions,
			String newId,
			String label,
			OWLChangeTracker changeTracker) throws RelationCreationException
	{
		if (logicalDefinitions == null || logicalDefinitions.isEmpty()) {
			return InferredRelations.EMPTY;
		}
		ProcessState.addMessage(state, "Start inferring relationships from logical definition.");
		OWLOntologyManager owlManager = targetOntology.getManager();
		OWLDataFactory owlDataFactory = owlManager.getOWLDataFactory();
		IRI iri = IRI.create(newId);
		Pair<OWLClass,OWLAxiom> pair = addClass(iri, changeTracker);
		addLabel(iri, label, changeTracker);
		for (MDef def : logicalDefinitions) {
			String expression = getFullExpression(def);
			try {
				OWLClassExpression owlClassExpression = syntaxTool.parseManchesterExpression(expression);
				OWLEquivalentClassesAxiom axiom = owlDataFactory.getOWLEquivalentClassesAxiom(pair.getOne(),
						owlClassExpression);
				changeTracker.apply(new AddAxiom(targetOntology.getSourceOntology(), axiom));

			} catch (ParserException exception) {
				throw new RelationCreationException("Could not create OWL class expressions from expression: " + expression, exception);
			}
		}

		InferAllRelationshipsTask task = new InferAllRelationshipsTask(targetOntology, iri, changeTracker, tempIdPrefix, state, useIsInferred);

		factory.updateBuffered(targetOntologyId);
		ReasonerTaskManager reasonerManager = factory.getDefaultTaskManager(targetOntology);
		reasonerManager.setProcessState(state);
		try {
			reasonerManager.runManagedTask(task);
		} catch (InvalidManagedInstanceException exception) {
			throw new RelationCreationException("Could not create releations due to an invalid reasoner.", exception);
		} finally {
			reasonerManager.dispose();
			reasonerManager.removeProcessState();
		}
		InferredRelations inferredRelations = task.getInferredRelations();
		Set<OWLAxiom> classRelationAxioms = inferredRelations.getClassRelationAxioms();
		if (classRelationAxioms != null) {
			// defensive copy
			classRelationAxioms = new HashSet<OWLAxiom>(classRelationAxioms);
		}
		else {
			classRelationAxioms = new HashSet<OWLAxiom>();
		}
		classRelationAxioms.add(pair.getTwo());
		inferredRelations.setClassRelationAxioms(classRelationAxioms);
		ProcessState.addMessage(state, "Finished inferring relationships from logical definition.");
		return inferredRelations;
	}

	static Pair<OWLClass, OWLAxiom> addClass(IRI iri, OWLChangeTracker changeTracker) {
		OWLDataFactory factory = changeTracker.getTarget().getOWLOntologyManager().getOWLDataFactory();
		OWLClass owlClass = factory.getOWLClass(iri);
		OWLDeclarationAxiom owlDeclarationAxiom = factory.getOWLDeclarationAxiom(owlClass);
		changeTracker.apply(new AddAxiom(changeTracker.getTarget(), owlDeclarationAxiom));
		return new Pair<OWLClass, OWLAxiom>(owlClass, owlDeclarationAxiom);
	}
	
	static void addLabel(IRI iri,
			String label,
			OWLChangeTracker changeTracker)
	{
		OWLDataFactory owlDataFactory = changeTracker.getTarget().getOWLOntologyManager().getOWLDataFactory();
		OWLAnnotationAssertionAxiom axiom = owlDataFactory.getOWLAnnotationAssertionAxiom(owlDataFactory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
				iri,
				owlDataFactory.getOWLLiteral(label));
		changeTracker.apply(new AddAxiom(changeTracker.getTarget(), axiom));
	}
}
