FROM python:3.13-slim@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"

WORKDIR /app
RUN pip install --no-cache-dir uv==0.10.2
COPY apps/support-agent/pyproject.toml apps/support-agent/uv.lock ./
RUN uv sync --locked --no-dev

COPY apps/api/src/main/proto /contracts
RUN mkdir -p /app/generated && \
    uv run --no-sync python -m grpc_tools.protoc \
      -I/contracts \
      --python_out=/app/generated \
      --grpc_python_out=/app/generated \
      /contracts/gole/support/v1/support_agent.proto

COPY apps/support-agent/src ./src
ENV PYTHONPATH="/app/src:/app/generated"

RUN useradd --system --uid 10002 --create-home support-agent && \
    chown -R support-agent:support-agent /app
USER support-agent

EXPOSE 50051
HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=10s \
  CMD ["python", "-m", "gole_support_agent.healthcheck"]
ENTRYPOINT ["python", "-m", "gole_support_agent.server"]
