/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.andes.kernel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.wso2.andes.server.slot.SlotDeliveryWorkerManager;
import org.wso2.andes.store.cassandra.CQLBasedMessageStoreImpl;
import org.wso2.andes.store.jdbc.H2BasedMessageStoreImpl;
import org.wso2.andes.server.ClusterResourceHolder;
import org.wso2.andes.server.cassandra.MessageExpirationWorker;
import org.wso2.andes.server.cassandra.OnflightMessageTracker;
import org.wso2.andes.server.cassandra.QueueDeliveryWorker;
import org.wso2.andes.server.cassandra.TopicDeliveryWorker;
import org.wso2.andes.server.cluster.ClusterManager;
import org.wso2.andes.server.cluster.coordination.MessageIdGenerator;
import org.wso2.andes.server.cluster.coordination.TimeStampBasedMessageIdGenerator;
import org.wso2.andes.server.configuration.ClusterConfiguration;
import org.wso2.andes.server.util.AndesConstants;
import org.wso2.andes.server.util.AndesUtils;
import org.wso2.andes.subscription.SubscriptionStore;
import org.wso2.andes.tools.utils.DisruptorBasedExecutor;


/**
 * This class will handle all message related functions of WSO2 Message Broker
 */
public class MessagingEngine {
    private static final Logger log = Logger.getLogger(MessagingEngine.class);
    private static MessagingEngine messagingEngine = null;
    private MessageStore durableMessageStore;
    private MessageStore inMemoryMessageStore;
    private DisruptorBasedExecutor disruptorBasedExecutor;
    private SubscriptionStore subscriptionStore;
    private MessageIdGenerator messageIdGenerator;
    private ClusterConfiguration config;

    public static synchronized MessagingEngine getInstance() {
        if(messagingEngine == null){
            messagingEngine = new MessagingEngine();
        }
        return messagingEngine;
    }

    private MessagingEngine() {
        inMemoryMessageStore = new H2BasedMessageStoreImpl(true);
        config = ClusterResourceHolder.getInstance().getClusterConfiguration();
        configureMessageIDGenerator();
    }


    public MessageStore getDurableMessageStore() {
		return durableMessageStore;
	}

    public MessageStore getInMemoryMessageStore() {
        return inMemoryMessageStore;
    }

    public SubscriptionStore getSubscriptionStore() {
        return subscriptionStore;
    }

    public DurableStoreConnection initializeMessageStore(String messageStoreClassName) throws AndesException {

        try {
            DurableStoreConnection durableStoreConnection;
            Class clazz = Class.forName(messageStoreClassName);
            Object o = clazz.newInstance();

            if (!(o instanceof org.wso2.andes.kernel.MessageStore)) {
                throw new ClassCastException("Message store class must implement " + MessageStore.class + ". Class " + clazz +
                        " does not.");
            }
            durableMessageStore = (MessageStore) o;
            durableStoreConnection = durableMessageStore.initializeMessageStore();
            subscriptionStore = AndesContext.getInstance().getSubscriptionStore();
            inMemoryMessageStore.initializeMessageStore();
            disruptorBasedExecutor = new DisruptorBasedExecutor(durableMessageStore, null);

            return durableStoreConnection;
        } catch (Exception e) {
            log.error("Cannot initialize message store", e);
            throw new AndesException(e);
        }
    }

    public ClusterConfiguration getConfig() {
        return config;
    }

	public void messageContentReceived(AndesMessagePart part) {
        disruptorBasedExecutor.messagePartReceived(part);
    }

    public AndesMessagePart getMessageContentChunk(long messageID, int offsetInMessage) throws AndesException{
        return durableMessageStore.getContent(messageID, offsetInMessage);
    }

