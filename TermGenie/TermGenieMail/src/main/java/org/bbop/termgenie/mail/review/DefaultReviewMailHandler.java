package org.bbop.termgenie.mail.review;

import java.util.List;

import org.apache.commons.mail.EmailException;
import org.apache.log4j.Logger;
import org.bbop.termgenie.mail.MailHandler;
import org.bbop.termgenie.ontology.entities.CommitHistoryItem;
import org.bbop.termgenie.ontology.entities.CommitedOntologyTerm;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class DefaultReviewMailHandler implements ReviewMailHandler {

	private static final Logger logger = Logger.getLogger(DefaultReviewMailHandler.class);

	private final MailHandler mailHandler;
	private final String fromAddress;
	private final String fromName;

	/**
	 * @param mailHandler
	 * @param fromAddress
	 * @param fromName
	 */
	@Inject
	public DefaultReviewMailHandler(MailHandler mailHandler,
			@Named("DefaultReviewMailHandlerFromAddress") String fromAddress,
			@Named("DefaultReviewMailHandlerFromName") String fromName)
	{
		super();
		this.mailHandler = mailHandler;
		this.fromAddress = fromAddress;
		this.fromName = fromName;
	}

	@Override
	public void handleReviewMail(CommitHistoryItem item) {
		List<CommitedOntologyTerm> terms = item.getTerms();
		StringBuilder body = new StringBuilder();
		final String init;
		final String subject;
		if (terms.size() > 1) {
			subject = "Your requested terms have been committed to the ontology.";
			init = "Hello,\n\nafter a review the following requested terms have been committed to ontology:\n\n";
		}
		else {
			subject = "Your requested term has been committed to the ontology.";
			init = "Hello,\n\nafter a review the following requested term has been committed to ontology:\n\n";
		}
		body.append(init);
		for (CommitedOntologyTerm commitedOntologyTerm : terms) {
			body.append(commitedOntologyTerm.getObo());
			body.append('\n');
		}
		final String email = item.getEmail();

		try {
			mailHandler.sendEmail(subject, body.toString(), fromAddress, fromName, email);
		} catch (EmailException exception) {
			logger.warn("Could not send e-mail to user: " + email, exception);
		}
	}

}
