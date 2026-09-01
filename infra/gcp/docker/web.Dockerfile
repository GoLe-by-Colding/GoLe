FROM node:22-bookworm-slim AS build
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
RUN corepack enable && corepack prepare pnpm@10.30.3 --activate
WORKDIR /src
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile
COPY apps/web/ apps/web/
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_PAYMENT_MODE
ARG NEXT_PUBLIC_PORTONE_STORE_ID
ARG NEXT_PUBLIC_PORTONE_CHANNEL_KEY
ARG NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_PAYMENT_MODE=$NEXT_PUBLIC_PAYMENT_MODE
ENV NEXT_PUBLIC_PORTONE_STORE_ID=$NEXT_PUBLIC_PORTONE_STORE_ID
ENV NEXT_PUBLIC_PORTONE_CHANNEL_KEY=$NEXT_PUBLIC_PORTONE_CHANNEL_KEY
ENV NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY=$NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY
RUN pnpm --filter web build

FROM node:22-bookworm-slim
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
RUN corepack enable && corepack prepare pnpm@10.30.3 --activate \
    && useradd --system --uid 10001 --create-home gole
WORKDIR /app
COPY --from=build --chown=gole:gole /src/package.json /src/pnpm-lock.yaml /src/pnpm-workspace.yaml ./
COPY --from=build --chown=gole:gole /src/node_modules ./node_modules
COPY --from=build --chown=gole:gole /src/apps/web ./apps/web
USER gole
EXPOSE 3000
CMD ["pnpm", "--filter", "web", "start", "--hostname", "0.0.0.0", "--port", "3000"]