    public void messageReceived(AndesMessageMetadata message, long channelID) throws AndesException {
        try {

            if (message.getExpirationTime() > 0l)  {
                //store message in MESSAGES_FOR_EXPIRY_COLUMN_FAMILY Queue
                durableMessageStore.addMessageToExpiryQueue(message.getMessageID(), message.getExpirationTime(), message.isTopic(), message.getDestination());
            }

            if (message.isTopic) {
                List<AndesSubscription> subscriptionList = subscriptionStore.getActiveClusterSubscribersForDestination(message.getDestination(), true);
                if (subscriptionList.size() == 0) {
                    log.info("Message routing key: " + message.getDestination() + " No routes in cluster.");
                    List<Long> messageIdList =  new ArrayList<Long>();
                    messageIdList.add(message.getMessageID());
                    durableMessageStore.deleteMessageParts(messageIdList);
                }

                Set<String> targetAndesNodeSet;
                boolean originalMessageUsed = false;
                boolean hasNonDurableSubscriptions = false;
                for (AndesSubscription subscriberQueue : subscriptionList) {
                    if (subscriberQueue.isDurable()) {
                    	/**
                    	 * If message is durable, we clone the message (if there is more than one subscription) and send them to a queue created for with the name of the
                    	 * Subscription ID by writing it to the global queue.
                    	 */
                    	message.setDestination(subscriberQueue.getTargetQueue());
                    	AndesMessageMetadata clone;
                    	if(!originalMessageUsed){
                    		originalMessageUsed = true;
                    		clone = message;
                    	}else{
                    		clone = cloneAndesMessageMetadataAndContent(message);
                    	}
                		//We must update the routing key in metadata as well
                		clone.updateMetadata(subscriberQueue.getTargetQueue());

                		String globalQueueName = AndesUtils.getGlobalQueueNameForDestinationQueue(clone.getDestination());
                        QueueAddress globalQueueAddress = new QueueAddress(QueueAddress.QueueType.GLOBAL_QUEUE, globalQueueName);
                    	sendMessageToMessageStore(globalQueueAddress, clone, channelID);
                    } else {
                    	hasNonDurableSubscriptions = true;
                    }
                }

                if(hasNonDurableSubscriptions){
                	/**
                	 * If there are non durable subscriptions, we write it to each node where there is a subscription. Here we collect all nodes to which we need to write to.
                	 */
                    targetAndesNodeSet  = subscriptionStore.getNodeQueuesHavingSubscriptionsForTopic(message.getDestination());
                    for (String hostId : targetAndesNodeSet) {
                    	AndesMessageMetadata clone;
                    	if(!originalMessageUsed){
                    		originalMessageUsed = true;
                    		clone = message;
                    	}else{
                    		clone = message.deepClone(messageIdGenerator.getNextId());
                    	}
                        QueueAddress globalQueueAddress = new QueueAddress(QueueAddress.QueueType.TOPIC_NODE_QUEUE, hostId);
                    	sendMessageToMessageStore(globalQueueAddress, clone, channelID);
                    }
                }
            } else {
            	//If Queue, we write the message to Global Queue
                String globalQueueName = AndesUtils.getGlobalQueueNameForDestinationQueue(message.getDestination());
                QueueAddress globalQueueAddress = new QueueAddress(QueueAddress.QueueType.GLOBAL_QUEUE, globalQueueName);
            	sendMessageToMessageStore(globalQueueAddress, message, channelID);
            }
        } catch (Exception e) {
            throw new AndesException("Error in storing the message to the store", e);
        }
    }

    public AndesMessageMetadata getMessageMetaData(long messageID) throws AndesException{
         return durableMessageStore.getMetaData(messageID);
    }

    public void ackReceived(AndesAckData ack) throws AndesException {
        if(config.isInMemoryMode()) {
            List<AndesAckData> ackData = new ArrayList<AndesAckData>();
            ackData.add(ack);
            inMemoryMessageStore.ackReceived(ackData);
        } else {
            disruptorBasedExecutor.ackReceived(ack);
        }
    }

    public void messageReturned(List<AndesAckData> ackList) {

    }

