/*
 * XBrlAPI - A background process tinkering with X for proper BrlAPI behavior
 *
 * Copyright (C) 2003-2023 by Samuel Thibault <Samuel.Thibault@ens-lyon.org>
 *
 * XBrlAPI comes with ABSOLUTELY NO WARRANTY.
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

/* Compile with:
 * gcc -O3 -Wall xbrlapi.c -L/usr/X11R6/lib -lbrlapi -lX11 -o xbrlapi
 */

#include "prologue.h"

#include <stdio.h>
#include <stdarg.h>
#include <signal.h>
#include <string.h>

#ifdef HAVE_LANGINFO_H
#include <langinfo.h>
#endif /* HAVE_LANGINFO_H */

#ifdef HAVE_SYS_SELECT_H
#include <sys/select.h>
#else /* HAVE_SYS_SELECT_H */
#include <sys/time.h>
#endif /* HAVE_SYS_SELECT_H */

#ifdef HAVE_ICONV_H
#include <iconv.h>
#endif /* HAVE_ICONV_H */

#include <X11/X.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>
#include <X11/XKBlib.h>
#include <X11/keysym.h>

#undef CAN_SIMULATE_KEY_PRESSES
#if defined(HAVE_X11_EXTENSIONS_XTEST_H) && defined(HAVE_X11_EXTENSIONS_XKB_H)
#include <X11/extensions/XTest.h>
#define CAN_SIMULATE_KEY_PRESSES
#else /* HAVE_X11_EXTENSIONS_XTEST_H && HAVE_X11_EXTENSIONS_XKB_H */
#warning key press simulation not supported by this build - check that libxtst has been installed
#endif /* HAVE_X11_EXTENSIONS_XTEST_H && HAVE_X11_EXTENSIONS_XKB_H */

#include "xsel.h"

#define BRLAPI_NO_DEPRECATED
#include "brlapi.h"

#include "cmdline.h"

#define debugf(fmt, ...) do { if (verbose) fprintf(stderr, fmt, ## __VA_ARGS__); } while (0)

/******************************************************************************
 * option handling
 */

static char *auth;
static char *host;
static char *xDisplay;
static int no_daemon;
static int quiet;
static int verbose;
static int xkb_major_opcode;

static int brlapi_fd;

static void *clipboardData;

BEGIN_OPTION_TABLE(programOptions)
  { .word = "brlapi",
    .letter = 'b',
    .argument = strtext("[host][:port]"),
    .setting.string = &host,
    .description = strtext("BrlAPI host and/or port to connect to")
  },

  { .word = "auth",
    .letter = 'a',
    .argument = strtext("scheme+..."),
    .setting.string = &auth,
    .description = strtext("BrlAPI authorization/authentication schemes")
  },

  { .word = "display",
    .letter = 'd',
    .argument = strtext("display"),
    .setting.string = &xDisplay,
    .description = strtext("X display to connect to")
  },

  { .word = "quiet",
    .letter = 'q',
    .setting.flag = &quiet,
    .description = strtext("Do not write any text to the braille device")
  },

  { .word = "verbose",
    .letter = 'v',
    .setting.flag = &verbose,
    .description = strtext("Write debugging output to stdout")
  },

  { .word = "no-daemon",
    .letter = 'n',
    .setting.flag = &no_daemon,
    .description = strtext("Remain a foreground process")
  },
END_OPTION_TABLE(programOptions)

/******************************************************************************
 * error handling
 */

static void api_cleanExit(void) {
  if (brlapi_fd>=0)
  {
    close(brlapi_fd);
    brlapi_fd=-1;
  }
}

/* dumps errors which are fatal to brlapi only */
static void fatal_brlapi_errno(const char *msg, const char *fmt, ...) {
  brlapi_perror(msg);
  if (fmt) {
    va_list va;
    va_start(va,fmt);
    vfprintf(stderr,fmt,va);
    va_end(va);
  }
  api_cleanExit();
}

static void exception_handler(int error, brlapi_packetType_t type, const void *packet, size_t size) {
  char str[0X100];
  brlapi_strexception(str,0X100, error, type, packet, size);
  fprintf(stderr, "xbrlapi: BrlAPI exception: %s\nDisconnecting from brlapi\n", str);
  api_cleanExit();
}

/* dumps errors which are fatal to the whole xbrlapi */
static void fatal_errno(const char *msg, const char *fmt, ...) {
  perror(msg);
  if (fmt) {
    va_list va;
    va_start(va,fmt);
    vfprintf(stderr,fmt,va);
    va_end(va);
  }
  exit(PROG_EXIT_FATAL);
}

