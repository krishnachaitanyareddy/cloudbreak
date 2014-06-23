package com.sequenceiq.cloudbreak.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.controller.NotFoundException;
import com.sequenceiq.cloudbreak.controller.json.BlueprintJson;
import com.sequenceiq.cloudbreak.controller.json.IdJson;
import com.sequenceiq.cloudbreak.converter.BlueprintConverter;
import com.sequenceiq.cloudbreak.domain.Blueprint;
import com.sequenceiq.cloudbreak.domain.Status;
import com.sequenceiq.cloudbreak.domain.User;
import com.sequenceiq.cloudbreak.repository.BlueprintRepository;
import com.sequenceiq.cloudbreak.websocket.WebsocketService;
import com.sequenceiq.cloudbreak.websocket.message.StatusMessage;

@Service
public class BlueprintService {

    @Autowired
    private BlueprintRepository blueprintRepository;

    @Autowired
    private BlueprintConverter blueprintConverter;

    @Autowired
    private WebsocketService websocketService;

    public IdJson addBlueprint(User user, BlueprintJson blueprintJson) {
        Blueprint blueprint = blueprintConverter.convert(blueprintJson);
        blueprint.setUser(user);
        blueprintRepository.save(blueprint);
        websocketService.send("/topic/blueprint", new StatusMessage(blueprint.getId(), blueprint.getName(), Status.CREATE_COMPLETED.name()));
        return new IdJson(blueprint.getId());
    }

    public Set<BlueprintJson> getAll(User user) {
        return blueprintConverter.convertAllEntityToJson(user.getBlueprints());
    }

    public BlueprintJson get(Long id) {
        Blueprint blueprint = blueprintRepository.findOne(id);
        if (blueprint == null) {
            throw new NotFoundException(String.format("Blueprint '%s' not found.", id));
        }
        return blueprintConverter.convert(blueprint);
    }

    public void delete(Long id) {
        Blueprint blueprint = blueprintRepository.findOne(id);
        if (blueprint == null) {
            websocketService.send("/topic/blueprint", new StatusMessage(id, "null", Status.DELETE_FAILED.name()));
            throw new NotFoundException(String.format("Blueprint '%s' not found.", id));
        }
        blueprintRepository.delete(blueprint);
        websocketService.send("/topic/blueprint", new StatusMessage(blueprint.getId(), blueprint.getName(), Status.DELETE_COMPLETED.name()));
    }
}