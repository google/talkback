/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2023 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#include <linux/unistd.h>

SCF_BEGIN_VALUES(systemCall)
  #ifdef __NR_accept
  {__NR_accept},
  #endif /* __NR_accept */
  
  #ifdef __NR_access
  {__NR_access},
  #endif /* __NR_access */
  
  #ifdef __NR_arch_prctl
  {__NR_arch_prctl},
  #endif /* __NR_arch_prctl */
  
  #ifdef __NR_bind
  {__NR_bind},
  #endif /* __NR_bind */
  
  #ifdef __NR_brk
  {__NR_brk},
  #endif /* __NR_brk */
  
  #ifdef __NR_chdir
  {__NR_chdir},
  #endif /* __NR_chdir */
  
  #ifdef __NR_chmod
  {__NR_chmod},
  #endif /* __NR_chmod */
  
  #ifdef __NR_clock_getres
  {__NR_clock_getres},
  #endif /* __NR_clock_getres */
  
  #ifdef __NR_clock_gettime
  {__NR_clock_gettime},
  #endif /* __NR_clock_gettime */
  
  #ifdef __NR_clock_nanosleep
  {__NR_clock_nanosleep},
  #endif /* __NR_clock_nanosleep */
  
  #ifdef __NR_clone
  {__NR_clone},
  #endif /* __NR_clone */
  
  #ifdef __NR_close
  {__NR_close},
  #endif /* __NR_close */
  
  #ifdef __NR_connect
  {__NR_connect},
  #endif /* __NR_connect */
  
  #ifdef __NR_dup
  {__NR_dup},
  #endif /* __NR_dup */
  
  #ifdef __NR_dup2
  {__NR_dup2},
  #endif /* __NR_dup2 */
  
  #ifdef __NR_eventfd2
  {__NR_eventfd2},
  #endif /* __NR_eventfd2 */
  
  #ifdef __NR_execve
  {__NR_execve},
  #endif /* __NR_execve */
  
  #ifdef __NR_exit
  {__NR_exit},
  #endif /* __NR_exit */
  
  #ifdef __NR_exit_group
  {__NR_exit_group},
  #endif /* __NR_exit_group */
  
  #ifdef __NR_fchdir
  {__NR_fchdir},
  #endif /* __NR_fchdir */
  
  #ifdef __NR_fcntl
  {__NR_fcntl},
  #endif /* __NR_fcntl */
  
  #ifdef __NR_fork
  {__NR_fork},
  #endif /* __NR_fork */
  
  #ifdef __NR_fstat
  {__NR_fstat},
  #endif /* __NR_fstat */
  
  #ifdef __NR_ftruncate
  {__NR_ftruncate},
  #endif /* __NR_ftruncate */
  
  #ifdef __NR_futex
  {__NR_futex},
  #endif /* __NR_futex */
  
  #ifdef __NR_getcwd
  {__NR_getcwd},
  #endif /* __NR_getcwd */
  
  #ifdef __NR_getdents
  {__NR_getdents},
  #endif /* __NR_getdents */
  
  #ifdef __NR_getdents64
  {__NR_getdents64},
  #endif /* __NR_getdents64 */
  
  #ifdef __NR_getegid
  {__NR_getegid},
  #endif /* __NR_getegid */
  
  #ifdef __NR_geteuid
  {__NR_geteuid},
  #endif /* __NR_geteuid */
  
  #ifdef __NR_getgid
  {__NR_getgid},
  #endif /* __NR_getgid */
  
  #ifdef __NR_getpeername
  {__NR_getpeername},
  #endif /* __NR_getpeername */
  
  #ifdef __NR_getpgrp
  {__NR_getpgrp},
  #endif /* __NR_getpgrp */
  
  #ifdef __NR_getpid
  {__NR_getpid},
  #endif /* __NR_getpid */
  
  #ifdef __NR_getppid
  {__NR_getppid},
  #endif /* __NR_getppid */
  
  #ifdef __NR_getrandom
  {__NR_getrandom},
  #endif /* __NR_getrandom */
  
  #ifdef __NR_getresgid
  {__NR_getresgid},
  #endif /* __NR_getresgid */
  
  #ifdef __NR_getresuid
  {__NR_getresuid},
  #endif /* __NR_getresuid */
  
  #ifdef __NR_get_robust_list
  {__NR_get_robust_list},
  #endif /* __NR_get_robust_list */
  
  #ifdef __NR_getsockname
  {__NR_getsockname},
  #endif /* __NR_getsockname */
  
  #ifdef __NR_getsockopt
  {__NR_getsockopt},
  #endif /* __NR_getsockopt */
  
  #ifdef __NR_gettid
  {__NR_gettid},
  #endif /* __NR_gettid */
  
  #ifdef __NR_getuid
  {__NR_getuid},
  #endif /* __NR_getuid */
  
  #ifdef __NR_ioctl
  {__NR_ioctl},
  #endif /* __NR_ioctl */
  
  #ifdef __NR_kill
  {__NR_kill},
  #endif /* __NR_kill */
  
  #ifdef __NR_link
  {__NR_link},
  #endif /* __NR_link */
  
  #ifdef __NR_listen
  {__NR_listen},
  #endif /* __NR_listen */
  
  #ifdef __NR_lseek
  {__NR_lseek},
  #endif /* __NR_lseek */
  
  #ifdef __NR_madvise
  {__NR_madvise},
  #endif /* __NR_madvise */
  
  #ifdef __NR_memfd_create
  {__NR_memfd_create},
  #endif /* __NR_memfd_create */
  
  #ifdef __NR_mkdir
  {__NR_mkdir},
  #endif /* __NR_mkdir */
  
  #ifdef __NR_mknod
  {__NR_mknod},
  #endif /* __NR_mknod */
  
  #ifdef __NR_mmap
  {__NR_mmap},
  #endif /* __NR_mmap */
  
  #ifdef __NR_mprotect
  {__NR_mprotect},
  #endif /* __NR_mprotect */
  
  #ifdef __NR_mremap
  {__NR_mremap},
  #endif /* __NR_mremap */
  
  #ifdef __NR_munmap
  {__NR_munmap},
  #endif /* __NR_munmap */
  
  #ifdef __NR_nanosleep
  {__NR_nanosleep},
  #endif /* __NR_nanosleep */
  
  #ifdef __NR_open
  {__NR_open},
  #endif /* __NR_open */
  
  #ifdef __NR_openat
  {__NR_openat},
  #endif /* __NR_openat */
  
  #ifdef __NR_pipe
  {__NR_pipe},
  #endif /* __NR_pipe */
  
  #ifdef __NR_pipe2
  {__NR_pipe2},
  #endif /* __NR_pipe2 */
  
  #ifdef __NR_poll
  {__NR_poll},
  #endif /* __NR_poll */
  
  #ifdef __NR_ppoll
  {__NR_ppoll},
  #endif /* __NR_ppoll */
  
  #ifdef __NR_prctl
  {__NR_prctl},
  #endif /* __NR_prctl */
  
  #ifdef __NR_pread64
  {__NR_pread64},
  #endif /* __NR_pread64 */
  
  #ifdef __NR_prlimit64
  {__NR_prlimit64},
  #endif /* __NR_prlimit64 */
  
  #ifdef __NR_read
  {__NR_read},
  #endif /* __NR_read */
  
  #ifdef __NR_readlink
  {__NR_readlink},
  #endif /* __NR_readlink */
  
  #ifdef __NR_readv
  {__NR_readv},
  #endif /* __NR_readv */
  
  #ifdef __NR_recvfrom
  {__NR_recvfrom},
  #endif /* __NR_recvfrom */
  
  #ifdef __NR_recvmsg
  {__NR_recvmsg},
  #endif /* __NR_recvmsg */
  
  #ifdef __NR_restart_syscall
  {__NR_restart_syscall},
  #endif /* __NR_restart_syscall */
  
  #ifdef __NR_rt_sigaction
  {__NR_rt_sigaction},
  #endif /* __NR_rt_sigaction */
  
  #ifdef __NR_rt_sigprocmask
  {__NR_rt_sigprocmask},
  #endif /* __NR_rt_sigprocmask */
  
  #ifdef __NR_rt_sigreturn
  {__NR_rt_sigreturn},
  #endif /* __NR_rt_sigreturn */
  
  #ifdef __NR_sched_getaffinity
  {__NR_sched_getaffinity},
  #endif /* __NR_sched_getaffinity */
  
  #ifdef __NR_sched_getattr
  {__NR_sched_getattr},
  #endif /* __NR_sched_getattr */
  
  #ifdef __NR_sched_setattr
  {__NR_sched_setattr},
  #endif /* __NR_sched_setattr */
  
  #ifdef __NR_select
  {__NR_select},
  #endif /* __NR_select */
  
  #ifdef __NR_sendmmsg
  {__NR_sendmmsg},
  #endif /* __NR_sendmmsg */
  
  #ifdef __NR_sendmsg
  {__NR_sendmsg},
  #endif /* __NR_sendmsg */
  
  #ifdef __NR_sendto
  {__NR_sendto},
  #endif /* __NR_sendto */
  
  #ifdef __NR_set_robust_list
  {__NR_set_robust_list},
  #endif /* __NR_set_robust_list */
  
  #ifdef __NR_setsid
  {__NR_setsid},
  #endif /* __NR_setsid */
  
  #ifdef __NR_setsockopt
  {__NR_setsockopt},
  #endif /* __NR_setsockopt */
  
  #ifdef __NR_set_tid_address
  {__NR_set_tid_address},
  #endif /* __NR_set_tid_address */
  
  #ifdef __NR_socket
  {__NR_socket},
  #endif /* __NR_socket */
  
  #ifdef __NR_socketpair
  {__NR_socketpair},
  #endif /* __NR_socketpair */
  
  #ifdef __NR_stat
  {__NR_stat},
  #endif /* __NR_stat */
  
  #ifdef __NR_statfs
  {__NR_statfs},
  #endif /* __NR_statfs */
  
  #ifdef __NR_symlink
  {__NR_symlink},
  #endif /* __NR_symlink */
  
  #ifdef __NR_sysinfo
  {__NR_sysinfo},
  #endif /* __NR_sysinfo */
  
  #ifdef __NR_tgkill
  {__NR_tgkill},
  #endif /* __NR_tgkill */
  
  #ifdef __NR_times
  {__NR_times},
  #endif /* __NR_times */
  
  #ifdef __NR_umask
  {__NR_umask},
  #endif /* __NR_umask */
  
  #ifdef __NR_uname
  {__NR_uname},
  #endif /* __NR_uname */
  
  #ifdef __NR_unlink
  {__NR_unlink},
  #endif /* __NR_unlink */
  
  #ifdef __NR_wait4
  {__NR_wait4},
  #endif /* __NR_wait4 */
  
  #ifdef __NR_write
  {__NR_write},
  #endif /* __NR_write */
  
  #ifdef __NR_writev
  {__NR_writev},
  #endif /* __NR_writev */
SCF_END_VALUES
