package org.sakaiproject.portal.chat.entity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.sakaiproject.component.api.ComponentManager;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.entitybroker.DeveloperHelperService;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.EntityProvider;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Createable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Describeable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Inputable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Outputable;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.exception.EntityException;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.portal.api.PortalChatPermittedHelper;
import org.sakaiproject.presence.api.PresenceService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;

/**
 * Provides all the RESTful targets for the portal chat code in chat.js. Clustering
 * is catered for using a JGroups channel.
 *
 * @author Adrian Fish (a.fish@lancaster.ac.uk)
 */
public final class PCServiceEntityProvider extends AbstractEntityProvider implements Receiver, EntityProvider, Createable, Inputable, Outputable, ActionsExecutable, AutoRegisterEntityProvider, Describeable {

	protected final Logger logger = Logger.getLogger(getClass());

	private final static ResourceLoader rb = new ResourceLoader("portal-chat");

	private final static String WEBRTC_SERVER_REGEX = "^(turn|stun):(([^:]*):([^@]*)@){0,1}([^:@]*(:[0-9]{1,5}){0,1})$";
	
	public final static String ENTITY_PREFIX = "portal-chat";
	
    /* SAK-20565. Gets set to false if Profile2 isn't available */
    private boolean connectionsAvailable = true;
    
    /* Setting used to configure if site users should be available in the chat. */
    private boolean showSiteUsers = true;
    
    private int pollInterval = 5000;

    private boolean isVideoEnabled = false;

    private final List<PortalVideoServer> iceServers = new ArrayList<PortalVideoServer>();

    /* SAK-20565. We now use reflection to call the profile connection methods */
    private Object profileServiceObject = null;
    private Method getConnectionsForUserMethod = null;
    private Method getUuidMethod = null;
    private Method setProfileMethod = null;
    private Method setPrivacyMethod = null;
    private Method setPreferencesMethod = null;

