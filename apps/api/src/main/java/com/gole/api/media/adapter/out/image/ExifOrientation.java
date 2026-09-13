package com.gole.api.media.adapter.out.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads only the bounded IFD0 orientation value. All metadata is discarded after rotation. */
final class ExifOrientation {
    private ExifOrientation() {}

    static int jpeg(byte[] bytes) {
        int position = 2;
        while (position + 4 <= bytes.length && (bytes[position] & 255) == 255) {
            int marker = bytes[position + 1] & 255;
            if (marker == 0xda || marker == 0xd9) break;
            int length = ((bytes[position + 2] & 255) << 8) | (bytes[position + 3] & 255);
            if (length < 2 || length > bytes.length - position - 2) return 1;
            if (marker == 0xe1
                    && length >= 16
                    && bytes[position + 4] == 'E'
                    && bytes[position + 5] == 'x'
                    && bytes[position + 6] == 'i'
                    && bytes[position + 7] == 'f'
                    && bytes[position + 8] == 0
                    && bytes[position + 9] == 0) {
                return tiff(bytes, position + 10, length - 8);
            }
            position += length + 2;
        }
        return 1;
    }

    private static int tiff(byte[] bytes, int offset, int length) {
        try {
            ByteBuffer data = ByteBuffer.wrap(bytes, offset, length).slice();
            short magic = data.getShort(0);
            if (magic != 0x4949 && magic != 0x4d4d) return 1;
            data.order(magic == 0x4949 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            if (data.getShort(2) != 42) return 1;
            int ifd = data.getInt(4);
            if (ifd < 8 || ifd > length - 2) return 1;
            int entries = Short.toUnsignedInt(data.getShort(ifd));
            if (entries > (length - ifd - 2) / 12) return 1;
            for (int i = 0; i < entries; i++) {
                int at = ifd + 2 + i * 12;
                if (data.getShort(at) == 0x112 && data.getShort(at + 2) == 3 && data.getInt(at + 4) == 1) {
                    int value = Short.toUnsignedInt(data.getShort(at + 8));
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
        return 1;
    }
}
