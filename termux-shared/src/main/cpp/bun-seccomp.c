/*
 * Convert Android seccomp SIGSYS traps (openat2 / fchmodat2 / close_range)
 * into ENOSYS so Bun's existing fallbacks run.
 *
 * Bun 1.4.x issues these syscalls during `bun install` bin linking; Android's
 * seccomp policy delivers SIGSYS instead of ENOSYS, which kills the process
 * ("Unknown signal 31"). Upstream fix: oven-sh/bun#39775 / #39084 (not in 1.4.2).
 *
 * Preload ONLY this library for Bun — do not combine with libinvapp-redirector
 * (path redirector + Bun optional natives can also SIGSYS).
 */
#define _GNU_SOURCE

#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <signal.h>
#include <string.h>
#include <ucontext.h>
#include <unistd.h>

#include <android/log.h>

#ifndef SYS_SECCOMP
#define SYS_SECCOMP 1
#endif

/* Linux syscall numbers (aarch64 / x86_64 share these for the calls we care about). */
#ifndef SYS_close_range
#define SYS_close_range 436
#endif
#ifndef SYS_openat2
#define SYS_openat2 437
#endif
#ifndef SYS_fchmodat2
#define SYS_fchmodat2 452
#endif

#define LOG_TAG "InvappBunSeccomp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

typedef int (*sigaction_fn)(int, const struct sigaction*, struct sigaction*);

static sigaction_fn real_sigaction;
static struct sigaction previous_sigsys;
static volatile int handler_installed;

static void install_sigsys_handler(void);

static int is_android_blocked_syscall(int nr) {
    return nr == SYS_openat2 || nr == SYS_fchmodat2 || nr == SYS_close_range;
}

static void set_enosys_return(void* context) {
    if (!context) return;
#if defined(__aarch64__)
    ucontext_t* uc = (ucontext_t*)context;
    uc->uc_mcontext.regs[0] = (unsigned long)-ENOSYS;
#elif defined(__x86_64__)
    ucontext_t* uc = (ucontext_t*)context;
    uc->uc_mcontext.gregs[REG_RAX] = (long)-ENOSYS;
#elif defined(__arm__)
    ucontext_t* uc = (ucontext_t*)context;
    uc->uc_mcontext.arm_r0 = (unsigned long)-ENOSYS;
#elif defined(__i386__)
    ucontext_t* uc = (ucontext_t*)context;
    uc->uc_mcontext.gregs[REG_EAX] = (long)-ENOSYS;
#else
    (void)context;
#endif
}

static void syscall_sigsys_handler(int signo, siginfo_t* info, void* context) {
    if (signo == SIGSYS && info && info->si_code == SYS_SECCOMP
        && is_android_blocked_syscall(info->si_syscall)) {
        set_enosys_return(context);
        return;
    }

    if (previous_sigsys.sa_flags & SA_SIGINFO) {
        if (previous_sigsys.sa_sigaction)
            previous_sigsys.sa_sigaction(signo, info, context);
        return;
    }

    if (previous_sigsys.sa_handler == SIG_IGN)
        return;
    if (previous_sigsys.sa_handler && previous_sigsys.sa_handler != SIG_DFL) {
        previous_sigsys.sa_handler(signo);
        return;
    }

    if (!real_sigaction)
        return;
    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_handler = SIG_DFL;
    real_sigaction(SIGSYS, &action, NULL);
    raise(SIGSYS);
}

static void install_sigsys_handler(void) {
    if (!real_sigaction)
        real_sigaction = (sigaction_fn)dlsym(RTLD_NEXT, "sigaction");
    if (!real_sigaction)
        return;

    struct sigaction action;
    memset(&action, 0, sizeof(action));
    sigemptyset(&action.sa_mask);
    action.sa_sigaction = syscall_sigsys_handler;
    action.sa_flags = SA_SIGINFO | SA_RESTART;
    if (real_sigaction(SIGSYS, &action, &previous_sigsys) == 0)
        handler_installed = 1;
}

static void* reinstall_after_startup(void* unused) {
    (void)unused;
    /* Bun may reset signal handlers during early init — reinstall shortly after. */
    struct timespec delay = { .tv_sec = 0, .tv_nsec = 50 * 1000 * 1000 };
    nanosleep(&delay, NULL);
    install_sigsys_handler();
    delay.tv_nsec = 200 * 1000 * 1000;
    nanosleep(&delay, NULL);
    install_sigsys_handler();
    return NULL;
}

__attribute__((constructor))
static void bun_seccomp_init(void) {
    install_sigsys_handler();
    pthread_t thread;
    if (pthread_create(&thread, NULL, reinstall_after_startup, NULL) == 0)
        pthread_detach(thread);
    LOGI("SIGSYS→ENOSYS shim loaded (openat2/fchmodat2/close_range)");
}

int sigaction(int signo, const struct sigaction* action, struct sigaction* old_action) {
    if (!real_sigaction)
        real_sigaction = (sigaction_fn)dlsym(RTLD_NEXT, "sigaction");
    if (!real_sigaction) {
        errno = ENOSYS;
        return -1;
    }

    /* Keep our handler sticky — Bun install must not replace it with SIG_DFL. */
    if (signo == SIGSYS && action) {
        if (old_action) {
            memset(old_action, 0, sizeof(*old_action));
            old_action->sa_sigaction = syscall_sigsys_handler;
            old_action->sa_flags = SA_SIGINFO | SA_RESTART;
            sigemptyset(&old_action->sa_mask);
        }
        previous_sigsys = *action;
        return 0;
    }

    return real_sigaction(signo, action, old_action);
}
