package com.sequenceiq.cloudbreak.core.flow2.stack.provision.action;

import java.util.Map;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.event.setup.PrepareImageRequest;
import com.sequenceiq.cloudbreak.cloud.event.setup.SetupResult;
import com.sequenceiq.cloudbreak.cloud.model.Image;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackContext;
import com.sequenceiq.cloudbreak.service.image.ImageService;

@Component("PrepareImageAction")
public class PrepareImageAction extends AbstractStackCreationAction<SetupResult> {
    @Inject
    private ImageService imageService;

    public PrepareImageAction() {
        super(SetupResult.class);
    }

    @Override
    protected void doExecute(StackContext context, SetupResult payload, Map<Object, Object> variables) {
        Image image = imageService.getImage(context.getCloudContext().getId());
        sendEvent(context.getFlowId(), new PrepareImageRequest<>(context.getCloudContext(), context.getCloudCredential(), image));
    }

    @Override
    protected Long getStackId(SetupResult payload) {
        return payload.getRequest().getCloudContext().getId();
    }
}
