FROM node:22-bookworm-slim@sha256:83f487e0a63425e5b4d146fb5e5be574bcbe1b7b843d3ebafdd95eaf7767a7e5 AS build
ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
RUN corepack enable && corepack prepare pnpm@10.30.3 --activate
WORKDIR /src
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
COPY packages/core/package.json packages/core/package.json
RUN pnpm --filter web... install --frozen-lockfile
COPY apps/web/ apps/web/
COPY packages/core/ packages/core/
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_PAYMENT_MODE
ARG NEXT_PUBLIC_PORTONE_STORE_ID
ARG NEXT_PUBLIC_PORTONE_CHANNEL_KEY
ARG NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY
ARG NEXT_PUBLIC_GA_MEASUREMENT_ID
ARG NEXT_PUBLIC_GTM_ID
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_PAYMENT_MODE=$NEXT_PUBLIC_PAYMENT_MODE
ENV NEXT_PUBLIC_PORTONE_STORE_ID=$NEXT_PUBLIC_PORTONE_STORE_ID
ENV NEXT_PUBLIC_PORTONE_CHANNEL_KEY=$NEXT_PUBLIC_PORTONE_CHANNEL_KEY
ENV NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY=$NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY
ENV NEXT_PUBLIC_GA_MEASUREMENT_ID=$NEXT_PUBLIC_GA_MEASUREMENT_ID
ENV NEXT_PUBLIC_GTM_ID=$NEXT_PUBLIC_GTM_ID
RUN pnpm --filter web build

FROM node:22-bookworm-slim@sha256:83f487e0a63425e5b4d146fb5e5be574bcbe1b7b843d3ebafdd95eaf7767a7e5
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
COPY --from=build --chown=gole:gole /src/packages/core ./packages/core
USER gole
EXPOSE 3000
CMD ["pnpm", "--filter", "web", "start", "--hostname", "0.0.0.0", "--port", "3000"]
