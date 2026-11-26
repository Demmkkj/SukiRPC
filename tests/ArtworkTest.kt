package dev.nuvio.presence

fun main() {
    val good = listOf("https://image.tmdb.org/t/p/w500/test.jpg", "https://images.metahub.space/poster/medium/tt1234567/img")
    val bad = listOf("http://image.tmdb.org/t/p/w500/test.jpg", "https://image.tmdb.org/t/p/w500/test.jpg?token=abc", "https://image.tmdb.org/t/p/w500/test.jpg#fragment", "https://user@image.tmdb.org/t/p/w500/test.jpg", "https://image.tmdb.org:443/t/p/w500/test.jpg", "https://image.tmdb.org.example.com/t/p/w500/test.jpg", "https://127.0.0.1/t/p/w500/test.jpg", "https://images.metahub.space/poster/medium/invalid/img", "not a URL")
    good.forEach { check(publicArtwork(it) == it) }
    bad.forEach { check(publicArtwork(it).isEmpty()) }
    println("PASS: public artwork URL validation")
}
