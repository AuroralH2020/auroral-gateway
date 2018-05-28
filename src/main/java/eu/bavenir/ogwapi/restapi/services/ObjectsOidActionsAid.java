package eu.bavenir.ogwapi.restapi.services;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.configuration2.XMLConfiguration;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import eu.bavenir.ogwapi.commons.messages.NetworkMessageRequest;
import eu.bavenir.ogwapi.commons.messages.NetworkMessageResponse;
import eu.bavenir.ogwapi.restapi.Api;
import eu.bavenir.ogwapi.commons.CommunicationManager;

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
 *   URL: 				[server]:[port]/api/objects/{oid}/actions/{aid}
 *   METHODS: 			GET, POST
 *   SPECIFICATION:		@see <a href="https://app.swaggerhub.com/apis/fserena/vicinity_gateway_api/">Gateway API</a>
 *   ATTRIBUTES:		oid - VICINITY identifier of the object (e.g. 0729a580-2240-11e6-9eb5-0002a5d5c51b).
 *   					aid - Action identifier (as in object description) (e.g. switch).
 *   
 * @author sulfo
 *
 */
public class ObjectsOidActionsAid extends ServerResource {

	// === CONSTANTS ===
	
	/**
	 * Name of the Object ID attribute.
	 */
	private static final String ATTR_OID = "oid";
	
	/**
	 * Name of the Action ID attribute.
	 */
	private static final String ATTR_AID = "aid";
	
	/**
	 * Name of the 'objects' attribute.
	 */
	private static final String ATTR_OBJECTS = "objects";
	
	/**
	 * Name of the 'actions' attribute.
	 */
	private static final String ATTR_ACTIONS = "actions";
	

	// === OVERRIDEN HTTP METHODS ===
	
	/**
	 * Gets specific action status of an available IoT object.
	 * 
	 * @return Latest action status.
	 */
	@Get
	public String represent() {
		String attrOid = getAttribute(ATTR_OID);
		String attrAid = getAttribute(ATTR_AID);
		String callerOid = getRequest().getChallengeResponse().getIdentifier();
		
		Logger logger = (Logger) getContext().getAttributes().get(Api.CONTEXT_LOGGER);
		
		if (attrOid == null || attrAid == null){
			logger.info("OID: " + attrOid + " AID: " + attrAid + " Given identifier does not exist.");
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, 
					"Given identifier does not exist.");
		}
		
		return getObjectAction(callerOid, attrOid, attrAid, logger);
	}
	
	
	/**
	 * Performs an action on an available IoT object.
	 * 
	 * @param entity Representation of the incoming JSON.
	 * @param object Model (from request).
	 * @return A task to perform an action was submitted.
	 */
	@Post("json")
	public String accept(Representation entity) {
		String attrOid = getAttribute(ATTR_OID);
		String attrAid = getAttribute(ATTR_AID);
		String callerOid = getRequest().getChallengeResponse().getIdentifier();
		
		Logger logger = (Logger) getContext().getAttributes().get(Api.CONTEXT_LOGGER);
		
		if (attrOid == null || attrAid == null){
			logger.info("OID: " + attrOid + " AID: " + attrAid + " Given identifier does not exist.");
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, 
					"Given identifier does not exist.");
		}

		if (!entity.getMediaType().equals(MediaType.APPLICATION_JSON)){
			logger.info("OID: " + attrOid + " AID: " + attrAid + " Invalid action description.");
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, 
					"Invalid action description.");
		}
		
		// get the json
		String actionJsonString = null;
		try {
			actionJsonString = entity.getText();
		} catch (IOException e) {
			logger.info(e.getMessage());
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, 
					"Invalid action description");
		}
	
		return storeAction(callerOid, attrOid, attrAid, actionJsonString, logger);
	}
	
	
	// === PRIVATE METHODS ===
	
	/**
	 * Stores new action.
	 * 
	 * @param sourceOid Caller OID.
	 * @param attrOid Called OID.
	 * @param attrAid Action ID.
	 * @param jsonString New representation of the Action.
	 * @param logger Logger taken previously from Context.
	 * @return Response text.
	 */
	private String storeAction(String sourceOid, String attrOid, String attrAid, String jsonString, Logger logger){

	
		return null;
	}
	
	
	/**
	 * Retrieves the Action defined as AID.
	 * 
	 * @param sourceOid Caller OID.
	 * @param attrOid Called OID.
	 * @param attrAid Action ID.
	 * @param logger Logger taken previously from Context.
	 * @return Response text.
	 */
	private String getObjectAction(String sourceOid, String attrOid, String attrAid, Logger logger){
		
		return null;
		
	}
}