//
// Created by Milk on 2021/5/16.
//

#include "DexDump.h"
#include "utils/HexDump.h"
#include "utils/Log.h"
#include "VmCore.h"
#include "utils/PointerCheck.h"
#include "jnihook/Art.h"
#include "jnihook/ArtM.h"

#include <cstdio>
#include <fcntl.h>
#include <cstring>
#include <cstdlib>
#include <map>
#include <list>
#include <sys/mman.h>
#include "unistd.h"

#include "dex/dex_file-inl.h"
#include "dex/dex_file_loader.h"
#include "jnihook/JniHook.h"
#include "xhook/xhook.h"
#include "Dobby/include/dobby.h"

#define _uintval(p)               reinterpret_cast<uintptr_t>(p)
#define _ptr(p)                   reinterpret_cast<void *>(p)
#define _align_up(x, n)           (((x) + ((n) - 1)) & ~((n) - 1))
#define _align_down(x, n)         ((x) & -(n))
#define _page_size                4096
#define _page_align(n)            _align_up(static_cast<uintptr_t>(n), _page_size)
#define _ptr_align(x)             _ptr(_align_down(reinterpret_cast<uintptr_t>(x), _page_size))
#define _make_rwx(p, n)           ::mprotect(_ptr_align(p), \
                                              _page_align(_uintval(p) + n) != _page_align(_uintval(p)) ? _page_align(n) + _page_size : _page_align(n), \
                                              PROT_READ | PROT_WRITE | PROT_EXEC)

using namespace std;
static int beginOffset = -2;
static const char *dumpPath;
std::list<int> dumped;
//这里本来用于hook程序获取cmdline的内容，但是实际测试并不能过检测。
//char *fakePath;

//这里是读取dex大小的位置（其实应该直接读地址？）
int read_little_endian_uint32(const uint8_t *address) {
    return ((int)address[0]      ) |
           ((int)address[1] <<  8) |
           ((int)address[2] << 16) |
           ((int)address[3] << 24);
}

void handleDumpByDexFile(void *dex_file) {
    char magic[8] = {0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00};
    if (!PointerCheck::check(dex_file)) {
        return;
    }
    auto dexFile = static_cast<art_lkchan::DexFile *>(dex_file);

    int size;
    //安卓15dex_file构造参数把size删了
    if (android_get_device_api_level() >= 35){
        const uint8_t *base = dexFile->Begin();
        //从dex起始地址偏移0x20的位置存储的是dex的大小
        auto size_begin = base + 0x20;
        size = read_little_endian_uint32(size_begin);
    }else{
        size = dexFile->Size();
    }

    list<int>::iterator iterator;
    for (iterator = dumped.begin(); iterator != dumped.end(); ++iterator) {
        int value = *iterator;
        if (size == value) {
            return;
        }
    }
    void *buffer = malloc(size);
    if (buffer) {
        memcpy(buffer, dexFile->Begin(), size);
        // fix magic
        memcpy(buffer, magic, sizeof(magic));

        const bool kVerifyChecksum = false;
        const bool kVerify = true;
        const art_lkchan::DexFileLoader dex_file_loader;
        std::string error_msg;
        std::vector<std::unique_ptr<const art_lkchan::DexFile>> dex_files;
        if (!dex_file_loader.OpenAll(reinterpret_cast<const uint8_t *>(buffer),
                                     size,
                                     "",
                                     kVerify,
                                     kVerifyChecksum,
                                     &error_msg,
                                     &dex_files)) {
            // Display returned error message to user. Note that this error behavior
            // differs from the error messages shown by the original Dalvik dexdump.
            ALOGE("Open dex error %s", error_msg.c_str());
            return;
        }

        char path[1024];
        sprintf(path, "%s/hook_%d.dex", dumpPath, size);
        auto fd = open(path, O_CREAT | O_WRONLY, 0600);
        ssize_t w = write(fd, buffer, size);
        fsync(fd);
        if (w > 0) {
            ALOGE("hook dump dex ======> %s", path);
        } else {
            remove(path);
        }
        close(fd);
        free(buffer);
        dumped.push_back(size);
    }
}

