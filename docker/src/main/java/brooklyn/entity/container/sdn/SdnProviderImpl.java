/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.container.sdn;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.container.docker.DockerInfrastructure;
import brooklyn.entity.container.sdn.ibm.SdnVeNetwork;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.QuorumCheck.QuorumChecks;
import brooklyn.util.net.Cidr;

import com.google.common.collect.Maps;

public abstract class SdnProviderImpl extends BasicStartableImpl implements SdnProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SdnProvider.class);

    protected transient Object addressMutex = new Object[0];
    protected transient Object hostMutex = new Object[0];

    @Override
    public void init() {
        LOG.info("Starting SDN provider id {}", getId());
        super.init();

        ConfigToAttributes.apply(this, DOCKER_INFRASTRUCTURE);

        BasicGroup agents = addChild(EntitySpec.create(BasicGroup.class)
                .configure(BasicGroup.RUNNING_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .configure(BasicGroup.UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty())
                .displayName("SDN Host Agents"));

        if (Entities.isManaged(this)) {
            Entities.manage(agents);
        }

        setAttribute(SDN_AGENTS, agents);

        setAttribute(ALLOCATED_IPS, 0);
        setAttribute(ALLOCATED_ADDRESSES, Maps.<String, InetAddress>newConcurrentMap());

        setAttribute(ALLOCATED_NETWORKS, 0);
        setAttribute(NETWORKS, Maps.<String, Cidr>newConcurrentMap());
        setAttribute(NETWORK_ALLOCATIONS, Maps.<String, Integer>newConcurrentMap());
        setAttribute(CONTAINER_ADDRESSES, Maps.<String, InetAddress>newConcurrentMap());
    }

    @Override
    public synchronized InetAddress getNextAddress() {
        synchronized (addressMutex) {
            Cidr cidr = getConfig(CIDR);
            Integer allocated = getAttribute(ALLOCATED_IPS);
            InetAddress next = cidr.addressAtOffset(allocated + 1);
            setAttribute(ALLOCATED_IPS, allocated + 1);
            return next;
        }
    }

    @Override
    public Cidr getSubnet(String subnetId, String subnetName) {
        synchronized (addressMutex) {
            Map<String, Cidr> networks = getAttribute(SdnProvider.NETWORKS);
            if (networks.containsKey(subnetId)) return networks.get(subnetId);

            Cidr networkCidr = getConfig(SdnVeNetwork.CONTAINER_NETWORK_CIDR);
            Integer networkSize = getConfig(SdnVeNetwork.CONTAINER_NETWORK_SIZE);
            Integer allocated = getAttribute(SdnVeNetwork.ALLOCATED_NETWORKS);

            InetAddress baseAddress = networkCidr.addressAtOffset(allocated * (2 << (32 - networkSize)));
            Cidr subnetCidr = new Cidr(baseAddress.getHostAddress() + "/" + networkSize);

            networks.put(subnetId, subnetCidr);
            setAttribute(SdnVeNetwork.ALLOCATED_NETWORKS, allocated + 1);
            setAttribute(SdnVeNetwork.NETWORKS, networks);

            return subnetCidr;
        }
    }

    @Override
    public DynamicCluster getDockerHostCluster() {
        return getConfig(DOCKER_INFRASTRUCTURE).getAttribute(DockerInfrastructure.DOCKER_HOST_CLUSTER);
    }

    @Override
    public Group getAgents() { return getAttribute(SDN_AGENTS); }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityEvent(EventType type, Entity member) {
            ((SdnProviderImpl) super.entity).onHostChanged(member);
        }
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        addHostTrackerPolicy();
        super.start(locations);

        setAttribute(SERVICE_UP, Boolean.TRUE);
    }

    @Override
    public void stop() {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        super.stop();
    }

    protected void addHostTrackerPolicy() {
        Group hosts = getDockerHostCluster();
        if (hosts != null) {
            MemberTrackingPolicy hostTrackerPolicy = addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                    .displayName("Docker host tracker")
                    .configure("group", hosts));
            LOG.info("Added policy {} to {}, during start", hostTrackerPolicy, this);
        }
    }

    private void onHostAdded(Entity item) {
        synchronized (hostMutex) {
            addHost(item);
        }
    }

    private void onHostRemoved(Entity item) {
        synchronized (hostMutex) {
            removeHost(item);
        }
    }

    private void onHostChanged(Entity item) {
        synchronized (hostMutex) {
            boolean exists = getDockerHostCluster().hasMember(item);
            Boolean running = item.getAttribute(SERVICE_UP);
            if (exists && running && item.getAttribute(SdnAgent.SDN_AGENT) == null) {
                onHostAdded(item);
            } else if (!exists) {
                onHostRemoved(item);
            }
        }
    }

    static {
        RendererHints.register(SDN_AGENTS, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(DOCKER_INFRASTRUCTURE, new RendererHints.NamedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }

}
