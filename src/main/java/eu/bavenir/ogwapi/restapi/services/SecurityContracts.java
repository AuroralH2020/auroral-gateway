package eu.bavenir.ogwapi.restapi.services;

import java.util.logging.Logger;

import org.apache.commons.configuration2.XMLConfiguration;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
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
 *   URL: 				[server]:[port]/api/security/contracts/{cid}
 *   METHODS: 			GET
 *   SPECIFICATION:		@see <a href="https://vicinityh2020.github.io/vicinity-gateway-api/#/">Gateway API</a>
 *   
 * @author jorge
 *
 */
public class SecurityContracts extends ServerResource {
	
	// === CONSTANTS ===
	
	/**
	 * Name of the organisation ID attribute.
	 */
	private static final String ATTR_CID = "cid";

	// === OVERRIDEN HTTP METHODS ===
	
	/**
	 * Retrieves the contract with a given organisation
	 * 
	 * @return Object { cid: String, ctid: String | null, items: Json[] }
	 */
	@Get
	public Representation represent() {
		
		Logger logger = (Logger) getContext().getAttributes().get(Api.CONTEXT_LOGGER);
		XMLConfiguration config = (XMLConfiguration) getContext().getAttributes().get(Api.CONTEXT_CONFIG);
		
		String attrCid = getAttribute(ATTR_CID);

		return getContracts(attrCid, logger, config);
	}
	
	// === PRIVATE METHODS ===
		
	/**
	 * Retrieves the contract with a given organisation
	 * 
	 * @param cid The ID of the organisation in question.
	 * @param logger Logger to be used. 
	 * 
	 * @return Object { cid: String, ctid: String | null, items: Json[] }
	 */
	private Representation getContracts(String cid, Logger logger, XMLConfiguration config){
		
		CommunicationManager communicationManager 
			= (CommunicationManager) getContext().getAttributes().get(Api.CONTEXT_COMMMANAGER);

		return communicationManager.getContracts(cid);
	
	}
}
