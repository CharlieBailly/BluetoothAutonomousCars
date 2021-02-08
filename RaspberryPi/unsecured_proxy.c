#include <stdio.h>
#include <unistd.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>
#include <sys/types.h> 
#include <arpa/inet.h> 
#include <netinet/in.h> 
#include <net/ethernet.h>
#include <stdlib.h>
#include <linux/if_packet.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <signal.h> 
#include <wait.h> 

int writer_thread_pid;
int reader_thread_pid;

//Interface réseau sur laquel on communique avec la voiture
const char* if_name = "lo";

int readGeonet(int clientSocket);
int writeGeonet(int clientSocket);
void sig_handler(int sig);

int main(int argc, char **argv)
{
    //Structure pour l'adresse BT
    struct sockaddr_rc loc_addr = { 0 }, rem_addr = { 0 };
    //Buffer bluetooth;
    char buf[1024] = { 0 };
    int s, client;
    socklen_t opt = sizeof(rem_addr);

    //Allocation du socket bluetooth
    s = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);

    //On associe le socket au premier adaptateur bluetooth disponible
    //sur le channel 1
    loc_addr.rc_family = AF_BLUETOOTH;
    loc_addr.rc_bdaddr = *BDADDR_ANY;
    loc_addr.rc_channel = (uint8_t) 1;
    bind(s, (struct sockaddr *)&loc_addr, sizeof(loc_addr));

    //On se place en mode écoute
    listen(s, 1);

    fprintf(stderr, "Proxy waiting waiting for a client\n");

    //On aatend l'arrivée d'un client
    client = accept(s, (struct sockaddr *)&rem_addr, &opt);

    //On récupère l'addresse du client
    ba2str( &rem_addr.rc_bdaddr, buf );
    fprintf(stderr, "accepted connection from %s\n", buf);

    //Création du processus d'écoute de la voiture
    int pid = fork();

    if(pid == 0)
    {
        //On commence la lecture des messages Geonet
        readGeonet(client);
    }
    else
    {
        writer_thread_pid = pid;

        //Création du processus d'écriture de la voiture
        int pid2 = fork();
        if(pid2 == 0)
        {
            //On commence l'écriture des messages Geonet reçus par l'app
            writeGeonet(client);
        }
        else
        {
            reader_thread_pid = pid2;
            //Gestion des interruptions pour ne pas laisser de processus orphelins
            signal(SIGTERM, sig_handler);
        }
    }

    int status;

    //Attente des processus d'écoute et d'écriture
    int ret = wait(&status);

    //On termine tout les processus fils
    if(ret == reader_thread_pid)
    {
        kill(writer_thread_pid, SIGTERM);
    }
    else if(ret == writer_thread_pid)
    {
        kill(reader_thread_pid, SIGTERM);
    }
    else
    {
        fprintf(stderr, "Wait failed");
    }

    //On ferme la connexion et le soscket
    close(client);
    close(s);

    return 0;
}

//Gestion du signal SIGTERM pour terminer les processus fils également
void sig_handler(int sig)
{
    kill(reader_thread_pid, SIGTERM);
    kill(writer_thread_pid, SIGTERM);
}

// Proxy app vers voiture
int writeGeonet(int clientSocket)
{
    //Initialisation du buffer BT
    char buf[1024] = { 0 };
    int bytes_read, i;

    //Initialisation des structures pour l'envoie Ethernet
    int sockfd;
	struct ifreq if_idx;
	struct ifreq if_mac;
	int tx_len = 0;
	char sendbuf[1024];
	struct ether_header *eh = (struct ether_header *) sendbuf;
	struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
	struct sockaddr_ll socket_address;

	// Ouverture d'un socket ethernet RAW
	if ((sockfd = socket(AF_PACKET, SOCK_RAW, IPPROTO_RAW)) == -1) {
	    perror("socket");
	}

	//On récupère l'index de l'interface réseau sur laquelle envoyer les messages
	memset(&if_idx, 0, sizeof(struct ifreq));
	strncpy(if_idx.ifr_name, if_name, IFNAMSIZ-1);
	if (ioctl(sockfd, SIOCGIFINDEX, &if_idx) < 0)
	    perror("SIOCGIFINDEX");

	//On récupère l'addresse MAC associée
	memset(&if_mac, 0, sizeof(struct ifreq));
	strncpy(if_mac.ifr_name, if_name, IFNAMSIZ-1);
	if (ioctl(sockfd, SIOCGIFHWADDR, &if_mac) < 0)
	    perror("SIOCGIFHWADDR");

    //MAC source (modifiée pour que vanetza ne le discard pas)
    eh->ether_shost[0] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[0];
    eh->ether_shost[1] = 0xff;
    eh->ether_shost[2] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[2];
    eh->ether_shost[3] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[3];
    eh->ether_shost[4] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[4];
    eh->ether_shost[5] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[5];
    //MAC de destination
    eh->ether_dhost[0] = 0xff;
    eh->ether_dhost[1] = 0xff;
    eh->ether_dhost[2] = 0xff;
    eh->ether_dhost[3] = 0xff;
    eh->ether_dhost[4] = 0xff;
    eh->ether_dhost[5] = 0xff;
    //EtherType (ici GeoNet)
    eh->ether_type = 0x4789;

    //Paramètres pour l'envoie
    socket_address.sll_ifindex = if_idx.ifr_ifindex;
    socket_address.sll_halen = ETH_ALEN;
    socket_address.sll_addr[0] = 0xff;
    socket_address.sll_addr[1] = 0xff;
    socket_address.sll_addr[2] = 0xff;
    socket_address.sll_addr[3] = 0xff;
    socket_address.sll_addr[4] = 0xff;
    socket_address.sll_addr[5] = 0xff;

    fprintf(stderr, "App to car proxy started ...\nNow waiting ... \n");

    while(1)
    {
        //Remise a 0 du buffer
        memset(buf, 0, sizeof(buf));

        //On attend de recevoir des données en BT
        bytes_read = read(clientSocket, buf, sizeof(buf));

        if( bytes_read > 0 ) 
        {
            tx_len = sizeof(struct ether_header);

            //On construit le header Ethernet
            memset(sendbuf, 0, 1024);

            //On remplie la partie payload avec le message reçu en BT
            for(i=0; i < sizeof(buf); i++)
            {
                sendbuf[tx_len++] = buf[i];
            }

            //On envoie le message à la voiture
            if (sendto(sockfd, sendbuf, tx_len, 0, (struct sockaddr*)&socket_address, sizeof(struct sockaddr_ll)) < 0)
            {
                fprintf(stderr, "A message from the app has been received but was not sent to the car\n");
            }
            else
            {
                fprintf(stderr, "Message received from the app and sent to the car\n");
            }
                
        
        }
    }
}

