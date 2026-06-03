FROM node:22-bookworm-slim

RUN apt-get update \
  && apt-get install -y --no-install-recommends openjdk-17-jdk-headless \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY backend ./backend
COPY web ./web

RUN cd web && npm ci
RUN javac -encoding UTF-8 -cp "backend/lib/*" -d web/.bridge-build $(find backend/src -name "*.java") web/server/bridge.java
RUN cd web && npm run build -- --configLoader runner

ENV NODE_ENV=production

EXPOSE 5174

CMD ["npm", "--prefix", "web", "run", "start"]
