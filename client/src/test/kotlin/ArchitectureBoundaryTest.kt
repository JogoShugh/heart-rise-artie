import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * bdd-gates: ARTIE reaches the network only through the Transport
 * interface. No other production file may import an HTTP or socket
 * library, so the client cannot send a request its tests do not see.
 */
class ArchitectureBoundaryTest {

    // java.net.URI is plain data (resolving targets); the HTTP clients are I/O.
    private val forbiddenPrefixes = listOf(
        "java.net.http", "java.net.Socket", "java.net.URL", "io.ktor", "okhttp3", "org.apache.hc",
    )

    @Test
    fun `only Transport implementations import network libraries`() {
        Konsist.scopeFromProduction()
            .files
            .filter { file -> file.classesAndInterfacesAndObjects().none { it.hasParentWithName("Transport") } }
            .assertFalse { file ->
                file.hasImport { import -> forbiddenPrefixes.any { import.name.startsWith(it) } }
            }
    }
}
