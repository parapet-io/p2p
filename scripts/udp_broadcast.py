from socket import *
import time

def run():
    cs = socket(AF_INET, SOCK_DGRAM)
    cs.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
    cs.setsockopt(SOL_SOCKET, SO_BROADCAST, 1)
    while 1:
        cs.sendto('1'.encode(), ('255.255.255.255', 9999))
        time.sleep(1)
        print("sent udp\n")

run()