import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * bdd-gates: ARTIE reaches the network only through a protocol binding
 * (SPEC §6.0): a Transport for HTTP, or a Binding such as CoapBinding. No
 * other production file may import a network library, so the client cannot
 * send a request its tests do not see.
 */
class ArchitectureBoundaryTest {

    // java.net.URI is plain data (resolving targets); the protocol clients are I/O.
    private val forbiddenPrefixes = listOf(
        "java.net.http", "java.net.Socket", "java.net.URL", "io.ktor", "okhttp3", "org.apache.hc",
        "org.eclipse.californium",
    )

    @Test
    fun `only Transport and Binding implementations import network libraries`() {
        Konsist.scopeFromProduction()
            .files
            .filter { file ->
                file.classesAndInterfacesAndObjects().none { it.hasParentWithName("Transport", "Binding") }
            }
            .assertFalse { file ->
                file.hasImport { import -> forbiddenPrefixes.any { import.name.startsWith(it) } }
            }
    }
}