static void fatal(const char *fmt, ...) {
  if (fmt) {
    va_list va;
    va_start(va,fmt);
    vfprintf(stderr,fmt,va);
    va_end(va);
  }
  exit(PROG_EXIT_FATAL);
}

/******************************************************************************
 * brlapi handling
 */


#ifndef MIN
#define MIN(a, b) (((a) < (b))? (a): (b))
#endif /* MIN */

static void clipboardContentChanged(brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, void *priv, const void *data, size_t len);

static int tobrltty_init(char *auth, char *host) {
  brlapi_connectionSettings_t settings;
  unsigned int x,y;
  settings.host=host;
  settings.auth=auth;
  static int had_succeeded;
  brlapi_param_clientPriority_t priority;
  brlapi_param_retainDots_t dots;

  if ((brlapi_fd = brlapi_openConnection(&settings,&settings))<0)
  {
    if (!had_succeeded)
    {
      /* This is the first attempt to connect to BRLTTY, and it failed.
       * Return the error immediately to the user, to provide feedback to users
       * running xbrlapi by hand, but not fill logs, eat battery, spam
       * 127.0.0.1 with reconnection attempts.
       */
      fatal_brlapi_errno("openConnection",gettext("cannot connect to braille devices daemon brltty at %s\n"),settings.host);
      exit(PROG_EXIT_FATAL);
    }
    return 0;
  }
  /* We achieved connecting to BRLTTY.  If BRLTTY dies later on, we will
   * silently try to reconnect to it.  */
  had_succeeded = 1;

  if (brlapi_getDisplaySize(&x,&y)<0)
  {
    fatal_brlapi_errno("getDisplaySize",NULL);
    return 0;
  }

  if (x == 0)
  {
    /* Braille device not initialized yet */
    api_cleanExit();
    return 0;
  }

  brlapi_setExceptionHandler(exception_handler);

  /* Our output is really not very interesting */
  priority = 10;
  brlapi_setParameter(BRLAPI_PARAM_CLIENT_PRIORITY, 0, BRLAPI_PARAMF_LOCAL, &priority, sizeof(priority));

  /* We prefer to get translated keypresses */
  dots = 0;
  brlapi_setParameter(BRLAPI_PARAM_RETAIN_DOTS, 0, BRLAPI_PARAMF_LOCAL, &dots, sizeof(dots));

  /* X already has some clipboard content */
  if (clipboardData)
    brlapi_setParameter(BRLAPI_PARAM_CLIPBOARD_CONTENT, 0, BRLAPI_PARAMF_GLOBAL, clipboardData, strlen(clipboardData));

  /* We want to monitor clipboard changes */
  brlapi_watchParameter(BRLAPI_PARAM_CLIPBOARD_CONTENT, 0, BRLAPI_PARAMF_GLOBAL, clipboardContentChanged, NULL, NULL, 0);

  return 1;
}

static int getXVTnb(void);

static void getVT(void) {
  char *path = getenv("WINDOWPATH");
  char *vtnr = getenv("XDG_VTNR");
  int vtno = -1;
  if (!path && !vtnr)
    /* Workaround for old xinit/xdm/gdm/kdm */
    vtno = getXVTnb();

  if (path || vtnr || vtno == -1) {
    if (brlapi_enterTtyModeWithPath(NULL,0,NULL)<0)
    {
      fatal_brlapi_errno("geTtyPath",gettext("cannot get tty\n"));
      return;
    }
  } else {
    if (brlapi_enterTtyMode(vtno,NULL)<0)
    {
      fatal_brlapi_errno("enterTtyMode",gettext("cannot get tty %d\n"),vtno);
      return;
    }
  }

  if (brlapi_ignoreAllKeys()<0)
  {
    fatal_brlapi_errno("ignoreAllKeys",gettext("cannot ignore keys\n"));
    return;
  }
#ifdef CAN_SIMULATE_KEY_PRESSES
  /* All X keysyms with any modifier */
  brlapi_keyCode_t cmd = BRLAPI_KEY_TYPE_SYM;
  if (brlapi_acceptKeys(brlapi_rangeType_type, &cmd, 1))
  {
    fatal_brlapi_errno("acceptKeys",NULL);
    return;
  }
  cmd = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SHIFT;
  if (brlapi_acceptKeys(brlapi_rangeType_key, &cmd, 1))
  {
    fatal_brlapi_errno("acceptKeys",NULL);
    return;
  }
  cmd = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_UPPER;
  if (brlapi_acceptKeys(brlapi_rangeType_key, &cmd, 1))
  {
    fatal_brlapi_errno("acceptKeys",NULL);
    return;
  }
  cmd = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CONTROL;
  if (brlapi_acceptKeys(brlapi_rangeType_key, &cmd, 1))
  {
    fatal_brlapi_errno("acceptKeys",NULL);
    return;
  }
  cmd = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_META;
  if (brlapi_acceptKeys(brlapi_rangeType_key, &cmd, 1))
  {
    fatal_brlapi_errno("acceptKeys",NULL);
    return;
  }
#endif /* CAN_SIMULATE_KEY_PRESSES */
}

