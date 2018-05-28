package eu.bavenir.ogwapi.restapi.services;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.configuration2.XMLConfiguration;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import eu.bavenir.ogwapi.commons.messages.NetworkMessageRequest;
import eu.bavenir.ogwapi.commons.messages.NetworkMessageResponse;
import eu.bavenir.ogwapi.commons.messages.StatusMessage;
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
 *   URL: 				[server]:[port]/api/objects/{oid}/properties/{pid}
 *   METHODS: 			GET, PUT
 *   SPECIFICATION:		@see <a href="https://app.swaggerhub.com/apis/fserena/vicinity_gateway_api/">Gateway API</a>
 *   ATTRIBUTES:		oid - VICINITY identifier of the object (e.g. 0729a580-2240-11e6-9eb5-0002a5d5c51b).
 *   					pid - Property identifier (as in object description) (e.g. temp1).
 *   
 * @author sulfo
 *
 */
public class ObjectsOidPropertiesPid extends ServerResource {

	// === CONSTANTS ===
	
	/**
	 * Name of the Object ID attribute.
	 */
	private static final String ATTR_OID = "oid";
	
	/**
	 * Name of the Process ID attribute.
	 */
	private static final String ATTR_PID = "pid";
	
	/**
	 * Name of the 'objects' attribute.
	 */
	private static final String ATTR_OBJECTS = "objects";
	
	/**
	 * Name of the 'properties' attribute.
	 */
	private static final String ATTR_PROPERTIES = "properties";
	

	// === OVERRIDEN HTTP METHODS ===
	
	/**
	 * Gets the property value of an available IoT object.
	 * 
	 * @return Latest property value.
	 */
	@Get
	public Representation represent() {
		String attrOid = getAttribute(ATTR_OID);
		String attrPid = getAttribute(ATTR_PID);
		String callerOid = getRequest().getChallengeResponse().getIdentifier();
		
		Logger logger = (Logger) getContext().getAttributes().get(Api.CONTEXT_LOGGER);
		
		if (attrOid == null || attrPid == null){
			logger.info("OID: " + attrOid + " PID: " + attrPid + " Given identifier does not exist.");
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, 
					"Given identifier does not exist.");
		}
		
