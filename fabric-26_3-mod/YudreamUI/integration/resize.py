"""Resize only this fixture's real X11 client window; Minecraft handles the resulting viewport event."""
import ctypes as C
import ctypes.util
import sys
from env import ROOT

def resize(width,height):
 x=C.CDLL(ctypes.util.find_library('X11'));x.XOpenDisplay.restype=C.c_void_p;display=x.XOpenDisplay(None)
 if not display:raise RuntimeError('X11 display unavailable; run the fixture with SDL_VIDEO_DRIVER=x11')
 x.XDefaultRootWindow.argtypes=[C.c_void_p];x.XDefaultRootWindow.restype=C.c_ulong
 x.XInternAtom.argtypes=[C.c_void_p,C.c_char_p,C.c_int];x.XInternAtom.restype=C.c_ulong
 x.XGetWindowProperty.argtypes=[C.c_void_p,C.c_ulong,C.c_ulong,C.c_long,C.c_long,C.c_int,C.c_ulong,C.POINTER(C.c_ulong),C.POINTER(C.c_int),C.POINTER(C.c_ulong),C.POINTER(C.c_ulong),C.POINTER(C.c_void_p)]
 x.XQueryTree.argtypes=[C.c_void_p,C.c_ulong,C.POINTER(C.c_ulong),C.POINTER(C.c_ulong),C.POINTER(C.POINTER(C.c_ulong)),C.POINTER(C.c_uint)]
 x.XFree.argtypes=[C.c_void_p];x.XResizeWindow.argtypes=[C.c_void_p,C.c_ulong,C.c_uint,C.c_uint];x.XFlush.argtypes=[C.c_void_p];x.XCloseDisplay.argtypes=[C.c_void_p]
 atom=x.XInternAtom(display,b'_NET_WM_PID',0);pid=int((ROOT/'client/pid').read_text());matches=[]
 def visit(window,depth=0):
  kind=C.c_ulong();fmt=C.c_int();count=C.c_ulong();rest=C.c_ulong();value=C.c_void_p()
  x.XGetWindowProperty(display,window,atom,0,1,0,0,C.byref(kind),C.byref(fmt),C.byref(count),C.byref(rest),C.byref(value))
  if value.value:
   if count.value and fmt.value==32 and C.cast(value,C.POINTER(C.c_ulong))[0]==pid:matches.append(window)
   x.XFree(value)
  if depth>=5:return
  root=C.c_ulong();parent=C.c_ulong();kids=C.POINTER(C.c_ulong)();n=C.c_uint()
  if x.XQueryTree(display,window,C.byref(root),C.byref(parent),C.byref(kids),C.byref(n)):
   for i in range(n.value):visit(kids[i],depth+1)
   if kids:x.XFree(kids)
 visit(x.XDefaultRootWindow(display))
 if len(matches)!=1:raise RuntimeError(f'Expected one fixture window, found {len(matches)}')
 x.XResizeWindow(display,matches[0],width,height);x.XFlush(display);x.XCloseDisplay(display)
 return {'width':width,'height':height}
if __name__=='__main__':print(resize(int(sys.argv[1]),int(sys.argv[2])))
