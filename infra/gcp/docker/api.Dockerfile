FROM eclipse-temurin:21-jdk-jammy@sha256:ce5767b7222312d42395f5bab033cd91f09e44032a2f21bdfd7b5b912dbe1e77 AS build
WORKDIR /src/apps/api
COPY apps/api/ ./
RUN ./gradlew bootJar --no-daemon -x test

# Reproducible HEIF decoder build. The published pillow-heif 1.7 wheel embeds
# libheif 1.23.3, so build against the patched 1.23.4 source instead.
FROM eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf AS media-build
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates cmake build-essential pkg-config libde265-dev python3-dev python3-venv \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL https://github.com/strukturag/libheif/archive/refs/tags/v1.23.4.tar.gz -o /tmp/libheif.tar.gz \
    && echo "ce7739356637b7371dcc0ae876027f6f692de9c9ace8cd0e9ed8d79a01ea61fe  /tmp/libheif.tar.gz" | sha256sum -c - \
    && tar xzf /tmp/libheif.tar.gz -C /tmp \
    && cmake -S /tmp/libheif-1.23.4 -B /tmp/heif-build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/opt/libheif \
       -DBUILD_TESTING=OFF -DWITH_EXAMPLES=OFF -DENABLE_PLUGIN_LOADING=OFF \
       -DWITH_X265=OFF -DWITH_AOM_DECODER=OFF -DWITH_AOM_ENCODER=OFF -DWITH_X264=OFF -DWITH_OpenH264_DECODER=OFF \
    && cmake --build /tmp/heif-build --parallel 2 && cmake --install /tmp/heif-build
COPY infra/gcp/docker/media-decoder.requirements.txt /tmp/media-decoder.requirements.txt
RUN python3 -m venv /opt/heif \
    && PKG_CONFIG_PATH=/opt/libheif/lib/pkgconfig LD_LIBRARY_PATH=/opt/libheif/lib \
       /opt/heif/bin/pip install --no-cache-dir --no-binary=pillow-heif --only-binary=Pillow -r /tmp/media-decoder.requirements.txt

FROM eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf AS media-decoder
RUN apt-get update && apt-get install -y --no-install-recommends curl python3 libde265-0 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home gole
COPY --from=media-build /opt/libheif/lib/ /opt/libheif/lib/
COPY --from=media-build /opt/heif/ /opt/heif/
RUN echo /opt/libheif/lib > /etc/ld.so.conf.d/gole-heif.conf && ldconfig \
    && /opt/heif/bin/python -c "import pillow_heif; assert pillow_heif.libheif_version() == '1.23.4'"
# Verify the actual native decoder, orientation and EXIF removal at image build time.
COPY apps/api/src/main/resources/media/heif_decode.py /tmp/heif-smoke/decode.py
COPY apps/api/src/test/resources/media/phone-oriented-gps.heic /tmp/heif-smoke/input.heic
RUN /opt/heif/bin/python -I /tmp/heif-smoke/decode.py /tmp/heif-smoke/input.heic /tmp/heif-smoke/output.jpg 8192 8192 16000000 5242880 \
    && /opt/heif/bin/python -c "from PIL import Image; i=Image.open('/tmp/heif-smoke/output.jpg'); assert i.size == (48,80); assert not i.getexif(); assert i.getpixel((10,10))[0] > 200; assert i.getpixel((10,70))[2] > 200" \
    && rm -r /tmp/heif-smoke
USER gole

FROM media-decoder AS runtime
WORKDIR /app
COPY --from=build /src/apps/api/build/libs/api-0.0.1-SNAPSHOT.jar /app/api.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx1536m", "-jar", "/app/api.jar"]
