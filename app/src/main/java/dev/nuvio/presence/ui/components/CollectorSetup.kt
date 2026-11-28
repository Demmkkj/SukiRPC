package dev.nuvio.presence

import dev.nuvio.presence.ui.themes.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*

@Composable internal fun CollectorSetup(ip:String, changeIp:(String)->Unit, disabled:Boolean, onReset:()->Unit,onBusyChange:(Boolean)->Unit={}){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var port by rememberSaveable{mutableStateOf("5555")}
    var installing by remember{mutableStateOf(false)}
    var confirmation by remember{mutableStateOf(false)}
    var mismatch by remember{mutableStateOf(false)}
    var result by remember{mutableStateOf("")}
    fun install(replace:Boolean){
        val target=ip;val adbPort=port.toIntOrNull()?:0
        installing=true;onBusyChange(true);mismatch=false;result="Preparando…"
        scope.launch {
            try {
                withContext(Dispatchers.IO){CollectorInstaller.install(ctx.applicationContext,target,adbPort,replace){msg->android.os.Handler(android.os.Looper.getMainLooper()).post{result=msg}}}
                if(replace)onReset()
                Monitor.log("Collector instalado e autorizado")
            }catch(e:CancellationException){throw e}
            catch(e:CollectorInstaller.SignatureMismatch){mismatch=true;result=e.message?:"Assinatura incompatível"}
            catch(e:Exception){result="Falha: "+(e.message?:e.javaClass.simpleName).take(250)}
            finally{installing=false;onBusyChange(false)}
        }
    }
    GlassCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Ative a depuração ADB da sua TV para instalar o Collector.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(ip,changeIp,label={Text("IP da Fire TV")},singleLine=true,enabled=!installing,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(port,{port=it.filter{c->c.isDigit()}.take(5)},label={Text("Porta ADB")},singleLine=true,enabled=!installing,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        GlassButton(onClick={confirmation=true},enabled=!disabled&&!installing&&ip.isNotBlank()&&(port.toIntOrNull()?:0) in 1..65535,modifier=Modifier.fillMaxWidth()){Text(if(installing)"Configurando…" else "Instalar ou atualizar Collector")}
        if(installing)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(result.isNotBlank())Text(result,style=MaterialTheme.typography.bodyMedium)
        if(mismatch)GlassOutlinedButton(onClick={confirmation=true},enabled=!installing&&!disabled){Text("Reinstalar Collector")}
        Text("Após parear, desative a depuração ADB.",style=MaterialTheme.typography.bodySmall)
    }}
    if(confirmation)AlertDialog(onDismissRequest={confirmation=false},title={Text(if(mismatch)"Reinstalar Collector?" else "Configurar sua Fire TV?")},text={Text(if(mismatch)"A assinatura mudou. Remover somente o Collector antigo apaga seu pareamento e configurações. O Nuvio não será removido." else "O app instalará o Collector e concederá a ele acesso de notificação para consultar a MediaSession do Nuvio. Confirme somente na sua própria TV. Você poderá revogar o acesso depois.")},confirmButton={TextButton(onClick={confirmation=false;install(mismatch)}){Text("Confirmar")}},dismissButton={TextButton(onClick={confirmation=false}){Text("Cancelar")}})
}
