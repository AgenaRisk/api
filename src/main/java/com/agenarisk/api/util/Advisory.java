package com.agenarisk.api.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Advisory class allows to recover in recoverable situations and compile advisory messages instead of throwing exceptions.<br>
 * Basic scenario: when loading a model in AgenaRisk Desktop, we'd rather load even invalid node tables and let the user sort them out, than to fail model load with an error.<br>
 * Advisory class is thread-safe.
 * 
 * @author Eugene Dementiev
 */
public class Advisory {
	
	// Maps an advisory group to some kind of key. The key could be process ID, model ID or something like that
	private static final Map<Object, AdvisoryGroup> advisoryGroups = Collections.synchronizedMap(new HashMap<>());
	
	// Maps threads to their respective AdvisoryGroups for quick lookup
	private static final Map<Thread, AdvisoryGroup> threadsToGroups = Collections.synchronizedMap(new HashMap<>());
	
	/**
	 * Returns an AdvisoryGroup instance for the provided key.<br>
	 * If no AdvisoryGroup exists for this key, it is created and then returned.
	 * 
	 * @param key The key to uniquely identify the AdvisoryGroup. For example, a process ID or an ID of one of parallel jobs.
	 * 
	 * @return AdvisoryGroup instance
	 */
	public static AdvisoryGroup getGroupByKey(Object key){
		
		synchronized(Advisory.class){
			if (!advisoryGroups.containsKey(key)){
				advisoryGroups.put(key, new AdvisoryGroup(key));
			}

			return advisoryGroups.get(key);
		}
	}
	
	/**
	 * Returns AdvisoryGroup to which Thread.currentThread() is linked to, or null
	 * 
	 * @return AdvisoryGroup for current thread
	 */
	public static AdvisoryGroup getCurrentThreadGroup(){
		synchronized(Advisory.class){
			return threadsToGroups.get(Thread.currentThread());
		}
	}
	
	/**
	 * Returns AdvisoryGroup to which the provided Thread is linked to is linked to, or null
	 * 
	 * @param thread Thread to check if linked to an AdvisoryGroup
	 * 
	 * @return AdvisoryGroup for provided Thread
	 */
	public static AdvisoryGroup getGroupByThread(Thread thread){
		synchronized(Advisory.class){
			return threadsToGroups.get(thread);
		}
	}
	
	/**
	 * Returns true if Thread.currentThread() is linked to an AdvisoryGroup (also creates AdvisoryMessage and adds it to that group).<br>
	 * Otherwise returns false.
	 * 
	 * @param message text for the AdvisoryMessage
	 * 
	 * @return true if an AdvisoryMessage was recorded
	 */
	public static boolean addMessageIfLinked(String message){
		synchronized(Advisory.class){
			AdvisoryGroup agroup = getCurrentThreadGroup();
			if (agroup != null){
				agroup.addMessage(new Advisory.AdvisoryMessage(message));
				return true;
			}
			else {
				return false;
			}
		}
	}
	
	/**
	 * Compiles a list of AdvisoryMessage texts, including messages of any Throwable causes of those AdvisoryMessages, for the AdvisoryGroup linked to Thread.currentThread().
	 * 
	 * @return List of messages
	 */
	public static List<String> compileAdvisoryMessagesForCurrentThreadGroup(){
		return compileAdvisoryMessages(getCurrentThreadGroup());
	}
	
	/**
	 * Compiles a list of AdvisoryMessage texts, including messages of any Throwable causes of those AdvisoryMessages, for the provided AdvisoryGroup.
	 * 
	 * @param agroup AdvisoryGroup to compile messages from
	 * 
	 * @return List of messages
	 */
	public static List<String> compileAdvisoryMessages(AdvisoryGroup agroup){
		synchronized(Advisory.class){
			List<String> lines = new ArrayList<>();

			if (agroup == null){
				return lines;
			}

			agroup.getMessages().stream().forEachOrdered(amsg -> {
				lines.add(amsg.getMessage());
				Throwable cause = amsg.getCause();
				while(cause != null){
					if (cause.getMessage() != null && !cause.getMessage().isEmpty()){
						lines.add(cause.getMessage());
						cause = cause.getCause();
					}
				}
			});

			return lines;
		}
	}
	
