#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <fcntl.h>
#include <android/log.h>

#define LOG_TAG "InvappRedirector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/* Official Termux packages hardcode these path prefixes. */
static const char* OLD_DATA_PREFIX = "data/data/com.termux";
static const size_t OLD_DATA_PREFIX_LEN = 20; /* strlen("data/data/com.termux") */

static const char* OLD_USER_PREFIX = "data/user/0/com.termux";
static const size_t OLD_USER_PREFIX_LEN = 21; /* strlen("data/user/0/com.termux") */

/* Must match TermuxConstants.TERMUX_PACKAGE_NAME / applicationId. */
static const char* NEW_PREFIX = "/data/data/com.involvex.termux_app";
static const char* OLD_PKG = "com.termux";
static const char* NEW_PKG = "com.involvex.termux_app";
/* Lengths derived at runtime — do not hardcode (easy to get wrong). */

__attribute__((constructor))
static void redirector_init(void) {
    LOGI("loaded; redirecting com.termux → %s", NEW_PREFIX);
}

static const char* redirect_path(const char* path, char* buffer, size_t bufsize) {
    if (!path) return path;

    const char* p = path;
    /* Handle ./ prefix */
    if (p[0] == '.' && p[1] == '/') p += 2;
    /* Handle leading / */
    if (p[0] == '/') p++;

    if (strncmp(p, OLD_DATA_PREFIX, OLD_DATA_PREFIX_LEN) == 0) {
        snprintf(buffer, bufsize, "%s%s", NEW_PREFIX, p + OLD_DATA_PREFIX_LEN);
        return buffer;
    }
    if (strncmp(p, OLD_USER_PREFIX, OLD_USER_PREFIX_LEN) == 0) {
        snprintf(buffer, bufsize, "%s%s", NEW_PREFIX, p + OLD_USER_PREFIX_LEN);
        return buffer;
    }
    return path;
}

/*
 * dpkg maintainer scripts ship with #!/data/data/com.termux/... shebangs.
 * Kernel shebang resolution ignores LD_PRELOAD, so rewrite scripts in-place
 * immediately before execve when they still contain the old package name.
 */
static void rewrite_termux_paths_in_script(const char* path) {
    if (!path) return;

    static int (*orig_open)(const char*, int, ...);
    static ssize_t (*orig_read)(int, void*, size_t);
    static ssize_t (*orig_write)(int, const void*, size_t);
    static off_t (*orig_lseek)(int, off_t, int);
    static int (*orig_close)(int);
    static int (*orig_ftruncate)(int, off_t);
    if (!orig_open) orig_open = dlsym(RTLD_NEXT, "open");
    if (!orig_read) orig_read = dlsym(RTLD_NEXT, "read");
    if (!orig_write) orig_write = dlsym(RTLD_NEXT, "write");
    if (!orig_lseek) orig_lseek = dlsym(RTLD_NEXT, "lseek");
    if (!orig_close) orig_close = dlsym(RTLD_NEXT, "close");
    if (!orig_ftruncate) orig_ftruncate = dlsym(RTLD_NEXT, "ftruncate");

    int fd = orig_open(path, O_RDONLY);
    if (fd < 0) return;

    char hdr[2];
    if (orig_read(fd, hdr, 2) != 2 || hdr[0] != '#' || hdr[1] != '!') {
        orig_close(fd);
        return;
    }

    off_t size = orig_lseek(fd, 0, SEEK_END);
    if (size < 0 || size > 1024 * 1024) {
        orig_close(fd);
        return;
    }
    orig_lseek(fd, 0, SEEK_SET);

    char* data = (char*)malloc((size_t)size + 1);
    if (!data) {
        orig_close(fd);
        return;
    }
    if (orig_read(fd, data, (size_t)size) != size) {
        free(data);
        orig_close(fd);
        return;
    }
    orig_close(fd);
    data[size] = '\0';

    if (!strstr(data, OLD_PKG)) {
        free(data);
        return;
    }

    const size_t old_pkg_len = strlen(OLD_PKG);
    const size_t new_pkg_len = strlen(NEW_PKG);

    /* Worst-case expansion bound. */
    size_t out_cap = (size_t)size * (new_pkg_len + 1) + 1;
    char* out = (char*)malloc(out_cap);
    if (!out) {
        free(data);
        return;
    }

    size_t oi = 0;
    for (size_t i = 0; i < (size_t)size; ) {
        if (i + old_pkg_len <= (size_t)size
            && memcmp(data + i, OLD_PKG, old_pkg_len) == 0) {
            memcpy(out + oi, NEW_PKG, new_pkg_len);
            oi += new_pkg_len;
            i += old_pkg_len;
        } else {
            out[oi++] = data[i++];
        }
    }

    fd = orig_open(path, O_WRONLY | O_TRUNC);
    if (fd >= 0) {
        if (orig_write(fd, out, oi) == (ssize_t)oi) {
            LOGI("rewrote package paths in script: %s", path);
        }
        orig_close(fd);
    }

    free(data);
    free(out);
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

/* Bionic fortify / newer libc entry point used instead of open(). */
int __open_2(const char* path, int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig___open_2)(const char*, int);
    if (!orig___open_2) orig___open_2 = dlsym(RTLD_NEXT, "__open_2");
    if (!orig___open_2) {
        static int (*orig_open)(const char*, int, ...);
        if (!orig_open) orig_open = dlsym(RTLD_NEXT, "open");
        return orig_open(redirected, flags);
    }
    return orig___open_2(redirected, flags);
}

