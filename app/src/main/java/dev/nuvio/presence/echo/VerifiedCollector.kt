package dev.nuvio.presence

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

@Suppress("DEPRECATION")
internal fun verifiedCollector(c:Context,file:File){
    val digest=MessageDigest.getInstance("SHA-256")
    c.assets.open("collector.apk").use{input->file.outputStream().use{output->java.security.DigestInputStream(input,digest).copyTo(output)}}
    val expected=c.assets.open("collector.sha256").bufferedReader().use{it.readText().trim()}
    check(digest.digest().joinToString(""){"%02x".format(it)}==expected){"Integridade do Collector inválida"}
    val pm=c.packageManager
    val apk=requireNotNull(pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNATURES))
    val own=pm.getPackageInfo(c.packageName,PackageManager.GET_SIGNATURES)
    val trusted=own.signatures?.toSet().orEmpty()
    check(apk.packageName=="dev.nuvio.collector" && trusted.isNotEmpty() && apk.signatures?.toSet()==trusted){"Assinatura do Collector inválida"}
}
