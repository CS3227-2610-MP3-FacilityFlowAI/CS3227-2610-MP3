package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.CallHandle;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.PreparedChat;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderChatRequest;

class ProviderArchitectureTest {
    private static final List<Class<?>> TOP_LEVEL_PROVIDER_TYPES = List.of(
            SocLlmProviderCore.class,
            SocLlmProviderPort.class,
            ProviderEnvelopeDecoder.class,
            ProviderFailure.class,
            DecompressedBody.class,
            CompressedProviderBody.class,
            SocLlmSmartReportAssistant.class,
            SocLlmTriageAssistant.class);

    private static final List<Class<?>> MUTATING_INPUT_TYPES = List.of(
            PreparedChat.class,
            CallHandle.class,
            ProviderChatRequest.class,
            DecompressedBody.class);

    @Test
    void reqAi005AndAiSec005ProviderImplementationTypesArePackagePrivate() {
        for (Class<?> type : TOP_LEVEL_PROVIDER_TYPES) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName() + " must not be public");
            assertFalse(Modifier.isProtected(type.getModifiers()), type.getName() + " must not be protected");
        }
    }

    @Test
    void reqAi005AndAiSec005MutableInputConstructorsAreNotExternallyAccessible() {
        for (Class<?> type : MUTATING_INPUT_TYPES) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                assertFalse(
                        Modifier.isPublic(constructor.getModifiers()),
                        constructor + " must not be publicly constructible");
                assertFalse(
                        Modifier.isProtected(constructor.getModifiers()),
                        constructor + " must not be protected");
            }
        }
    }

    @Test
    void reqAi005AndAiSec005RoleAndWebSourcesDoNotDependOnProviderInternals() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        Set<String> roleOrWebSegments = Set.of("web", "requester", "technician", "manager", "role");
        try (var sources = Files.walk(sourceRoot)) {
            List<Path> forbiddenDependencies = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> hasAnySegment(path, roleOrWebSegments))
                    .filter(path -> containsProviderReference(path))
                    .toList();

            assertTrue(
                    forbiddenDependencies.isEmpty(),
                    "Role/web source must not reference ai.provider: " + forbiddenDependencies);
        }
    }

    private static boolean hasAnySegment(Path path, Set<String> expected) {
        for (Path segment : path) {
            if (expected.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsProviderReference(Path source) {
        try {
            return Files.readString(source).contains("sg.edu.nus.facilityflow.ai.provider");
        } catch (IOException failure) {
            throw new AssertionError("Unable to inspect source architecture: " + source, failure);
        }
    }
}
