#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"
#include <jni.h>
#include <memory>
#include <string>
#include "rpc_outcome.h"
#include <android/log.h>

static std::shared_ptr<discordpp::Client> client;
static RpcOutcome outcome;
static std::string utf8(JNIEnv* env,jbyteArray a){
    if(!a)return {};
    std::string s(static_cast<size_t>(env->GetArrayLength(a)), '\0');
    if(!s.empty())env->GetByteArrayRegion(a,0,static_cast<jsize>(s.size()),reinterpret_cast<jbyte*>(s.data()));
    return s;
}
extern "C" JNIEXPORT void JNICALL Java_dev_nuvio_presence_NativeRpc_nativeInit(JNIEnv*,jobject){
    if(!client){client=std::make_shared<discordpp::Client>();client->SetApplicationId(1575308145528406036ULL);}
}
extern "C" JNIEXPORT void JNICALL Java_dev_nuvio_presence_NativeRpc_nativePublish(JNIEnv* env,jobject,jbyteArray title,jbyteArray detail,jbyteArray art,jboolean watching,jlong start,jlong end,jbyteArray activityName){
    const auto id = outcome.begin();
    if (!client) { outcome.complete(id, false); return; }
    auto t=utf8(env,title),d=utf8(env,detail),a=utf8(env,art);
    discordpp::Activity activity;
    activity.SetType(watching?discordpp::ActivityTypes::Watching:discordpp::ActivityTypes::Playing);
    auto name=utf8(env,activityName);
    activity.SetName(name.empty()?std::string("Nuvio TV"):name);
    if(t.size()>=2)activity.SetDetails(t);
    if(d.size()>=2)activity.SetState(d);
    if(start>0||end>0){discordpp::ActivityTimestamps ts;if(start>0)ts.SetStart(static_cast<uint64_t>(start));if(end>start)ts.SetEnd(static_cast<uint64_t>(end));activity.SetTimestamps(ts);}
    if(!a.empty()){discordpp::ActivityAssets assets;assets.SetLargeImage(a);activity.SetAssets(assets);}
    client->UpdateRichPresence(activity,[id](const discordpp::ClientResult& result){
        if (!outcome.complete(id, result.Successful())) return;
        __android_log_print(ANDROID_LOG_INFO,"NuvioRPC","RPC callback: %s",result.Successful()?"ok":"failed");
    });
}
extern "C" JNIEXPORT void JNICALL Java_dev_nuvio_presence_NativeRpc_nativeStudio(JNIEnv* env,jobject,jobjectArray fields,jint type,jlong start,jlong end){
    const auto id = outcome.begin();
    if (!client || !fields || env->GetArrayLength(fields) != 11) { outcome.complete(id, false); return; }
    std::string f[11];
    for(int i=0;i<11;i++){auto a=(jbyteArray)env->GetObjectArrayElement(fields,i);f[i]=utf8(env,a);if(a)env->DeleteLocalRef(a);}
    discordpp::Activity activity;
    switch(type){
        case 1:activity.SetType(discordpp::ActivityTypes::Watching);break;
        case 2:activity.SetType(discordpp::ActivityTypes::Listening);break;
        case 3:activity.SetType(discordpp::ActivityTypes::Competing);break;
        default:activity.SetType(discordpp::ActivityTypes::Playing);break;
    }
    activity.SetName(f[0]);
    if(!f[1].empty())activity.SetDetails(f[1]);
    if(!f[2].empty())activity.SetState(f[2]);
    if(!f[3].empty()||!f[5].empty()){
        discordpp::ActivityAssets assets;
        if(!f[3].empty()){assets.SetLargeImage(f[3]);if(!f[4].empty())assets.SetLargeText(f[4]);}
        if(!f[5].empty()){assets.SetSmallImage(f[5]);if(!f[6].empty())assets.SetSmallText(f[6]);}
        activity.SetAssets(assets);
    }
    if(start>0){discordpp::ActivityTimestamps ts;ts.SetStart(static_cast<uint64_t>(start));if(end>start)ts.SetEnd(static_cast<uint64_t>(end));activity.SetTimestamps(ts);}
    for(int i=7;i<11;i+=2){if(!f[i].empty()&&!f[i+1].empty()){discordpp::ActivityButton b;b.SetLabel(f[i]);b.SetUrl(f[i+1]);activity.AddButton(b);}}
    client->UpdateRichPresence(activity,[id](const discordpp::ClientResult& result){ outcome.complete(id, result.Successful()); });
}
extern "C" JNIEXPORT void JNICALL Java_dev_nuvio_presence_NativeRpc_nativeClear(JNIEnv*,jobject){outcome.begin();if(client)client->ClearRichPresence();}
extern "C" JNIEXPORT void JNICALL Java_dev_nuvio_presence_NativeRpc_nativeTick(JNIEnv*,jobject){if(client)discordpp::RunCallbacks();}
extern "C" JNIEXPORT jint JNICALL Java_dev_nuvio_presence_NativeRpc_nativeOutcome(JNIEnv*,jobject){return outcome.read();}