int __openat_2(int dirfd, const char* path, int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig___openat_2)(int, const char*, int);
    if (!orig___openat_2) orig___openat_2 = dlsym(RTLD_NEXT, "__openat_2");
    if (!orig___openat_2) {
        static int (*orig_openat)(int, const char*, int, ...);
        if (!orig_openat) orig_openat = dlsym(RTLD_NEXT, "openat");
        return orig_openat(dirfd, redirected, flags);
    }
    return orig___openat_2(dirfd, redirected, flags);
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
    rewrite_termux_paths_in_script(redirected);
    static int (*orig_execve)(const char*, char* const[], char* const[]);
    if (!orig_execve) orig_execve = dlsym(RTLD_NEXT, "execve");
    return orig_execve(redirected, argv, envp);
}

int execv(const char* path, char* const argv[]) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    rewrite_termux_paths_in_script(redirected);
    static int (*orig_execv)(const char*, char* const[]);
    if (!orig_execv) orig_execv = dlsym(RTLD_NEXT, "execv");
    return orig_execv(redirected, argv);
}

int execvp(const char* file, char* const argv[]) {
    /* Absolute paths may still be maintainer scripts; relative names are PATH lookups. */
    if (file && file[0] == '/') {
        char buf[4096];
        const char* redirected = redirect_path(file, buf, sizeof(buf));
        rewrite_termux_paths_in_script(redirected);
        static int (*orig_execvp)(const char*, char* const[]);
        if (!orig_execvp) orig_execvp = dlsym(RTLD_NEXT, "execvp");
        return orig_execvp(redirected, argv);
    }
    static int (*orig_execvp)(const char*, char* const[]);
    if (!orig_execvp) orig_execvp = dlsym(RTLD_NEXT, "execvp");
    return orig_execvp(file, argv);
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

int mkdir(const char* path, mode_t mode) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_mkdir)(const char*, mode_t);
    if (!orig_mkdir) orig_mkdir = dlsym(RTLD_NEXT, "mkdir");
    return orig_mkdir(redirected, mode);
}

int mkdirat(int dirfd, const char* path, mode_t mode) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_mkdirat)(int, const char*, mode_t);
    if (!orig_mkdirat) orig_mkdirat = dlsym(RTLD_NEXT, "mkdirat");
    return orig_mkdirat(dirfd, redirected, mode);
}

int unlink(const char* path) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_unlink)(const char*);
    if (!orig_unlink) orig_unlink = dlsym(RTLD_NEXT, "unlink");
    return orig_unlink(redirected);
}

int unlinkat(int dirfd, const char* path, int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_unlinkat)(int, const char*, int);
    if (!orig_unlinkat) orig_unlinkat = dlsym(RTLD_NEXT, "unlinkat");
    return orig_unlinkat(dirfd, redirected, flags);
}