// Proxy voiture vers app
int readGeonet(int cleintSocket)
{
    //Buffer Ethernet
    char buffer[65536];

    //Création du socket Ethernet
    int fd = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));

    if (fd == -1) 
    {
        perror("socket");
        exit(1);
    }

    //On détermine l'index du l'interface réseau à utiliser
    struct ifreq ifr;
    size_t if_name_len = strlen(if_name);
    if (if_name_len < sizeof(ifr.ifr_name)) 
    {
        memcpy(ifr.ifr_name, if_name, if_name_len);
        ifr.ifr_name[if_name_len] = 0;
    } else {
        fprintf(stderr, "interface name is too long\n");
        exit(1);
    }

    if (ioctl(fd,SIOCGIFINDEX, &ifr) == -1) 
    {
        perror("ioctl");
        exit(1);
    }

    int ifindex=ifr.ifr_ifindex;

    
    //On place l'interrface en écoute globale
    struct packet_mreq mreq = {0};
    mreq.mr_ifindex = ifindex;
    mreq.mr_type = PACKET_MR_PROMISC;
    if (setsockopt(fd, SOL_PACKET, PACKET_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) == -1) {
        perror("setsockopt");
        exit(1);
    }

    //Paramètres du socket
    struct sockaddr_ll addr = {0};
    addr.sll_family = AF_PACKET;
    addr.sll_ifindex = ifindex;
    addr.sll_protocol = 0x4789;

    //On associe le socket à ce que le recherche, sur l'interface voulue
    if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) 
    {
        perror("bind");
        exit(1);
    }

    struct sockaddr_ll src_addr;
    socklen_t src_addr_len = sizeof(src_addr);

    fprintf(stderr, "Car to app proxy started ...\nNow waiting ... \n");

    while(1)
    {
        //On attend une trame Ethernet
        ssize_t count = recvfrom(fd, buffer, sizeof(buffer), 0, (struct sockaddr*)&src_addr, &src_addr_len);

        if (count == -1) 
        {
            //Gestion des erreurs
            perror("recvfrom");
            exit(1);
        } 
        else if (count == sizeof(buffer)) 
        {
            //Gestion des erreurs
            fprintf(stderr, "frame too large for buffer: truncated\n");
        } 
        else 
        {
            //On parse le header Ethernet de la trame reçu
            struct ethhdr *eth = (struct ethhdr *)(buffer);

            //On vérifie que c'est bien une trame GeoNet et qu'elle ne vient pas du proxy app vers voiture
            if(eth->h_proto == 18313 && !(eth->h_source[0] == 0x00 && eth->h_source[1] == 0xff && eth->h_source[2] == 0x00 && eth->h_source[3] == 0x00 && eth->h_source[4] == 0x00 && eth->h_source[5] == 0x00))
            {
                //On deplace le pointeur du buffer pour se placer dans la zone data
                unsigned char * data = (buffer + sizeof(struct ethhdr));
                int remaining_data = count - sizeof(struct ethhdr);
                int i;

                /*
                //Logging
                for(i=0;i<remaining_data;i++)
                {
                    if(i!=0 && i%16==0)
                    {
                        printf("\n");
                    }
                    
                    printf(" %.2X ",data[i]);
                }
                printf("\n\n");*/

                //On envoie le message reçu à l'app
                if(write(cleintSocket, data, remaining_data)<0)
                {
                    perror("Write failed");
                }
                else
                {
                    fprintf(stderr, "Message received from the car and sent to the app");
                }
            }
            
        }
    }
}