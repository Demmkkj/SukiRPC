#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"
#include <jni.h>
#include <memory>
#include <string>
#include <atomic>
#include <android/log.h>

static std::shared_ptr<discordpp::Client> client;
static std::atomic<int> outcome{0};
static std::atomic<unsigned long long> serial{0};
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
    if(!client){outcome=-1;return;}
    auto id=++serial;outcome=0;
    auto t=utf8(env,title),d=utf8(env,detail),a=utf8(env,art);
    discordpp::Activity activity;
    activity.SetType(watching?discordpp::ActivityTypes::Watching:discordpp::ActivityTypes::Playing);
    auto name=utf8(env,activityName);
    activity.SetName(name.empty()?std::string("Nuvio TV"):name);
    if(t.size()>=2)activity.SetDetails(t);
    if(d.size()>=2)activity.SetState(d);
    if(start>0){discordpp::ActivityTimestamps ts;ts.SetStart(static_cast<uint64_t>(start));if(end>start)ts.SetEnd(static_cast<uint64_t>(end));activity.SetTimestamps(ts);}
    if(!a.empty()){discordpp::ActivityAssets assets;assets.SetLargeImage(a);activity.SetAssets(assets);}
    client->UpdateRichPresence(activity,[id](const discordpp::ClientResult& result){
        if(serial.load()!=id)return;
        outcome=result.Successful()?1:-1;
        __android_log_print(ANDROID_LOG_INFO,"NuvioRPC","RPC callback: %s",result.Successful()?"ok":"failed");
    });
}
extern "C" JNIEXPORT void JNICALL Java_dev_nuvio_presence_NativeRpc_nativeClear(JNIEnv*,jobject){++serial;outcome=0;if(client)client->ClearRichPresence();}
extern "C" JNIEXPORT void JNICALL Java_dev_nuvio_presence_NativeRpc_nativeTick(JNIEnv*,jobject){if(client)discordpp::RunCallbacks();}
extern "C" JNIEXPORT jint JNICALL Java_dev_nuvio_presence_NativeRpc_nativeOutcome(JNIEnv*,jobject){return outcome.load();}
