/*******************************************************************************
 * Copyright (c) 2013 Sierra Wireless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 ******************************************************************************/
package m3da.server.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In memory thread safe implementation of {@link StoreService}. Will discard the oldest messages if the maximum of message per client is reached.
 */
public class InMemoryStoreService implements StoreService {

	private static final Logger LOG = LoggerFactory.getLogger(InMemoryStoreService.class);

	/** maximum messages in the queue for a client */
	private int maxMessage;

	/**
	 * Create a store service with a limit of number of stored message per client.
	 * 
	 * @param maxMessage
	 *            the maximum message stored for a unique client
	 */
	public InMemoryStoreService(int maxMessage) {
		super();
		this.maxMessage = maxMessage;
	}

	private Map<String /* client id */, Map<Long /* reception nanos date */, List<Message>>> receivedData = new HashMap<String, Map<Long, List<Message>>>();

	private Map<String /* client id */, Queue<Message> /* message waiting to be sent */> dataToSend = new HashMap<String, Queue<Message>>();

	@Override
	public synchronized void enqueueReceivedData(String clientId, long receptionInNanoSec, List<Message> newData) {
		LOG.debug("enqueueReceivedData( clientId = {}, receptionInnanoSec = {}, newData = {} )", clientId, receptionInNanoSec, newData);

		Map<Long, List<Message>> msgQueue = receivedData.get(clientId);
		if (msgQueue == null) {
			/** we use the TreeMap because we need to remove the element in natural key order */
			msgQueue = new TreeMap<Long, List<Message>>();
			receivedData.put(clientId, msgQueue);
		}

		msgQueue.put(receptionInNanoSec, newData);

		// check if we have too much received data
		Iterator<Entry<Long, List<Message>>> iterator = msgQueue.entrySet().iterator();
		while (msgQueue.size() - maxMessage > 0 && iterator.next() != null) {
			// we should purge some message
			iterator.remove();
		}
	}

	@Override
	public synchronized Map<Long, List<Message>> lastReceivedData(String clientId) {
		LOG.debug("lastReceivedData( clientid = {} )", clientId);
		return receivedData.get(clientId);
	}

	@Override
	public synchronized void enqueueDataToSend(String clientId, List<Message> newData) {
		LOG.debug("enqueueDataToSend( clientid = {} , newData = {} )", clientId, newData);
		Queue<Message> queue = dataToSend.get(clientId);
		if (queue == null) {
			queue = new LinkedBlockingQueue<Message>();
			dataToSend.put(clientId, queue);
		}
		queue.addAll(newData);
	}

	@Override
	public synchronized List<Message> popDataToSend(String clientId) {
		LOG.debug("popDataToSend( clientid = {} )", clientId);
		Queue<Message> queue = dataToSend.get(clientId);
		if (queue == null) {
			return null;
		}

		// empty the queue and send to the caller
		List<Message> result = new ArrayList<Message>(queue);
		queue.clear();
		return result;
	}

	@Override
	public void start() {
		LOG.debug("start");
		// nothing to do here
	}

	@Override
	public void stop() {
		LOG.debug("stop");
		// nothing to do here
	}
}
