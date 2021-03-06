package eu.bavenir.ogwapi.restapi;

import java.util.logging.Logger;

import org.apache.commons.configuration2.XMLConfiguration;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.ChallengeScheme;
import org.restlet.routing.Router;
import org.restlet.security.ChallengeAuthenticator;

import eu.bavenir.ogwapi.restapi.security.AuthenticationVerifier;
import eu.bavenir.ogwapi.restapi.services.AgentsAgidObjects;
import eu.bavenir.ogwapi.restapi.services.AgentsAgidObjectsDelete;
import eu.bavenir.ogwapi.restapi.services.AgentsAgidObjectsUpdate;
import eu.bavenir.ogwapi.restapi.services.EventsEid;
import eu.bavenir.ogwapi.restapi.services.Objects;
import eu.bavenir.ogwapi.restapi.services.ObjectsLogin;
import eu.bavenir.ogwapi.restapi.services.ObjectsLogout;
import eu.bavenir.ogwapi.restapi.services.ObjectsOid;
import eu.bavenir.ogwapi.restapi.services.ObjectsOidActions;
import eu.bavenir.ogwapi.restapi.services.ObjectsOidActionsAid;
import eu.bavenir.ogwapi.restapi.services.ObjectsOidActionsAidTasksTid;
import eu.bavenir.ogwapi.restapi.services.ObjectsOidEvents;
import eu.bavenir.ogwapi.restapi.services.ObjectsOidEventsEid;
import eu.bavenir.ogwapi.restapi.services.ObjectsOidProperties;
import eu.bavenir.ogwapi.restapi.services.ObjectsOidPropertiesPid;
import eu.bavenir.ogwapi.restapi.services.SecurityPrivacy;
import eu.bavenir.ogwapi.restapi.services.SecurityContracts;
import eu.bavenir.ogwapi.restapi.services.DiscoveryPartners;
import eu.bavenir.ogwapi.restapi.services.DiscoveryPartnersCid;
import eu.bavenir.ogwapi.restapi.services.ObjectsCid;
import eu.bavenir.ogwapi.restapi.services.DiscoveryCommunities;
import eu.bavenir.ogwapi.restapi.services.DiscoveryOrganisationNodes;
import eu.bavenir.ogwapi.restapi.services.DiscoveryOrganisationNodesCid;
import eu.bavenir.ogwapi.restapi.services.DiscoveryCommunityNodesCommid;
import eu.bavenir.ogwapi.restapi.services.DiscoveryOrganisationItems;
import eu.bavenir.ogwapi.restapi.services.DiscoveryContractItemsCtid;
import eu.bavenir.ogwapi.commons.CommunicationManager;
import eu.bavenir.ogwapi.commons.monitoring.MessageCounter;


/*
 * STRUCTURE:
 * - constants
 * - fields
 * - public methods
 * - private methods
 */

/**
 * RESTLET application that serves incoming calls for the Gateway API. After being instantialised, it initialises
 * objects necessary to be available to all API services (like Communication Node). It routes the requests to their 
 * respective {@link org.restlet.resource.ServerResource Resources}. The HTTP authentication against Gateway API is 
 * also checked in the {@link eu.bavenir.ogwapi.restapi.security.AuthenticationVerifier AuthenticationVerifier}.
 * 
 *   
 * @author sulfo
 *
 */
public class Api extends Application {
	
	/* === CONSTANTS === */
	
	/**
	 * Contextual name of the {@link org.apache.commons.configuration2.XMLConfiguration configuration} object inserted
	 * into the context.
	 */
	public static final String CONTEXT_CONFIG = "config";
	
	/**
	 * Contextual name of the {@link java.util.logging.Logger logger} object inserted into the context. 
	 */
	public static final String CONTEXT_LOGGER = "logger";
	
	/**
	 * Contextual name of the {@link eu.bavenir.ogwapi.commons.CommunicationManager CommunicationManager}, object
	 * inserted into the context.
	 */
	public static final String CONTEXT_COMMMANAGER = "communicationManager";
	
	/**
	 * Name of the configuration parameter for setting the realm of RESTLET BEARER authentication method. 
	 */
	private static final String CONF_PARAM_AUTHREALM = "api.authRealm";
	
