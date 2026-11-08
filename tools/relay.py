# TCP relay: LAN 0.0.0.0:18787 -> 127.0.0.1:18787 (adb forward -> emulator 8787). Lets a phone on the same Wi-Fi reach the emulator's upload server.
import socket, threading, sys
LISTEN=("0.0.0.0", int(sys.argv[1]) if len(sys.argv)>1 else 18787); TARGET=("127.0.0.1", int(sys.argv[2]) if len(sys.argv)>2 else 18787)
def pump(a,b):
    try:
        while True:
            d=a.recv(65536)
            if not d: break
            b.sendall(d)
    except OSError: pass
    finally:
        try: b.shutdown(socket.SHUT_WR)
        except OSError: pass
def serve(c):
    try: t=socket.create_connection(TARGET, timeout=10)
    except OSError: c.close(); return
    threading.Thread(target=pump,args=(c,t),daemon=True).start(); pump(t,c); c.close(); t.close()
s=socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); s.bind(LISTEN); s.listen(16)
print("relay", LISTEN, "->", TARGET, flush=True)
while True:
    c,_=s.accept(); threading.Thread(target=serve,args=(c,),daemon=True).start()
