package com.aegis.code_sandbox.docker;

import com.aegis.code_sandbox.config.JudgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerHostResolverTest {

    @Test
    void requiresAnExplicitHostInsteadOfFallingBackToTheLocalSocket() {
        JudgeProperties properties = new JudgeProperties();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DockerHostResolver.resolve(properties));

        assertTrue(error.getMessage().contains("JUDGE_DOCKER_HOST"));
        assertTrue(error.getMessage().contains("local socket"));
    }

    @Test
    void rejectsRemotePlainTcpByDefault() {
        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("tcp://10.20.30.40:2375");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DockerHostResolver.resolve(properties));

        assertTrue(error.getMessage().contains("Refusing an unauthenticated Docker daemon"));
    }

    @Test
    void rejectsPlainTcpEvenWhenInsecureFlagIsSetForRemoteHost() {
        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("tcp://10.20.30.40:2375");
        properties.getDocker().setAllowInsecureTcp(true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DockerHostResolver.resolve(properties));

        assertTrue(error.getMessage().contains("only permits loopback daemons"));
    }

    @Test
    void allowsExplicitInsecureLoopbackForDevelopment() {
        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("tcp://127.0.0.1:2375");
        properties.getDocker().setAllowInsecureTcp(true);

        DockerHostResolver.Plan plan = DockerHostResolver.resolve(properties);

        assertEquals(DockerHostResolver.Kind.TCP_INSECURE_LOOPBACK, plan.kind());
        assertEquals("tcp://127.0.0.1:2375", plan.dockerHost().toString());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("unauthenticated loopback")));
    }

    @Test
    void requiresCertificateDirectoryWhenTlsIsEnabled() {
        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("tcp://docker.internal:2376");
        properties.getDocker().setTlsVerify("1");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DockerHostResolver.resolve(properties));

        assertTrue(error.getMessage().contains("DOCKER_CERT_PATH"));
    }

    @Test
    void acceptsTlsWhenAllCertificatesAreReadable(@TempDir Path certs) throws IOException {
        Files.writeString(certs.resolve("ca.pem"), "ca");
        Files.writeString(certs.resolve("cert.pem"), "cert");
        Files.writeString(certs.resolve("key.pem"), "key");

        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("tcp://docker.internal:2376");
        properties.getDocker().setTlsVerify("1");
        properties.getDocker().setCertPath(certs.toString());

        DockerHostResolver.Plan plan = DockerHostResolver.resolve(properties);

        assertEquals(DockerHostResolver.Kind.TCP_TLS, plan.kind());
        assertEquals(certs.toString(), plan.certPath());
    }

    @Test
    void sshRequiresKnownHostsWhenStrictCheckingIsEnabled(@TempDir Path dir) throws IOException {
        Path key = Files.writeString(dir.resolve("id_ed25519"), "key");
        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("");
        properties.getDocker().getSsh().setHost("docker.example.com");
        properties.getDocker().getSsh().setUser("deploy");
        properties.getDocker().getSsh().setKeyPath(key.toString());
        properties.getDocker().getSsh().setStrictHostKeyChecking(true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DockerHostResolver.resolve(properties));

        assertTrue(error.getMessage().contains("DOCKER_SSH_KNOWN_HOSTS"));
    }

    @Test
    void sshPlanCarriesTargetAndIgnoresDockerHost(@TempDir Path dir) throws IOException {
        Path key = Files.writeString(dir.resolve("id_ed25519"), "key");
        Path knownHosts = Files.writeString(dir.resolve("known_hosts"), "docker.example.com ssh-ed25519 AAAA");
        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("tcp://ignored.example.com:2375");
        properties.getDocker().getSsh().setHost("docker.example.com");
        properties.getDocker().getSsh().setUser("deploy");
        properties.getDocker().getSsh().setPort(2222);
        properties.getDocker().getSsh().setKeyPath(key.toString());
        properties.getDocker().getSsh().setKnownHosts(knownHosts.toString());

        DockerHostResolver.Plan plan = DockerHostResolver.resolve(properties);

        assertEquals(DockerHostResolver.Kind.SSH, plan.kind());
        assertEquals("docker.example.com", plan.sshTarget().host());
        assertEquals("deploy", plan.sshTarget().user());
        assertEquals(2222, plan.sshTarget().port());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("DOCKER_HOST")));
    }

    @Test
    void sshRequiresAKeyOrPassword() {
        JudgeProperties properties = new JudgeProperties();
        properties.getDocker().setHost("ssh://deploy@docker.example.com");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DockerHostResolver.resolve(properties));

        assertTrue(error.getMessage().contains("DOCKER_SSH_KEY_PATH"));
    }
}
