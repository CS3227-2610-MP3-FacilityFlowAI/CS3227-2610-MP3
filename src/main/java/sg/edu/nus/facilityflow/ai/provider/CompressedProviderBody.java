package sg.edu.nus.facilityflow.ai.provider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Bounded gzip response wrapper. The wire limit is enforced before bytes reach
 * the post-decompression decoder, which prevents a compressed stream from
 * bypassing the provider response budget.
 */
final class CompressedProviderBody {
    static final int MAX_COMPRESSED_BYTES = 1_048_576;
    private static final int BUFFER_SIZE = 8_192;

    private CompressedProviderBody() {}

    static InputStream gzip(InputStream source) throws IOException {
        Objects.requireNonNull(source, "source");
        return new GZIPInputStream(new BoundedWireInputStream(source), BUFFER_SIZE);
    }

    private static final class BoundedWireInputStream extends InputStream {
        private final InputStream delegate;
        private int bytesRead;

        private BoundedWireInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            if (atWireLimit()) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (atWireLimit()) {
                return -1;
            }
            int allowed = Math.min(length, MAX_COMPRESSED_BYTES - bytesRead);
            int read = delegate.read(bytes, offset, allowed);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private boolean atWireLimit() throws IOException {
            if (bytesRead >= MAX_COMPRESSED_BYTES) {
                int extra = delegate.read();
                if (extra >= 0) {
                    throw new ProviderFailure(ProviderFailure.Code.BODY_TOO_LARGE);
                }
                return true;
            }
            return false;
        }
    }
}