    /**
     * remove messages of the queue matching to some destination queue
     * @param destinationQueue  destination queue name to match
     * @return  number of messages removed
     * @throws AndesException
     */
    public int removeMessagesOfQueue(String destinationQueue) throws AndesException {
        long lastProcessedMessageID = 0;
        int messageCount = 0;
        List<AndesMessageMetadata> messageList = durableMessageStore.getNextNMessageMetadataFromQueue(destinationQueue, lastProcessedMessageID, 500);
        List<AndesRemovableMetadata> messageMetaDataList = new ArrayList<AndesRemovableMetadata>();
        List<Long> messageIdList = new ArrayList<Long>();
        while (messageList.size() != 0) {

            // update metadata lists.
            for (AndesMessageMetadata metadata : messageList) {
                messageMetaDataList.add(new AndesRemovableMetadata(metadata.getMessageID(), metadata.getDestination()));
                messageIdList.add(metadata.getMessageID());
            }

            messageCount += messageIdList.size();
            lastProcessedMessageID = messageIdList.get(messageIdList.size() - 1);

            //remove metadata
            durableMessageStore.deleteMessageMetadataFromQueue(destinationQueue, messageMetaDataList);
            //remove content
            durableMessageStore.deleteMessageParts(messageIdList);

            messageMetaDataList.clear();
            messageIdList.clear();
            messageList = durableMessageStore.getNextNMessageMetadataFromQueue(destinationQueue, lastProcessedMessageID, 500);
        }
        return messageCount;
    }

    /**
     * remove in-memory messages tracked for this queue
     *
     * @param destinationQueueName name of queue messages should be removed
     * @throws AndesException
     */
    public void removeInMemoryMessagesAccumulated(String destinationQueueName) throws AndesException {
        //remove in-memory messages accumulated due to sudden subscription closing
        QueueDeliveryWorker queueDeliveryWorker = ClusterResourceHolder.getInstance().getQueueDeliveryWorker();
        if (queueDeliveryWorker != null) {
            queueDeliveryWorker.clearMessagesAccumilatedDueToInactiveSubscriptionsForQueue(destinationQueueName);
        }
        //remove sent but not acked messages
        OnflightMessageTracker.getInstance().getSentButNotAckedMessagesOfQueue(destinationQueueName);
    }

    public long generateNewMessageId() {
        return messageIdGenerator.getNextId();
    }

    private void sendMessageToMessageStore(QueueAddress queueAdr, AndesMessageMetadata message, long channelID)throws AndesException{
    	if(message.isPersistent()){
            disruptorBasedExecutor.messageCompleted(queueAdr, message, channelID);
    	}else{
    		//TODO stop creating this list
    		List<AndesMessageMetadata> list = new ArrayList<AndesMessageMetadata>(1);
    		list.add(message);
    		inMemoryMessageStore.addMessageMetaData(queueAdr ,list);
    	}
    }

    private AndesMessageMetadata cloneAndesMessageMetadataAndContent(AndesMessageMetadata message) throws AndesException {
        long newMessageId = messageIdGenerator.getNextId();
    	AndesMessageMetadata clone = message.deepClone(newMessageId);

        //duplicate message content
        ((CQLBasedMessageStoreImpl) durableMessageStore).duplicateMessageContent(message.getMessageID(),newMessageId);

        return clone;

    }

    public static String getMyNodeQueueName(){
        ClusterManager clusterManager = ClusterResourceHolder.getInstance().getClusterManager();
        return AndesConstants.NODE_QUEUE_NAME_PREFIX + clusterManager.getMyNodeID();

    }

    private void configureMessageIDGenerator() {
        // configure message ID generator
        String idGeneratorImpl = config.getMessageIdGeneratorClass();
        if (idGeneratorImpl != null && !"".equals(idGeneratorImpl)) {
            try {
                Class clz = Class.forName(idGeneratorImpl);

                Object o = clz.newInstance();
                messageIdGenerator = (MessageIdGenerator) o;
            } catch (Exception e) {
                log.error("Error while loading Message id generator implementation : " + idGeneratorImpl +
                        " adding TimeStamp based implementation as the default", e);
                messageIdGenerator = new TimeStampBasedMessageIdGenerator();
            }
        } else {
            messageIdGenerator = new TimeStampBasedMessageIdGenerator();
        }
    }

