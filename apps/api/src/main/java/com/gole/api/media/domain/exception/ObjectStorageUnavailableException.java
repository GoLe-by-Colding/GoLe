package com.gole.api.media.domain.exception;

/** S3/MinIO에 연결할 수 없어 미디어 작업을 일시적으로 수행할 수 없을 때 발생한다. */
public class ObjectStorageUnavailableException extends RuntimeException {

    public ObjectStorageUnavailableException(Throwable cause) {
        super("Object storage is temporarily unavailable", cause);
    }
}
