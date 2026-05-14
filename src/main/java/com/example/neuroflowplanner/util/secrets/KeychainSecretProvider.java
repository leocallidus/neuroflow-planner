package com.example.neuroflowplanner.util.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class KeychainSecretProvider implements SecretProvider {

    static final String SERVICE_NAME = "neuroflow-planner";
    static final String MAC_ACCOUNT_NAME = "neuroflow-planner";
    static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private final String osName;
    private final CommandExecutor commandExecutor;

    public KeychainSecretProvider() {
        this(System.getProperty("os.name"), new ProcessCommandExecutor());
    }

    KeychainSecretProvider(String osName, CommandExecutor commandExecutor) {
        this.osName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public String name() {
        return "os-keychain";
    }

    @Override
    public String getSecret(String secretId) {
        String canonicalId = canonicalizeSecretId(secretId);
        if (canonicalId == null) {
            return null;
        }

        if (isMac()) {
            CommandResult result = commandExecutor.execute(
                    List.of(
                            "security", "find-generic-password",
                            "-a", MAC_ACCOUNT_NAME,
                            "-s", toMacService(canonicalId),
                            "-w"
                    ),
                    null,
                    COMMAND_TIMEOUT
            );
            if (result.exitCode == 0) {
                return trimToNull(result.stdout);
            }
            return null;
        }

        if (isLinux()) {
            CommandResult result = commandExecutor.execute(
                    List.of(
                            "secret-tool", "lookup",
                            "service", SERVICE_NAME,
                            "key", canonicalId
                    ),
                    null,
                    COMMAND_TIMEOUT
            );
            if (result.exitCode == 0) {
                return trimToNull(result.stdout);
            }
            return null;
        }

        return null;
    }

    @Override
    public boolean storeSecret(String secretId, String secretValue) {
        String canonicalId = canonicalizeSecretId(secretId);
        if (canonicalId == null || secretValue == null || secretValue.isBlank()) {
            return false;
        }

        if (isMac()) {
            CommandResult result = commandExecutor.execute(
                    List.of(
                            "security", "add-generic-password",
                            "-a", MAC_ACCOUNT_NAME,
                            "-s", toMacService(canonicalId),
                            "-w", secretValue,
                            "-U"
                    ),
                    null,
                    COMMAND_TIMEOUT
            );
            return result.exitCode == 0;
        }

        if (isLinux()) {
            CommandResult result = commandExecutor.execute(
                    List.of(
                            "secret-tool", "store",
                            "--label=NeuroFlow Planner API Key",
                            "service", SERVICE_NAME,
                            "key", canonicalId
                    ),
                    secretValue,
                    COMMAND_TIMEOUT
            );
            return result.exitCode == 0;
        }

        return false;
    }

    @Override
    public boolean clearSecret(String secretId) {
        String canonicalId = canonicalizeSecretId(secretId);
        if (canonicalId == null) {
            return false;
        }

        if (isMac()) {
            CommandResult result = commandExecutor.execute(
                    List.of(
                            "security", "delete-generic-password",
                            "-a", MAC_ACCOUNT_NAME,
                            "-s", toMacService(canonicalId)
                    ),
                    null,
                    COMMAND_TIMEOUT
            );
            return result.exitCode == 0;
        }

        if (isLinux()) {
            CommandResult result = commandExecutor.execute(
                    List.of(
                            "secret-tool", "clear",
                            "service", SERVICE_NAME,
                            "key", canonicalId
                    ),
                    null,
                    COMMAND_TIMEOUT
            );
            return result.exitCode == 0;
        }

        return false;
    }

    private String canonicalizeSecretId(String secretId) {
        if (secretId == null || secretId.isBlank()) {
            return null;
        }
        if (EnvSecretProvider.SECRET_LEGACY_API_KEY.equals(secretId)) {
            return EnvSecretProvider.SECRET_EXTERNAL_API_KEY;
        }
        return secretId.trim();
    }

    private boolean isMac() {
        return osName.contains("mac");
    }

    private boolean isLinux() {
        return osName.contains("linux");
    }

    private String toMacService(String secretId) {
        return SERVICE_NAME + "." + secretId;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    interface CommandExecutor {
        CommandResult execute(List<String> command, String stdin, Duration timeout);
    }

    static final class CommandResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    static final class ProcessCommandExecutor implements CommandExecutor {
        @Override
        public CommandResult execute(List<String> command, String stdin, Duration timeout) {
            ProcessBuilder builder = new ProcessBuilder(command);
            try {
                Process process = builder.start();

                if (stdin != null) {
                    process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
                }
                process.getOutputStream().close();

                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new CommandResult(-1, "", "command timeout");
                }

                String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                return new CommandResult(process.exitValue(), stdout, stderr);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return new CommandResult(-1, "", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
    }
}
