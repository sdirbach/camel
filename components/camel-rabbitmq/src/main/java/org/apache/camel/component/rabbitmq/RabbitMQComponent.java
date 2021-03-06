/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.rabbitmq;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.TrustManager;

import com.rabbitmq.client.ConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQComponent extends UriEndpointComponent {

    public static final String ARG_PREFIX = "arg.";
    public static final String EXCHANGE_ARG_PREFIX = "exchange.";
    public static final String QUEUE_ARG_PREFIX = "queue.";
    public static final String BINDING_ARG_PREFIX = "binding.";

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQComponent.class);

    @Metadata(label = "common")
    private String hostname;
    @Metadata(label = "common", defaultValue = "5672")
    private int portNumber;
    @Metadata(label = "security", defaultValue = ConnectionFactory.DEFAULT_USER, secret = true)
    private String username = ConnectionFactory.DEFAULT_USER;
    @Metadata(label = "security", defaultValue = ConnectionFactory.DEFAULT_PASS, secret = true)
    private String password = ConnectionFactory.DEFAULT_PASS;
    @Metadata(label = "common")
    private ConnectionFactory connectionFactory;
    @Metadata(label = "common", defaultValue = "true")
    private boolean autoDetectConnectionFactory = true;

    public RabbitMQComponent() {
        super(RabbitMQEndpoint.class);
    }

    public RabbitMQComponent(CamelContext context) {
        super(context, RabbitMQEndpoint.class);
    }

    @Override
    protected RabbitMQEndpoint createEndpoint(String uri,
                                              String remaining,
                                              Map<String, Object> params) throws Exception {

        String host = getHostname();
        int port = getPortNumber();
        String exchangeName = remaining;

        if (remaining.contains(":") || remaining.contains("/")) {
            LOG.warn("The old syntax rabbitmq://hostname:port/exchangeName is deprecated. You should configure the hostname on the component or ConnectionFactory");
            try {
                URI u = new URI("http://" + remaining);
                host = u.getHost();
                port = u.getPort();
                if (u.getPath().trim().length() > 1) {
                    exchangeName = u.getPath().substring(1);
                } else {
                    exchangeName = "";
                }
            } catch (Throwable e) {
                // ignore
            }
        }

        // ConnectionFactory reference
        ConnectionFactory connectionFactory = resolveAndRemoveReferenceParameter(params, "connectionFactory", ConnectionFactory.class, getConnectionFactory());

        // try to lookup if there is a single instance in the registry of the ConnectionFactory
        if (connectionFactory == null && isAutoDetectConnectionFactory()) {
            Map<String, ConnectionFactory> map = getCamelContext().getRegistry().findByTypeWithName(ConnectionFactory.class);
            if (map != null && map.size() == 1) {
                Map.Entry<String, ConnectionFactory> entry = map.entrySet().iterator().next();
                connectionFactory = entry.getValue();
                String name = entry.getKey();
                if (name == null) {
                    name = "anonymous";
                }
                LOG.info("Auto-detected single instance: {} of type ConnectionFactory in Registry to be used as ConnectionFactory when creating endpoint: {}", name, uri);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> clientProperties = resolveAndRemoveReferenceParameter(params, "clientProperties", Map.class);
        TrustManager trustManager = resolveAndRemoveReferenceParameter(params, "trustManager", TrustManager.class);
        RabbitMQEndpoint endpoint;
        if (connectionFactory == null) {
            endpoint = new RabbitMQEndpoint(uri, this);
        } else {
            endpoint = new RabbitMQEndpoint(uri, this, connectionFactory);
        }
        endpoint.setHostname(host);
        endpoint.setPortNumber(port);
        endpoint.setUsername(getUsername());
        endpoint.setPassword(getPassword());
        endpoint.setExchangeName(exchangeName);
        endpoint.setClientProperties(clientProperties);
        endpoint.setTrustManager(trustManager);
        setProperties(endpoint, params);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating RabbitMQEndpoint with host {}:{} and exchangeName: {}",
                    new Object[]{endpoint.getHostname(), endpoint.getPortNumber(), endpoint.getExchangeName()});
        }

        HashMap<String, Object> args = new HashMap<>();
        args.putAll(IntrospectionSupport.extractProperties(params, ARG_PREFIX));
        endpoint.setArgs(args);

        HashMap<String, Object> argsCopy = new HashMap<>(args);
        
        // Combine the three types of rabbit arguments with their individual endpoint properties
        endpoint.getExchangeArgs().putAll(IntrospectionSupport.extractProperties(argsCopy, EXCHANGE_ARG_PREFIX));
        endpoint.getQueueArgs().putAll(IntrospectionSupport.extractProperties(argsCopy, QUEUE_ARG_PREFIX));
        endpoint.getBindingArgs().putAll(IntrospectionSupport.extractProperties(argsCopy, BINDING_ARG_PREFIX));

        return endpoint;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the running rabbitmq instance or cluster.
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPortNumber() {
        return portNumber;
    }

    /**
     * Port number for the host with the running rabbitmq instance or cluster.
     */
    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username in case of authenticated access
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for authenticated access
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * To use a custom RabbitMQ connection factory. When this option is set, all
     * connection options (connectionTimeout, requestedChannelMax...) set on URI
     * are not used
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public boolean isAutoDetectConnectionFactory() {
        return autoDetectConnectionFactory;
    }

    /**
     * Whether to auto-detect looking up RabbitMQ connection factory from the registry.
     * When enabled and a single instance of the connection factory is found then it will be used.
     * An explicit connection factory can be configured on the component or endpoint level which takes precedence.
     */
    public void setAutoDetectConnectionFactory(boolean autoDetectConnectionFactory) {
        this.autoDetectConnectionFactory = autoDetectConnectionFactory;
    }
}
