package org.bbop.termgenie.ontology.go;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bbop.termgenie.cvs.CVSTools;
import org.bbop.termgenie.ontology.CommitException;
import org.bbop.termgenie.ontology.CommitHistoryStore;
import org.bbop.termgenie.ontology.CommitInfo.CommitMode;
import org.bbop.termgenie.ontology.Committer;
import org.bbop.termgenie.ontology.OntologyCommitReviewPipeline;
import org.bbop.termgenie.ontology.OntologyCommitReviewPipelineStages;
import org.bbop.termgenie.ontology.OntologyTaskManager;
import org.bbop.termgenie.ontology.OntologyTaskManager.OntologyTask;
import org.bbop.termgenie.ontology.entities.CommitHistoryItem;
import org.bbop.termgenie.ontology.entities.CommitedOntologyTerm;
import org.bbop.termgenie.ontology.go.GoCvsHelper.OboCommitData;
import org.bbop.termgenie.ontology.obo.OBOWriterTools;
import org.obolibrary.obo2owl.Owl2Obo;
import org.obolibrary.oboformat.model.OBODoc;

import owltools.graph.OWLGraphWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class GeneOntologyReviewCommitAdapter extends OntologyCommitReviewPipeline<CVSTools, OboCommitData, OBODoc> implements
		OntologyCommitReviewPipelineStages
{

	private final GoCvsHelper helper;

	@Inject
	GeneOntologyReviewCommitAdapter(@Named("GeneOntologyTaskManager") OntologyTaskManager source,
			CommitHistoryStore store,
			GoCvsHelper helper)
	{
		super(source, store, helper.isSupportAnonymus());
		this.helper = helper;
	}

	@Override
	protected String createDiff(CommitHistoryItem historyItem, OntologyTaskManager source)
			throws CommitException
	{

		CreateDiffTask task = new CreateDiffTask(historyItem);
		source.runManagedTask(task);
		if (task.getException() != null) {
			throw error("Could not create diff", task.getException());
		}
		if (task.diff == null) {
			throw error("Could not create diff: empty result");
		}
		return task.diff;
	}

	private class CreateDiffTask extends OntologyTask {

		private final CommitHistoryItem historyItem;

		private String diff = null;

		public CreateDiffTask(CommitHistoryItem historyItem) {
			this.historyItem = historyItem;
		}

		@Override
		protected void runCatching(OWLGraphWrapper managed) throws Exception {
			Owl2Obo owl2Obo = new Owl2Obo();
			OBODoc oboDoc = owl2Obo.convert(managed.getSourceOntology());

			List<CommitedOntologyTerm> terms = historyItem.getTerms();
			boolean succcess = applyChanges(terms, oboDoc);
			if (succcess) {
				List<String> ids = new ArrayList<String>(terms.size());
				for (CommitedOntologyTerm term : terms) {
					ids.add(term.getId());
				}
				diff = OBOWriterTools.writeTerms(ids, oboDoc);
			}
		}
	}

	@Override
	protected OboCommitData prepareWorkflow(File workFolder) throws CommitException {
		return helper.prepareWorkflow(workFolder);
	}

	@Override
	protected CVSTools prepareSCM(CommitMode mode,
			String username,
			String password,
			OboCommitData data) throws CommitException
	{
		return helper.createCVS(mode, username, password, data.cvsFolder);
	}

	@Override
	protected void updateSCM(CVSTools scm, OBODoc targetOntology, OboCommitData data)
			throws CommitException
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected OBODoc retrieveTargetOntology(CVSTools scm, OboCommitData data)
			throws CommitException
	{
		return helper.retrieveTargetOntology(scm, data);
	}

	@Override
	protected void checkTargetOntology(OboCommitData data, OBODoc targetOntology)
			throws CommitException
	{
		helper.checkTargetOntology(data, targetOntology);
	}

	@Override
	protected boolean applyChanges(List<CommitedOntologyTerm> terms, OBODoc ontology)
			throws CommitException
	{
		return helper.applyHistoryChanges(terms, ontology);
	}

	@Override
	protected void createModifiedTargetFile(OboCommitData data, OBODoc ontology)
			throws CommitException
	{
		helper.createModifiedTargetFile(data, ontology);
	}

	@Override
	protected void commitToRepository(String username, CVSTools scm, OboCommitData data, String diff)
			throws CommitException
	{
		helper.commitToRepository(username, scm, data, diff);
	}

	@Override
	protected CommitMode getCommitMode() {
		return helper.getCommitMode();
	}

	@Override
	protected String getCommitUserName() {
		return helper.getCommitUserName();
	}

	@Override
	protected String getCommitPassword() {
		return helper.getCommitPassword();
	}

	@Override
	public Committer getReviewCommitter() {
		return this;
	}

	@Override
	public BeforeReview getBeforeReview() {
		return this;
	}

	@Override
	public AfterReview getAfterReview() {
		return this;
	}

}