	/**
	 * Name of the configuration parameter for setting the authentication method.
	 */
	private static final String CONF_PARAM_AUTHMETHOD = "api.authMetod";
	
	/**
	 * Default value for setting the realm of RESTLET BEARER authentication schema.
	 */
	private static final String CONF_DEF_AUTHREALM = "bavenir.eu";
	
	/**
	 * Default value for setting the authentication method.
	 */
	private static final String CONF_DEF_AUTHMETHOD = "basic";
	
	
	
	
	/* === FIELDS === */
	
	// obligatory stuff
	private XMLConfiguration config;
	private Logger logger;
	
	// communication node
	private CommunicationManager communicationManager;
	
	// application context
	private Context applicationContext;
	
	// whether to use authentication or not - not using authentication is considered to be for debugging purposes only
	// as the credentials for XMPP network need to be hard coded in such case
	private boolean useAuthentication;
	
	// challenge scheme to use 
	private ChallengeScheme challengeScheme;
	
	
	/* === PUBLIC METHODS === */
	
	/**
	 * Constructor, initialises necessary objects and inserts the {@link org.apache.commons.configuration2.XMLConfiguration
	 * configuration}, {@link java.util.logging.Logger logger} and {@link eu.bavenir.ogwapi.xmpp.CommunicationManager
	 * CommunicationNode} into the RESTLET {@link org.restlet.Context context}.
	 * 
	 * All parameters are mandatory, failure to include them will lead to a swift end of application execution.
	 * 
	 * @param config Configuration object.
	 * @param logger Java logger.
	 */
	public Api(XMLConfiguration config, Logger logger, MessageCounter messageCounter){
		this.config = config;
		this.logger = logger;
		
		// this will initialise the CommunicationNode
		communicationManager = new CommunicationManager(config, logger, messageCounter);
		
		// insert stuff into context
		applicationContext = new Context();
		
		applicationContext.getAttributes().put(CONTEXT_CONFIG, config);
		applicationContext.getAttributes().put(CONTEXT_LOGGER, logger);
		applicationContext.getAttributes().put(CONTEXT_COMMMANAGER, communicationManager);
		
		applicationContext.setLogger(logger);
		
		setContext(applicationContext);
		
		// load authentication challenge scheme method from configuration
		configureChallengeScheme();
	}
	
	
	/**
	 * Creates a root RESTLET that will receive all incoming calls.
	 */
	@Override
	public synchronized Restlet createInboundRoot() {
		
		
		// create a router Restlet that routes each call to a new instance of 
		Router router = new Router(getContext());

		// define routes
		
		// AUTHENTICATION
		router.attach("/objects/login", ObjectsLogin.class);
		router.attach("/objects/logout", ObjectsLogout.class);
		
		
		// CONSUMPTION
		// get
		router.attach("/objects/{oid}/properties", ObjectsOidProperties.class);
		// get, put
		router.attach("/objects/{oid}/properties/{pid}", ObjectsOidPropertiesPid.class);
		router.attach("/objects/{oid}/actions", ObjectsOidActions.class);
		router.attach("/objects/{oid}/actions/{aid}", ObjectsOidActionsAid.class);
		router.attach("/objects/{oid}/actions/{aid}/tasks/{tid}", ObjectsOidActionsAidTasksTid.class);
		
		
		// EXPOSING
		router.attach("/objects/{oid}/events", ObjectsOidEvents.class);
		router.attach("/objects/{oid}/events/{eid}", ObjectsOidEventsEid.class);
		router.attach("/events/{eid}", EventsEid.class);
		
		
		// DISCOVERY
		// get
		router.attach("/objects", Objects.class); // Get all objects agent can see (only OIDs)
		// post
		router.attach("/objects/{oid}", ObjectsOid.class); // Send remote req for thing desc (Use this for discovery) (Re-route to fetch td from agent)
		// get, post
		router.attach("/agents/{agid}/objects", AgentsAgidObjects.class); // Get all object under agent (OIDs and TDs) (!!! REMOVE IF REDUNDANT)
		// get 
		router.attach("/agents/cid/{reqid}", ObjectsCid.class); // Get organisation ID of a given infrastructure item (devices, service, node)
		// get
		router.attach("/agents/partners", DiscoveryPartners.class); // Get all my organisation partners
		// get
		router.attach("/agents/partner/{cid}", DiscoveryPartnersCid.class); // Get information about a partner organisation
		// get
		router.attach("/agents/communities", DiscoveryCommunities.class); // Get communities where my node participates
		// get
		router.attach("/discovery/nodes/organisation", DiscoveryOrganisationNodes.class); // Get all nodes in my organisaition
		// get
		router.attach("/discovery/nodes/organisation/{cid}", DiscoveryOrganisationNodesCid.class); // Get all nodes I can see from other partner organisation
		// get
		router.attach("/discovery/nodes/community/{commid}", DiscoveryCommunityNodesCommid.class); // Get all nodes in a community
		// get
		router.attach("/discovery/items/organisation", DiscoveryOrganisationItems.class); // Get all my organisation items
		// get
		router.attach("/discovery/items/contract/{ctid}", DiscoveryContractItemsCtid.class); // Get all items in a contract


		// REGISTRY
		// post, put
		router.attach("/agents/{agid}/objects", AgentsAgidObjects.class);
		// put
		router.attach("/agents/{agid}/objects/update", AgentsAgidObjectsUpdate.class);
		// post
		router.attach("/agents/{agid}/objects/delete", AgentsAgidObjectsDelete.class);
		
		// SECURITY
		// get
		router.attach("/security/privacy", SecurityPrivacy.class); // Get privacy of NODE objects
		// get
		router.attach("/security/contracts/{cid}", SecurityContracts.class); // Get contract info with some organisation

		// solve the question of API authentication
		if (useAuthentication){
			// create authenticator
			ChallengeAuthenticator authenticator = createAuthenticator();
			
			// enable authentication
			authenticator.setNext(router);
			return authenticator;
		} 
		
		return router;
	}

	
	/* === PRIVATE METHODS === */
	
