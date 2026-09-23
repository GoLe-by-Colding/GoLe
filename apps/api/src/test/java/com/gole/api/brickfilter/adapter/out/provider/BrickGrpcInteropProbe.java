package com.gole.api.brickfilter.adapter.out.provider;

import com.gole.api.brickfilter.domain.model.BrickJob.Mode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** 별도 단발 검증 명령에서 Python fake-editor 서버와 실제 wire 계약을 확인한다. */
public final class BrickGrpcInteropProbe {
    public static void main(String[] args) throws Exception {
        var adapter = new GrpcBrickGenerator(true, "test", args[0], "interop-test-token-not-a-real-secret-1234");
        try {
            var input = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", input);
            for (Mode mode : Mode.values()) {
                byte[] output = adapter.generate(input.toByteArray(), mode);
                var image = ImageIO.read(new ByteArrayInputStream(output));
                if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
                    throw new IllegalStateException("INVALID_INTEROP_IMAGE");
                }
            }
            System.out.println("Java → Python gRPC → LangGraph → fake editor: two modes passed");
        } finally {
            adapter.close();
        }
    }
}
