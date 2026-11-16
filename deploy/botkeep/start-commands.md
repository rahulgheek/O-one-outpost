# Botkeep start commands

Botkeep assigns `SERVER_PORT`; Spring Boot reads it automatically. Do **not**
add `-Dserver.port=...` to these commands.

Select **Java 21** for every workload. The JAR is expected at the project root
as `app.jar` (the ZIP from `deploy/scripts/package-botkeep.ps1` contains it).

| Workload | Start command |
|---|---|
| `gateway-service` | `java -Xms96m -Xmx384m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -jar app.jar` |
| `auth-service` | `java -Xms96m -Xmx320m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -jar app.jar` |
| `arena-service` | `java -Xms96m -Xmx384m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -jar app.jar` |
| `assessment-service` | `java -Xms96m -Xmx320m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -jar app.jar` |
| `analytics-service` | `java -Xms64m -Xmx256m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -jar app.jar` |

## Notes

- `-Xss256k` keeps thread stacks small on a 2 GB workload.
- `-XX:+ExitOnOutOfMemoryError` makes Botkeep show a clean restart instead of a
  half-dead JVM.
- If a workload has more than 2 GB, you can raise `-Xmx`, but keep at least
  400–500 MB for the JVM itself (metaspace, code cache, threads, GC).
- If you upload the JAR under a different name, change `-jar app.jar` to match.
- Do not use `target/*.jar` unless Botkeep built the project from source and the
  Maven target directory exists. The recommended flow is the prebuilt `app.jar`.
- Keep log levels at `INFO` in production (`LOG_LEVEL_*` variables). DEBUG log
  volume can exhaust the workload's memory and log quota.