	/**
	 * Returns an instance of ChallengeAuthenticator, that is able to authenticate requests against the XMPP network.
	 * 
	 * @return A custom ChallengeAuthenticator.
	 */
	private ChallengeAuthenticator createAuthenticator() {
		
		String realm = config.getString(CONF_PARAM_AUTHREALM, CONF_DEF_AUTHREALM);
		
		logger.config("Authentication realm: " + realm);
		
		AuthenticationVerifier authVerifier = new AuthenticationVerifier(communicationManager, logger);

		ChallengeAuthenticator auth = new ChallengeAuthenticator(
								applicationContext, false, challengeScheme, realm, authVerifier);
		
		return auth;
    }

	
	
	/**
	 * Translates the authentication method string from configuration file into RESTLET readable authentication
	 * scheme. If the string is not recognised as valid scheme, or if the configuration string says "none", null
	 * is returned. 
	 * 
	 * @return RESTLET challenge scheme.
	 */
	private void configureChallengeScheme(){
		
		String challengeScheme = config.getString(CONF_PARAM_AUTHMETHOD, CONF_DEF_AUTHMETHOD);
		
		useAuthentication = true;
		
		switch (challengeScheme){
		case "basic":
			logger.config("HTTP Basic challenge authentication scheme configured.");
			this.challengeScheme = ChallengeScheme.HTTP_BASIC;
			break;
			
		case "digest":
			logger.config("HTTP Digest challenge authentication scheme configured.");
			this.challengeScheme = ChallengeScheme.HTTP_DIGEST;
			break;
			
		case "bearer":
			logger.config("HTTP Bearer challenge authentication scheme configured.");
			this.challengeScheme = ChallengeScheme.HTTP_OAUTH_BEARER;
			break;
			
		case "none":
			logger.config("No authentication for API is configured.");
			// this will disable the check for authentication method, otherwise exception is to be expected - that is
			// how the program treats invalid authentication method 
			useAuthentication = false;
			this.challengeScheme = null;
			break;
			
			default:
				logger.warning("Invalid API authentication scheme, reverting to basic.");
				this.challengeScheme = ChallengeScheme.HTTP_BASIC;
		}
	}
}
