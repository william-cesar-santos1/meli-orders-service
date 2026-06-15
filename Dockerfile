# PROBLEMA: imagem unica com JDK completo expoe compilador, jmap, jstack e ferramentas de debug em producao.
# Superficie de ataque inclui o toolchain completo — um atacante com RCE tem acesso ao compilador Java.
# Camadas nao otimizadas invalidam todo o cache a cada mudanca de codigo, mesmo que dependencias nao mudem.
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests
EXPOSE 8080
CMD ["java", "-jar", "target/meli-orders-service-1.0-SNAPSHOT.jar"]