    /**
     * start message delivery. Start threads. If not created create.
     * @throws Exception
     */
    public void startMessageDelivery() throws Exception{


//        log.info("Starting queue message publisher");
//        QueueDeliveryWorker qdw =
//                ClusterResourceHolder.getInstance().getQueueDeliveryWorker();
//        if(qdw == null) {
//            boolean isInMemoryMode = ClusterResourceHolder.getInstance().getClusterConfiguration().isInMemoryMode();
//            int queueWorkerInterval = ClusterResourceHolder.getInstance().getClusterConfiguration().getQueueWorkerInterval();
////            QueueDeliveryWorker queueDeliveryWorker = new QueueDeliveryWorker(queueWorkerInterval, isInMemoryMode);
////            ClusterResourceHolder.getInstance().setQueueDeliveryWorker(queueDeliveryWorker);
//        }  else {
//            if (!qdw.isWorking()) {
//                qdw.setWorking();
//            }
//        }
//
//        log.info("Starting topic message publisher");
//        TopicDeliveryWorker tdw =
//                ClusterResourceHolder.getInstance().getTopicDeliveryWorker();
//        if(tdw == null) {
//            TopicDeliveryWorker topicDeliveryWorker = new TopicDeliveryWorker();
//            ClusterResourceHolder.getInstance().setTopicDeliveryWorker(topicDeliveryWorker);
//        }  else {
//            if (!tdw.isWorking()) {
//                tdw.setWorking();
//            }
//        }
//
//        log.info("Starting Disruptor writing messages to store");
    }

    /**
     * Stop message delivery threads
     */
    public void stopMessageDelivery() {

        log.info("Stopping queue message publisher");
        QueueDeliveryWorker qdw = ClusterResourceHolder.getInstance().getQueueDeliveryWorker();
        if(qdw != null && qdw.isWorking()) {
            qdw.stopFlusher();
        }

        log.info("Stopping topic message publisher");
        TopicDeliveryWorker tdw =
                ClusterResourceHolder.getInstance().getTopicDeliveryWorker();
        if (tdw != null && tdw.isWorking()) {
            tdw.stopWorking();
        }


        //stop all slotDeliveryWorkers
        SlotDeliveryWorkerManager.getInstance().stopSlotDeliveryWorkers();
        log.info("Stopping Disruptor writing messages to store");

    }

    public void close() {

        stopMessageDelivery();
        //todo: hasitha - we need to wait all jobs are finished, all executors have no future tasks
        stopMessageExpirationWorker();
        durableMessageStore.close();
        inMemoryMessageStore.close();

    }

    /**
     * Start Checking for Expired Messages (JMS Expiration)
     */
    public void startMessageExpirationWorker() {
        log.info("Starting Message Expiration Checker");

        MessageExpirationWorker mew =
                ClusterResourceHolder.getInstance().getMessageExpirationWorker();

        if(mew == null) {

            MessageExpirationWorker messageExpirationWorker = new MessageExpirationWorker();
            ClusterResourceHolder.getInstance().setMessageExpirationWorker(messageExpirationWorker);

        }  else {
            if (!mew.isWorking()) {
                mew.setWorking();
            }
        }

        log.info("Message Expiration Checker has started.");
    }

    /**
     * Stop Checking for Expired Messages (JMS Expiration)
     */
    public void stopMessageExpirationWorker() {

        log.info("Stopping Message Expiration Checker");

        MessageExpirationWorker mew = ClusterResourceHolder.getInstance().getMessageExpirationWorker();
        if(mew != null && mew.isWorking()) {
            mew.stopWorking();
        }
    }

}
