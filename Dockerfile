# Build compatível com Docker e Podman (podman build -t siab-backend .)

# ---- Estágio 1: build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
# Baixa as dependências antes de copiar o código, para aproveitar cache
# de camada em builds subsequentes.
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Estágio 2: runtime ----
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# O javacv-platform empacota o OpenCV inteiro como uma única lib nativa
# (opencv_objdetect + opencv_highgui juntos), então mesmo só usando
# CascadeClassifier (Fase 3 - SegmentationService), o carregamento da lib
# nativa exige o GTK2, ausente na imagem "jre-jammy" mínima. Sem isso, o
# Spring falha no @PostConstruct do SegmentationService e o container não
# sobe (UnsatisfiedLinkError: libgtk-x11-2.0.so.0).
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgtk2.0-0 libcanberra-gtk-module libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/siab-backend.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
