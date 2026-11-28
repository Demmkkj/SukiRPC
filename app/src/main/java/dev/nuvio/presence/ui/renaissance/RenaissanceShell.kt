package dev.nuvio.presence.ui.renaissance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.nuvio.presence.ui.themes.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun RenaissanceShell(tab:Int,onTab:(Int)->Unit,content: @Composable (PaddingValues) -> Unit){
    val id=LocalThemeId.current
    val mode=LocalGraphics.current
    val colors=MaterialTheme.colorScheme
    val blur=if(useThemeBlur(id,mode))remember(id){HazeState()}else null
    val context=LocalContext.current
    val version=remember(context){runCatching{context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull().orEmpty()}
    val parent=parentRoute(tab)
    val current=primaryRoute(tab)
    BackHandler(enabled=parent!=null){parent?.let(onTab)}
    CompositionLocalProvider(LocalUltraBackdrop provides blur){
        Box(Modifier.fillMaxSize()){
            Box(if(blur!=null)Modifier.fillMaxSize().hazeSource(blur) else Modifier.fillMaxSize()){
                ThemeBackdrop()
                LocalSkin.current?.let{ThemeWallpaper(LocalBackground.current,it,mode)}
            }
            Scaffold(
                containerColor=Color.Transparent,
                contentColor=colors.onBackground,
                topBar={
                    Column(if(blur!=null)Modifier.ultraGlass(RoundedCornerShape(0.dp),colors.surface.copy(alpha=.72f),strong=true) else Modifier.background(colors.background.copy(alpha=.96f))){
                        TopAppBar(
                            title={
                                if(tab==0)Text("suki.",style=MaterialTheme.typography.headlineMedium,
                                    fontWeight=FontWeight.Bold,letterSpacing=(-1.2).sp)
                                else Text(pageName(tab),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                            },
                            navigationIcon={if(parent!=null)IconButton(onClick={onTab(parent)}){Icon(Icons.Filled.ArrowBack,"Voltar")}},
                            actions={
                                if(tab==0)Text(version,fontFamily=Mono,style=MaterialTheme.typography.labelSmall,
                                    color=colors.onSurfaceVariant,modifier=Modifier.padding(end=16.dp))
                                else Text(id.label,maxLines=1,overflow=TextOverflow.Ellipsis,
                                    style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,
                                    modifier=Modifier.widthIn(max=110.dp).padding(end=12.dp))
                            },
                            colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent,
                                titleContentColor=colors.onBackground,navigationIconContentColor=colors.onBackground)
                        )
                        if(current==9){
                            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                FilterChip(selected=tab==9,onClick={onTab(9)},label={Text("Aplicativos")},modifier=Modifier.weight(1f))
                                FilterChip(selected=tab==1,onClick={onTab(1)},label={Text("Dispositivos")},modifier=Modifier.weight(1f))
                            }
                        }
                        HorizontalDivider(color=colors.outlineVariant)
                    }
                },
                bottomBar={
                    Surface(color=if(blur!=null)Color.Transparent else colors.surface){
                        Column(if(blur!=null)Modifier.ultraGlass(RoundedCornerShape(0.dp),colors.surface.copy(alpha=.78f),strong=true) else Modifier){
                            HorizontalDivider(color=colors.outlineVariant)
                            NavigationBar(containerColor=if(blur!=null)Color.Transparent else colors.surface,tonalElevation=0.dp){
                                val items=listOf(
                                    Triple(0,"Início",Icons.Filled.Home),
                                    Triple(9,"Fontes",Icons.Filled.List),
                                    Triple(5,"Studio",Icons.Filled.AddCircle),
                                    Triple(8,"Histórico",Icons.Filled.DateRange),
                                    Triple(3,"Ajustes",Icons.Filled.Settings)
                                )
                                items.forEach{(route,label,icon)->
                                    NavigationBarItem(selected=current==route,onClick={onTab(route)},
                                        icon={Icon(icon,contentDescription=null)},
                                        label={Text(label,maxLines=1,style=MaterialTheme.typography.labelSmall)},
                                        alwaysShowLabel=false,
                                        colors=NavigationBarItemDefaults.colors(
                                            selectedIconColor=colors.primary,selectedTextColor=colors.primary,
                                            indicatorColor=colors.primary.copy(alpha=.14f),
                                            unselectedIconColor=colors.onSurfaceVariant,unselectedTextColor=colors.onSurfaceVariant))
                                }
                            }
                        }
                    }
                },
                content=content
            )
        }
    }
}
