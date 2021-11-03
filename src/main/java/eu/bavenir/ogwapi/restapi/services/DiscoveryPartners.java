package eu.bavenir.ogwapi.restapi.services;

import java.util.logging.Logger;

import org.apache.commons.configuration2.XMLConfiguration;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import eu.bavenir.ogwapi.commons.CommunicationManager;
import eu.bavenir.ogwapi.restapi.Api;


/*
 * STRUCTURE
 * - constants
 * - public methods overriding HTTP methods 
 * - private methods
 */


/**
 * This class implements a {@link org.restlet.resource.ServerResource ServerResource} interface for following
 * Gateway API calls:
 * 
 *   URL: 				[server]:[port]/api/agents/partners
 *   METHODS: 			GET
 *   SPECIFICATION:		@see <a href="https://vicinityh2020.github.io/vicinity-gateway-api/#/">Gateway API</a>
 *   
 * @author jorge
 *
 */
public class DiscoveryPartners extends ServerResource {
	
	// === OVERRIDEN HTTP METHODS ===
	
	/**
	 * Returns relationship between requester organisation and node organisation.
	 * 
	 * @return string (enum: Me, Friend, Other)
	 */
	@Get
	public Representation represent() {
		
		Logger logger = (Logger) getContext().getAttributes().get(Api.CONTEXT_LOGGER);
		XMLConfiguration config = (XMLConfiguration) getContext().getAttributes().get(Api.CONTEXT_CONFIG);
		
		return getPartners(logger, config);
	}
	
	// === PRIVATE METHODS ===
		
	/**
	 * Retrieves the list of IoT objects registered under given Agent from the Neighbourhood Manager. 
	 * 
	 * @param oid The ID of the Agent in question.
	 * @param logger Logger to be used. 
	 * 
	 * @return All VICINITY identifiers of objects registered under specified agent.
	 */
	private Representation getPartners(Logger logger, XMLConfiguration config){
		
		CommunicationManager communicationManager 
			= (CommunicationManager) getContext().getAttributes().get(Api.CONTEXT_COMMMANAGER);

		return communicationManager.getPartners();
	
	}
}
