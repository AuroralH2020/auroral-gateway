package eu.bavenir.ogwapi.commons;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.apache.commons.configuration2.XMLConfiguration;

import eu.bavenir.ogwapi.commons.messages.CodesAndReasons;
import eu.bavenir.ogwapi.commons.messages.NetworkMessageRequest;
import eu.bavenir.ogwapi.commons.messages.NetworkMessageResponse;
import eu.bavenir.ogwapi.commons.messages.StatusMessage;

/*
 * STRUCTURE:
 * - constants
 * - fields
 * - public methods
 * - private methods
 */

/*
 * Unit testing:
 * 1. connecting multiple users
 * 2. getting connection lists
 * 3. retrieving rosters of the users
 * 4. sending messages
 * 		a. to online contacts in the roster while online
 * 		b. to offline contact in the roster while online (sending to offline option is true)
 * 		c. to offline contact in the roster while online (sending to offline option is false)
 * 		d. to a contact not in the roster while online
 * 		e. all the above while offline
 * 5. disconnect
 */


/**
 * 
 * This class serves as a connection manager for XMPP communication over P2P network. There is usually only need
 * for a single instance of this class, even if there are several devices connecting through the Gateway API. The 
 * instance of this class maintains a pool of connection descriptors, where each descriptor represents one separate 
 * client connection. The thread safe pool is based on a synchronized {@link java.util.HashMap HashMap} implementation.
 * 
 *  It is important that the private methods for operations over the descriptor pool are used when extending or 
 *  modifying this class instead of the direct approach to the descriptorPool HashMap (direct methods can however still
 *  be used if they don't alter the HashMap's structure or values).
 *  
 *  Usual modus operandi of this class is as follows:
 *  
 *  1. establishConnection - use as many times as necessary
 *  2. use all the methods as necessary for communication
 *  3. terminateConnection - if there is a need to close a connection for some device. 
 *  4. terminateAllConnections - cleanup when the application shuts down. 
 *  
 *  After step 4, it is safe to start a new with step 1. 
 *  
 *  
 *  MESSAGES
 *  
 *  All communication among connected XMPP clients is exchanged via XMPP messages. In general there are only 2 types
 *  of messages that are exchanged:
 *  
 *  a) requests  -	An Object is requesting an access to a service of another Object (or Agent). This request needs to 
 *  				be propagated across XMPP network and at the end of the communication pipe, the message has to be
 *  				translated into valid HTTP request to an Agent service. Translating the message is as well as 
 *  				their detailed structure is described in {@link RestAgentConnector AgentCommunicator}. See
 *  				{@link NetworkMessageRequest NetworkMessageRequest}.
 *  
 *  b) responses -	The value returned by Object / Agent services in JSON, that is propagated back to the caller. See
 *  				{@link NetworkMessageResponse NetworkMessageResponse}.
 *  
 *    
 * @author sulfo
 *
 */
public class CommunicationManager {

	
	/* === CONSTANTS === */
	
	private static final String CONFIG_PARAM_SESSIONRECOVERY = "general.sessionRecovery";
	
	private static final String CONFIG_DEF_SESSIONRECOVERY = "proactive";
	
	private static final String CONFIG_PARAM_SESSIONEXPIRATION = "general.sessionExpiration";
	
	private static final int CONFIG_DEF_SESSIONEXPIRATION = 30;
	
	
	private static final int SESSIONRECOVERYPOLICY_INT_ERROR = 0;
	
	private static final int SESSIONRECOVERYPOLICY_INT_PASSIVE = 1;
	
	private static final int SESSIONRECOVERYPOLICY_INT_NONE = 2;
	
	private static final int SESSIONRECOVERYPOLICY_INT_PROACTIVE = 3;
	
	private static final String SESSIONRECOVERYPOLICY_STRING_PASSIVE = "passive";
	
	private static final String SESSIONRECOVERYPOLICY_STRING_NONE = "none";
	
	private static final String SESSIONRECOVERYPOLICY_STRING_PROACTIVE = "proactive";
	
	/**
	 * How often will gateway check whether or not the connections are still up (ms).
	 */
	private static final int SESSIONRECOVERY_CHECKINTERVAL_PROACTIVE = 30000;
	
	private static final int SESSIONRECOVERY_CHECKINTERVAL_PASSIVE = 5000;
	
