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
 *   URL: 				[server]:[port]/api/discovery/items/contract/{ctid}
 *   METHODS: 			GET
 *   SPECIFICATION:		@see <a href="https://vicinityh2020.github.io/vicinity-gateway-api/#/">Gateway API</a>
 *   ATTRIBUTES:		cid - AURORAL Identifier of and organisation 
 *   					(e.g. 1dae4326-44ae-4b98-bb75-15aa82516cc3).
 *   
 * @author jorge
 *
 */
public class DiscoveryContractItemsCtid extends ServerResource {
	
	// === CONSTANTS ===
	
	/**
	 * Name of the Agent ID attribute.
	 */
	private static final String ATTR_CID = "ctid";
	
	// === OVERRIDEN HTTP METHODS ===
	
	/**
	 * Retrieves contracted items
	 * 
	 * @return string[]
	 */
	@Get
	public Representation represent() {
		
		Logger logger = (Logger) getContext().getAttributes().get(Api.CONTEXT_LOGGER);
		XMLConfiguration config = (XMLConfiguration) getContext().getAttributes().get(Api.CONTEXT_CONFIG);
		
		String attrCid = getAttribute(ATTR_CID);
		
		return getContractItems(attrCid, logger, config);
	}
	
	// === PRIVATE METHODS ===
		
	/**
	 * Retrieves contracted items
	 * 
	 * @param ctid The ID of the contract
	 * @param logger Logger to be used. 
	 * 
	 * @return string[]
	 */
	private Representation getContractItems(String ctid, Logger logger, XMLConfiguration config){
		
		CommunicationManager communicationManager 
			= (CommunicationManager) getContext().getAttributes().get(Api.CONTEXT_COMMMANAGER);

		return communicationManager.getContractItems(ctid);
	
	}
}