static char *last_name;

static void api_setLastName(void) {
  if (!last_name) return;
  if (brlapi_writeText(0,last_name)<0) {
    brlapi_perror("writeText");
    fprintf(stderr,gettext("xbrlapi: cannot write window name %s\n"),last_name);
  }
}

static void api_setName(const char *wm_name) {
  if (brlapi_fd<0) return;

  debugf("%s got focus\n",wm_name);
  if (last_name) {
    if (!strcmp(wm_name,last_name)) return;
    free(last_name);
  }
  if (!(last_name=strdup(wm_name))) fatal_errno("strdup(wm_name)",NULL);
  api_setLastName();
}

static int last_win;

static void api_setLastFocus(void)
{
  if (brlapi_setFocus(last_win)<0)
    fatal_brlapi_errno("setFocus",gettext("cannot set focus to %#010x\n"),last_win);
}

static void api_setFocus(int win) {
  if (brlapi_fd<0) return;
  debugf("%#010x (%d) got focus\n",win,win);
  last_win = win;
  api_setLastFocus();
}

/******************************************************************************
 * X handling
 */

static const char *Xdisplay;
static Display *dpy;

static Window curWindow;
static Atom netWmNameAtom, utf8StringAtom;

static XSelData xselData;

static volatile sig_atomic_t grabFailed;

#ifdef HAVE_ICONV_H
iconv_t utf8Conv = (iconv_t)(-1);
#endif /* HAVE_ICONV_H */

#define WINHASHBITS 12

static struct window {
  Window win;
  Window root;
  char *wm_name;
  struct window *next;
} *windows[(1<<WINHASHBITS)];

#define WINHASH(win) windows[(win)>>(32-WINHASHBITS)^(win&((1<<WINHASHBITS)-1))]

static void add_window(Window win, Window root, char *wm_name) {
  struct window *cur;
  if (!(cur=malloc(sizeof(struct window))))
    fatal_errno("malloc(struct window)",NULL);
  cur->win=win;
  cur->wm_name=wm_name;
  cur->root=root;
  cur->next=WINHASH(win);
  WINHASH(win)=cur;
}

static struct window *window_of_Window(Window win) {
  struct window *cur;
  for (cur=WINHASH(win); cur && cur->win!=win; cur=cur->next);
  return cur;
}

static int isRootWindow (Window win) {
  if (win == PointerRoot) return 1;

  {
    int count = ScreenCount(dpy);
     
    for (int index=0; index<count; index+=1) {
      if (RootWindow(dpy, index) == win) {
        return 1;
      }
    }
  }

  return 0;
}

static int del_window(Window win) {
  struct window **pred;
  struct window *cur;

  for (pred=&WINHASH(win); cur = *pred, cur && cur->win!=win; pred=&cur->next);

  if (cur) {
    *pred=cur->next;
    free(cur->wm_name);
    free(cur);
    return 0;
  } else return -1;
}

static int ErrorHandler(Display *dpy, XErrorEvent *ev) {
  char buffer[128];
  if (ev->error_code==BadWindow) {
    grabFailed=1;
    return 0;
  }
#ifdef CAN_SIMULATE_KEY_PRESSES
  if (ev->request_code == xkb_major_opcode && ev->minor_code == X_kbSetMap) {
    /* Server refused our Xkb remapping request, probably the buggy version 21, ignore error */
    fprintf(stderr,gettext("xbrlapi: server refused our mapping request, could not synthesize key\n"));
    return 0;
  }
#endif
  if (XGetErrorText(dpy, ev->error_code, buffer, sizeof(buffer)))
    fatal("XGetErrorText");
  fprintf(stderr,gettext("xbrlapi: X Error %d, %s on display %s\n"), ev->type, buffer, XDisplayName(Xdisplay));
  fprintf(stderr,gettext("xbrlapi: resource %#010lx, req %u:%u\n"),ev->resourceid,ev->request_code,ev->minor_code);
  exit(PROG_EXIT_FATAL);
}

