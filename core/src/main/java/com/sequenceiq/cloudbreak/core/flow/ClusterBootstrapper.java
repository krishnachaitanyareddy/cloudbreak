package com.sequenceiq.cloudbreak.core.flow;

import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_CONTAINER_ORCHESTRATOR;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_DOCKER_CONTAINER_AMBARI;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_DOCKER_CONTAINER_AMBARI_DB;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_DOCKER_CONTAINER_DOCKER_CONSUL_WATCH_PLUGN;
import static com.sequenceiq.cloudbreak.EnvironmentVariableConfig.CB_DOCKER_CONTAINER_REGISTRATOR;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.core.CloudbreakException;
import com.sequenceiq.cloudbreak.core.flow.context.BootstrapApiContext;
import com.sequenceiq.cloudbreak.core.flow.context.ClusterScalingContext;
import com.sequenceiq.cloudbreak.core.flow.context.ContainerOrchestratorClusterContext;
import com.sequenceiq.cloudbreak.core.flow.context.FlowContext;
import com.sequenceiq.cloudbreak.core.flow.context.ProvisioningContext;
import com.sequenceiq.cloudbreak.domain.InstanceGroup;
import com.sequenceiq.cloudbreak.domain.InstanceMetaData;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.orchestrator.CloudbreakOrchestratorException;
import com.sequenceiq.cloudbreak.orchestrator.ContainerOrchestrator;
import com.sequenceiq.cloudbreak.orchestrator.ContainerOrchestratorCluster;
import com.sequenceiq.cloudbreak.orchestrator.ContainerOrchestratorTool;
import com.sequenceiq.cloudbreak.orchestrator.Node;
import com.sequenceiq.cloudbreak.repository.StackRepository;
import com.sequenceiq.cloudbreak.service.PollingService;

