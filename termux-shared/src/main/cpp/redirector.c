#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <android/log.h>

#define LOG_TAG "InvappRedirector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static const char* OLD_PREFIX = "data/data/com.termux";
static const char* NEW_PREFIX = "/data/data/com.invapp";

static const char* redirect_path(const char* path, char* buffer, size_t bufsize) {
    if (!path) return path;

    const char* p = path;
    // Handle ./ prefix
    if (p[0] == '.' && p[1] == '/') p += 2;
    // Handle leading /
    if (p[0] == '/') p++;

    if (strncmp(p, OLD_PREFIX, 20) == 0) {
        snprintf(buffer, bufsize, "%s%s", NEW_PREFIX, p + 20);
        // LOGI("Redirecting: %s -> %s", path, buffer);
        return buffer;
    }
    return path;
}

int open(const char* path, int flags, ...) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_open)(const char*, int, ...);
    if (!orig_open) orig_open = dlsym(RTLD_NEXT, "open");

    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode_t mode = va_arg(args, mode_t);
        va_end(args);
        return orig_open(redirected, flags, mode);
    }
    return orig_open(redirected, flags);
}

int openat(int dirfd, const char* path, int flags, ...) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_openat)(int, const char*, int, ...);
    if (!orig_openat) orig_openat = dlsym(RTLD_NEXT, "openat");

    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode_t mode = va_arg(args, mode_t);
        va_end(args);
        return orig_openat(dirfd, redirected, flags, mode);
    }
    return orig_openat(dirfd, redirected, flags);
}

int stat(const char* path, struct stat* sbuf) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_stat)(const char*, struct stat*);
    if (!orig_stat) orig_stat = dlsym(RTLD_NEXT, "stat");
    return orig_stat(redirected, sbuf);
}

int lstat(const char* path, struct stat* sbuf) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_lstat)(const char*, struct stat*);
    if (!orig_lstat) orig_lstat = dlsym(RTLD_NEXT, "lstat");
    return orig_lstat(redirected, sbuf);
}

int fstatat(int dirfd, const char* path, struct stat* sbuf, int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_fstatat)(int, const char*, struct stat*, int);
    if (!orig_fstatat) orig_fstatat = dlsym(RTLD_NEXT, "fstatat");
    return orig_fstatat(dirfd, redirected, sbuf, flags);
}

int execve(const char* filename, char* const argv[], char* const envp[]) {
    char buf[4096];
    const char* redirected = redirect_path(filename, buf, sizeof(buf));
    static int (*orig_execve)(const char*, char* const[], char* const[]);
    if (!orig_execve) orig_execve = dlsym(RTLD_NEXT, "execve");
    return orig_execve(redirected, argv, envp);
}

int access(const char* path, int mode) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_access)(const char*, int);
    if (!orig_access) orig_access = dlsym(RTLD_NEXT, "access");
    return orig_access(redirected, mode);
}

int faccessat(int dirfd, const char* path, int mode, int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_faccessat)(int, const char*, int, int);
    if (!orig_faccessat) orig_faccessat = dlsym(RTLD_NEXT, "faccessat");
    return orig_faccessat(dirfd, redirected, mode, flags);
}

int mkdirat(int dirfd, const char* path, mode_t mode) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_mkdirat)(int, const char*, mode_t);
    if (!orig_mkdirat) orig_mkdirat = dlsym(RTLD_NEXT, "mkdirat");
    return orig_mkdirat(dirfd, redirected, mode);
}

ssize_t readlinkat(int dirfd, const char* path, char* buf, size_t bufsiz) {
    char pbuf[4096];
    const char* redirected = redirect_path(path, pbuf, sizeof(pbuf));
    static ssize_t (*orig_readlinkat)(int, const char*, char*, size_t);
    if (!orig_readlinkat) orig_readlinkat = dlsym(RTLD_NEXT, "readlinkat");
    return orig_readlinkat(dirfd, redirected, buf, bufsiz);
}
