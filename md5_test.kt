import io.ktor.util.Digest

fun main() {
    println(Digest("MD5").build(ByteArray(0)))
}
