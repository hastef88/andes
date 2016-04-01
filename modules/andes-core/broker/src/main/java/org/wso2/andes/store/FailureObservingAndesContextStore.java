/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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

package org.wso2.andes.store;

import org.wso2.andes.configuration.util.ConfigurationProperties;
import org.wso2.andes.kernel.AndesBinding;
import org.wso2.andes.kernel.AndesContextStore;
import org.wso2.andes.kernel.AndesException;
import org.wso2.andes.kernel.AndesExchange;
import org.wso2.andes.kernel.AndesQueue;
import org.wso2.andes.kernel.AndesSubscription;
import org.wso2.andes.kernel.DurableStoreConnection;
import org.wso2.andes.kernel.slot.Slot;
import org.wso2.andes.kernel.slot.SlotState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;

/**
 * Implementation of {@link AndesContextStore} which observes failures such is
 * connection errors. Any {@link AndesContextStore} implementation specified in
 * broker.xml will be wrapped by this class.
 *
 */
public class FailureObservingAndesContextStore implements AndesContextStore {

    /**
     * {@link AndesContextStore} specified in broker.xml
     */
    private AndesContextStore wrappedAndesContextStoreInstance;

    /**
     * Future referring to a scheduled task which check the connectivity to the
     * store.
     * Used to cancel the periodic task after store becomes operational.
     */
    private ScheduledFuture<?> storeHealthDetectingFuture;

