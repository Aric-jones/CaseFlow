FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y \
    chromium \
    fonts-wqy-zenhei \
    fonts-noto-cjk \
    libx11-xcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    libnss3 \
    libatk-bridge2.0-0 \
    libgtk-3-0 \
    && rm -rf /var/lib/apt/lists/*

ENV PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers
RUN mkdir -p /opt/pw-browsers

WORKDIR /app

COPY backend/target/caseflow-backend-1.0.0.jar app.jar

RUN mkdir -p uploads/ui-screenshots

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
