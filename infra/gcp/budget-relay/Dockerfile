FROM python:3.13-alpine

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    BUDGET_STATE_DIR=/state

RUN addgroup -S gole && adduser -S -G gole -u 10001 gole \
    && mkdir -p /app /state \
    && chown -R gole:gole /app /state

COPY --chown=gole:gole budget_relay.py /app/budget_relay.py

USER gole
WORKDIR /app
VOLUME ["/state"]

CMD ["python", "/app/budget_relay.py"]
