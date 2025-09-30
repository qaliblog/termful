     .global blob
     .global blob_size
     .section .rodata
 blob:
     // No bootstrap files embedded - users will select their own distributions
     .byte 0x00
 1:
 blob_size:
     .int 1b - blob