static int getXVTnb(void) {
  Window root;
  Atom property;
  Atom actual_type;
  int actual_format;
  unsigned long nitems;
  unsigned long bytes_after;
  unsigned char *buf;
  int vt = -1;

  root=DefaultRootWindow(dpy);

  if ((property=XInternAtom(dpy,"XFree86_VT",False))==None) {
    fprintf(stderr,gettext("xbrlapi: no XFree86_VT atom\n"));
    return -1;
  }

  if (XGetWindowProperty(dpy,root,property,0,1,False,AnyPropertyType,
    &actual_type, &actual_format, &nitems, &bytes_after, &buf)) {
    fprintf(stderr,gettext("xbrlapi: cannot get root window XFree86_VT property\n"));
    return -1;
  }

  if (nitems<1) {
    fprintf(stderr, gettext("xbrlapi: no items for VT number\n"));
    goto out;
  }
  if (nitems>1)
    fprintf(stderr,gettext("xbrlapi: more than one item for VT number\n"));
  switch (actual_type) {
  case XA_CARDINAL:
  case XA_INTEGER:
  case XA_WINDOW:
    switch (actual_format) {
    case 8:  vt = (*(uint8_t *)buf); break;
    case 16: vt = (*(uint16_t *)buf); break;
    case 32: vt = (*(uint32_t *)buf); break;
    default: fprintf(stderr, gettext("xbrlapi: bad format for VT number\n")); goto out;
    }
    break;
  default: fprintf(stderr, gettext("xbrlapi: bad type for VT number\n")); goto out;
  }
out:
  if (!XFree(buf)) fatal("XFree(VTnobuf)");
  return vt;
}

static int grabWindow(Window win,int level) {
#ifdef DEBUG
  char spaces[level+1];
#endif /* DEBUG */

  grabFailed=0;
  if (!XSelectInput(dpy,win,PropertyChangeMask|FocusChangeMask|SubstructureNotifyMask) || grabFailed)
    return 0;

#ifdef DEBUG
  memset(spaces,' ',level);
  spaces[level]='\0';
  debugf("%sgrabbed %#010lx\n",spaces,win);
#endif /* DEBUG */
  return 1;
}

static char *getWindowTitle(Window win) {
  int wm_name_size=32;
  Atom actual_type;
  int actual_format;
  unsigned long nitems,bytes_after;
  unsigned char *wm_name=NULL;
  char *ret;

  do {
    if (XGetWindowProperty(dpy,win,netWmNameAtom,0,wm_name_size,False,
	/*XA_STRING*/AnyPropertyType,&actual_type,&actual_format,&nitems,&bytes_after,
	&wm_name)) {
      wm_name = NULL;
      break; /* window disappeared or not available */
    }
    wm_name_size+=bytes_after;
    if (!bytes_after) break;
    if (!XFree(wm_name)) fatal("tempo_XFree(wm_name)");
  } while (1);
  if (!wm_name) do {
    if (XGetWindowProperty(dpy,win,XA_WM_NAME,0,wm_name_size,False,
	/*XA_STRING*/AnyPropertyType,&actual_type,&actual_format,&nitems,&bytes_after,
	&wm_name))
      return NULL; /* window disappeared */
    if (wm_name_size >= nitems + 1) break;
    wm_name_size += bytes_after + 1;
    if (!XFree(wm_name)) fatal("tempo_XFree(wm_name)");
  } while (1);
  if (actual_type==None) {
    XFree(wm_name);
    return NULL;
  }
  wm_name[nitems++] = 0;
  ret = strdup((char *) wm_name);
  XFree(wm_name);
  debugf("type %ld name %s len %ld\n",actual_type,ret,nitems);
#ifdef HAVE_ICONV_H
  {
    if (actual_type == utf8StringAtom && utf8Conv != (iconv_t)(-1)) {
      char *ret2;
      size_t input_size, output_size;
      char *input, *output;

      input_size = nitems;
      input = ret;
      output_size = nitems * MB_CUR_MAX;
      output = ret2 = malloc(output_size);
      if (iconv(utf8Conv, &input, &input_size, &output, &output_size) == -1) {
	free(ret2);
      } else {
	free(ret);
	ret = realloc(ret2, nitems * MB_CUR_MAX - output_size);
	debugf("-> %s\n",ret);
      }
    }
  }
#endif /* HAVE_ICONV_H */
  return ret;
}

static int grabWindows(Window win,int level) {
  Window root,parent,*children;
  unsigned int nchildren,i;
  int res=1;

  if (!grabWindow(win,level)) return 1; /* window disappeared */

  if (!XQueryTree(dpy,win,&root,&parent,&children,&nchildren)) return 0;

  add_window(win,root,getWindowTitle(win));

  if (!children) return 1;

  for (i=0;i<nchildren;i++)
    if (children[i] && !grabWindows(children[i],level+1)) {
      res=0;
      break;
    }

  if (!XFree(children)) fatal("XFree(children)");
  return res;
}