HOOK_JNI(int, kill, pid_t __pid, int __signal) {
    ALOGE("hooked so kill");
    return 0;
}

HOOK_JNI(int, killpg, int __pgrp, int __signal) {
    ALOGE("hooked so killpg");
    return 0;
}


HOOK_FUN(void, LoadMethodV, void *thiz,
         void *dex_file,
         void *method,
         void *klass,
         void *mai,
         void *dst) {
    orig_LoadMethodV(thiz, dex_file, method, klass, mai, dst);
    handleDumpByDexFile(dex_file);
}

HOOK_FUN(void, LoadMethodO, void *thiz,
         void *dex_file,
         void *method,
         void *klass,
         void *dst) {
    orig_LoadMethodO(thiz, dex_file, method, klass, dst);
    handleDumpByDexFile(dex_file);
}

HOOK_FUN(void, LoadMethodM, void *thiz,
         void *thread,
         void *dex_file,
         void *method,
         void *klass,
         void *dst) {
    orig_LoadMethodM(thiz, thread, dex_file, method, klass, dst);
    handleDumpByDexFile(dex_file);
}

HOOK_FUN(void, LoadMethodL, void *thiz,
         void *thread,
         void *dex_file,
         void *method,
         void *klass) {
    orig_LoadMethodL(thiz, thread, dex_file, method, klass);
    handleDumpByDexFile(dex_file);
}

//这里是hook程序的so文件加载。
//具体请看：https://xrefandroid.com/android-15.0.0_r1/xref/art/runtime/jni/java_vm_ext.cc#72
/*HOOK_FUN(void, LoadNativeLibraryV, void *thiz,
         void *env,
         const std::string& path,
         void *class_loader,
         void *caller_class,
         void *error_msg) {
    const char* soPath = path.c_str();
    if (strstr(soPath,"libjiagu")!=nullptr) {
        log_print_debug("加载了360的壳so文件");

    }else if (strstr(soPath,"libjgdtc")!=nullptr) {
        log_print_debug("加载了360的真实so文件");
    }
    orig_LoadNativeLibraryV(thiz, env, path, class_loader, caller_class, error_msg);
//    log_print_debug(path.c_str());
}*/

void init(JNIEnv *env) {
    const char *soName = ".*\\.so$";
    xhook_register(soName, "kill", (void *) new_kill,
                   (void **) (&orig_kill));
    xhook_register(soName, "killpg", (void *) new_killpg,
                   (void **) (&orig_killpg));

    xhook_refresh(0);

    jlongArray emptyCookie = VmCore::loadEmptyDex(env);
    if (emptyCookie==nullptr){
        return;
    }
    jsize arrSize = env->GetArrayLength(emptyCookie);
    if (env->ExceptionCheck() == JNI_TRUE) {
        return;
    }
    jlong *long_data = env->GetLongArrayElements(emptyCookie, nullptr);

    for (int i = 0; i < arrSize; ++i) {
        jlong cookie = long_data[i];
        if (cookie == 0) {
            continue;
        }
        auto dex = reinterpret_cast<char *>(cookie);
        for (int ii = 0; ii < 10; ++ii) {
            auto value = *(size_t *) (dex + ii * sizeof(size_t));
            if (value == 1872) {
                beginOffset = ii - 1;
                // auto dexBegin = *(size_t *) (dex + beginOffset * sizeof(size_t));
                // HexDump(reinterpret_cast<char *>(dexBegin), 10, 0);
                env->ReleaseLongArrayElements(emptyCookie, long_data, 0);
                return;
            }
        }
    }
    env->ReleaseLongArrayElements(emptyCookie, long_data, 0);
    beginOffset = -1;
}

