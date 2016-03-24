/*
 *  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.inbound.endpoint.protocol.file;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.task.TaskStartupObserver;
import org.wso2.carbon.inbound.endpoint.common.InboundRequestProcessorImpl;
import org.wso2.carbon.inbound.endpoint.common.InboundTask;
import org.wso2.carbon.inbound.endpoint.protocol.PollingConstants;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

public class VFSProcessor extends InboundRequestProcessorImpl implements TaskStartupObserver {

    private static final String ENDPOINT_POSTFIX = "FILE" + COMMON_ENDPOINT_POSTFIX;
    private static final Log log = LogFactory.getLog(VFSProcessor.class);

    private FilePollingConsumer fileScanner;
    private Properties vfsProperties;
    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;

    public VFSProcessor(InboundProcessorParams params) {
        this.name = params.getName();
        this.vfsProperties = params.getProperties();
        try {
            this.interval = Long.parseLong(vfsProperties
                    .getProperty(PollingConstants.INBOUND_ENDPOINT_INTERVAL));
        } catch (NumberFormatException nfe) {
            throw new SynapseException("Invalid numeric value for interval.", nfe);
        } catch (Exception e) {
            throw new SynapseException("Invalid value for interval.", e);
        }
        this.sequential = true;
        if (vfsProperties.getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL) != null) {
            this.sequential = Boolean.parseBoolean(vfsProperties
                    .getProperty(PollingConstants.INBOUND_ENDPOINT_SEQUENTIAL));
        }
        this.coordination = true;
        if (vfsProperties.getProperty(PollingConstants.INBOUND_COORDINATION) != null) {
            this.coordination = Boolean.parseBoolean(vfsProperties
                    .getProperty(PollingConstants.INBOUND_COORDINATION));
        }
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.synapseEnvironment = params.getSynapseEnvironment();
    }

    /**
     * This will be called at the time of synapse artifact deployment.
     */
    public void init() {
        if (isPinnedServerEnabled()) {
            if (isPinnedServer()) {
                startInboundFileListener();
            } else {
                log.info("Inbound file listener " + name +
                        " not started as it is not pinned to this server");
            }
        } else {
            startInboundFileListener();
        }
    }

    public void startInboundFileListener() {
        log.info("Inbound file listener " + name + " starting ...");
        fileScanner = new FilePollingConsumer(vfsProperties, name, synapseEnvironment, interval);
        fileScanner.registerHandler(new FileInjectHandler(injectingSeq, onErrorSeq, sequential,
                synapseEnvironment, vfsProperties));
        start();
    }

    private boolean isPinnedServerEnabled() {
        if (vfsProperties.getProperty(PollingConstants.INBOUND_PINNED_SERVER) != null) {
            return true;
        }

        return false;
    }

    private boolean isPinnedServer() {
        String thisServerName = System.getProperty("pinServerName", null);
        if (thisServerName == null || "".equals(thisServerName)) {
            thisServerName = getServerHost();
            if (thisServerName == null || "".equals(thisServerName)) {
                thisServerName = "localhost";
            }
        }

        String pinnedServersValue = vfsProperties.getProperty(
                PollingConstants.INBOUND_PINNED_SERVER, null);

        List<String> pinnedServers = getPinnedServers(pinnedServersValue);
        if (pinnedServers != null && !pinnedServers.isEmpty()) {
            if (pinnedServers.contains(thisServerName)) {
                return true;
            }
        }

        return false;
    }

    private List<String> getPinnedServers(String pinnedServersValue) {
        StringTokenizer st = new StringTokenizer(pinnedServersValue, " ,");
        List<String> pinnedServersList = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.length() != 0) {
                pinnedServersList.add(token);
            }
        }
        return pinnedServersList;
    }

    private String getServerHost() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            if (addr != null) {
                return addr.getHostName();
            }
        } catch (UnknownHostException e) {
            log.warn("Unable to get the hostName or IP address of the server", e);
        }

        return null;
    }

    /**
     * Register/start the schedule service
     * */
    public void start() {
        InboundTask task = new FileTask(fileScanner, interval);
        start(task, ENDPOINT_POSTFIX);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void update() {
        // This will not be called for inbound endpoints
    }
}
