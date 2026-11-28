package dev.nuvio.presence.ui.themes

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import kotlinx.coroutines.launch
import dev.nuvio.presence.ui.renaissance.renaissanceTypography
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

internal enum class ThemeId(val label:String,val subtitle:String,val radius:Int,val spacing:Int,val columns:Int) {
    GENESIS("Genesis", "Violeta sobre grafite", 26, 16, 2),
    GENESIS_LIGHT("Genesis Claro", "Violeta sobre porcelana", 26, 16, 2),
    RENAISSANCE("Renaissance","Grafite, sinal e precisão",16,14,2),
    ECLIPSE("Eclipse 2.0","Atmosfera lunar e violeta",20,16,2),
    OBSIDIAN("Obsidian","Monocromático e preciso",2,8,1),
    RELIC("Relic","Relíquias e metais antigos",4,14,2),
    PHANTOM("Phantom","Vidro fosco e névoa azul",24,20,2),
    LIQUID("Liquid Glass","Reflexos e cápsulas translúcidas",32,18,2),
    AERO("Frutiger Aero","Água, céu e tecnologia dos anos 2000",14,12,2),
    PRISM("Prism","Violeta, azul e rosa",22,16,2),
    CRIMSON("Crimson","Carmesim sobre preto OLED",6,14,2),
    FROST("Frost","Vidro glacial",18,16,2),
    AURORA("Aurora","Luzes esmeralda e oceano",22,16,2),
    NEBULA("Nebula","Azul cósmico e rosa",24,16,2),
    ECHO("Echo","Ondas em azul profundo e ciano",18,16,2),
    CHRONICLE("Chronicle","Arquivo em âmbar e ouro",14,16,2),
    HORIZON("Horizon","Horizonte espacial em violeta e ciano",20,16,2);
    companion object { fun parse(value:String?)=entries.firstOrNull{it.name==value}?:GENESIS }
}
internal fun ThemeId.isGenesis(): Boolean = this == ThemeId.GENESIS || this == ThemeId.GENESIS_LIGHT