		return getObjectProperty(callerOid, attrOid, attrPid, logger);
		
	}
	
	
	/**
	 * Sets the property value of an available IoT object.
	 * 
	 * @param entity Representation of the incoming JSON.
	 * @param object Model.
	 */
	@Put("json")
	public Representation store(Representation entity) {
		String attrOid = getAttribute(ATTR_OID);
		String attrPid = getAttribute(ATTR_PID);
		String callerOid = getRequest().getChallengeResponse().getIdentifier();
		
		Logger logger = (Logger) getContext().getAttributes().get(Api.CONTEXT_LOGGER);
		
		if (attrOid == null || attrPid == null){
			logger.info("OID: " + attrOid + " PID: " + attrPid 
									+ " Object or property does not exist under given identifier.");
			
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, 
					"Object or property does not exist under given identifier.");
		}
		
		if (!entity.getMediaType().equals(MediaType.APPLICATION_JSON)){
			logger.info("OID: " + attrOid + " PID: " + attrPid 
					+ " Invalid property description - must be a valid JSON.");
			
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, 
					"Invalid property description - must be a valid JSON.");
		}
		
		// get the json
		String propertyJsonString = null;
		try {
			propertyJsonString = entity.getText();
		} catch (IOException e) {
			logger.info(e.getMessage());
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, 
					"Invalid property description");
		}
		
		return updateProperty(callerOid, attrOid, attrPid, propertyJsonString, logger);
	}
	
	
	// === PRIVATE METHODS ===
	
	/**
	 * Updates the property defined as PID.
	 * 
	 * @param sourceOid Caller OID.
	 * @param attrOid Called OID.
	 * @param attrPid Property ID.
	 * @param jsonString New representation of the property.
	 * @param logger Logger taken previously from Context.
	 * @return Response text.
	 */
	private Representation updateProperty(String sourceOid, String attrOid, String attrPid, String jsonString, Logger logger){
		CommunicationManager communicationManager 
								= (CommunicationManager) getContext().getAttributes().get(Api.CONTEXT_COMMMANAGER);
		
		XMLConfiguration config = (XMLConfiguration) getContext().getAttributes().get(Api.CONTEXT_CONFIG);
		
		NetworkMessageRequest request = new NetworkMessageRequest(config);
		
		// we will need this newly generated ID, so we keep it
		int requestId = request.getRequestId();
		
		// now fill the thing
		request.setRequestOperation(NetworkMessageRequest.REQUEST_OPERATION_PUT);
		request.addAttribute(ATTR_OBJECTS, attrOid);
		request.addAttribute(ATTR_PROPERTIES, attrPid);
		
		request.setRequestBody(jsonString);
		
		// all set
		if (!communicationManager.sendMessage(sourceOid, attrOid, request.buildMessageString())){
			logger.info("Destination object " + attrOid + " is not online.");
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Destination object is not online.");
		}
		
		// this will wait for response
		NetworkMessageResponse response 
						= (NetworkMessageResponse) communicationManager.retrieveSingleMessage(sourceOid, requestId);
		
		if (response == null){
			logger.info("No response message received. Source ID: " 
				+ sourceOid + " Destination ID: " + attrOid + " Property ID: " + attrPid  
				+ " Request ID: " + requestId);
			throw new ResourceException(Status.CONNECTOR_ERROR_CONNECTION,
					"No valid response from remote object, possible message timeout.");
		}
		
		// if the return code is different than 2xx, make it visible
		if ((response.getResponseCode() / 200) != 1){
			logger.info("Source object: " + sourceOid + " Destination object: " + attrOid 
					+ " Response code: " + response.getResponseCode() + " Reason: " + response.getResponseCodeReason());
			
			StatusMessage statusMessage = new StatusMessage();
			statusMessage.setError(true);
			statusMessage.addMessage(StatusMessage.MESSAGE_CODE, String.valueOf(response.getResponseCode()));
			statusMessage.addMessage(StatusMessage.MESSAGE_REASON, response.getResponseCodeReason());
			
			return new JsonRepresentation(statusMessage.buildMessage().toString());
		}
		
		return new JsonRepresentation(response.getResponseBody());
		
	}
	
	
	/**
	 * Retrieves the property defined as PID.
	 * 
	 * @param sourceOid Caller OID.
	 * @param attrOid Called OID.
	 * @param attrPid Property ID.
	 * @param logger Logger taken previously from Context.
	 * @return Response text.
	 */
	private Representation getObjectProperty(String sourceOid, String attrOid, String attrPid, Logger logger){
		
		// send message to the right object
		CommunicationManager communicationNode 
								= (CommunicationManager) getContext().getAttributes().get(Api.CONTEXT_COMMMANAGER);
		
		XMLConfiguration config = (XMLConfiguration) getContext().getAttributes().get(Api.CONTEXT_CONFIG);
		
		NetworkMessageRequest request = new NetworkMessageRequest(config);
		
		// we will need this newly generated ID, so we keep it
		int requestId = request.getRequestId();
		
		// now fill the thing
		request.setRequestOperation(NetworkMessageRequest.REQUEST_OPERATION_GET);
		request.addAttribute(ATTR_OBJECTS, attrOid);
		request.addAttribute(ATTR_PROPERTIES, attrPid);
		
		// all set
		if (!communicationNode.sendMessage(sourceOid, attrOid, request.buildMessageString())){
			logger.info("Destination object " + attrOid + " is not online.");
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Destination object is not online.");
		}
		
		// this will wait for response
		NetworkMessageResponse response 
						= (NetworkMessageResponse) communicationNode.retrieveSingleMessage(sourceOid, requestId);
		
		if (response == null){
			logger.info("No response message received. Source ID: " 
				+ sourceOid + " Destination ID: " + attrOid + " Property ID: " + attrPid  
				+ " Request ID: " + requestId);
			throw new ResourceException(Status.CONNECTOR_ERROR_CONNECTION,
					"No valid response from remote object, possible message timeout.");
		}
		
		// if the return code is different than 2xx, make it visible
		if ((response.getResponseCode() / 200) != 1){
			logger.info("Source object: " + sourceOid + " Destination object: " + attrOid 
					+ " Response code: " + response.getResponseCode() + " Reason: " + response.getResponseCodeReason());
			
			StatusMessage statusMessage = new StatusMessage();
			statusMessage.setError(true);
			statusMessage.addMessage(StatusMessage.MESSAGE_CODE, String.valueOf(response.getResponseCode()));
			statusMessage.addMessage(StatusMessage.MESSAGE_REASON, response.getResponseCodeReason());
			
			return new JsonRepresentation(statusMessage.buildMessage().toString());
		}
		
		return new JsonRepresentation(response.getResponseBody());
	}
	
}