@Component
public class ClusterBootstrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterBootstrapper.class);
    private static final int POLLING_INTERVAL = 5000;
    private static final int MAX_POLLING_ATTEMPTS = 100;

    @Value("${cb.container.orchestrator:" + CB_CONTAINER_ORCHESTRATOR + "}")
    private ContainerOrchestratorTool containerOrchestratorTool;

    @Value("${cb.docker.container.ambari:" + CB_DOCKER_CONTAINER_AMBARI + "}")
    private String ambariDockerImageName;

    @Value("${cb.docker.container.registrator:" + CB_DOCKER_CONTAINER_REGISTRATOR + "}")
    private String registratorDockerImageName;

    @Value("${cb.docker.container.docker.consul.watch.plugn:" + CB_DOCKER_CONTAINER_DOCKER_CONSUL_WATCH_PLUGN + "}")
    private String consulWatchPlugnDockerImageName;

    @Value("${cb.docker.container.ambari.db:" + CB_DOCKER_CONTAINER_AMBARI_DB + "}")
    private String postgresDockerImageName;

    @javax.annotation.Resource
    private Map<ContainerOrchestratorTool, ContainerOrchestrator> containerOrchestrators;

    @Autowired
    private StackRepository stackRepository;

    @Autowired
    private PollingService<BootstrapApiContext> bootstrapApiPollingService;

    @Autowired
    private BootstrapApiCheckerTask bootstrapApiCheckerTask;

    @Autowired
    private PollingService<ContainerOrchestratorClusterContext> clusterAvailabilityPollingService;

    @Autowired
    private ClusterAvailabilityCheckerTask clusterAvailabilityCheckerTask;

    public FlowContext bootstrapCluster(ProvisioningContext provisioningContext) throws CloudbreakException {
        Stack stack = stackRepository.findOneWithLists(provisioningContext.getStackId());
        InstanceGroup gateway = stack.getGatewayInstanceGroup();
        InstanceMetaData gatewayInstance = gateway.getInstanceMetaData().iterator().next();

        Set<Node> nodes = new HashSet<>();
        for (InstanceMetaData instanceMetaData : stack.getRunningInstanceMetaData()) {
            int volumeCount = instanceMetaData.getInstanceGroup().getTemplate().getVolumeCount();
            Set<String> dataVolumes = new HashSet<>();
            for (int i = 1; i <= volumeCount; i++) {
                dataVolumes.add("/hadoopfs/fs" + i);
            }
            nodes.add(new Node(instanceMetaData.getPrivateIp(), instanceMetaData.getPublicIp(), getHostname(instanceMetaData.getLongName()), dataVolumes));
        }

        try {
            ContainerOrchestrator containerOrchestrator = containerOrchestrators.get(containerOrchestratorTool);
            bootstrapApiPollingService.pollWithTimeout(
                    bootstrapApiCheckerTask,
                    new BootstrapApiContext(stack, gatewayInstance.getPublicIp(), containerOrchestrator),
                    POLLING_INTERVAL,
                    MAX_POLLING_ATTEMPTS);
            ContainerOrchestratorCluster cluster = containerOrchestrator.bootstrap(gatewayInstance.getPublicIp(), nodes, stack.getConsulServers());
            clusterAvailabilityPollingService.pollWithTimeout(
                    clusterAvailabilityCheckerTask,
                    new ContainerOrchestratorClusterContext(stack, containerOrchestrator, cluster),
                    POLLING_INTERVAL,
                    MAX_POLLING_ATTEMPTS);
            String cloudPlatform = provisioningContext.getCloudPlatform().name();
            containerOrchestrator.startRegistrator(cluster, registratorDockerImageName);
            containerOrchestrator.startAmbariServer(cluster, postgresDockerImageName, ambariDockerImageName, cloudPlatform);
            containerOrchestrator.startAmbariAgents(cluster, ambariDockerImageName, cluster.getNodes().size() - 1, cloudPlatform);
            containerOrchestrator.startConsulWatches(cluster, consulWatchPlugnDockerImageName, cluster.getNodes().size());
        } catch (CloudbreakOrchestratorException e) {
            throw new CloudbreakException(e);
        }

        return new ProvisioningContext.Builder()
                .setAmbariIp(provisioningContext.getAmbariIp())
                .setDefaultParams(stack.getId(), stack.cloudPlatform())
                .build();
    }

    public void bootstrapNewNodes(ClusterScalingContext clusterScalingContext) throws CloudbreakException {
        Stack stack = stackRepository.findOneWithLists(clusterScalingContext.getStackId());
        InstanceGroup gateway = stack.getGatewayInstanceGroup();
        InstanceMetaData gatewayInstance = gateway.getInstanceMetaData().iterator().next();

        Set<Node> nodes = new HashSet<>();
        for (InstanceMetaData instanceMetaData : stack.getRunningInstanceMetaData()) {
            if (clusterScalingContext.getUpscaleCandidateAddresses().contains(instanceMetaData.getPrivateIp())) {
                int volumeCount = instanceMetaData.getInstanceGroup().getTemplate().getVolumeCount();
                Set<String> dataVolumes = new HashSet<>();
                for (int i = 1; i <= volumeCount; i++) {
                    dataVolumes.add("/hadoopfs/fs" + i);
                }
                nodes.add(new Node(instanceMetaData.getPrivateIp(), instanceMetaData.getPublicIp(), getHostname(instanceMetaData.getLongName()), dataVolumes));
            }
        }

        try {
            ContainerOrchestrator containerOrchestrator = containerOrchestrators.get(containerOrchestratorTool);
            ContainerOrchestratorCluster cluster = containerOrchestrator.bootstrapNewNodes(gatewayInstance.getPublicIp(), nodes);
            clusterAvailabilityPollingService.pollWithTimeout(
                    clusterAvailabilityCheckerTask,
                    new ContainerOrchestratorClusterContext(stack, containerOrchestrator, cluster),
                    POLLING_INTERVAL,
                    MAX_POLLING_ATTEMPTS);
            containerOrchestrator.startAmbariAgents(cluster, ambariDockerImageName, cluster.getNodes().size(), clusterScalingContext.getCloudPlatform().name());
            containerOrchestrator.startConsulWatches(cluster, consulWatchPlugnDockerImageName, cluster.getNodes().size());
        } catch (CloudbreakOrchestratorException e) {
            throw new CloudbreakException(e);
        }
    }

    private String getHostname(String longName) {
        return longName.split("\\.")[0];
    }
}