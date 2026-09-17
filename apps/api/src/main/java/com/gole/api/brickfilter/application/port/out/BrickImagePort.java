package com.gole.api.brickfilter.application.port.out;

public interface BrickImagePort {
    byte[] sanitize(byte[] image, boolean result);
}