    /**
     * {@inheritDoc}
     */
    public FailureObservingAndesContextStore(AndesContextStore wrapped) {
        this.wrappedAndesContextStoreInstance = wrapped;
        this.storeHealthDetectingFuture = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DurableStoreConnection init(ConfigurationProperties connectionProperties) throws AndesException {

        try {
            return wrappedAndesContextStoreInstance.init(connectionProperties);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> getAllStoredDurableSubscriptions() throws AndesException {

        try {
            return wrappedAndesContextStoreInstance.getAllStoredDurableSubscriptions();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getAllDurableSubscriptionsByID() throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getAllDurableSubscriptionsByID();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSubscriptionExist(String subscriptionId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.isSubscriptionExist(subscriptionId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeDurableSubscription(AndesSubscription subscription) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.storeDurableSubscription(subscription);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateDurableSubscription(AndesSubscription subscription) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.updateDurableSubscription(subscription);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateDurableSubscriptions(Map<String, String> subscriptions) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.updateDurableSubscriptions(subscriptions);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeDurableSubscription(AndesSubscription subscription) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.removeDurableSubscription(subscription);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeNodeDetails(String nodeID, String data) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.storeNodeDetails(nodeID, data);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getAllStoredNodeData() throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getAllStoredNodeData();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNodeData(String nodeID) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.removeNodeData(nodeID);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addMessageCounterForQueue(String destinationQueueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.addMessageCounterForQueue(destinationQueueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMessageCountForQueue(String destinationQueueName) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getMessageCountForQueue(destinationQueueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetMessageCounterForQueue(String storageQueueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.resetMessageCounterForQueue(storageQueueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeMessageCounterForQueue(String destinationQueueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.removeMessageCounterForQueue(destinationQueueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementMessageCountForQueue(String destinationQueueName, long incrementBy) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.incrementMessageCountForQueue(destinationQueueName, incrementBy);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decrementMessageCountForQueue(String destinationQueueName, long decrementBy) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.decrementMessageCountForQueue(destinationQueueName, decrementBy);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeExchangeInformation(String exchangeName, String exchangeInfo) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.storeExchangeInformation(exchangeName, exchangeInfo);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AndesExchange> getAllExchangesStored() throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getAllExchangesStored();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteExchangeInformation(String exchangeName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteExchangeInformation(exchangeName);

        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeQueueInformation(String queueName, String queueInfo) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.storeQueueInformation(queueName, queueInfo);

        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AndesQueue> getAllQueuesStored() throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getAllQueuesStored();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteQueueInformation(String queueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteQueueInformation(queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeBindingInformation(String exchange, String boundQueueName, String bindingInfo)
            throws AndesException {
        try {
            wrappedAndesContextStoreInstance.storeBindingInformation(exchange, boundQueueName, bindingInfo);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AndesBinding> getBindingsStoredForExchange(String exchangeName) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getBindingsStoredForExchange(exchangeName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteBindingInformation(String exchangeName, String boundQueueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteBindingInformation(exchangeName, boundQueueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    @Override
    public void createSlot(String slotRanges, long publishedNodeId, long queueId) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.createSlot(slotRanges, publishedNodeId, queueId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        wrappedAndesContextStoreInstance.close();
    }

    @Override
    public void updateLastPublishedMessageId(long nodeId, long queueId, long lastPublishedMessageId) throws
            AndesException {
        try {
            wrappedAndesContextStoreInstance.updateLastPublishedMessageId(nodeId, queueId, lastPublishedMessageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    @Override
    public long getLastPublishedMessageId(long nodeId, long queueId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getLastPublishedMessageId(nodeId, queueId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    @Override
    public void insertLastPublishedMessageId(long nodeId, long queueId, long lastPublishedMessageId) throws
            AndesException {
        try {
            wrappedAndesContextStoreInstance.insertLastPublishedMessageId(nodeId, queueId, lastPublishedMessageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    @Override
    public Map<Long, Long> getLastPublishedMessageIdsOfAllNodes(long queueId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getLastPublishedMessageIdsOfAllNodes(queueId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    @Override
    public Map<Long, Long> getNodeIdToLastAssignedId(long queueId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getNodeIdToLastAssignedId(queueId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    @Override
    public void updateAssignedMessageId(long uniqueNodeId, long queueId, long lastAssignedMessageId) throws AndesException {

    }

    @Override
    public void insertLastAssignedMessageId(long nodeId, long queueId, long lastAssignedMessageId) throws AndesException {

    }

    @Override
    public Slot selectNewSlot(long queueId) throws AndesException {
        return null;
    }

    /**
     * {@inheritDoc}.
     * <p>
     * Alters the behavior where
     * <ol>
     * <li>checks the operational status of the wrapped context store</li>
     * <li>if context store is operational it will cancel the periodic task</li>
     * </ol>
     */
    @Override
    public boolean isOperational(String testString, long testTime) {
        boolean operational = false;
        if (wrappedAndesContextStoreInstance.isOperational(testString, testTime)) {
            operational = true;
            if (storeHealthDetectingFuture != null) {
                // we have detected that store is operational therefore
                // we don't need to run the periodic task to check weather store
                // is available.
                storeHealthDetectingFuture.cancel(false);
                storeHealthDetectingFuture = null;
            }

        }
        return operational;
    }

    /**
     * A convenient method to notify all {@link StoreHealthListener}s that
     * context store became offline
     *
     * @param e
     *            the exception occurred.
     */
    private synchronized void notifyFailures(AndesStoreUnavailableException e) {

        if (storeHealthDetectingFuture == null) {
            // this is the first failure
            FailureObservingStoreManager.notifyStoreNonOperational(e,
                                                                   wrappedAndesContextStoreInstance);
            storeHealthDetectingFuture = FailureObservingStoreManager.scheduleHealthCheckTask(this);

        }
    }

    /**
     * Delete a slot from store
     *
     * @param startMessageId start message id of slot
     * @param endMessageId   end message id of slot
     * @throws AndesException
     */
    @Override
    public boolean deleteSlot(long startMessageId, long endMessageId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.deleteSlot(startMessageId, endMessageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Delete all slots by queue name
     *
     * @param queueName name of queue
     * @throws AndesException
     */
    @Override
    public void deleteSlotsByQueueName(String queueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteSlotsByQueueName(queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Delete message ids by queue name
     *
     * @param queueName name of queue
     * @throws AndesException
     */
    @Override
    public void deleteMessageIdsByQueueName(String queueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteMessageIdsByQueueName(queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Unassign and return slot
     *
     * @param startMessageId start message id of slot
     * @param endMessageId   end message id of slot
     * @throws AndesException
     */
    @Override
    public void deleteSlotAssignment(long startMessageId, long endMessageId) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteSlotAssignment(startMessageId, endMessageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Unassign slots by queue name
     *
     * @param nodeId    id of node
     * @param queueName name of queue
     * @throws AndesException
     */
    @Override
    public void deleteSlotAssignmentByQueueName(String nodeId, String queueName) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteSlotAssignmentByQueueName(nodeId, queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSlotAssignment(long nodeId, long queueId, String slotRangesString) throws
            AndesException {
        try {
            wrappedAndesContextStoreInstance.createSlotAssignment(nodeId, queueId, slotRangesString);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Select unassigned slots for a given queue name
     *
     * @param queueId name of queue
     * @return unassigned slot object if found
     * @throws AndesException
     */
    @Override
    public Slot selectUnAssignedSlot(long queueId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.selectUnAssignedSlot(queueId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Get last assigned id for a queue
     *
     * @param queueName name of queue
     * @return last assigned id of queue
     * @throws AndesException
     */
    @Override
    public long getQueueToLastAssignedId(String queueName) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getQueueToLastAssignedId(queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Set last assigned id for a given queue
     *
     * @param queueName name of queue
     * @param messageId id of message
     * @throws AndesException
     */
    @Override
    public void setQueueToLastAssignedId(String queueName, long messageId) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.setQueueToLastAssignedId(queueName, messageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Get last published id for a given node
     *
     * @param nodeId id of node
     * @return last published if of node
     * @throws AndesException
     */
    @Override
    public long getLocalSafeZoneOfNode(String nodeId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getLocalSafeZoneOfNode(nodeId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Set last published id for a given node
     *
     * @param nodeId    id of node
     * @param messageId id of message
     * @throws AndesException
     */
    @Override
    public void setLocalSafeZoneOfNode(String nodeId, long messageId) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.setLocalSafeZoneOfNode(nodeId, messageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePublisherNodeId(String nodeId) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.removePublisherNodeId(nodeId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Get all message published nodes
     *
     * @return set of published nodes
     * @throws AndesException
     */
    @Override
    public TreeSet<String> getMessagePublishedNodes() throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getMessagePublishedNodes();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Set slots states
     *
     * @param startMessageId start message id of slot
     * @param endMessageId   end message id of slot
     * @param slotState      state of slot
     * @throws AndesException
     */
    @Override
    public void setSlotState(long startMessageId, long endMessageId, SlotState slotState) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.setSlotState(startMessageId, endMessageId, slotState);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Slot getOverlappedSlot(String nodeId, String queueName) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getOverlappedSlot(nodeId, queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Add message ids to store
     *
     * @param queueName name of queue
     * @param messageId id of message
     * @throws AndesException
     */
    @Override
    public void addMessageId(String queueName, long messageId) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.addMessageId(queueName, messageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Get message ids for a given queue
     *
     * @param queueName name of queue
     * @return set of message ids
     * @throws AndesException
     */
    @Override
    public TreeSet<Long> getMessageIds(String queueName) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getMessageIds(queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Delete a message id
     *
     * @param messageId id of message
     * @throws AndesException
     */
    @Override
    public void deleteMessageId(long messageId) throws AndesException {
        try {
            wrappedAndesContextStoreInstance.deleteMessageId(messageId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Get all assigned slots for give node
     *
     * @param nodeId id of node
     * @return set of assigned slot objects
     * @throws AndesException
     */
    @Override
    public TreeSet<Slot> getAssignedSlotsByNodeId(String nodeId) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getAssignedSlotsByNodeId(nodeId);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Get all slots for a give queue
     *
     * @param queueName name of queue
     * @return set of slot object for queue
     * @throws AndesException
     */
    @Override
    public TreeSet<Slot> getAllSlotsByQueueName(String queueName) throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getAllSlotsByQueueName(queueName);
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getAllQueues() throws AndesException {
        try {
            return wrappedAndesContextStoreInstance.getAllQueues();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }

    /**
     * Clear and reset slot storage
     *
     * @throws AndesException
     */
    @Override
    public void clearSlotStorage() throws AndesException {
        try {
            wrappedAndesContextStoreInstance.clearSlotStorage();
        } catch (AndesStoreUnavailableException exception) {
            notifyFailures(exception);
            throw exception;
        }
    }
}
