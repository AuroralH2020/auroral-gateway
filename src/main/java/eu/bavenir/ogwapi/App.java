package eu.bavenir.ogwapi;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import com.mashape.unirest.http.Unirest;

import eu.bavenir.ogwapi.commons.monitoring.MessageCounter;
import eu.bavenir.ogwapi.restapi.RestletThread;
import eu.bavenir.ogwapi.commons.connectors.NeighbourhoodManagerConnector;

/**
 * Main class of the Gateway API program. Loads configuration, initialises
 * logger and runs the threads. It also waits for signal from the OS to run its
 * shutdown hook and perform a cleanup.
 * 
 * @author sulfo
 *
 */
public class App {

	/* === CONSTANTS === */

	/**
	 * Path to configuration file.
	 */
	private static final String CONFIG_PATH = "config/GatewayConfig.xml";

	/**
	 * Name of configuration parameter for switching the additional logging of
	 * events to console (aside from logging them into the log file) on or off.
	 */
	private static final String CONFIG_PARAM_LOGGINGCONSOLEOUTPUT = "logging.consoleOutput";

	/**
	 * Name of the configuration parameter for path to log file.
	 */
	private static final String CONFIG_PARAM_LOGGINGFILE = "logging.file";

	/**
	 * Name of the configuration parameter for logging level.
	 */
	private static final String CONFIG_PARAM_LOGGINGLEVEL = "logging.level";

	/**
	 * Name of the configuration parameter for enabling logging rotation.
	 */
	private static final String CONFIG_PARAM_LOGGINGROTATION = "logging.logRotation";

	/**
	 * Name of the configuration parameter for size of the logging files.
	 */
	private static final String CONFIG_PARAM_LOGGINGSIZE = "logging.maxLogSize";

	/**
	 * Name of the configuration parameter for max number of log files to keep.
	 */
	private static final String CONFIG_PARAM_LOGGINGMAXFILES = "logging.maxNumFiles";

	/**
	 * Default value of {@link #CONFIG_PARAM_LOGGINGCONSOLEOUTPUT
	 * CONFIG_PARAM_LOGGINGCONSOLEOUTPUT} configuration parameter. This value is
	 * taken into account when no suitable value is found in the configuration file.
	 */
	private static final Boolean CONFIG_DEF_LOGGINGCONSOLEOUTPUT = false;

	/**
	 * Default value of {@link #CONFIG_PARAM_LOGGINGLEVEL CONFIG_PARAM_LOGGINGLEVEL}
	 * configuration parameter. This value is taken into account when no suitable
	 * value is found in the configuration file.
	 */
	private static final String CONFIG_DEF_LOGGINGLEVEL = "INFO";

	/**
	 * Default value of {@link #CONFIG_PARAM_LOGGINGROTATION} configuration parameter. 
	 * This value is taken into account when no suitable value is found in the configuration file.
	 */
	private static final Boolean CONFIG_DEF_LOGGINGROTATION = true;

	/**
	 * Default value of {@link #CONFIG_PARAM_LOGGINGSIZE} configuration parameter. 
	 * This value is taken into account when no suitable value is found in the configuration file.
	 */
	private static final Integer CONFIG_DEF_LOGGINGSIZE = 10485760;

	/**
	 * Default value of {@link #CONFIG_PARAM_LOGGINGMAXFILES} configuration parameter. 
	 * This value is taken into account when no suitable value is found in the configuration file.
	 */
	private static final Integer CONFIG_DEF_LOGGINGMAXFILES = 7;

	/**
	 * Error message for configuration loading failure.
	 */
	private static final String ERR_CONF = "Failure during loading the configuration. Does the config file exist?";

	/**
	 * Error message for failure during the logging file creation.
	 */
	private static final String ERR_LOGGING = "The log file could not be created. Please check the write permissions "
			+ "for given directory.";

	/**
	 * Error message for initialisation failure.
	 */
	private static final String ERR_INIT = "Initialization failed. See standard error stream.";

