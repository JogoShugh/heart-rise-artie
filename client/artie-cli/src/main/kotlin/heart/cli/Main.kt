package heart.cli

import heart.script.NavigatorProvider
import java.nio.file.Path
import kotlin.system.exitProcess

/** `artie run <script> [name=value …] [options]`, in the current folder, with the client on the classpath. */
fun main(args: Array<String>) {
    val folder = Path.of("").toAbsolutePath()
    val cli = ArtieCli(folder, System.out, ::readlnOrNull, NavigatorProvider::load, reportBuilder())
    exitProcess(cli.run(args.toList()))
}

/** heart-run-report's builder, installed in `report/` beside `lib/`, where this jar is; null when absent. */
private fun reportBuilder(): Path? {
    val jar = Path.of(ArtieCli::class.java.protectionDomain.codeSource.location.toURI())
    return jar.parent?.parent?.resolve("report/build-run-report.sh")?.takeIf { it.toFile().isFile }
}