int rmdir(const char* path) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_rmdir)(const char*);
    if (!orig_rmdir) orig_rmdir = dlsym(RTLD_NEXT, "rmdir");
    return orig_rmdir(redirected);
}

int rename(const char* oldpath, const char* newpath) {
    char oldbuf[4096];
    char newbuf[4096];
    const char* redirected_old = redirect_path(oldpath, oldbuf, sizeof(oldbuf));
    const char* redirected_new = redirect_path(newpath, newbuf, sizeof(newbuf));
    static int (*orig_rename)(const char*, const char*);
    if (!orig_rename) orig_rename = dlsym(RTLD_NEXT, "rename");
    return orig_rename(redirected_old, redirected_new);
}

int renameat(int olddirfd, const char* oldpath, int newdirfd, const char* newpath) {
    char oldbuf[4096];
    char newbuf[4096];
    const char* redirected_old = redirect_path(oldpath, oldbuf, sizeof(oldbuf));
    const char* redirected_new = redirect_path(newpath, newbuf, sizeof(newbuf));
    static int (*orig_renameat)(int, const char*, int, const char*);
    if (!orig_renameat) orig_renameat = dlsym(RTLD_NEXT, "renameat");
    return orig_renameat(olddirfd, redirected_old, newdirfd, redirected_new);
}

int renameat2(int olddirfd, const char* oldpath, int newdirfd, const char* newpath,
              unsigned int flags) {
    char oldbuf[4096];
    char newbuf[4096];
    const char* redirected_old = redirect_path(oldpath, oldbuf, sizeof(oldbuf));
    const char* redirected_new = redirect_path(newpath, newbuf, sizeof(newbuf));
    static int (*orig_renameat2)(int, const char*, int, const char*, unsigned int);
    if (!orig_renameat2) orig_renameat2 = dlsym(RTLD_NEXT, "renameat2");
    if (!orig_renameat2) {
        /* Fallback if renameat2 is unavailable. */
        static int (*orig_renameat)(int, const char*, int, const char*);
        if (!orig_renameat) orig_renameat = dlsym(RTLD_NEXT, "renameat");
        return orig_renameat(olddirfd, redirected_old, newdirfd, redirected_new);
    }
    return orig_renameat2(olddirfd, redirected_old, newdirfd, redirected_new, flags);
}

int link(const char* oldpath, const char* newpath) {
    char oldbuf[4096];
    char newbuf[4096];
    const char* redirected_old = redirect_path(oldpath, oldbuf, sizeof(oldbuf));
    const char* redirected_new = redirect_path(newpath, newbuf, sizeof(newbuf));
    static int (*orig_link)(const char*, const char*);
    if (!orig_link) orig_link = dlsym(RTLD_NEXT, "link");
    return orig_link(redirected_old, redirected_new);
}

int linkat(int olddirfd, const char* oldpath, int newdirfd, const char* newpath, int flags) {
    char oldbuf[4096];
    char newbuf[4096];
    const char* redirected_old = redirect_path(oldpath, oldbuf, sizeof(oldbuf));
    const char* redirected_new = redirect_path(newpath, newbuf, sizeof(newbuf));
    static int (*orig_linkat)(int, const char*, int, const char*, int);
    if (!orig_linkat) orig_linkat = dlsym(RTLD_NEXT, "linkat");
    return orig_linkat(olddirfd, redirected_old, newdirfd, redirected_new, flags);
}

int symlink(const char* target, const char* linkpath) {
    char targetbuf[4096];
    char linkbuf[4096];
    const char* redirected_target = redirect_path(target, targetbuf, sizeof(targetbuf));
    const char* redirected_link = redirect_path(linkpath, linkbuf, sizeof(linkbuf));
    static int (*orig_symlink)(const char*, const char*);
    if (!orig_symlink) orig_symlink = dlsym(RTLD_NEXT, "symlink");
    return orig_symlink(redirected_target, redirected_link);
}