	private static final int SESSIONEXPIRATION_MINIMAL_VALUE = 5;
	
	
	
	/* === FIELDS === */
	
	// hash map containing connections identified by object IDs
	private Map<String, ConnectionDescriptor> descriptorPool;
	
	/**
	 * Indicates the policy that the OGWAPI should take during session recovery.
	 */
	private int sessionRecoveryPolicy;
	
	private int sessionExpiration;
	
	
	
	// logger and configuration
	private XMLConfiguration config;
	private Logger logger;
	
	
	
	/* === PUBLIC METHODS === */
	
	
	/**
	 * Constructor, initialises necessary objects. All parameters are mandatory, failure to include them can lead 
	 * to a swift end of application.
	 * 
	 * @param config Configuration object.
	 * @param logger Java logger.
	 */
	public CommunicationManager(XMLConfiguration config, Logger logger){
		descriptorPool = Collections.synchronizedMap(new HashMap<String, ConnectionDescriptor>());
		
		this.sessionExpiration = 0;
		
		this.config = config;
		this.logger = logger;
		
		// load the configuration for the session recovery policy
		String sessionRecoveryPolicyString = config.getString(CONFIG_PARAM_SESSIONRECOVERY, CONFIG_DEF_SESSIONRECOVERY);
		
		translateSessionRecoveryConf(sessionRecoveryPolicyString);
		
		if (sessionRecoveryPolicy == SESSIONRECOVERYPOLICY_INT_ERROR) {
			// wrong configuration parameter entered - set it to default
			logger.severe("Wrong parameter entered for " + CONFIG_PARAM_SESSIONRECOVERY + " in the configuration file: "  
					+ sessionRecoveryPolicyString + ". Setting to default: " + CONFIG_DEF_SESSIONRECOVERY);
			
			translateSessionRecoveryConf(CONFIG_DEF_SESSIONRECOVERY);
			
		} else {
			logger.config("The session recovery policy is set to " + sessionRecoveryPolicyString + ".");
		}
				
		// timer for session checking - N/A if the session recovery is set to none
		if (sessionRecoveryPolicy != SESSIONRECOVERYPOLICY_INT_NONE) {
			
			int checkInterval;
			
			switch(sessionRecoveryPolicy) {
			case SESSIONRECOVERYPOLICY_INT_PASSIVE:
				checkInterval = SESSIONRECOVERY_CHECKINTERVAL_PASSIVE;
				
				sessionExpiration = config.getInt(CONFIG_PARAM_SESSIONEXPIRATION, CONFIG_DEF_SESSIONEXPIRATION);
				
				// if somebody put there too small number, we will turn it to default
				if (sessionExpiration < SESSIONEXPIRATION_MINIMAL_VALUE) {
					sessionExpiration = CONFIG_DEF_SESSIONEXPIRATION;
				}
				
				sessionExpiration = sessionExpiration * 1000;
				
				logger.config("Session expiration is set to " + sessionExpiration + "ms");
				break;
				
			case SESSIONRECOVERYPOLICY_INT_PROACTIVE:
				checkInterval = SESSIONRECOVERY_CHECKINTERVAL_PROACTIVE;
				break;
				
				default:
					// if something goes wrong, don't let the timer stand in our way
					checkInterval = Integer.MAX_VALUE;
			}
			
			
			Timer timerForSessionRecovery = new Timer();
			timerForSessionRecovery.schedule(new TimerTask() {
				@Override
				public void run() {
					recoverSessions();
				}
			}, checkInterval, checkInterval);
		}
	}
	
	
	
	// ADMINISTRATION METHODS - not directly related to interfaces
	