//回填codeitem这里实在是看不懂，从安卓13开始便没有修复，使用动态加载代替的
void fixCodeItem(JNIEnv *env, const art_lkchan::DexFile *dex_file_, size_t begin) {
    //遍历dex_file_的classdef
    for (size_t classdef_ctr = 0; classdef_ctr < dex_file_->NumClassDefs(); ++classdef_ctr) {
        //取出dexfile的classdef以及一些类的数据
        const art_lkchan::DexFile::ClassDef &cd = dex_file_->GetClassDef(classdef_ctr);
        const uint8_t *class_data = dex_file_->GetClassData(cd);
        auto &classTypeId = dex_file_->GetTypeId(cd.class_idx_);
        std::string class_name = dex_file_->GetTypeDescriptor(classTypeId);

        if (class_data != nullptr) {
            //这里获取类的所有方法数据
            art_lkchan::ClassDataItemIterator cdit(*dex_file_, class_data);
            cdit.SkipAllFields();
            //遍历这个类的所有方法
            while (cdit.HasNextMethod()) {
                //获取类中的方法信息
                const art_lkchan::DexFile::MethodId &method_id_item = dex_file_->GetMethodId(
                        cdit.GetMemberIndex());
                auto method_name = dex_file_->GetMethodName(method_id_item);
                auto method_signature = dex_file_->GetMethodSignature(
                        method_id_item).ToString().c_str();
                //通过反射方式获取一个返回值
                auto java_method = VmCore::findMethod(env, class_name.c_str(), method_name,
                                                      method_signature);
                if (java_method) {
                    //反射获取方法的artMethod字段的指针  -- http://aospxref.com/android-14.0.0_r2/xref/libcore/ojluni/src/main/java/java/lang/reflect/Executable.java
                    auto artMethod = ArtM::GetArtMethod(env, java_method);

                    const art_lkchan::DexFile::CodeItem *orig_code_item = cdit.GetMethodCodeItem();

                    if (cdit.GetMethodCodeItemOffset() && orig_code_item) {

                        auto codeItemSize = dex_file_->GetCodeItemSize(*orig_code_item);
                        //安卓高版本这里感觉可以替换成hook artMethod 的 SetCodeItem 函数，毕竟函数抽取壳总是要还原codeitem的
                        auto new_code_item =
                                begin + ArtM::GetArtMethodDexCodeItemOffset(artMethod);

                        memcpy((void *) orig_code_item,
                               (void *) new_code_item,
                               codeItemSize);

                    }
                } else {
                    env->ExceptionClear();
                }
                cdit.Next();
            }
        }
    }
}

void DexDump::cookieDumpDex(JNIEnv *env, jlong cookie, jstring dir, jboolean fix) {
    //没有执行初始化的话执行初始化
    if (beginOffset == -2) {
        init(env);
    }
    if (beginOffset == -1) {
        ALOGD("dumpDex not support!");
        return;
    }
    char magic[8] = {0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00};
    //获取cookie指针地址
    auto base = reinterpret_cast<char *>(cookie);
    //获取dex文件在内存中的起始地址
    auto begin = *(size_t *) (base + beginOffset * sizeof(size_t));
    if (!PointerCheck::check(reinterpret_cast<void *>(begin))) {
        return;
    }
    //定义写出的dex文件路径
    auto dirC = env->GetStringUTFChars(dir, 0);
    //从起始地址偏移0x20来获取dex文件实际大小
    auto dexSizeOffset = ((unsigned long) begin) + 0x20;
    int size = read_little_endian_uint32((const uint8_t*)dexSizeOffset);//*(size_t *) dexSizeOffset;
    //申请一块dex长度的地址存放dex数据
    void *buffer = malloc(size);
    if (buffer) {
        //如果地址可用（申请成功）把dex数据复制到新地址
        memcpy(buffer, reinterpret_cast<const void *>(begin), size);
        // fix magic
        memcpy(buffer, magic, sizeof(magic));

        const bool kVerifyChecksum = false;
        const bool kVerify = true;
        //创造一个DexFileLoader变量
        const art_lkchan::DexFileLoader dex_file_loader;
        std::string error_msg;
        std::vector<std::unique_ptr<const art_lkchan::DexFile>> dex_files;
        //把dex对应的一些数据写到这个DexFileLoader变量中
        if (!dex_file_loader.OpenAll(reinterpret_cast<const uint8_t *>(buffer),
                                     size,
                                     "",
                                     kVerify,
                                     kVerifyChecksum,
                                     &error_msg,
                                     &dex_files)) {
            // Display returned error message to user. Note that this error behavior
            // differs from the error messages shown by the original Dalvik dexdump.
            ALOGE("Open dex error %s", error_msg.c_str());
            return;
        }
        //判断是否开启了深度脱壳
        if (fix) {
            //开启了深度脱壳的话把JNIEnv *,DexFile原始指针？（不是很懂）,Dex在内存中起始地址传入fixCodeItem开始修复方法CodeItem
            fixCodeItem(env, dex_files[0].get(), begin);
        }
        //创建写出的dex文件
        char path[1024];
        sprintf(path, "%s/cookie_%d.dex", dirC, size);
        auto fd = open(path, O_CREAT | O_WRONLY, 0600);
        //写出dex文件并刷新数据
        ssize_t w = write(fd, buffer, size);
        fsync(fd);
        if (w > 0) {
            ALOGE("cookie dump dex ======> %s", path);
        } else {
            remove(path);
        }
        close(fd);
        //释放内存
        free(buffer);
        env->ReleaseStringUTFChars(dir, dirC);
    }
}