int symlinkat(const char* target, int newdirfd, const char* linkpath) {
    char targetbuf[4096];
    char linkbuf[4096];
    const char* redirected_target = redirect_path(target, targetbuf, sizeof(targetbuf));
    const char* redirected_link = redirect_path(linkpath, linkbuf, sizeof(linkbuf));
    static int (*orig_symlinkat)(const char*, int, const char*);
    if (!orig_symlinkat) orig_symlinkat = dlsym(RTLD_NEXT, "symlinkat");
    return orig_symlinkat(redirected_target, newdirfd, redirected_link);
}

int chmod(const char* path, mode_t mode) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_chmod)(const char*, mode_t);
    if (!orig_chmod) orig_chmod = dlsym(RTLD_NEXT, "chmod");
    return orig_chmod(redirected, mode);
}

int fchmodat(int dirfd, const char* path, mode_t mode, int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_fchmodat)(int, const char*, mode_t, int);
    if (!orig_fchmodat) orig_fchmodat = dlsym(RTLD_NEXT, "fchmodat");
    return orig_fchmodat(dirfd, redirected, mode, flags);
}

int chown(const char* path, uid_t owner, gid_t group) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_chown)(const char*, uid_t, gid_t);
    if (!orig_chown) orig_chown = dlsym(RTLD_NEXT, "chown");
    return orig_chown(redirected, owner, group);
}

int lchown(const char* path, uid_t owner, gid_t group) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_lchown)(const char*, uid_t, gid_t);
    if (!orig_lchown) orig_lchown = dlsym(RTLD_NEXT, "lchown");
    return orig_lchown(redirected, owner, group);
}

int fchownat(int dirfd, const char* path, uid_t owner, gid_t group, int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_fchownat)(int, const char*, uid_t, gid_t, int);
    if (!orig_fchownat) orig_fchownat = dlsym(RTLD_NEXT, "fchownat");
    return orig_fchownat(dirfd, redirected, owner, group, flags);
}

int utimensat(int dirfd, const char* path, const struct timespec times[2], int flags) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_utimensat)(int, const char*, const struct timespec[2], int);
    if (!orig_utimensat) orig_utimensat = dlsym(RTLD_NEXT, "utimensat");
    return orig_utimensat(dirfd, redirected, times, flags);
}

int truncate(const char* path, off_t length) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static int (*orig_truncate)(const char*, off_t);
    if (!orig_truncate) orig_truncate = dlsym(RTLD_NEXT, "truncate");
    return orig_truncate(redirected, length);
}

FILE* fopen(const char* path, const char* mode) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static FILE* (*orig_fopen)(const char*, const char*);
    if (!orig_fopen) orig_fopen = dlsym(RTLD_NEXT, "fopen");
    return orig_fopen(redirected, mode);
}

FILE* freopen(const char* path, const char* mode, FILE* stream) {
    char buf[4096];
    const char* redirected = redirect_path(path, buf, sizeof(buf));
    static FILE* (*orig_freopen)(const char*, const char*, FILE*);
    if (!orig_freopen) orig_freopen = dlsym(RTLD_NEXT, "freopen");
    return orig_freopen(redirected, mode, stream);
}

DIR* opendir(const char* name) {
    char buf[4096];
    const char* redirected = redirect_path(name, buf, sizeof(buf));
    static DIR* (*orig_opendir)(const char*);
    if (!orig_opendir) orig_opendir = dlsym(RTLD_NEXT, "opendir");
    return orig_opendir(redirected);
}

ssize_t readlink(const char* path, char* buf, size_t bufsiz) {
    char pbuf[4096];
    const char* redirected = redirect_path(path, pbuf, sizeof(pbuf));
    static ssize_t (*orig_readlink)(const char*, char*, size_t);
    if (!orig_readlink) orig_readlink = dlsym(RTLD_NEXT, "readlink");
    return orig_readlink(redirected, buf, bufsiz);
}

ssize_t readlinkat(int dirfd, const char* path, char* buf, size_t bufsiz) {
    char pbuf[4096];
    const char* redirected = redirect_path(path, pbuf, sizeof(pbuf));
    static ssize_t (*orig_readlinkat)(int, const char*, char*, size_t);
    if (!orig_readlinkat) orig_readlinkat = dlsym(RTLD_NEXT, "readlinkat");
    return orig_readlinkat(dirfd, redirected, buf, bufsiz);
}
