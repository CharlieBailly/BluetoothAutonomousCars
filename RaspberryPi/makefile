CC=gcc

proxy.o: proxy.c
	$(CC) -c proxy.c -o proxy.o

proxy: proxy.o
	$(CC) proxy.o -o proxy -lssl -lcrypto -lbluetooth

unsecured_proxy.o: unsecured_proxy.c
	$(CC) -c unsecured_proxy.c -o unsecured_proxy.o

uproxy: unsecured_proxy.o
	$(CC) unsecured_proxy.o -o uproxy -lbluetooth

clean:
	rm -f *.o server uproxy