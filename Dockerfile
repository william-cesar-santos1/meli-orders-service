# SOLUÇÃO: multi-stage separa toolchain de runtime — compilador nao vai para producao.
# Principio: minima superficie de ataque. Estagio builder tem JDK 21 completo;
# runtime tem apenas JRE slim e o JAR extraido em camadas para cache incremental.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# SOLUÇÃO: copiar arquivos de build antes do codigo-fonte maximiza cache de dependencias Maven.
# Camadas de dependencias so sao reconstruidas quando pom.xml muda, nao a cada commit de codigo.
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -q

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests -q && \
    java -Djarmode=layertools \
         -jar target/meli-orders-service-1.0-SNAPSHOT.jar extract

# SOLUÇÃO: runtime usa JRE slim (sem compilador) — imagem final < 120 MB.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# SOLUÇÃO: usuario nao-root elimina escalada de privilegio caso haja RCE no container.
RUN addgroup --system app && adduser --system --ingroup app app

# SOLUÇÃO: --chown garante que arquivos pertencem ao usuario app desde a copia,
# sem camada extra de RUN chown que aumentaria o tamanho da imagem.
COPY --from=builder --chown=app:app /app/dependencies/ ./
COPY --from=builder --chown=app:app /app/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /app/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /app/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