    private PortalChatPermittedHelper portalChatPermittedHelper;
	public void setPortalChatPermittedHelper(PortalChatPermittedHelper portalChatPermittedHelper) {
		this.portalChatPermittedHelper = portalChatPermittedHelper;
	}

	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}
	
	private EmailService emailService;
	public void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}

	private PresenceService presenceService;
	public void setPresenceService(PresenceService presenceService) {
		this.presenceService = presenceService;
	}
	
	private ServerConfigurationService serverConfigurationService;
	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}
	
	private DeveloperHelperService developerService = null;
	public void setDeveloperService(DeveloperHelperService developerService) {
		this.developerService = developerService;
	}
	
	/* A mapping of a list of messages onto the user id they are intended for */
	private final Map<String, List<UserMessage>> messageMap = new HashMap<String,List<UserMessage>>();
	
    /*
     *  A mapping of timestamps onto the user id that sent the heartbeat. The initial capacity should be set
     *  to the number of app servers in your cluster times the max number of threads per app server. This is
     *  configurable in sakai.properties as portalchat.heartbeatmap.size.
     */
	private Map<String,UserMessage> heartbeatMap;

    /* JGroups channel for keeping the above maps in sync across nodes in a Sakai cluster */
    private Channel clusterChannel = null;
    private boolean clustered = false;

    private String portalUrl;

    private String service;

    private String serverName;

    public void init() {
    	
        service = serverConfigurationService.getString("ui.service","Sakai");

        portalUrl = serverConfigurationService.getServerUrl() + "/portal";

        serverName = serverConfigurationService.getServerName();
        
        pollInterval = serverConfigurationService.getInt("portal.chat.pollInterval", 5000);

        showSiteUsers = serverConfigurationService.getBoolean("portal.chat.showSiteUsers", true);
        
        isVideoEnabled = serverConfigurationService.getBoolean("portal.chat.video", false);

        if (isVideoEnabled) {
            String [] servers = serverConfigurationService.getStrings("portal.chat.video.servers");
            if (servers == null) {
                servers = new String[]{"stun:stun.l.google.com:19302"};
            }
            for (String server : servers) {
                iceServers.add(new PortalVideoServer(server));
            }
        }

        try {
            String channelId = serverConfigurationService.getString("portalchat.cluster.channel");
            if (channelId != null && !channelId.equals("")) {
            	// Pick up the config file from sakai home if it exists
            	File jgroupsConfig = new File(serverConfigurationService.getSakaiHomePath() + File.separator + "jgroups-config.xml");
            	if (jgroupsConfig.exists()) {
            		if (logger.isDebugEnabled()) {
            			logger.debug("Using jgroups config file: " + jgroupsConfig.getAbsolutePath());
            		}
            		clusterChannel = new JChannel(jgroupsConfig);
            	} else {
                    logger.debug("No jgroups config file. Using jgroup defaults.");
            		clusterChannel = new JChannel();
            	}
            	
            	if (logger.isDebugEnabled()) {
            		logger.debug("JGROUPS PROTOCOL: " + clusterChannel.getProtocolStack().printProtocolSpecAsXML());
            	}
            	
                clusterChannel.setReceiver(this);
                clusterChannel.connect(channelId);
                // We don't want a copy of our JGroups messages sent back to us
                clusterChannel.setDiscardOwnMessages(true);
                clustered = true;

                logger.info("Portal chat is connected on JGroups channel '" + channelId + "'"); 
            } else {
                logger.info("No 'portalchat.cluster.channel' specified in sakai.properties. JGroups will not be used and chat messages will not be replicated."); 
            }
        } catch (Exception e) {
            logger.error("Error creating JGroups channel. Chat messages will now NOT BE KEPT IN SYNC", e);
        }
        
        int heartbeatMapSize = serverConfigurationService.getInt("portalchat.heartbeatmap.size",1000);
        heartbeatMap = new ConcurrentHashMap<String,UserMessage>(heartbeatMapSize,0.75F,64);

        // SAK-20565. Get handles on the profile2 connections methods if available. If not, unset the connectionsAvailable flag.
        ComponentManager componentManager = org.sakaiproject.component.cover.ComponentManager.getInstance();
        profileServiceObject = componentManager.get("org.sakaiproject.profile2.service.ProfileService");
            
        if (profileServiceObject != null) {
            try {
                getConnectionsForUserMethod = profileServiceObject.getClass().getMethod("getConnectionsForUser",new Class[] {String.class});
                try {
                    Class personClass = Class.forName("org.sakaiproject.profile2.model.Person");
                    try {
                        getUuidMethod = personClass.getMethod("getUuid",null);
                    } catch (Exception e) {
                        logger.warn("Failed to set getUuidMethod");
                    }
                    try {
                        Class clazz = Class.forName("org.sakaiproject.profile2.model.UserProfile");
                        setProfileMethod = personClass.getMethod("setProfile",new Class[] {clazz});
                    } catch (Exception e) {
                        logger.warn("Failed to set setProfileMethod");
                    }
                    try {
                        Class clazz = Class.forName("org.sakaiproject.profile2.model.ProfilePrivacy");
                        setPrivacyMethod = personClass.getMethod("setPrivacy",new Class[] {clazz});
                    } catch (Exception e) {
                        logger.warn("Failed to set setPrivacyMethod");
                    }
                    try {
                        Class clazz = Class.forName("org.sakaiproject.profile2.model.ProfilePreferences");
                        setPreferencesMethod = personClass.getMethod("setPreferences",new Class[] {clazz});
                    } catch (Exception e) {
                        logger.warn("Failed to set setPreferencesMethod");
                    }
                } catch (Exception e) {
                    logger.error("Failed to find Person class. Connections will NOT be available in portal chat.",e);
                    connectionsAvailable = false;
                }
            } catch (Exception e) {
                logger.warn("Failed to set getConnectionsForUserMethod. Connections will NOT be available in portal chat.");
                connectionsAvailable = false;
            }
        } else {
            logger.warn("Failed to find ProfileService interface. Connections will NOT be available in portal chat.");
            connectionsAvailable = false;
        }
    }
    
    public void destroy() {
    	
    	logger.debug("DESTROY!!!!!");
    	
    	if (clusterChannel != null && clusterChannel.isConnected()) {
    		// This calls disconnect() first
    		clusterChannel.close();
    	}
    }

    /**
     * Uses reflection to call Profile2's connections method.
     *
     * @returns A list of Person instances cunningly disguised as lowly Objects
     */
    private List<Object> getConnectionsForUser(String uuid) {

        List<Object> connections = new ArrayList<Object>();

        if (connectionsAvailable == false) {
            return connections;
        }

        try {
            connections = (List<Object>) getConnectionsForUserMethod.invoke(profileServiceObject,new Object[] {uuid});
        } catch (Exception e) {
            logger.error("Failed to invoke the getConnectionsForUser method. Returning an empty connections list ...", e);
        }

        List<Object> connectionsWithPermissions = new ArrayList<Object>();
        for (Object personObject : connections) {

            String connectionUuid = null;

            try {
            	connectionUuid = (String) getUuidMethod.invoke(personObject,null);
                
                // Null all the person stuff to reduce the download size
                if (setProfileMethod != null) {
                    setProfileMethod.invoke(personObject,new Object[] {null});
                }
                if (setPrivacyMethod != null) {
                    setPrivacyMethod.invoke(personObject,new Object[] {null});
                }
                if (setPreferencesMethod != null) {
                    setPreferencesMethod.invoke(personObject,new Object[] {null});
                }
                
            } catch (Exception e) {
                logger.error("Failed to invoke getUuid on a Person instance. Skipping this person ...",e);
                continue;
            }

            // Only add the connection if that person is allowed to use portal chat.
            if (portalChatPermittedHelper.checkChatPermitted(connectionUuid)) {
            	connectionsWithPermissions.add(personObject);
            }
        }        
        return connectionsWithPermissions;
    }

	public String getEntityPrefix() {
		return ENTITY_PREFIX;
	}
	
	public String[] getHandledOutputFormats() {
	    return new String[] { Formats.TXT ,Formats.JSON};
	}

	public Object getSampleEntity() {
		return new UserMessage();
	}

    /**
     * New messages come in here. The recipient is indicated by the parameter 'to'.
     */
	public String createEntity(EntityReference ref, Object entity, Map<String, Object> params) {

		final User currentUser = userDirectoryService.getCurrentUser();
		final User anon = userDirectoryService.getAnonymousUser();
		
		if (anon.equals(currentUser)) {
			throw new SecurityException("You must be logged in to use this service");
		}
		
		final String to = (String) params.get("to");
		if (to == null) throw new IllegalArgumentException("You must supply a recipient");
		
		if (to.equals(currentUser.getId())) {
			throw new IllegalArgumentException("You can't chat with yourself");
		}
		
		final Date now = new Date();

		final UserMessage lastHeartbeat = heartbeatMap.get(to);
		
		if (lastHeartbeat == null) return "OFFLINE";
			
		if ((now.getTime() - lastHeartbeat.timestamp) >= pollInterval)
			return "OFFLINE";

		String message = (String) params.get("message");

		if (message == null) {
            throw new IllegalArgumentException("You must supply a message");
        }

        // Sakai plays the role of signalling server in the WebRTC architecture.
		boolean isVideoSignal = "true".equals(params.get("video"));

        if(logger.isDebugEnabled()) {
            logger.debug("message: " + message);
            logger.debug("isVideoSignal: " + isVideoSignal);
        }
		
		// Sanitise the message. XSS attacks. Unescape single quotes. They are valid.
		if (!isVideoSignal) { 
			message = StringEscapeUtils.escapeHtml4(
						StringEscapeUtils.escapeEcmaScript(message)).replaceAll("\\\\'", "'");
		}

		final UserMessage userMessage = new UserMessage(currentUser.getId(), to, message, isVideoSignal);

		addMessageToMap(userMessage);
		
        if (clustered) {
            try {
            	if (logger.isDebugEnabled()) {
                    logger.debug("Sending " + (isVideoSignal ? "video signal " : "") + "message to cluster ...");
                }
                Message msg = new Message(null, null, userMessage);
            	clusterChannel.send(msg);
            } catch (Exception e) {
                logger.error("Error sending JGroups message", e);
            }
        }
		
		return "success";
	}

	public String[] getHandledInputFormats() {
	    return new String[] { Formats.HTML };
	}
	
	public class UserMessage implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		public String from;
	    public String to;
		public String content;
		public long timestamp;
		public boolean video;
		public boolean clear;
		
		private UserMessage() {}

		private UserMessage(String from, boolean clear) {
			this(from,null,null,false,clear);
		}

		private UserMessage(String from, String to, String content, boolean video) {
			this(from,to,content,video,false);
		}

		private UserMessage(String from, String content) {
			this(from,null,content,false,false);
		}

        private UserMessage(String from, String to, String content, boolean video, boolean clear) {

            this.to = to;
			this.from = from;
			this.content = content;
			this.timestamp = (new Date()).getTime();
			this.video = video;
			this.clear = clear;
		}
        
        private void writeObject(java.io.ObjectOutputStream out) throws IOException {

        	out.writeObject(from);
        	out.writeObject(to);
        	out.writeObject(content);
        	out.writeObject(timestamp);
        	out.writeObject(video);
        	out.writeObject(clear);
        }
        
        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {

        	this.from = (String) in.readObject();
        	this.to = (String) in.readObject();
        	this.content = (String) in.readObject();
        	this.timestamp = (Long) in.readObject();
        	this.video = (Boolean) in.readObject();
        	this.clear = (Boolean) in.readObject();
        }
	}

	public class PortalChatUser {
		
		public String id;
		public String displayName;
		public boolean offline = false;
		public String video;
		
		public PortalChatUser(String id, String displayName, boolean offline, String video) {

			this.id = id;
			this.displayName = displayName;
			this.offline = offline;
			this.video = video;
		}
	}

	public class PortalVideoServer {

		public String protocol;
		public String host;
		public String username;
		public String credential;
		
		public PortalVideoServer(String url) {

			if (url.matches(WEBRTC_SERVER_REGEX)) {
				this.protocol = url.replaceFirst(WEBRTC_SERVER_REGEX, "$1");
				this.username = url.replaceFirst(WEBRTC_SERVER_REGEX, "$3");
				this.credential = url.replaceFirst(WEBRTC_SERVER_REGEX, "$4");
				this.host = url.replaceFirst(WEBRTC_SERVER_REGEX, "$5");
			} else {
				logger.warn("WebRTC Server doesn't match expected format!!");
			}
		}
	}
	
    /**
     * The JS client calls this to grab the latest data in one call. Connections, latest messages, online users
     * and present users (in a site) are all returned in one lump of JSON. If the online parameter is supplied and
     * true, a heartbeat is stamped for the sender as well.
     */
	@EntityCustomAction(action = "latestData", viewKey = EntityView.VIEW_SHOW)
	public Map<String,Object> handleLatestData(EntityReference ref, Map<String,Object> params) {
		
		logger.debug("handleLatestData");

		User currentUser = userDirectoryService.getCurrentUser();
		User anon = userDirectoryService.getAnonymousUser();
		
		if (anon.equals(currentUser)) {
			throw new SecurityException("You must be logged in to use this service");
			//return new HashMap<String,Object>(0);
		}
		
		String online = (String) params.get("online");
		String videoAgent = (String) params.get("videoAgent");
		
		if (logger.isDebugEnabled()) logger.debug("online: " + online);
		
		if (online != null && "true".equals(online)) {
			
			if (logger.isDebugEnabled()) logger.debug(currentUser.getEid() + " is online. Stamping their heartbeat ...");
			
			UserMessage userMessage = new UserMessage(currentUser.getId(), videoAgent);
			heartbeatMap.put(currentUser.getId(), userMessage);

            if (clustered) {
            	
            	logger.debug("We are clustered. Propagating heartbeat ...");
            	
                Message msg = new Message(null, null, userMessage);
                try {
                    clusterChannel.send(msg);
                    logger.debug("Heartbeat message sent.");
                } catch (Exception e) {
                    logger.error("Error sending JGroups heartbeat message", e);
                }
            }
		}
		else {
			
			if (logger.isDebugEnabled()) logger.debug(currentUser.getEid() + " is offline. Removing them from the message map ...");
			synchronized(messageMap) {
				messageMap.remove(currentUser.getId());
			}

	      sendClearMessage(currentUser.getId());

			if (logger.isDebugEnabled()) logger.debug(currentUser.getEid() + " is offline. Returning an empty data map ...");
			
			return new HashMap<String,Object>(0);
		}

		List<PortalChatUser> presentUsers = new ArrayList<PortalChatUser>();

		String siteId = (String) params.get("siteId");
		
		if (logger.isDebugEnabled()) logger.debug("Site ID: " +  siteId);
		
        if (siteId != null && siteId.length() > 0 && showSiteUsers) {
			// A site id has been specified, so we refresh our presence at the 
			// location and retrieve the present users
			String location = siteId + "-presence";
			presenceService.setPresence(location);
			List<User> presentSakaiUsers = presenceService.getPresentUsers(siteId + "-presence");
			presentSakaiUsers.remove(currentUser);
			for (User user : presentSakaiUsers) {
				UserMessage heartbeat = heartbeatMap.get(user.getId());
				// Flag this user as offline if they can't access portal chat
				boolean offline = !portalChatPermittedHelper.checkChatPermitted(user.getId());
				// If the DB and the jGroups have got out of sync there might not be any entry in the heartbeat map.
				if (heartbeat == null) {
					logger.info("Failed to find " + user.getId() + " in heartbeat map.");
				} else {
					presentUsers.add(new PortalChatUser(user.getId(), user.getDisplayName(), offline, heartbeat.content));
				}
			}
        }
		
		List<Object> connections = getConnectionsForUser(currentUser.getId());
		
		List<PortalChatUser> onlineConnections = new ArrayList<PortalChatUser>(connections.size());
		
		Date now = new Date();
		
		for (Object personObject : connections) {

            String uuid = null;

            try {
                uuid = (String) getUuidMethod.invoke(personObject,null);
                
                // Null all the person stuff to reduce the download size
                if (setProfileMethod != null) {
                    setProfileMethod.invoke(personObject,new Object[] {null});
                }
                if (setPrivacyMethod != null) {
                    setPrivacyMethod.invoke(personObject,new Object[] {null});
                }
                if (setPreferencesMethod != null) {
                    setPreferencesMethod.invoke(personObject,new Object[] {null});
                }
                
            } catch (Exception e) {
                logger.error("Failed to invoke getUuid on a Person instance. Skipping this person ...",e);
                continue;
            }
			
			UserMessage lastHeartbeat = heartbeatMap.get(uuid);
			
			if (lastHeartbeat == null) continue;
			
			if ((now.getTime() - lastHeartbeat.timestamp) < pollInterval) {
				onlineConnections.add(new PortalChatUser(uuid,uuid,false,lastHeartbeat.content));
			}
		}
		
		List<UserMessage> messages = new ArrayList<UserMessage>();
		List<UserMessage> videoMessages = new ArrayList<UserMessage>();

		String currentUserId = currentUser.getId();
		
		synchronized(messageMap) {
			if (messageMap.containsKey(currentUserId)) {
				// Grab the user's messages
				messages = splitMessages(messageMap.get(currentUserId),videoMessages);
				// Now we can reset the replicated map.
				messageMap.remove(currentUserId);
			}

            sendClearMessage(currentUserId);
		}

		Map<String,Object> data = new HashMap<String,Object>(4);
		
		data.put("connections", connections);
		data.put("messages", messages);
		data.put("videoMessages", videoMessages);
		data.put("online", onlineConnections);
		data.put("showSiteUsers", showSiteUsers);
		data.put("presentUsers", presentUsers);
		data.put("connectionsAvailable", connectionsAvailable);
		
		return data;
	}

	// Return plain messages and add video messages to video list
	public List<UserMessage> splitMessages(List<UserMessage> source, List<UserMessage> video) {
		List<UserMessage> plain = source;
		if (isVideoEnabled) {
			plain = new ArrayList<UserMessage>();
		    for (UserMessage element: source) {
		        if (element.video) {
		            video.add(element);
		        } else {
		        	plain.add(element);
		        }
		    }
		}
	    return plain;
	}
	
    private void sendClearMessage(String userId) {
        if (clustered) {
            try {
            	
            	if (logger.isDebugEnabled()) logger.debug("Sending messagMap clear message for " + userId + " ...");
            	UserMessage userMessage = new UserMessage(userId,true);
                Message msg = new Message(null, null, userMessage);
                clusterChannel.send(msg);
            } catch (Exception e) {
                logger.error("Error sending JGroups clear message", e);
            }
        }
    }

	@EntityCustomAction(action = "ping", viewKey = EntityView.VIEW_SHOW)
	public String handlePing(EntityReference ref) {

		User currentUser = userDirectoryService.getCurrentUser();
		User anon = userDirectoryService.getAnonymousUser();
		
		if (anon.equals(currentUser)) {
			throw new SecurityException("You must be logged in to use this service");
		}
		
		String userId = ref.getId();
		
		try {
			String email = userDirectoryService.getUser(userId).getEmail();
            new EmailSender(email, rb.getFormattedMessage("email.subject", new String[]{service}), rb.getFormattedMessage("email.body", new String[]{currentUser.getDisplayName(), service, portalUrl}));
		}
		catch (Exception e) {
			throw new EntityException("Failed to send email",userId);
		}
		
		return "success";
	}

	@EntityCustomAction(action = "servers", viewKey = EntityView.VIEW_SHOW)
	public Map<String,Object> handleServers(EntityReference ref) {

		final User currentUser = userDirectoryService.getCurrentUser();
		final User anon = userDirectoryService.getAnonymousUser();
		
		if (anon.equals(currentUser)) {
			throw new SecurityException("You must be logged in to use this service");
		}

		final Map<String,Object> data = new HashMap<String,Object>();
		data.put("iceServers", iceServers);
		return data;
	}
	
    /**
     * Implements a threadsafe addition to the message map
     */
    private void addMessageToMap(UserMessage m) {

        synchronized (messageMap) {
            final List<UserMessage> current = messageMap.get(m.to);

            if (current != null) {
                final List<UserMessage> copy = new ArrayList<UserMessage>(current.size());
                copy.addAll(current);
                copy.add(m);
                messageMap.put(m.to, copy);
            } else {
                messageMap.put(m.to, Arrays.asList(m));
            }
        }   
    }

	private class EmailSender implements Runnable {

		private Thread runner;

		private String email;
		private String subject;
		private String message;

		public EmailSender(String email, String subject, String message) {

			this.email = email;
			this.subject = subject;
			this.message = message;
			runner = new Thread(this, "PC EmailSender thread");
			runner.start();
		}

		public synchronized void run() {

			try {
				final List<String> additionalHeaders = new ArrayList<String>();
				additionalHeaders.add("Content-Type: text/plain; charset=ISO-8859-1");

				final String emailFromAddress = "\"" + service + "\" <no-reply@" + serverName + ">";
				emailService.send(emailFromAddress, email, subject, message, email, null, additionalHeaders);
			} catch (Exception e) {
                logger.error("sendEmail() failed for email: " + email,e);
			}
		}
	}
	
    /**
     * JGroups message listener.
     */
    public void receive(Message msg) {

        Object o = msg.getObject();
        if (o instanceof UserMessage) {
            UserMessage message = (UserMessage) o;
            if (message.to == null) {
            	if (message.clear) {
                    String userId = message.from;
                    synchronized (messageMap) {
                        messageMap.remove(userId);
    				}
            	} else {
            		logger.debug("Received heartbeat from cluster ...");
            		heartbeatMap.put(message.from, message);
            	}
            } else  {
            	if (logger.isDebugEnabled()) logger.debug("Received " + (message.video ? "video" : "") + "message from cluster ...");
                addMessageToMap(message);
            } 
        }
    }
	
	public void getState(OutputStream arg0) throws Exception {
	}

	public void setState(InputStream arg0) throws Exception {
	}

	public void block() {
	}

	public void suspect(Address arg0) {
	}

	public void unblock() {
	}

	public void viewAccepted(View arg0) {
	}
}