static void setName(const struct window *window) {
  if (!window->wm_name) {
    if (window->win!=window->root)
      api_setName("window without name");
  } else api_setName(window->wm_name);
}

static void setFocus(Window win) {
  curWindow=win;
  api_setFocus((uint32_t)win);

  if (!quiet) {
    struct window *window = window_of_Window(win);

    if (window) {
      setName(window);
    } else {
      fprintf(stderr, gettext("xbrlapi: didn't grab window %#010lx but got focus\n"), win);
      api_setName(isRootWindow(win)? "root window": "unnamed window");
    }
  }
}

#ifdef CAN_SIMULATE_KEY_PRESSES
static int tryModifiers(KeyCode keycode, unsigned int *modifiers, unsigned int modifiers_try, KeySym keysym) {
  KeySym keysymRet;
  unsigned int modifiersRet;
  if (!XkbLookupKeySym(dpy, keycode, modifiers_try, &modifiersRet, &keysymRet))
    return 0;
  if (keysymRet != keysym)
    return 0;
  *modifiers |= modifiers_try;
  return 1;
}

static void ignoreServerKeys(void) {
  brlapi_range_t range = {
    .first = BRLAPI_KEY_FLG(ControlMask|Mod1Mask),
    .last  = BRLAPI_KEY_FLG(ControlMask|Mod1Mask)|~BRLAPI_KEY_FLAGS_MASK,
  };
  if (brlapi_ignoreKeyRanges(&range, 1))
  {
    fatal_brlapi_errno("ignoreKeyRanges",NULL);
    return;
  }
}
#endif /* CAN_SIMULATE_KEY_PRESSES */

static void clipboardContentChanged(brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, void *priv, const void *data, size_t len) {
  free(clipboardData);
  clipboardData = strndup(data, len);
  debugf("new clipboard content from BrlAPI: '%s'\n", (const char *) clipboardData);
  if (dpy)
    XSelSet(dpy, &xselData);
}

static void XClipboardContentChanged(const char *data, unsigned long size) {
  free(clipboardData);
  if (data) {
    clipboardData = strndup(data, size);
    brlapi_setParameter(BRLAPI_PARAM_CLIPBOARD_CONTENT, 0, BRLAPI_PARAMF_GLOBAL, clipboardData, size);
    debugf("new clipboard content from X: '%s'\n", (const char *) clipboardData);
  } else
    clipboardData = NULL;
}