internal enum class GraphicsMode { ECO, BALANCED, ULTRA; companion object { fun parse(value:String?)=entries.firstOrNull{it.name==value}?:BALANCED } }
internal val LocalThemeId=staticCompositionLocalOf{ThemeId.GENESIS}
internal val LocalGraphics=staticCompositionLocalOf{GraphicsMode.BALANCED}
private fun c(n:Long)=Color(n)
private data class Palette(val bg:Color,val surface:Color,val accent:Color,val ink:Color,val muted:Color,val line:Color,val glow:Color,val onAccent:Color)
private fun officialPalette(id:ThemeId):Palette=when(id){
    ThemeId.GENESIS->Palette(c(0xff100e17),c(0xff1e192b),c(0xffc7a8ff),c(0xfff7f1ff),c(0xffc4b8d2),c(0xff635474),c(0xff312247),c(0xff281542))
    ThemeId.GENESIS_LIGHT->Palette(c(0xfff8f4ff),c(0xffeee6f9),c(0xff5d3699),c(0xff231830),c(0xff5f516d),c(0xff9e8caf),c(0xffe4d5f9),Color.White)
    ThemeId.RENAISSANCE->Palette(c(0xff0b0c0f),c(0xff171a20),c(0xff8c9cff),c(0xffedf0f8),c(0xffa3a8b6),c(0xff343944),c(0xff202538),c(0xff101526))
    ThemeId.HORIZON->Palette(c(0xff050812),c(0xff101b36),c(0xff7ee9f3),c(0xffeffaff),c(0xffb2c1df),c(0xff425578),c(0xff41327e),c(0xff07212d))
    ThemeId.CHRONICLE->Palette(c(0xff100e0a),c(0xff211d16),c(0xffffbe62),c(0xfffff2db),c(0xffcfbfa6),c(0xff81673e),c(0xff44321e),c(0xff251a09))
    ThemeId.ECHO->Palette(c(0xff091528),c(0xff10283e),c(0xff85def1),c(0xffe5f7ff),c(0xffacccdb),c(0xff385d80),c(0xff1b4987),c(0xff08222e))
    ThemeId.AURORA->Palette(c(0xff071922),c(0xff123341),c(0xff74eac1),c(0xffeffffa),c(0xffabcac4),c(0xff49777d),c(0xff4c427a),c(0xff032e23))
    ThemeId.NEBULA->Palette(c(0xff090f30),c(0xff202244),c(0xfff1b3df),c(0xfffff5ff),c(0xffc5b8d9),c(0xff655886),c(0xff513b78),c(0xff35152d))
    ThemeId.PRISM->Palette(c(0xff120e24),c(0xff24223c),c(0xfff1a5d9),c(0xfff7f2ff),c(0xffc6bad7),c(0xff65567e),c(0xff323c80),c(0xff2a0b21))
    ThemeId.CRIMSON->Palette(Color.Black,c(0xff180c10),c(0xffff90a4),c(0xffffedf0),c(0xffceafb6),c(0xff723146),c(0xff300917),c(0xff310614))
    ThemeId.FROST->Palette(c(0xffdcecf6),Color.White,c(0xff205577),c(0xff102b3e),c(0xff415c6d),c(0xff819eaf),c(0xffb2d7ed),Color.White)
    ThemeId.ECLIPSE->Palette(c(0xff0e0916),c(0xff21172e),c(0xffd0adff),c(0xfff5efff),c(0xffbdb0ce),c(0xff5a436e),c(0xff392359),c(0xff241037))
    ThemeId.OBSIDIAN->Palette(Color.Black,c(0xff111111),Color.White,c(0xfff4f4f4),c(0xffb6b6b6),c(0xff454545),Color.Black,Color.Black)
    ThemeId.RELIC->Palette(c(0xff17130f),c(0xff282119),c(0xffd8bb81),c(0xfff4e7cd),c(0xffc3b59c),c(0xff86704b),c(0xff453520),c(0xff20180e))
    ThemeId.PHANTOM->Palette(c(0xff0b1420),c(0xff1b3042),c(0xffa3d6ff),c(0xffedf6ff),c(0xffb7cddd),c(0xff507088),c(0xff254961),c(0xff102232))
    ThemeId.LIQUID->Palette(c(0xffd5e3f5),c(0xffe8eff9),c(0xff234e87),c(0xff162e51),c(0xff405674),c(0xff819ab9),c(0xffb5a5e9),Color.White)
    ThemeId.AERO->Palette(c(0xffd0f3ff),c(0xffedfbff),c(0xff00578d),c(0xff103a46),c(0xff315e69),c(0xff4c98ba),c(0xff81d95b),Color.White)
}
internal val LocalSkin=staticCompositionLocalOf<Skin?> { null }
internal val LocalBackground=staticCompositionLocalOf<File?> { null }
internal fun officialSkin(id:ThemeId):Skin {
    val p=officialPalette(id)
    val colors=listOf(p.accent,p.accent,p.accent,p.bg,p.surface,p.ink,p.muted,p.line,p.glow,p.onAccent)
    val nums=defaultNumbers+mapOf("radius" to id.radius,"blur" to (if(id in listOf(ThemeId.AERO,ThemeId.LIQUID,ThemeId.PHANTOM))24 else 0),
        "motion" to (if(id==ThemeId.AURORA)25 else 0),"glow" to (if(id==ThemeId.OBSIDIAN)0 else 45))
    return Skin(id.label,id.name,colorKeys.zip(colors.map{"#%06X".format(it.toArgb() and 0xffffff)}).toMap(),nums)
}
private fun Skin.color(key:String)=Color(android.graphics.Color.parseColor(colors.getValue(key)))
@Composable private fun palette(id:ThemeId):Palette {
    val s=LocalSkin.current?:return officialPalette(id)
    return remember(s){Palette(s.color("background"),s.color("surface"),s.color("primary"),s.color("text"),s.color("muted"),s.color("border"),s.color("gradient"),s.color("onPrimary"))}
}
@Composable internal fun MetamorphosisTheme(id:ThemeId,mode:GraphicsMode,content:@Composable ()->Unit){
    val p=palette(id)
    val base=if(id==ThemeId.LIQUID||id==ThemeId.AERO||id==ThemeId.FROST||id==ThemeId.GENESIS_LIGHT)lightColorScheme() else darkColorScheme()
    val colors=base.copy(primary=p.accent,onPrimary=p.onAccent,primaryContainer=p.surface,onPrimaryContainer=p.ink,
        secondary=LocalSkin.current?.color("secondary")?:p.accent,onSecondary=p.onAccent,secondaryContainer=p.surface,onSecondaryContainer=p.ink,
        tertiary=LocalSkin.current?.color("accent")?:p.accent,onTertiary=p.onAccent,tertiaryContainer=p.surface,onTertiaryContainer=p.ink,
        background=p.bg,onBackground=p.ink,surface=p.surface,onSurface=p.ink,surfaceVariant=p.surface,onSurfaceVariant=p.muted,
        surfaceContainer=p.surface,surfaceContainerLow=p.surface,surfaceContainerHigh=p.surface,surfaceContainerHighest=p.surface,
        surfaceContainerLowest=p.bg,outline=p.line,outlineVariant=p.line.copy(alpha=.5f),surfaceTint=Color.Transparent)
    val font=LocalSkin.current?.let{when(it.styles["font"]){"Serif"->FontFamily.Serif;"Mono"->FontFamily.Monospace;else->FontFamily.SansSerif}}?:when(id){ThemeId.RELIC->FontFamily.Serif;ThemeId.OBSIDIAN->FontFamily.Monospace;else->FontFamily.SansSerif}
    val t=Typography()
    val type=if((id.isGenesis() || id==ThemeId.RENAISSANCE) && LocalSkin.current==null) renaissanceTypography() else t.copy(headlineMedium=t.headlineMedium.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),titleLarge=t.titleLarge.copy(fontFamily=font),titleMedium=t.titleMedium.copy(fontFamily=font),labelLarge=t.labelLarge.copy(fontFamily=font),bodyMedium=t.bodyMedium.copy(fontFamily=font))
    val shape=RoundedCornerShape((LocalSkin.current?.numbers?.get("radius")?:id.radius).dp)
    CompositionLocalProvider(LocalThemeId provides id,LocalGraphics provides mode,LocalUltraBackdrop provides null,LocalContentColor provides p.ink){
        MaterialTheme(colorScheme=colors,typography=type,shapes=Shapes(extraSmall=shape,small=shape,medium=shape,large=shape,extraLarge=shape),content=content)
    }
}
@Composable internal fun ThemeBackdrop(modifier:Modifier=Modifier){
    val id=LocalThemeId.current;val mode=LocalGraphics.current;val p=palette(id)
    // The animation is created only for the real screen and only in Ultra.
    // Theme previews, Balanced and Eco do not run a continuous animation.
    val skin=LocalSkin.current
    val context=LocalContext.current
    val lifecycle=(context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    var visible by remember(lifecycle){mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)==true)}
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver { _,_->visible=lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)==true }
        lifecycle?.addObserver(observer);onDispose{lifecycle?.removeObserver(observer)}
    }
    val animated = visible && android.animation.ValueAnimator.areAnimatorsEnabled() && mode==GraphicsMode.ULTRA &&
        (if(skin!=null)(skin.numbers.getValue("motion")>0) else LocalUltraBackdrop.current!=null && !id.isGenesis())
    val motion = if (animated) {
        val transition = rememberInfiniteTransition(label = "ambient glass")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(15000-(skin?.numbers?.get("speed")?:50)*100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ambient motion")
    } else null
    Canvas(modifier.fillMaxSize().background(p.bg)){
        val phase = (motion?.value ?: 0f)*(skin?.numbers?.get("motion")?:100)/100f
        if(mode!=GraphicsMode.ECO && id!=ThemeId.OBSIDIAN){
            if(skin!=null){
                if(skin.styles["gradientStyle"]!="Solid"){val colors=listOf(p.glow,p.bg);drawRect(if(skin.styles["gradientStyle"]=="Diagonal") Brush.linearGradient(colors) else Brush.radialGradient(colors,Offset(size.width*(.7f-.2f*phase),size.height*.2f),size.maxDimension*.8f))};val glow=skin.numbers.getValue("glow")/200f;if(glow>0)drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha=glow),Color.Transparent),Offset(size.width*.18f,size.height*.65f),size.width*.7f),size.width*.7f,Offset(size.width*.18f,size.height*.65f))
            }else if(id==ThemeId.AERO){
                drawRect(Brush.verticalGradient(listOf(c(0xff62cafa),c(0xffdef9ff),c(0xff81d95b))))
                val count=if(mode==GraphicsMode.ULTRA) 11 else 5
                repeat(count){i->val r=size.width*(.04f+(i%3)*.025f);val o=Offset(size.width*((i*37%97)/100f)+size.width*.025f*phase*(if(i%2==0)1 else -1),size.height*((i*29%89)/100f)-size.height*.045f*phase);drawCircle(Color.White.copy(alpha=.22f),r,o);drawCircle(Color.White.copy(alpha=.65f),r,o,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}
            }else{
                drawRect(Brush.radialGradient(listOf(p.glow,p.bg),center=Offset(size.width*(.82f-.19f*phase),size.height*(.12f+.14f*phase)),radius=size.maxDimension*.85f))
                if(id==ThemeId.PRISM||id==ThemeId.LIQUID||id==ThemeId.PHANTOM)drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha=.24f),Color.Transparent),center=Offset(size.width*.15f,size.height*.72f),radius=size.width*.7f),radius=size.width*.7f,center=Offset(size.width*.15f,size.height*.72f))
                if(animated && (id==ThemeId.LIQUID||id==ThemeId.PHANTOM)){
                    val center=Offset(size.width*(.18f+.64f*phase),size.height*.53f)
                    val radius=size.width*.52f
                    drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha=.20f),Color.Transparent),center=center,radius=radius),radius,center)
                }
                if(id==ThemeId.RELIC){val inset=12.dp.toPx();drawRect(p.line.copy(alpha=.35f),topLeft=Offset(inset,inset),size=androidx.compose.ui.geometry.Size((size.width-2*inset).coerceAtLeast(0f),(size.height-2*inset).coerceAtLeast(0f)),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}
            }
        }
    }
}
@Composable internal fun ThemePanel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    val id=LocalThemeId.current;val p=palette(id);val mode=LocalGraphics.current
    val shape=RoundedCornerShape((LocalSkin.current?.numbers?.get("radius")?:id.radius).dp)
    val glass=mode!=GraphicsMode.ECO&&(id==ThemeId.LIQUID||id==ThemeId.PHANTOM||id==ThemeId.AERO||id==ThemeId.FROST)
    val stroke = LocalSkin.current?.numbers?.get("stroke") ?: if (id.isGenesis()) 0 else 1
    val custom=LocalSkin.current
    val brush=if(custom!=null){val color=p.surface.copy(alpha=custom.numbers.getValue("opacity")/100f);Brush.verticalGradient(listOf(color,color))} else if(glass)Brush.verticalGradient(listOf(p.surface.copy(alpha=.92f),p.surface.copy(alpha=.72f),p.surface.copy(alpha=.92f))) else Brush.verticalGradient(listOf(p.surface,p.surface))
    val live=liveBlurActive()
    val panel=if(live)modifier.ultraGlass(shape,p.surface.copy(alpha=if(id==ThemeId.PHANTOM).66f else .48f),strong=true)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.20f),Color.Transparent,Color.White.copy(alpha=.08f))))
        else modifier.clip(shape).background(brush)
    val edge = if (stroke > 0) Modifier.border(stroke.dp, p.line.copy(alpha = if (glass) .65f else .8f), shape) else Modifier
    Column(panel.shadow((custom?.numbers?.get("shadow") ?: 0).dp, shape).then(edge).padding(id.spacing.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
private val destinations=listOf(0 to "Início",5 to "RPC Studio",1 to "Dispositivos",6 to "Temas",3 to "Configurações",7 to "Sobre",8 to "Chronicle",9 to "Integrações")
internal fun pageName(tab:Int)=when(tab){2->"Rich Presence";4->"Diagnóstico";else->destinations.firstOrNull{it.first==tab}?.second?:"SukiRPC"}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MetamorphosisShell(tab:Int,onTab:(Int)->Unit,content:@Composable (PaddingValues)->Unit){
    val id=LocalThemeId.current;val mode=LocalGraphics.current;val p=palette(id)
    val drawer=rememberDrawerState(DrawerValue.Closed);val scope=rememberCoroutineScope()
    val blurState=if(useThemeBlur(id,mode))remember(id){HazeState()}else null
    CompositionLocalProvider(LocalUltraBackdrop provides blurState){
    val live=blurState!=null
    val drawerShape=RoundedCornerShape(topEnd=id.radius.dp,bottomEnd=id.radius.dp)
    ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=true,drawerContent={
        ModalDrawerSheet(modifier=Modifier.widthIn(max=320.dp),drawerShape=drawerShape,drawerContainerColor=if(live)Color.Transparent else p.surface,drawerContentColor=p.ink){
            Column(Modifier.fillMaxHeight().fillMaxWidth().then(if(live)Modifier.ultraGlass(drawerShape,p.surface.copy(alpha=.85f),strong=true) else Modifier).verticalScroll(rememberScrollState()).padding(id.spacing.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                ThemePanel(Modifier.fillMaxWidth()){
                    Text(when(id){ThemeId.RELIC->"✧  SUKI / RELIC";ThemeId.AERO->"SukiRPC • Aero";ThemeId.OBSIDIAN->"SUKIRPC_";else->"SukiRPC"},style=MaterialTheme.typography.headlineMedium)
                    Text(id.label,style=MaterialTheme.typography.bodySmall,color=p.muted)
                }
                destinations.forEachIndexed{index,(key,label)->
                    val active=key==tab||(key==3&&tab in listOf(2,4))
                    val marker=when(id){ThemeId.RELIC->"◇";ThemeId.OBSIDIAN->"%02d".format(index+1);ThemeId.AERO->listOf("⌂","▶","▣","✿","⚙","ⓘ","◷","◇")[index];else->if(active)"●" else "○"}
                    NavigationDrawerItem(label={Text(label)},selected=active,onClick={onTab(key);scope.launch{drawer.close()}},icon={Text(marker,color=p.accent)},shape=RoundedCornerShape((LocalSkin.current?.numbers?.get("radius")?:id.radius).dp),colors=NavigationDrawerItemDefaults.colors(selectedContainerColor=p.accent.copy(alpha=.17f),selectedTextColor=p.ink,unselectedTextColor=p.muted))
                }
                HorizontalDivider(color=p.line);Text("METAMORPHOSIS",letterSpacing=2.sp,style=MaterialTheme.typography.labelSmall)
            }
        }
    }){
        Box(Modifier.fillMaxSize()){
            Box(if(blurState!=null)Modifier.fillMaxSize().hazeSource(blurState) else Modifier.fillMaxSize()){
                ThemeBackdrop()
                LocalSkin.current?.let { ThemeWallpaper(LocalBackground.current,it,mode) }
            }
            Scaffold(containerColor=Color.Transparent,contentColor=p.ink,topBar={Box(if(live)Modifier.ultraGlass(RoundedCornerShape(0.dp),p.surface.copy(alpha=.72f),strong=true) else Modifier){TopAppBar(title={Text(pageName(tab),maxLines=1,overflow=TextOverflow.Ellipsis)},navigationIcon={IconButton(onClick={scope.launch{drawer.open()}}){Icon(Icons.Default.Menu,contentDescription="Abrir menu lateral")}},actions={Text(id.label,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(end=12.dp))},colors=TopAppBarDefaults.topAppBarColors(containerColor=if(live)Color.Transparent else if(mode==GraphicsMode.ECO)p.bg else p.surface.copy(alpha=.87f),scrolledContainerColor=if(live)Color.Transparent else p.surface,titleContentColor=p.ink,navigationIconContentColor=p.ink,actionIconContentColor=p.ink))}},content=content)
        }
    }
    }
}
@Composable internal fun ThemeWallpaper(image:Any?,skin:Skin,mode:GraphicsMode){
                if(image!=null){
                    val n=skin.numbers
                    Box(Modifier.fillMaxSize().clipToBounds()){
                        AsyncImage(model=image,contentDescription=null,modifier=Modifier.fillMaxSize().scale(n.getValue("scale")/100f)
                            .then(if(mode==GraphicsMode.ULTRA && android.os.Build.VERSION.SDK_INT>=32)Modifier.blur(n.getValue("imageBlur").dp) else Modifier),
                            alpha=n.getValue("imageOpacity")/100f,alignment=when(skin.styles["position"]){"Top"->Alignment.TopCenter;"Bottom"->Alignment.BottomCenter;else->Alignment.Center},
                            contentScale=when(skin.styles["fit"]){"Fit"->ContentScale.Fit;"Fill"->ContentScale.FillBounds;else->ContentScale.Crop})
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=n.getValue("dim")/100f)))
                    }
                }
}

