package com.ai.agentplatform.module.app.deploy.service.strategy;

import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.enums.DeployMode;

import java.io.IOException;
import java.util.List;

public interface DeployStrategy {

    DeployMode mode();

    DeployOutcome deploy(Long appId, List<AppCodeFile> files, String entryPath) throws IOException, InterruptedException;
}