	/**
	 * Retrieves the object IDs that has open connections to network via this CommunicationManager. 
	 * Based on these strings, the respective connections can be retrieved from the connection descriptor pool.
	 * 
	 * @return Set of object IDs. 
	 */
	public Set<String> getConnectionList(){
		
		Set<String> usernames = descriptorPool.keySet();
		
		logger.finest("-- Object IDs connected to network through this CommunicationManager: --");
		for (String string : usernames) {
			logger.finest(string);
		}
		logger.finest("-- End of list. --");
		return usernames;
	}
	
	
	/**
	 * Checks whether the connection {@link ConnectionDescriptor descriptor} instance exists for given object ID and 
	 * whether or not it is connected to the network. Returns true or false accordingly.  
	 * 
	 * @param objectId Object ID in question. 
	 * @return True if descriptor exists and the connection is established.
	 */
	public boolean isConnected(String objectId){
		
		if (sessionRecoveryPolicy == SESSIONRECOVERYPOLICY_INT_ERROR) {
			return false;
		}
		
		if (objectId == null) {
			logger.warning("CommunicationManager.isConnected: Invalid object ID.");
			return false;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(objectId);
		
		if (descriptor != null){
			return descriptor.isConnected();
		} else {
			logger.info("Object ID: '" + objectId + "' is not connected yet.");
			
			return false;
		}
	}
	
	
	/**
	 * Verifies the credentials of an object, when (for example) trying to reach its connection 
	 * {@link ConnectionDescriptor descriptor} instance via RESTLET API Authenticator. This method should be called after 
	 * {@link #isConnected(String) isConnected} is used for making sure, that the object is actually connected. 
	 * It is safe to use this method when processing authentication of every request, even in quick succession.
	 * 
	 * @param objectId Object ID in question.
	 * @param password The password that is to be verified. 
	 * @return True, if the password is valid.
	 */
	public boolean verifyPassword(String objectId, String password){
		ConnectionDescriptor descriptor = descriptorPoolGet(objectId);
		
		if (descriptor != null){
			return descriptor.verifyPassword(password);
		} else {
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + objectId + "'.");
			
			return false;
		}
	}
	
	
	/**
	 * Closes all open connections to network. It will also clear these connection handlers off the connection 
	 * descriptor pool table, preventing the re-connection (they have to be reconfigured to be opened again).
	 */
	public void terminateAllConnections(){
		
		Collection<ConnectionDescriptor> descriptors = descriptorPool.values();
		
		logger.info("Closing all connections.");
		
		for (ConnectionDescriptor descriptor: descriptors){
			if (descriptor != null){
				
				descriptor.disconnect();
				
			} else {
				logger.warning("Null record in the connection descriptor pool.");
			}
		}
		
		descriptorPoolClear();
		logger.finest("Connection descriptor pool flushed.");
	}
	
	
	
	// AUTHENTICATION INTERFACE
	
	
	/**
	 * Establishes a single connection for given object with preferences from configuration file and provided credentials. 
	 * The connection descriptor is then stored in the internal descriptor pool.
	 * 
	 * If the connection descriptor for given object already exists, it get terminated, discarded and then recreated.
	 * 
	 * 
	 * NOTE: This is the equivalent of GET /objects/login in the REST API.
	 * 
	 * @param objectId Object ID.
	 * @param password Password.
	 * @return StatusMessage, with the error flag set as false, if the login was successful. If not, the error flag is
	 * set to true.
	 */
	public StatusMessage establishConnection(String objectId, String password){
		
		ConnectionDescriptor descriptor;
		boolean verifiedOrConnected;
		StatusMessage statusMessage;
		
		if (sessionRecoveryPolicy == SESSIONRECOVERYPOLICY_INT_PASSIVE) {
			descriptor = descriptorPoolGet(objectId);
			
			if (descriptor.isConnected()) {
				
				if (descriptor.verifyPassword(password)) {
					descriptor.resetConnectionTimer();
					verifiedOrConnected = true;
				} else {
					verifiedOrConnected = false;
				}
				
			} else {
				verifiedOrConnected = descriptor.connect();
			}
			
		} else {
			// if there is a previous descriptor we should close the connection first, before reopening it again
			descriptor = descriptorPoolRemove(objectId);
			if (descriptor != null){
		
				descriptor.disconnect();
				
				logger.info("Reconnecting '" + objectId + "' to network.");
			}
			
			descriptor = new ConnectionDescriptor(objectId, password, config, logger);
			
			verifiedOrConnected = descriptor.connect();
		}
		
		if (verifiedOrConnected){
			logger.info("Connection for '" + objectId +"' was established.");
			
			// insert the connection descriptor into the pool
			descriptorPoolPut(objectId, descriptor);
			
			statusMessage = new StatusMessage(false, CodesAndReasons.CODE_200_OK, 
					CodesAndReasons.REASON_200_OK + "Login successfull.", StatusMessage.CONTENTTYPE_APPLICATIONJSON);
			
		} else {
			logger.info("Connection for '" + objectId +"' was not established.");
			statusMessage = new StatusMessage(true, CodesAndReasons.CODE_401_UNAUTHORIZED, 
					CodesAndReasons.REASON_401_UNAUTHORIZED + "Login unsuccessfull.", 
					StatusMessage.CONTENTTYPE_APPLICATIONJSON);
		}
		
		return statusMessage;
	}
	
	
	
