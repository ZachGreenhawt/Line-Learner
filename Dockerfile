FROM node:22-bookworm-slim

RUN apt-get update \
  && apt-get install -y --no-install-recommends openjdk-17-jdk-headless tesseract-ocr \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY backend ./backend
COPY web ./web

WORKDIR /app/web

RUN npm ci
WORKDIR /app
RUN javac -encoding UTF-8 -cp "backend/lib/*" -d web/.bridge-build $(find backend/src -name "*.java") web/server/bridge.java
WORKDIR /app/web
RUN npm run build -- --configLoader runner

WORKDIR /app

ENV NODE_ENV=production

EXPOSE 5174

CMD ["npm", "--prefix", "web", "run", "start"]
