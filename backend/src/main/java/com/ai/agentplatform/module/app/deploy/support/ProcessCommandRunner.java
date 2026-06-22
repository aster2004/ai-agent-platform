package com.ai.agentplatform.module.app.deploy.support;

import com.ai.agentplatform.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class ProcessCommandRunner {

    private ProcessCommandRunner() {
    }

    public static void run(Path workDir, long timeoutSeconds, String... command) throws IOException, InterruptedException {
        int exitCode = runAndGetExitCode(workDir, timeoutSeconds, command);
        if (exitCode != 0) {
            throw new BusinessException("命令执行失败(" + exitCode + "): " + lastOutput);
        }
        if (!lastOutput.isBlank()) {
            log.info("命令输出: {}", lastOutput);
        }
    }

    /** 启动长期运行进程（如 Windows 下 nginx），不等待退出 */
    public static void startDetached(Path workDir, String... command) throws IOException {
        List<String> cmd = List.of(command);
        ProcessBuilder builder = new ProcessBuilder(cmd);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        log.info("后台启动: {} (cwd={})", cmd, workDir);
        builder.start();
    }

    private static String lastOutput = "";

    private static int runAndGetExitCode(Path workDir, long timeoutSeconds, String... command)
            throws IOException, InterruptedException {
        List<String> cmd = List.of(command);
        ProcessBuilder builder = new ProcessBuilder(cmd);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        builder.redirectErrorStream(true);
        log.info("执行命令: {} (cwd={})", cmd, workDir);
        Process process = builder.start();
        lastOutput = readOutput(process);
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BusinessException("命令执行超时: " + String.join(" ", cmd));
        }
        return process.exitValue();
    }

    public static boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command, "--version");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            try {
                ProcessBuilder builder = new ProcessBuilder(command, "-v");
                builder.redirectErrorStream(true);
                Process process = builder.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                return finished && process.exitValue() == 0;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private static String readOutput(Process process) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        return sb.toString().trim();
    }
}