static void toX_f(const char *display) {
  Window root;
  XEvent ev;
  int i;
  int X_fd;
  fd_set readfds;
  int maxfd;
#ifdef CAN_SIMULATE_KEY_PRESSES
  int res;
  brlapi_keyCode_t code;
  unsigned int keysym, keycode, modifiers, next_modifiers = 0;
  Bool haveXTest;
  int eventBase, errorBase, majorVersion, minorVersion;
  XkbDescPtr xkb = NULL;
  XkbMapChangesRec changes = { .changed = XkbKeyTypesMask|XkbKeySymsMask };
  int oneGroupType[XkbNumKbdGroups] = { XkbOneLevelIndex };
  Status status;
  int last_remap_keycode = -1, remap_keycode;
#endif /* CAN_SIMULATE_KEY_PRESSES */

  Xdisplay = display;
  if (!Xdisplay) Xdisplay=getenv("DISPLAY");
  if (!(dpy=XOpenDisplay(Xdisplay))) fatal(gettext("cannot connect to display %s\n"),Xdisplay);

  if (!XSetErrorHandler(ErrorHandler)) fatal(gettext("strange old error handler\n"));

#ifdef CAN_SIMULATE_KEY_PRESSES
  haveXTest = XTestQueryExtension(dpy, &eventBase, &errorBase, &majorVersion, &minorVersion);

  {
    int foo;
    int major = XkbMajorVersion, minor = XkbMinorVersion;
    if (!XkbLibraryVersion(&major, &minor))
      fatal(gettext("Incompatible XKB library\n"));
    if (!XkbQueryExtension(dpy, &foo, &foo, &foo, &major, &minor))
      fatal(gettext("Incompatible XKB server support\n"));
    if (!XQueryExtension(dpy, "XKEYBOARD", &xkb_major_opcode, &foo, &foo))
      fatal(gettext("Could not get XKB major opcode\n"));
  }
#endif /* CAN_SIMULATE_KEY_PRESSES */

  XSelInit(dpy, &xselData);

  if (clipboardData)
    XSelSet(dpy, &xselData);

  X_fd = XConnectionNumber(dpy);

  if (brlapi_fd>=0)
  {
    getVT();
#ifdef CAN_SIMULATE_KEY_PRESSES
    ignoreServerKeys();
#endif /* CAN_SIMULATE_KEY_PRESSES */
  }
  netWmNameAtom = XInternAtom(dpy,"_NET_WM_NAME",False);
  utf8StringAtom = XInternAtom(dpy,"UTF8_STRING",False);

#if defined(HAVE_NL_LANGINFO) && defined(HAVE_ICONV_H)
  {
    char *localCharset = nl_langinfo(CODESET);
    if (strcmp(localCharset, "UTF-8")) {
      char buf[strlen(localCharset) + 10 + 1];
      snprintf(buf, sizeof(buf), "%s//TRANSLIT", localCharset);
      if ((utf8Conv = iconv_open(buf, "UTF-8")) == (iconv_t)(-1))
        utf8Conv = iconv_open(localCharset, "UTF-8");
    }
  }
#endif /* defined(HAVE_NL_LANGINFO) && defined(HAVE_ICONV_H) */

  for (i=0;i<ScreenCount(dpy);i++) {
    root=RootWindow(dpy,i);
    if (!grabWindows(root,0)) fatal(gettext("cannot grab windows on screen %d\n"),i);
  }

  {
    Window win;
    int revert_to;
    if (!XGetInputFocus(dpy,&win,&revert_to))
      fatal(gettext("failed to get first focus\n"));
    setFocus(win);
  }
  while(1) {
    struct timeval timeout={.tv_sec=1,.tv_usec=0};
    XFlush(dpy);
    FD_ZERO(&readfds);
    if (brlapi_fd>=0)
      FD_SET(brlapi_fd, &readfds);
    FD_SET(X_fd, &readfds);
    maxfd = brlapi_fd>=0 && X_fd<brlapi_fd ? brlapi_fd+1 : X_fd+1;
    /* Try to reconnect to brlapi every second while disconnected */
    if (select(maxfd,&readfds,NULL,NULL,brlapi_fd<=0?&timeout:NULL)<0)
      fatal_errno("select",NULL);
    if (FD_ISSET(X_fd,&readfds))

    while (XPending(dpy)) {
      if ((i=XNextEvent(dpy,&ev)))
	fatal("XNextEvent: %d\n",i);

      if (!XSelProcess(dpy, &xselData, &ev, clipboardData, XClipboardContentChanged))
      switch (ev.type) {
      /* focus events */
      case FocusIn:
	switch (ev.xfocus.detail) {
	case NotifyAncestor:
	case NotifyInferior:
	case NotifyNonlinear:
	case NotifyPointerRoot:
	case NotifyDetailNone:
	  setFocus(ev.xfocus.window); break;
	} break;
      case FocusOut:
	/* ignore
	switch (ev.xfocus.detail) {
	case NotifyAncestor:
	case NotifyInferior:
	case NotifyNonlinear:
	case NotifyPointerRoot:
	case NotifyDetailNone:
	printf("win %#010lx lost focus\n",ev.xfocus.window);
	break;
	}
	*/
	break;

      /* create & destroy events */
      case CreateNotify: {
      /* there's a race condition here : a window may get the focus or change
       * its title between it is created and we achieve XSelectInput on it */
	Window win = ev.xcreatewindow.window;
	struct window *window;
	if (!grabWindow(win,0)) break; /* window already disappeared ! */
	debugf("win %#010lx created\n",win);
	if (!(window = window_of_Window(ev.xcreatewindow.parent))) {
	  fprintf(stderr,gettext("xbrlapi: didn't grab parent of %#010lx\n"),win);
	  add_window(win,None,getWindowTitle(win));
	} else add_window(win,window->root,getWindowTitle(win));
      } break;
      case DestroyNotify:
	debugf("win %#010lx destroyed\n",ev.xdestroywindow.window);
	if (del_window(ev.xdestroywindow.window))
	  debugf("destroy: didn't grab window %#010lx\n",ev.xdestroywindow.window);
	break;

      /* Property change: WM_NAME ? */
      case PropertyNotify:
	if (ev.xproperty.atom==XA_WM_NAME ||
	    (netWmNameAtom != None && ev.xproperty.atom == netWmNameAtom)) {
	  Window win = ev.xproperty.window;
	  debugf("WM_NAME property of %#010lx changed\n",win);
	  struct window *window;
	  if (!(window=window_of_Window(win))) {
	    fprintf(stderr,gettext("xbrlapi: didn't grab window %#010lx\n"),win);
	    add_window(win,None,getWindowTitle(win));
	  } else {
	    if (window->wm_name)
	      if (!XFree(window->wm_name)) fatal(gettext("XFree(wm_name) for change"));
	    if ((window->wm_name=getWindowTitle(win))) {
	      if (!quiet && win==curWindow)
		api_setName(window->wm_name);
	    } else fprintf(stderr,gettext("xbrlapi: window %#010lx changed to NULL name\n"),win);
	  }
	}
	break;
      case MappingNotify:
	XRefreshKeyboardMapping(&ev.xmapping);
	break;
      /* ignored events */
      case UnmapNotify:
      case MapNotify:
      case MapRequest:
      case ReparentNotify:
      case ConfigureNotify:
      case GravityNotify:
      case ConfigureRequest:
      case CirculateNotify:
      case CirculateRequest:
      case ClientMessage:
	break;

      /* "shouldn't happen" events */
      default: fprintf(stderr,gettext("xbrlapi: unhandled event type: %d\n"),ev.type); break;
      }
    }
    if (brlapi_fd>=0) {
#ifdef CAN_SIMULATE_KEY_PRESSES
     if (haveXTest && FD_ISSET(brlapi_fd,&readfds)) {
      while (((res = brlapi_readKey(0, &code))==1)) {
	switch (code & BRLAPI_KEY_TYPE_MASK) {
	  case BRLAPI_KEY_TYPE_CMD:
	    switch (code & BRLAPI_KEY_CODE_MASK) {
              {
                unsigned int modifier;

              case BRLAPI_KEY_CMD_SHIFT:
                modifier = ShiftMask;
                goto doModifier;

              case BRLAPI_KEY_CMD_UPPER:
                modifier = LockMask;
                goto doModifier;

              case BRLAPI_KEY_CMD_CONTROL:
                modifier = ControlMask;
                goto doModifier;

              case BRLAPI_KEY_CMD_META:
                modifier = Mod1Mask;
                goto doModifier;

              doModifier:
                switch (code & BRLAPI_KEY_FLG_TOGGLE_MASK) {
                  case 0:
                    next_modifiers ^= modifier;
                    break;

                  case BRLAPI_KEY_FLG_TOGGLE_ON:
                    next_modifiers |= modifier;
                    break;

                  case BRLAPI_KEY_FLG_TOGGLE_OFF:
                    next_modifiers &= ~modifier;
                    break;

                  default:
                  case BRLAPI_KEY_FLG_TOGGLE_ON | BRLAPI_KEY_FLG_TOGGLE_OFF:
                    break;
                }

                break;
              }

	      default:
		fprintf(stderr, "xbrlapi: %s: %" BRLAPI_PRIxKEYCODE "\n",
			gettext("unexpected cmd"), code);
		break;
	    }
	    break;
	  case BRLAPI_KEY_TYPE_SYM:
	    modifiers = ((code & BRLAPI_KEY_FLAGS_MASK) >> BRLAPI_KEY_FLAGS_SHIFT) & 0xFF;
	    keysym = code & BRLAPI_KEY_CODE_MASK;
	    keycode = XKeysymToKeycode(dpy,keysym);
	    remap_keycode = -1;
	    if (keycode == NoSymbol) {
	      debugf(gettext("xbrlapi: Couldn't translate keysym %08X to keycode.\n"),keysym);
	      goto needRemap;
	    }

	    {
	      static const unsigned int tryTable[] = {
		0,
		ShiftMask,
		Mod2Mask,
		Mod3Mask,
		Mod4Mask,
		Mod5Mask,
		ShiftMask|Mod2Mask,
		ShiftMask|Mod3Mask,
		ShiftMask|Mod4Mask,
		ShiftMask|Mod5Mask,
		0
	      };
	      const unsigned int *try = tryTable;

	      do {
		if (tryModifiers(keycode, &modifiers, *try, keysym)) goto foundModifiers;
	      } while (*++try);

	      debugf(gettext("xbrlapi: Couldn't find modifiers to apply to %d for getting keysym %08X\n"),keycode,keysym);
	    }

	  needRemap:
	    {
	      /* Try tofind an unassigned keycode to remap it temporarily to the requested keysym. */
	      xkb = XkbGetMap(dpy,XkbKeyTypesMask|XkbKeySymsMask,XkbUseCoreKbd);
	      /* Start from big keycodes, usually unassigned. */
	      for (i = xkb->max_key_code;
		   i >= xkb->min_key_code && (XkbKeyNumGroups(xkb,i) != 0 || i == last_remap_keycode);
		   i--)
		;
	      if (i < xkb->min_key_code) {
		fprintf(stderr,gettext("xbrlapi: Couldn't find a keycode to remap for simulating unbound keysym %08X\n"),keysym);
		goto abortRemap;
	      }
	      remap_keycode = keycode = i;
	      next_modifiers = modifiers = 0;

	      /* Remap this keycode. */
	      changes.first_key_sym = keycode;
	      changes.num_key_syms = 1;
	      if ((status = XkbChangeTypesOfKey(xkb,keycode,1,XkbGroup1Mask,oneGroupType,&changes))) {
		debugf("Error while changing client keymap: %d\n", status);
		goto abortRemap;
	      }
	      XkbKeySymEntry(xkb,keycode,0,0) = keysym;
	      if (!XkbChangeMap(dpy,xkb,&changes)) {
		debugf("Error while changing server keymap\n");
		goto abortRemap;
	      }
	      XkbFreeKeyboard(xkb,0,True);
	      debugf("Remapped keycode %d to keysym %08X\n",keycode,keysym);
	    }

	  foundModifiers:
	    debugf("key %08X: (%d,%x,%x)\n", keysym, keycode, next_modifiers, modifiers);
	    modifiers |= next_modifiers;
	    next_modifiers = 0;
	    if (modifiers)
	      XkbLockModifiers(dpy, XkbUseCoreKbd, modifiers, modifiers);
	    XTestFakeKeyEvent(dpy,keycode,True,1);
	    XTestFakeKeyEvent(dpy,keycode,False,1);
	    if (modifiers)
	      XkbLockModifiers(dpy, XkbUseCoreKbd, modifiers, 0);

	    /* Remove previous keycode mapping */
	    if (last_remap_keycode != -1) {
	      /* Note: since X11 is asynchronous, we should not immediately
	       * unmap the just-mapped keycode, otherwise when the client
	       * eventually gets to read the new Xkb state from the server,
	       * the key might have been synthesized and the keycode unmapped
	       * already. We just hope the user does not type too fast for the
	       * application to catch up. */
	      xkb = XkbGetMap(dpy,XkbKeyTypesMask|XkbKeySymsMask,XkbUseCoreKbd);
	      changes.first_key_sym = last_remap_keycode;
	      changes.num_key_syms = 1;
	      if ((status = XkbChangeTypesOfKey(xkb,last_remap_keycode,0,XkbGroup1Mask,NULL,&changes))) {
		debugf("Oops, error while restoring client keymap: %d\n", status);
	      } else {
		XkbChangeMap(dpy,xkb,&changes);
		debugf("restored last keycode %d\n", last_remap_keycode);
	      }
	      XkbFreeKeyboard(xkb,0,True);
	    }
	    XFlush(dpy);
	    last_remap_keycode = remap_keycode;
	    break;

	  abortRemap:
	    XkbFreeKeyboard(xkb, 0, True);
	    xkb = NULL;
	    break;

	  default:
	    fprintf(stderr, "xbrlapi: %s: %" BRLAPI_PRIxKEYCODE "\n",
		    gettext("unexpected block type"), code);
	    next_modifiers = 0;
	    break;
	}
      }
      if (res<0)
	fatal_brlapi_errno("brlapi_readKey",NULL);
     }
#endif /* CAN_SIMULATE_KEY_PRESSES */
    } else {
      /* Try to reconnect */
      if (tobrltty_init(auth,host))
      {
	getVT();
#ifdef CAN_SIMULATE_KEY_PRESSES
	ignoreServerKeys();
#endif /* CAN_SIMULATE_KEY_PRESSES */
	api_setLastName();
	api_setLastFocus();
      }
    }
  }
}

/******************************************************************************
 * main
 */

static void term_handler(int foo) {
  api_cleanExit();
  exit(PROG_EXIT_SUCCESS);
}

int
main (int argc, char *argv[]) {
  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "xbrlapi",

      .usage = {
        .purpose = strtext("Augment an X session by supporting input typed on the braille device, showing the title of the focused window on the braille display, and switching braille focus to it."),
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  signal(SIGTERM,term_handler);
  signal(SIGINT,term_handler);
#ifdef SIGHUP
  signal(SIGHUP,term_handler);
#endif /* SIGHUP */
#ifdef SIGQUIT
  signal(SIGQUIT,term_handler);
#endif /* SIGQUIT */
#ifdef SIGPIPE
  signal(SIGPIPE,term_handler);
#endif /* SIGPIPE */

  tobrltty_init(auth,host);

  if (!no_daemon) {
    pid_t child = fork();
    if (child == -1)
      fatal_errno("failed to fork", NULL);

    if (child)
      exit(PROG_EXIT_SUCCESS);

    if (setsid() == -1)
      fatal_errno("failed to create background session", NULL);
  }

  toX_f(xDisplay);

  return PROG_EXIT_SUCCESS;
}