	/**
	 * Disconnects a single connection identified by the connection object ID given as the first parameter. 
	 * The second parameter specifies, whether the connection descriptor is to be destroyed after it is disconnected.
	 * If not, the descriptor will remain in the pool and it is possible to use it during eventual reconnection.
	 * Otherwise connection will have to be build a new (which can be useful when the connection needs to be 
	 * reconfigured). 
	 * 
	 * This is the equivalent of GET /objects/logout in the REST API.
	 * 
	 * @param objectId User name used to establish the connection.
	 * @param destroyConnectionDescriptor Whether the connection descriptor should also be destroyed or not. 
	 */
	public void terminateConnection(String objectId, boolean destroyConnectionDescriptor){
		
		ConnectionDescriptor descriptor = descriptorPoolGet(objectId); 
		
		if (descriptor != null){
			descriptor.disconnect();
		} else {
			logger.info("Attempting to terminate nonexisting connection. Object ID: '" + objectId + "'.");
		}
		
		if (destroyConnectionDescriptor){
			descriptorPoolRemove(objectId);
			logger.info("Connection for object ID '" + objectId + "' destroyed.");
		} else {
			// this will keep the connection in the pool
			logger.info("Connection for object ID '" + objectId + "' closed.");
		}

	}
	
	
	
	
	// CONSUMPTION INTERFACE

	
	// TODO documentation
	public StatusMessage getPropertyOfRemoteObject(String sourceOid, String destinationOid, String propertyId, 
			String body, Map<String, String> parameters) {
		
		if (sourceOid == null){
			logger.warning("Error when getting property of remote object. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when getting property of remote object. Destination object ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		if (propertyId == null){
			logger.warning("Error when getting property of remote object. The property ID is null. "
					+ "Source object: '" + sourceOid + "', destination object: '" + destinationOid);
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.getPropertyOfRemoteObject(destinationOid, propertyId, parameters, body);
		
	}
	
	
	// TODO documentation
	public StatusMessage setPropertyOfRemoteObject(String sourceOid, String destinationOid, String propertyId, 
			String body, Map<String,String> parameters) {
		
		if (sourceOid == null){
			logger.warning("Error when setting property of remote object. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when setting property of remote object. Destination object ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		if (propertyId == null){
			logger.warning("Error when setting property of remote object. The property ID is null. "
					+ "Source object: '" + sourceOid + "', destination object: '" + destinationOid);
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.setPropertyOfRemoteObject(destinationOid, propertyId, body, parameters);
	}

	
	
	// TODO documentation
	public StatusMessage startAction(String sourceOid, String destinationOid, String actionId, String body, 
			Map<String, String> parameters) {
		
		if (sourceOid == null){
			logger.warning("Error when starting action. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when starting action. Destination object ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		if (actionId == null){
			logger.warning("Error when starting action of remote object. The action ID is null. "
					+ "Source object: '" + sourceOid + "', destination object: '" + destinationOid);
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.startAction(destinationOid, actionId, body, parameters);
		
	}
	
	
	public StatusMessage updateTaskStatus(String sourceOid, String actionId, 
					String newStatus, String returnValue, Map<String, String> parameters) {
		
		if (sourceOid == null){
			logger.warning("Error when updating task status. Source object ID is null.");
			
			return null;
		}
		
		if (actionId == null){
			logger.warning("Error when updating task status. The action ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		if (newStatus == null){
			logger.warning("Error when updating task status. The new status is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null) {
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		}
		
		return descriptor.updateTaskStatus(actionId, newStatus, returnValue, parameters);
	}
	
	
	public StatusMessage retrieveTaskStatus(String sourceOid, String destinationOid, String actionId, String taskId, 
			Map<String, String> parameters, String body) {
		
		if (sourceOid == null){
			logger.warning("Error when retrieving task status. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when retrieving task status. Destination object ID is null.");
			
			return null;
		}
		
		if (actionId == null){
			logger.warning("Error when retrieving task status. The action ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		if (taskId == null){
			logger.warning("Error when retrieving task status. The task ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.retrieveTaskStatus(destinationOid, actionId, taskId, parameters, body);
	}
	
	
	
	public StatusMessage cancelRunningTask(String sourceOid, String destinationOid, String actionId, String taskId, 
			Map<String,String> parameters, String body) {
		
		if (sourceOid == null){
			logger.warning("Error when canceling task. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when canceling task. Destination object ID is null.");
			
			return null;
		}
		
		if (actionId == null){
			logger.warning("Error when canceling task. The action ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		if (taskId == null){
			logger.warning("Error when canceling task. The task ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.cancelRunningTask(destinationOid, actionId, taskId, parameters, body);
	}
	
	
	
	
	// DISCOVERY INTERFACE
	
	
	/**
	 * Retrieves a collection of roster entries for object ID (i.e. its contact list). If there is no connection 
	 * established for the given object ID, returns empty {@link java.util.Set Set}. 
	 * 
	 * NOTE: This is the equivalent of GET /objects in the REST API.
	 * 
	 * @param objectId Object ID the roster is to be retrieved for. 
	 * @return Set of roster entries. If no connection is established for the object ID, the collection is empty
	 * (not null). 
	 */
	public Set<String> getRosterEntriesForObject(String objectId){
		
		if (objectId == null){
			logger.warning("Error when retrieving contact list. Object ID is null.");
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(objectId);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + objectId + "'.");
			return Collections.emptySet();
		}
		
		Set<String> entries = descriptor.getRoster();
		
		if (entries == null) {
			return null;
		}
		
		// log it
		logger.finest("-- Roster for '" + objectId +"' --");
		for (String entry : entries) {
			logger.finest(entry + " Presence: " + "UNKNOWN");
		}
		logger.finest("-- End of roster --");
		
		return entries;
	}
	
	
	
	
	
	
	// EXPOSING INTERFACE
	
	
	/**
	 * Activates the event channel identified by the eventID. From the moment of activation, other devices in the 
	 * network will be able to subscribe to it and will receive events in case they are generated. 
	 * 
	 * If the event channel was never activated before (or there is other reason why it was not saved previously), it 
	 * gets created anew. In that case, the list of subscribers is empty.  
	 * 
	 * NOTE: This is the equivalent of POST /events/[eid] in the REST API.
	 * 
	 * @param sourceOid Object ID of the event channel owner.
	 * @param eventId Event ID.
	 * @return {@link StatusMessage StatusMessage} with error flag set to false, if the event channel was activated
	 * successfully.
	 */
	public StatusMessage activateEventChannel(String sourceOid, String eventId, Map<String, String> parameters, 
				String body) {
		
		if (sourceOid == null){
			logger.warning("Error when activating event channel. Source object ID is null.");
			
			return null;
		}
		
		if (eventId == null){
			logger.warning("Error when activating event channel. The event ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		// check the validity of the calling object
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			return null;
		}
		
		return descriptor.setLocalEventChannelStatus(eventId, true, parameters, body);
	}
	
	
	
	// TODO documentation
	// returns number of sent messages vs the number of subscribers
	public StatusMessage sendEventToSubscribedObjects(String sourceOid, String eventId, String body, 
			Map<String, String> parameters) {
		
		if (sourceOid == null){
			logger.warning("Error when sending event to subscribers. Source object ID is null.");
			
			return null;
		}
		
		if (eventId == null){
			logger.warning("Error when sending event to subscribers. The event ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}

		// check the validity of the calling object
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			return null;
		}
		
		return descriptor.sendEventToSubscribers(eventId, body, parameters);
		
	}
	
	
	
	/**
	 * De-activates the event channel identified by the eventID. From the moment of de-activation, other devices in the
	 * network will not be able to subscribe to it. Also no events are sent in case they are generated. 
	 * 
	 * The channel will still exist though along with the list of subscribers. If it gets re-activated, the list of
	 * subscribers will be the same as in the moment of de-activation.  
	 * 
	 * NOTE: This is the equivalent of DELETE /events/[eid] in the REST API.
	 * 
	 * @param objectId Object ID of the event channel owner
	 * @param eventID Event ID.
	 * @return {@link StatusMessage StatusMessage} with error flag set to false, if the event channel was activated
	 * successfully.
	 */
	public StatusMessage deactivateEventChannel(String sourceOid, String eventId, Map<String, String> parameters, 
				String body) {
		
		if (sourceOid == null){
			logger.warning("Error when deactivating event channel. Source object ID is null.");
			
			return null;
		}
		
		if (eventId == null){
			logger.warning("Error when deactivating event channel. The event ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		// check the validity of the calling object
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			return null;
		}
		
		return descriptor.setLocalEventChannelStatus(eventId, false, parameters, body);
	}
	
	
	
	// TODO documentation
	public StatusMessage getEventChannelStatus(String sourceOid, String destinationOid, String eventId, 
			Map<String, String> parameters, String body) {
		
		if (sourceOid == null){
			logger.warning("Error when retrieving event channel status. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when retrieving event channel status. Destination object ID is null.");
			
			return null;
		}
		
		if (eventId == null){
			logger.warning("Error when retrieving event channel status. The event ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.getEventChannelStatus(destinationOid, eventId, parameters, body);
	}
	
	
	
	// TODO documentation
	public StatusMessage subscribeToEventChannel(String sourceOid, String destinationOid, String eventId, 
			Map<String, String> parameters, String body) {
		
		if (sourceOid == null){
			logger.warning("Error when subscribing to an event channel. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when subscribing to an event channel. Destination object ID is null.");
			
			return null;
		}
		
		if (eventId == null){
			logger.warning("Error when subscribing to an event channel. The event ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.subscribeToEventChannel(destinationOid, eventId, parameters, body);
	}
	
	
	// TODO documentation
	public StatusMessage unsubscribeFromEventChannel(String sourceOid, String destinationOid, String eventId, 
			Map<String, String> parameters, String body) {
		
		if (sourceOid == null){
			logger.warning("Error when unsubscribing from an event channel. Source object ID is null.");
			
			return null;
		}
		
		if (destinationOid == null){
			logger.warning("Error when unsubscribing from an event channel. Destination object ID is null.");
			
			return null;
		}
		
		if (eventId == null){
			logger.warning("Error when unsubscribing from an event channel. The event ID is null. "
					+ "Source object: '" + sourceOid + "'.");
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceOid);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceOid + "'.");
			
			return null;
		} 
		
		return descriptor.unsubscribeFromEventChannel(destinationOid, eventId, parameters, body);
		
	}

	
	
	// REGISTRY INTERFACE
	
	
	
	
	
	
	
	// QUERY INTERFACE
	
	public String performSparqlSearch(String sourceObjectId, String sparqlQuery, Map<String, String> parameters) {
		
		if (sourceObjectId == null || sourceObjectId.isEmpty() || sparqlQuery == null || sparqlQuery.isEmpty()) {
			logger.warning("Method parameters can't be null nor empty.");
			
			return null;
		}
		
		ConnectionDescriptor descriptor = descriptorPoolGet(sourceObjectId);
		
		if (descriptor == null){
			logger.warning("Null record in the connection descriptor pool. Object ID: '" + sourceObjectId + "'.");
			
			return null;
		} 
		
		return descriptor.performSparqlQuery(sparqlQuery, parameters);
	}
	
	
	
	
	/* === PRIVATE METHODS === */
	
	/**
	 * Thread-safe method for inserting an object ID (K) and a descriptor (V) into the descriptor pool. This is a
	 * synchronised equivalent for {@link java.util.HashMap#put(Object, Object) put()} method of HashMap table.
	 * 
	 *    IMPORTANT: It is imperative to use only this method to interact with the descriptor pool when adding
	 *    or modifying functionality of this class and avoid the original HashMap's
	 *    {@link java.util.HashMap#put(Object, Object) put()} method. 
	 *    
	 * @param objectId The key part of the {@link java.util.HashMap HashMap} key-value pair in the descriptor pool.
	 * @param descriptor The value part of the {@link java.util.HashMap HashMap} key-value pair in the descriptor pool.
	 * @return The previous value associated with key, or null if there was no mapping for key. 
	 * (A null return can also indicate that the map previously associated null with key, if the implementation 
	 * supports null values.)
	 */
	private ConnectionDescriptor descriptorPoolPut(String objectId, ConnectionDescriptor descriptor){
		synchronized (descriptorPool){
			return descriptorPool.put(objectId, descriptor);
		}
	}
	
	
	/**
	 * Thread-safe method for retrieving a connection descriptor (V) from the descriptor pool by object ID (K). 
	 * This is a synchronised equivalent for {@link java.util.HashMap#get(Object) get()} method of HashMap table.
	 * 
	 *    IMPORTANT: It is imperative to use only this method to interact with the descriptor pool when adding
	 *    or modifying functionality of this class and avoid the original HashMap's
	 *    {@link java.util.HashMap#get(Object) get()} method. 
	 *    
	 * @param objectId The key part of the {@link java.util.HashMap HashMap} key-value pair in the descriptor pool.
	 * @return The value to which the specified key is mapped, or null if this map contains no mapping for the key.
	 */
	private ConnectionDescriptor descriptorPoolGet(String objectId){
		synchronized (descriptorPool){
			
			return descriptorPool.get(objectId);
		}
	}
	
	
	/**
	 * Thread-safe method for removing a connection descriptor (V) for the object ID (K) from the descriptor pool. 
	 * This is a synchronised equivalent for {@link java.util.HashMap#remove(Object) remove()} method of HashMap table.
	 * 
	 *    IMPORTANT: It is imperative to use only this method to interact with the descriptor pool when adding
	 *    or modifying functionality of this class and avoid the original HashMap's
	 *    {@link java.util.HashMap#remove(Object) remove()} method.
	 *    
	 * @param objectId The key part of the {@link java.util.HashMap HashMap} key-value pair in the descriptor pool.
	 * @return The previous value associated with key, or null if there was no mapping for key.
	 */
	private ConnectionDescriptor descriptorPoolRemove(String objectId){
		synchronized (descriptorPool){
			
			return descriptorPool.remove(objectId);
		}
	}
	
	
	/**
	 * Thread-safe method for clearing the descriptor pool. This is a synchronised equivalent for 
	 * {@link java.util.HashMap#clear() clear()} method of HashMap table.
	 * 
	 *    IMPORTANT: It is imperative to use only this method to interact with the descriptor pool when adding
	 *    or modifying functionality of this class and avoid the original HashMap's
	 *    {@link java.util.HashMap#clear() clear()} method. 
	 */
	private void descriptorPoolClear(){
		synchronized (descriptorPool){
			descriptorPool.clear();
		}
	}
	
	// this will save us a lot of cycles - it gets checked too often
	private void translateSessionRecoveryConf(String recoveryConfigString) {
		
		switch (recoveryConfigString) {
		case SESSIONRECOVERYPOLICY_STRING_PASSIVE:
			sessionRecoveryPolicy = SESSIONRECOVERYPOLICY_INT_PASSIVE;
			break;
			
		case SESSIONRECOVERYPOLICY_STRING_NONE:
			sessionRecoveryPolicy = SESSIONRECOVERYPOLICY_INT_NONE;
			break;
			
		case SESSIONRECOVERYPOLICY_STRING_PROACTIVE:
			sessionRecoveryPolicy = SESSIONRECOVERYPOLICY_INT_PROACTIVE;
			break;
			
			default:
				sessionRecoveryPolicy = SESSIONRECOVERYPOLICY_INT_ERROR;
				break;
				
		}
	}
	
	
	private void recoverSessions() {
		
		Set<String> connectionList = getConnectionList();
		ConnectionDescriptor descriptor;
		
		// remember we have to use our own methods (descriptorPoolGet etc) to access the hash map in a thread safe manner
		for (String oid : connectionList) {
			descriptor = descriptorPoolGet(oid);
			
			if (descriptor != null) {
				if (sessionRecoveryPolicy == SESSIONRECOVERYPOLICY_INT_PROACTIVE) {
					if (!descriptor.isConnected()) {
						logger.warning("Connection for " + descriptor.getObjectId() + " was interrupted. Reconnecting.");
						
						descriptor.connect();
					}
				}
				
				if (sessionRecoveryPolicy == SESSIONRECOVERYPOLICY_INT_PASSIVE) {
					
					// if the descriptor is connected but the connection timer is expired, disconnect
					if(descriptor.isConnected() 
							&& (System.currentTimeMillis() - descriptor.getLastConnectionTimerReset()) > sessionExpiration) {
						
						logger.warning("Session expired for object ID " + descriptor.getObjectId() + ". Disconnecting.");
						descriptor.disconnect();
						
					}
				}	
			}
		}
		
		
	}
}
