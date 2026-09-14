package com.gole.api.brickfilter.application.port.out;

import com.gole.api.brickfilter.domain.model.BrickJob.Mode;

public interface BrickGeneratorPort {
    boolean enabled();

    byte[] generate(byte[] image, Mode mode);
}
