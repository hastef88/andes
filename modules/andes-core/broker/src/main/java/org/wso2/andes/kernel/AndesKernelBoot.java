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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.andes.pool.AndesExecuter;
import org.wso2.andes.server.ClusterResourceHolder;
import org.wso2.andes.server.cassandra.AndesSubscriptionManager;
import org.wso2.andes.server.cluster.ClusterManagementInformationMBean;
import org.wso2.andes.server.cluster.ClusterManager;
import org.wso2.andes.server.configuration.ClusterConfiguration;
import org.wso2.andes.server.information.management.QueueManagementInformationMBean;
import org.wso2.andes.server.information.management.SubscriptionManagementInformationMBean;
import org.wso2.andes.server.slot.thrift.MBThriftServer;
import org.wso2.andes.server.slot.thrift.SlotManagementServiceImpl;
import org.wso2.andes.server.virtualhost.VirtualHost;
import org.wso2.andes.server.virtualhost.VirtualHostConfigSynchronizer;
import org.wso2.andes.store.jdbc.H2BasedAndesContextStoreImpl;
import org.wso2.andes.subscription.SubscriptionStore;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AndesKernelBoot {
    private static Log log = LogFactory.getLog(AndesKernelBoot.class);
    private static Configuration storeConfiguration;
    private static ClusterConfiguration clusterConfiguration;
    private static ScheduledExecutorService andesRecoveryTaskScheduler;

    /**
     * This will boot up all the components in
     * Andes kernel and bring the server to working state
     */
    public static void bootAndesKernel() throws Exception {

        //loadConfigurations - done from outside
        //startAndesStores - done from outside
        int threadPoolCount = 1;
        andesRecoveryTaskScheduler = Executors.newScheduledThreadPool(threadPoolCount);
        startAndesComponents();
        startHouseKeepingThreads();
        syncNodeWithClusterState();
        registerMBeans();
        startThriftServer();
        startMessaging();

    }

    /**
     * This will trigger graceful shutdown of andes broker
     *
     * @throws Exception
     */
    public static void shutDownAndesKernel() throws Exception {

        stopMessaging();
        stopThriftServer();
        unregisterMBeans();
        cleanUpAndNotifyCluster();
        stopHouseKeepingThreads();
        stopAndesComponents();
        //close stores
        AndesContext.getInstance().getAndesContextStore().close();
        MessagingEngine.getInstance().close();
    }

    /**
     * load configurations to andes kernel
     *
     * @param configuration configuration to load
     */
    public static void loadConfigurations(ClusterConfiguration configuration) {
        clusterConfiguration = configuration;
    }

    /**
     * start all andes stores message store/context store and AMQP construct store
     *
     * @param configuration store configurations
     * @param virtualHost   virtalhost to relate
     * @throws Exception
     */
    public static void startAndesStores(Configuration configuration, VirtualHost virtualHost) throws Exception {
        storeConfiguration = configuration;
        //initialize context store and message store
        if (ClusterResourceHolder.getInstance().getClusterConfiguration().isInMemoryMode()) {

        } else {

            //create a andes context store and register
            String contextStoreClassName = AndesContext.getInstance().getAndesContextStoreClass();
            Class clazz2 = Class.forName(contextStoreClassName);
            Object o2 = clazz2.newInstance();

            if (!(o2 instanceof AndesContextStore)) {
                throw new ClassCastException("Message store class must implement " + AndesContextStore.class + ". Class " + clazz2 +
                        " does not.");
            }
            AndesContextStore andesContextStore = (AndesContextStore) o2;
            DurableStoreConnection contextStoreConnection = andesContextStore.init();
            AndesContext.getInstance().setAndesContextStore(andesContextStore);

            //create subscription store
            SubscriptionStore subscriptionStore = new SubscriptionStore();
            AndesContext.getInstance().setSubscriptionStore(subscriptionStore);

            //create AMQP Constructs store
            AMQPConstructStore amqpConstructStore = new AMQPConstructStore();
            AndesContext.getInstance().setAMQPConstructStore(amqpConstructStore);


            //create a messaging engine and a message store
            MessagingEngine.getInstance().initializeMessageStore(
                    AndesContext.getInstance().getMessageStoreClass()
            );
        }

        /**
         * initialize amqp constructs syncing into Qpid
         */
        VirtualHostConfigSynchronizer _VirtualHostConfigSynchronizer = new VirtualHostConfigSynchronizer(virtualHost);
        ClusterResourceHolder.getInstance().setVirtualHostConfigSynchronizer(_VirtualHostConfigSynchronizer);
    }


    /**
     * bring the node to the state of the cluster
     *
     * @throws Exception
     */
    public static void syncNodeWithClusterState() throws Exception {
        //at the startup reload exchanges/queues/bindings and subscriptions
        log.info("Syncing exchanges, queues, bindings and subscriptions");
        ClusterResourceHolder.getInstance().getAndesRecoveryTask().recoverExchangesQueuesBindingsSubscriptions();
    }

    /**
     * clean up broker states and notify the cluster
     *
     * @throws Exception
     */
    private static void cleanUpAndNotifyCluster() throws Exception {
        //at the shutDown close all localSubscriptions and notify cluster
        log.info("Closing all local subscriptions existing...");
        ClusterResourceHolder.getInstance().getSubscriptionManager().closeAllLocalSubscriptionsOfNode();
        //tell cluster I am leaving
        ClusterResourceHolder.getInstance().getClusterManager().shutDownMyNode();
    }

    /**
     * start andes house keeping threads for the broker
     *
     * @throws Exception
     */
    public static void startHouseKeepingThreads() throws Exception {
        //reload exchanges/queues/bindings and subscriptions
        AndesRecoveryTask andesRecoveryTask = new AndesRecoveryTask();
        int scheduledPeriod = clusterConfiguration.getAndesRecoveryTaskInterval();
        andesRecoveryTaskScheduler.scheduleAtFixedRate(andesRecoveryTask, scheduledPeriod,
                scheduledPeriod, TimeUnit.SECONDS);
        ClusterResourceHolder.getInstance().setAndesRecoveryTask(andesRecoveryTask);
    }

    /**
     * stop andes house keeping threads
     *
     * @throws Exception
     */
    private static void stopHouseKeepingThreads() throws Exception {
        log.info("Stop syncing exchanges, queues, bindings and subscriptions...");
        int threadTerminationTimePerod = 20; // seconds
        try {
            andesRecoveryTaskScheduler.shutdown();
            andesRecoveryTaskScheduler.awaitTermination(threadTerminationTimePerod, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            andesRecoveryTaskScheduler.shutdownNow();
            log.warn("Recovery task scheduler is forcefully shutdown.");
            throw e;
        }

    }

    /**
     * register andes MBeans
     *
     * @throws Exception
     */
    public static void registerMBeans() throws Exception {

        ClusterManagementInformationMBean clusterManagementMBean = new
                ClusterManagementInformationMBean(ClusterResourceHolder.getInstance().getClusterManager());
        clusterManagementMBean.register();

        QueueManagementInformationMBean queueManagementMBean = new QueueManagementInformationMBean();
        queueManagementMBean.register();

        SubscriptionManagementInformationMBean subscriptionManagementInformationMBean = new SubscriptionManagementInformationMBean();
        subscriptionManagementInformationMBean.register();
    }

    /**
     * unregister any MBeans registered
     *
     * @throws Exception
     */
    private static void unregisterMBeans() throws Exception {

    }

    /**
     * start andes components
     *
     * @throws Exception
     */
    public static void startAndesComponents() throws Exception {

        /**
         * initialize subscription managing
         */
        AndesSubscriptionManager subscriptionManager =
                new AndesSubscriptionManager();
        ClusterResourceHolder.getInstance().setSubscriptionManager(subscriptionManager);
        subscriptionManager.init();

        /**
         * initialize cluster manager for managing nodes in MB cluster
         */
        ClusterManager clusterManager = new ClusterManager();
        clusterManager.init();
        ClusterResourceHolder.getInstance().setClusterManager(clusterManager);
    }

    /**
     * stop andes components
     *
     * @throws Exception
     */
    private static void stopAndesComponents() throws Exception {

    }

    /**
     * Start accepting and delivering messages
     *
     * @throws Exception
     */
    public static void startMessaging() throws Exception {
        MessagingEngine.getInstance().startMessageDelivery();
        MessagingEngine.getInstance().startMessageExpirationWorker();
    }

    /**
     * close transports and stop message delivery
     *
     * @throws Exception
     */
    private static void stopMessaging() throws Exception {
        MessagingEngine.getInstance().stopMessageExpirationWorker();
        //this will un-assign all slots currently owned
        MessagingEngine.getInstance().stopMessageDelivery();
    }


    /**
     * start the thrift server
     * @throws AndesException
     */
    private static void startThriftServer() throws AndesException {
        try {
            MBThriftServer.getInstance().start(AndesContext.getInstance().getThriftServerHost(),
                    AndesContext.getInstance().getThriftServerPort(),"MB-ThriftServer-main-thread");
        } catch (AndesException e) {
            throw new AndesException("Could not start the MB Thrift Server"+e);
        }
    }

    /**
     * Stop the thrift server
     */
    private static void stopThriftServer(){
        MBThriftServer.getInstance().stop();
    }
}