	/**
	 * Thread sleep time in milliseconds, used while waiting for signal.
	 */
	private static final long THREAD_SLEEP = 100;

	/**
	 * Name of the configuration parameter for keys path.
	 */
	private static final String CONFIG_PARAM_PATH = "platformSecurity.path";

	/**
	 * Default value for {@link #CONFIG_PARAM_PATH } parameter.
	 */
	private static final String CONFIG_DEF_PATH = "keystore/";

	/* === OTHER FIELDS === */

	// logging
	private static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	private static FileHandler logfileTxt;
	private static SimpleFormatter formatterTxt;

	// Apache commons configuration
	private static Configurations configurations;
	private static XMLConfiguration config;

	// executing threads
	private static RestletThread restletThread;

	// Classes started on-init
	private static MessageCounter messageCounter;
	private static NeighbourhoodManagerConnector nmConnector;

	// Security token temporary path
	private static String path;

	/* === METHODS === */

	/**
	 * Initialisation method called during application startup. Loads program
	 * configuration and sets logging facilities.
	 * 
	 * @return True if initialisation is successful. False otherwise.
	 */
	private static boolean initialize(final String configPath) {

		// === load the configuration file ===
		configurations = new Configurations();

		try {

			if (configPath != null) {

				System.out.println("Attempt to load configuration file from path: " + configPath);
				config = configurations.xml(configPath);

			} else {

				System.out.println("Attempt to load configuration file from path: " + CONFIG_PATH);
				config = configurations.xml(CONFIG_PATH);
			}

		} catch (final ConfigurationException e) {

			e.printStackTrace();
			System.err.println(ERR_CONF);
			return false;
		}

		// === set up the logger ===

		// set LOGGER logging level
		final String confLoggingLevel = config.getString(CONFIG_PARAM_LOGGINGLEVEL, CONFIG_DEF_LOGGINGLEVEL);
		final Level logLevel = translateLoggingLevel(confLoggingLevel);
		logger.setLevel(logLevel);

		// get whether the logger should log to console
		final Boolean confLoggingConsoleOutput = config.getBoolean(CONFIG_PARAM_LOGGINGCONSOLEOUTPUT,
				CONFIG_DEF_LOGGINGCONSOLEOUTPUT);

		final Logger rootLogger = Logger.getLogger("");
		final Handler[] handlers = rootLogger.getHandlers();
		if (handlers[0] instanceof ConsoleHandler) {
			if (confLoggingConsoleOutput == false) {
				// suppress the logging output to the console if set so
				rootLogger.removeHandler(handlers[0]);
			} else {
				// otherwise set the log level
				handlers[0].setLevel(logLevel);
			}
		}

		// log file - if set
		final String confLoggingFile = config.getString(CONFIG_PARAM_LOGGINGFILE);
		if (confLoggingFile != null && !confLoggingFile.isEmpty()) {

			// Check if log rotation is activated
			final Boolean logRotation = config.getBoolean(CONFIG_PARAM_LOGGINGROTATION, CONFIG_DEF_LOGGINGROTATION);
			final Integer logSize = config.getInt(CONFIG_PARAM_LOGGINGSIZE, CONFIG_DEF_LOGGINGSIZE);
			final Integer logMaxFiles = config.getInt(CONFIG_PARAM_LOGGINGMAXFILES, CONFIG_DEF_LOGGINGMAXFILES);

			// create the filename string (essentially adding the time stamp to the string)
			final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yy-MM-dd'T'HH_mm_ss.SSS");
			final String logFileName = String.format(confLoggingFile, LocalDateTime.now().format(formatter));

			// this is for the application itself - the RESTLET needs to be taken care of
			// later
			try {
				if (logRotation) {
					logfileTxt = new FileHandler(logFileName, logSize, logMaxFiles);
				} else {
					logfileTxt = new FileHandler(logFileName);
				}
			} catch (SecurityException | IOException e) {
				e.printStackTrace();
				System.err.println(ERR_LOGGING);
				return false;
			}
			formatterTxt = new SimpleFormatter();

			// put it together
			logfileTxt.setFormatter(formatterTxt);
			logfileTxt.setLevel(logLevel);
			logger.addHandler(logfileTxt);

			// now set the same log file for RESTLET - no kidding, this is from their site
			System.setProperty("java.util.logging.config.file", logFileName);

			// log message
			logger.config("Log file: " + logFileName);

		}

		// Generate the JWT and perform the handshake with the NM
		nmConnector = new NeighbourhoodManagerConnector(config, logger);
		nmConnector.handshake();

		// Initialize counters
		messageCounter = new MessageCounter(config, logger);

		// === set up the API thread ===
		restletThread = new RestletThread(config, logger, messageCounter);

		return true;
	}