void DexDump::hookDumpDex(JNIEnv *env, jstring dir) {
    dumpPath = env->GetStringUTFChars(dir, 0);
    const char *libart = "libart.so";

    //Vanilla Ice Cream  --安卓15
    void *loadMethod = DobbySymbolResolver(libart,
                                           "_ZN3art11ClassLinker10LoadMethodERKNS_7DexFileERKNS_13ClassAccessor6MethodENS_6ObjPtrINS_6mirror5ClassEEEPNS0_25MethodAnnotationsIteratorEPNS_9ArtMethodE");
    // UpsideDownCake  --安卓14
    if(!loadMethod) {
        loadMethod = DobbySymbolResolver(libart,
                                         "_ZN3art11ClassLinker10LoadMethodERKNS_7DexFileERKNS_13ClassAccessor6MethodENS_6ObjPtrINS_6mirror5ClassEEEPNS_9ArtMethodE");
    }

    if (!loadMethod) {
        // L
        loadMethod = DobbySymbolResolver(libart,
                                         "_ZN3art11ClassLinker10LoadMethodEPNS_6ThreadERKNS_7DexFileERKNS_21ClassDataItemIteratorENS_6HandleINS_6mirror5ClassEEE");
    }

    if (!loadMethod) {
        // M
        loadMethod = DobbySymbolResolver(libart,
                                         "_ZN3art11ClassLinker10LoadMethodEPNS_6ThreadERKNS_7DexFileERKNS_21ClassDataItemIteratorENS_6HandleINS_6mirror5ClassEEEPNS_9ArtMethodE");
    }
    if (!loadMethod) {
        // O
        loadMethod = DobbySymbolResolver(libart,
                                         "_ZN3art11ClassLinker10LoadMethodERKNS_7DexFileERKNS_13ClassAccessor6MethodENS_6HandleINS_6mirror5ClassEEEPNS_9ArtMethodE");
    }

    _make_rwx(loadMethod, _page_size);
    if (loadMethod) {
        if (android_get_device_api_level() >= 35){
            DobbyHook(loadMethod,(void *) new_LoadMethodV,
                      (void **) &orig_LoadMethodV);
        } else if (android_get_device_api_level() >= __ANDROID_API_O__) {
            DobbyHook(loadMethod, (void *) new_LoadMethodO,
                      (void **) &orig_LoadMethodO);
        } else if (android_get_device_api_level() >= __ANDROID_API_M__) {
            DobbyHook(loadMethod, (void *) new_LoadMethodM,
                      (void **) &orig_LoadMethodM);
        } else {
            DobbyHook(loadMethod, (void *) new_LoadMethodL,
                      (void **) &orig_LoadMethodL);
        }
    }
}

//hook程序加载的so文件
/*void DexDump::hookBeforeSoLoad(const char *fakePathArg) {
    fakePath = (char *)fakePathArg;
    const char *soName = "libart.so";
    //hook程序加载的so文件
    void *loadMethod = DobbySymbolResolver(soName,"_ZN3art9JavaVMExt17LoadNativeLibraryEP7_JNIEnvRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEP8_jobjectP7_jclassPS9_");
    _make_rwx(loadMethod, _page_size);
    DobbyHook(loadMethod,(void *) new_LoadNativeLibraryV,
              (void **) &orig_LoadNativeLibraryV);
}*/
