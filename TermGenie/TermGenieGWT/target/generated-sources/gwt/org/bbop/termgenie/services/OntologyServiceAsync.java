package org.bbop.termgenie.services;

import java.util.List;

import org.bbop.termgenie.services.OntologyService.TermSuggestion;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

public interface OntologyServiceAsync
{

    /**
     * GWT-RPC service  asynchronous (client-side) interface
     * @see org.bbop.termgenie.services.OntologyService
     */
    void getAvailableOntologies( AsyncCallback<java.util.List<String>> callback );

    /**
     * GWT-RPC service  asynchronous (client-side) interface
     * @see org.bbop.termgenie.services.OntologyService
     */
    void autocompleteQuery(String query, String ontology, AsyncCallback<List<TermSuggestion>> callback);


	/**
     * Utility class to get the RPC Async interface from client-side code
     */
    public static final class Util 
    { 
        private static OntologyServiceAsync instance;

        public static final OntologyServiceAsync getInstance()
        {
            if ( instance == null )
            {
                instance = (OntologyServiceAsync) GWT.create( OntologyService.class );
                ServiceDefTarget target = (ServiceDefTarget) instance;
                target.setServiceEntryPoint( GWT.getModuleBaseURL() + "OntologyService" );
            }
            return instance;
        }

        private Util()
        {
            // Utility class should not be instanciated
        }
    }
}