	/**
	 * Translates the string value of logging level configuration parameter to
	 * {@link java.util.logging.Level Level} object, that can be fed to
	 * {@link java.util.logging.logger Logger}. If the stringValue is null, it will
	 * return the default logging level set by {@link #CONFIG_DEF_LOGGINGLEVEL
	 * CONFIG_DEF_LOGGINGLEVEL} constant. If the string contains other unexpected
	 * value (worst case) returns {@link java.util.logging.level#INFO INFO}.
	 * 
	 * @param stringValue String value of the configuration parameter.
	 * @return String translated into {@link java.util.logging.level Level} object.
	 */
	private static Level translateLoggingLevel(String stringValue) {

		if (stringValue == null) {
			stringValue = CONFIG_DEF_LOGGINGLEVEL;
		}

		switch (stringValue) {

			case "OFF":
				return Level.OFF;

			case "SEVERE":
				return Level.SEVERE;

			case "WARNING":
				return Level.WARNING;

			case "INFO":
				return Level.INFO;

			case "CONFIG":
				return Level.CONFIG;

			case "FINE":
				return Level.FINE;

			case "FINER":
				return Level.FINER;

			case "FINEST":
				return Level.FINEST;

			default:
				return Level.INFO;
		}
	}

	/**
	 * Main method of the Vicinity Gateway application. In starts the thread and
	 * registers a shutdown hook that waits for OS signal to terminate.
	 * 
	 * @param args
	 */
	public static void main(final String[] args) {

		// config path
		String confPath = null;
		// load args
		if (args.length > 0) {

			for (int i = 0; i < args.length; i++) {
				if (args[i].equalsIgnoreCase("-C")) {

					if (args.length <= i + 1) {
						System.out.println("You have to put the value (path) after -c option!");
						System.exit(1);
					}
					confPath = args[++i];
				} else if (args[i].equalsIgnoreCase("-H")) {

					System.out.println("-c pathToTheConfigurationFile");
					System.exit(1);
				} else {
					System.out.println("Wrong Argument : " + args[i] + " :: -h for Help.");
					System.exit(1);
				}
			}
		}

		// attempt to initialise
		if (!initialize(confPath)) {
			System.out.println(ERR_INIT);
			System.exit(1);
		}

		// log message
		logger.info("Vicinity Gateway API initialized.");

		// start threads
		restletThread.start();

		// log message
		logger.fine("API thread started.");

		// register a shutdown hook - this will be executed after catching a signal to
		// terminate
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {

				try {
					// only system.out style logging can be executed at this phase
					// (but there's nothing too much interesting anyway...)
					System.out.println(
							"AURORAL Gateway API: Shutdown hook run, terminating threads and storing counters.");

					// Save counters
					messageCounter.saveCounters();

					// Remove token file
					nmConnector.byebye();

					// Pause waiting for AURORAL Agent to terminate item logouts
					restletThread.pauseThread(10000);

					// Terminate threads
					restletThread.terminateThread();
					restletThread.join();

					// close the Unirest
					Unirest.shutdown();

				} catch (final InterruptedException e) {
					// nothing else to do
					e.printStackTrace();
				} catch (final IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});

		while (true) {
			try {
				Thread.sleep(THREAD_SLEEP);
			} catch (final InterruptedException e) {
				// nothing else to do
				e.printStackTrace();
			}
		}

	}
}