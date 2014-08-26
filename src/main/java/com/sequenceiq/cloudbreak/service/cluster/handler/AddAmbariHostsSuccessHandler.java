package com.sequenceiq.cloudbreak.service.cluster.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.conf.ReactorConfig;
import com.sequenceiq.cloudbreak.domain.Cluster;
import com.sequenceiq.cloudbreak.domain.Status;
import com.sequenceiq.cloudbreak.domain.WebsocketEndPoint;
import com.sequenceiq.cloudbreak.repository.ClusterRepository;
import com.sequenceiq.cloudbreak.service.cluster.event.AddAmbariHostsSuccess;
import com.sequenceiq.cloudbreak.websocket.WebsocketService;
import com.sequenceiq.cloudbreak.websocket.message.StatusMessage;

import reactor.event.Event;
import reactor.function.Consumer;

@Service
public class AddAmbariHostsSuccessHandler implements Consumer<Event<AddAmbariHostsSuccess>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddAmbariHostsSuccessHandler.class);

    @Autowired
    private WebsocketService websocketService;

    @Autowired
    private ClusterRepository clusterRepository;

    @Override
    public void accept(Event<AddAmbariHostsSuccess> event) {
        AddAmbariHostsSuccess data = event.getData();
        Cluster cluster = clusterRepository.findById(data.getClusterId());
        LOGGER.info("Accepted {} event.", ReactorConfig.ADD_AMBARI_HOSTS_SUCCESS_EVENT);
        websocketService.sendToTopicUser(cluster.getUser().getEmail(), WebsocketEndPoint.CLUSTER,
                new StatusMessage(data.getClusterId(), cluster.getName(), Status.AVAILABLE.name()));
    }

}