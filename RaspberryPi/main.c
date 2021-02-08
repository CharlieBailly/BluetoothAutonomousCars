#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <openssl/ssl.h>
#include <openssl/err.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>

//Create the listening socket
int create_socket(int port)
{
    int s;
    struct sockaddr_rc loc_addr = { 0 }, rem_addr = { 0 };
    socklen_t opt = sizeof(rem_addr);

    //Parameters
    loc_addr.rc_family = AF_BLUETOOTH;
    loc_addr.rc_bdaddr = *BDADDR_ANY;
    loc_addr.rc_channel = (uint8_t) 1;

    //Socket creation 
    s = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
    
    //Check for errors
    if (s < 0) {
	perror("Unable to create socket");
	exit(EXIT_FAILURE);
    }

    //Binding socketto specified parameters
    if (bind(s, (struct sockaddr *)&loc_addr, sizeof(loc_addr))) {
	perror("Unable to bind");
	exit(EXIT_FAILURE);
    }

    //Listening
    if (listen(s, 1) < 0) {
	perror("Unable to listen");
	exit(EXIT_FAILURE);
    }

    return s;
}

//OpenSSL initilalisation
void init_openssl()
{ 
    SSL_load_error_strings();	
    OpenSSL_add_ssl_algorithms();
}

//OnpenSSL cleanup
void cleanup_openssl()
{
    EVP_cleanup();
}

SSL_CTX *create_context()
{
    const SSL_METHOD *method;
    SSL_CTX *ctx;

    method = SSLv23_server_method();

    ctx = SSL_CTX_new(method);
    if (!ctx) {
	perror("Unable to create SSL context");
	ERR_print_errors_fp(stderr);
	exit(EXIT_FAILURE);
    }

    return ctx;
}

//Configuring OpenSSL context
void configure_context(SSL_CTX *ctx)
{
    SSL_CTX_set_ecdh_auto(ctx, 1);

    //Certificate
    if (SSL_CTX_use_certificate_file(ctx, "./cert.pem", SSL_FILETYPE_PEM) <= 0) {
        ERR_print_errors_fp(stderr);
	exit(EXIT_FAILURE);
    }

    //Private key
    if (SSL_CTX_use_PrivateKey_file(ctx, "./key.pem", SSL_FILETYPE_PEM) <= 0 ) {
        ERR_print_errors_fp(stderr);
	exit(EXIT_FAILURE);
    }
}

int main(int argc, char **argv)
{
    int sock;
    SSL_CTX *ctx;

    //OPen SSL initialization
    init_openssl();
    ctx = create_context();

    configure_context(ctx);

    //Creating the bluetooth socket
    sock = create_socket(4433);

    //Handle connections
    while(1) {
        struct sockaddr_in addr;
        uint len = sizeof(addr);
        SSL *ssl;
        const char reply[] = "test\n";

        //Wait for a message on the socket
        int client = accept(sock, (struct sockaddr*)&addr, &len);
        if (client < 0) {
            perror("Unable to accept");
            exit(EXIT_FAILURE);
        }

        ssl = SSL_new(ctx);
        SSL_set_fd(ssl, client);

        if (SSL_accept(ssl) <= 0) {
            ERR_print_errors_fp(stderr);
        }
        else {
            SSL_write(ssl, reply, strlen(reply));
        }

        SSL_shutdown(ssl);
        SSL_free(ssl);
        close(client);
    }

    close(sock);
    SSL_CTX_free(ctx);
    cleanup_openssl();
}