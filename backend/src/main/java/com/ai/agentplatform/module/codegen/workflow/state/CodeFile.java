package com.ai.agentplatform.module.codegen.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String path;
    private String content;
}
