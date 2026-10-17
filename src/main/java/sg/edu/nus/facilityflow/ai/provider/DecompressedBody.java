package sg.edu.nus.facilityflow.ai.provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Streaming post-decompression provider body owned and closed by the decoder. */
final class DecompressedBody implements AutoCloseable {
    private final InputStream input;

    DecompressedBody(InputStream input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    static DecompressedBody of(byte[] bytes) {
        return new DecompressedBody(new ByteArrayInputStream(Objects.requireNonNull(bytes, "bytes")));
    }

    int read(byte[] target, int offset, int length) throws IOException {
        return input.read(target, offset, length);
    }

    int read() throws IOException {
        return input.read();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
