     .global bun_blob
     .global bun_blob_size
     .section .rodata
 bun_blob:
 #if defined __aarch64__
     .incbin "bun-linux-aarch64-android.zip"
 #elif defined __x86_64__
     .incbin "bun-linux-x64-android.zip"
 #else
     /* Official Bun Android builds exist only for aarch64 and x86_64. */
 #endif
 1:
 bun_blob_size:
     .int 1b - bun_blob