	/**
	 * AdvisoryGroup instance is associated with a number of Threads and is abstractly associated with some particular job (which is potentially multi-threaded).<br>
	 * AdvisoryGroup class is thread-safe.
	 */
	public static class AdvisoryGroup {
		private final Object key;
		private final Set<Thread> groupThreads = Collections.synchronizedSet(new HashSet<>());
		private final List<AdvisoryMessage> messages = Collections.synchronizedList(new ArrayList<>());
		
		protected AdvisoryGroup(Object key){
			this.key = key;
		}
		
		/**
		 * Links the provided Thread to this AdvisoryGroup.
		 * 
		 * @param thread Thread to link to this AdvisoryGroup
		 * 
		 * @throws RuntimeException if the provided Thread is already linked to another AdvisoryGroup
		 */
		public void linkToThread(Thread thread){
			synchronized(Advisory.class){
				AdvisoryGroup agroup = getGroupByThread(thread);
				if (agroup != null){
					throw new RuntimeException("Thread is already linked to a group with key :" + agroup.key);
				}
				groupThreads.add(thread);
				threadsToGroups.put(thread, this);
			}
		}
		
		/**
		 * Adds an AdvisoryMessage to this AdvisoryGroup.
		 * 
		 * @param message AdvisoryMessage to add to this AdvisoryGroup
		 */
		public void addMessage(AdvisoryMessage message){
			synchronized(Advisory.class){
				messages.add(message);
			}
		}
		
		/**
		 * Checks if this AdvisoryGroup is linked to the provided Thread.
		 * 
		 * @param thread Thread to check
		 * 
		 * @return true if the provided Thread is linked to this AdvisoryGroup and false otherwise
		 */
		public boolean isLinkedTo(Thread thread){
			synchronized(Advisory.class){
				return groupThreads.contains(thread);
			}
		}
		
		/**
		 * Returns the List of AdvisoryMessages for this AdvisoryGroup.<br>
		 * Warning: modifying this List directly will affect the workings of the Advisory and is NOT recommended.
		 * 
		 * @return List of AdvisoryMessages for this AdvisoryGroup
		 */
		public List<AdvisoryMessage> getMessages(){
			synchronized(Advisory.class){
				return messages;
			}
		}
		
		/**
		 * Returns the Set of Threads linked to this AdvisoryGroup.<br>
		 * Warning: modifying this Set directly will affect the workings of the Advisory and is NOT recommended.
		 * 
		 * @return Set of Threads linked to this AdvisoryGroup
		 */
		public Set<Thread> getGroupThreads(){
			synchronized(Advisory.class){
				return groupThreads;
			}
		}
	}
	
	/**
	 * AdvisoryMessage is an abstraction of a message with a possible Throwable cause.
	 */
	public static class AdvisoryMessage {
		
		private final String message;
		private final Throwable cause;
		
		/**
		 * Constructor for AdvisoryMessage.
		 * 
		 * @param message Message text
		 * @param cause Throwable cause of the AdvisoryMessage; can be null
		 */
		public AdvisoryMessage(String message, Throwable cause) {
			this.message = message;
			this.cause = cause;
		}

		/**
		 * Constructor for AdvisoryMessage.
		 * 
		 * @param message Message text
		 */
		public AdvisoryMessage(String message) {
			this(message, null);
		}

		/**
		 * Getter for the message text.
		 * 
		 * @return message text
		 */
		public String getMessage() {
			return message;
		}

		/**
		 * Getter for the Throwable cause.
		 * 
		 * @return the Throwable cause
		 */
		public Throwable getCause() {
			return cause;
		}
		
	}
}
