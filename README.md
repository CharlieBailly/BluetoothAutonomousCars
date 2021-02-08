# bluetooth-auto

# Serveur HTTPS

Le serveur HTTPS (HTTP + SSL/TLS) (TLS est la nouvelle version de SSL) est un serveur web hébergé sur une machine Ubuntu, à l'aide du logiciel Apache2 et du framework web Django. Au niveau du backend (côté serveur), on utilise django-rest-framework pour faire une API REST avec Django. Et pour la partie visuelle sur le navigateur (le frontend), on utilise ReactJS couplé à Material-UI pour avoir un visuel propre rapidement.


## Prérequis

### Environnement virutel python (pour le backend)

Pour pouvoir isoler proprement un projet utilisant du python, on utilise pipenv (pip + venv) (pip est un gestionnaire de packages python et venv un gestionnaire d'environnement virtuel python).

Il faut donc installer pip (ou plutôt pip3) et pipenv

```bash
sudo apt update
sudo apt install python3-pip
python3 -m pip install pipenv
```

### Node.js et NPM (pour le frontend)

[ReactJS](https://reactjs.org/) est une librairie javascript qui permet de construire facilement et de manière modulaire des interfaces utilisateurs. ReactJS utilise [Node.js](https://nodejs.org/) et s'installe via [npm](https://www.npmjs.com/) comme suit :

```bash
sudo apt update
sudo apt install nodejs npm
```

### Apache2

Apache2 est le serveur web, et il s'installe comme suit :

```bash
sudo apt update
sudo apt install apache2
```

## Installation

Il faut tout d'abord cloner notre projet git :
```bash
git clone https://gitlab.telecom-paris.fr/PAF/1920/bluetooth-auto.git
```
Deplacez-vous au niveau du Pipfile (pour pouvoir installer les packages pythons utiles) et du package.json (idem mais pour les packages npm)

```bash
cd bluetooth-auto/ServeurHTTPS/DjangoDirectory
```
Activez l'environnement virtuel python:
```bash
pipenv shell
```

Installez les packages présents dans l'environnement (ils sont listés dans le Pipfile):
```bash
pipenv install
```
Pour installer les packages Node.js faites la commande suivante (toujours au niveau du fichier package.json) :
```bash
npm install
```

Il reste à configurer Apache2, rendez-vous ici :
```bash
cd /etc/apache2/sites-available/
```
Et ajoutez les fichiers suivants :
- django_project.conf (redirige le port 80 sur le port 443)
```apache
<VirtualHost *:80>
        ServerAdmin admin@djangoproject.localhost
        ServerName www.paf-communications-bluetooth.online
        ServerAlias paf-communications-bluetooth.online
        DocumentRoot /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP
        ErrorLog ${APACHE_LOG_DIR}/error.log
        CustomLog ${APACHE_LOG_DIR}/access.log combined

        RewriteEngine on
        RewriteCond %{SERVER_NAME} =www.paf-communications-bluetooth.online [OR]
        RewriteCond %{SERVER_NAME} =paf-communications-bluetooth.online
        RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]
</VirtualHost>
```
- django_project-le-ssl.conf
```apache
<IfModule mod_ssl.c>
<VirtualHost *:443>
        ServerAdmin admin@djangoproject.localhost

        ServerName www.paf-communications-bluetooth.online
        ServerAlias paf-communications-bluetooth.online

        DocumentRoot /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP
        ErrorLog ${APACHE_LOG_DIR}/error.log
        CustomLog ${APACHE_LOG_DIR}/access.log combined

        #Configuration de pour relier apache2 à django

        Alias /static /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP/static
        <Directory /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP/static>
                Require all granted
        </Directory>

        Alias /static /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP/media
        <Directory /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP/media>
                Require all granted
        </Directory>

        <Directory /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP/ServeurHTTP>
                <Files wsgi.py>
                        Require all granted
                </Files>
        </Directory>


        WSGIDaemonProcess django_project python-path=/home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP python-home=/home/ubuntu/.local/share/virtualenvs/DjangoDirectory-L8YHwTXR
        WSGIProcessGroup django_project
        WSGIScriptAlias / /home/ubuntu/bluetooth-auto/ServeurHTTPS/DjangoDirectory/ServeurHTTP/ServeurHTTP/wsgi.py

        #
        Include /etc/letsencrypt/options-ssl-apache.conf
        SSLCertificateFile /etc/letsencrypt/live/paf-communications-bluetooth.online/fullchain.pem
        SSLCertificateKeyFile /etc/letsencrypt/live/paf-communications-bluetooth.online/privkey.pem


        SSLCACertificateFile /home/ubuntu/ca/intermediate/certs/ca-chain.cert.pem
        SSLVerifyClient require
        SSLVerifyDepth 5

        SSLOptions +StdEnvVars +ExportCertData


</VirtualHost>
</IfModule>
```

- Dans les deux fichiers, les ServerName et ServerAlias seront à remplacer par votre nom de domaine si vous voulez en acheter un, ou par l'ip publique de votre machine sinon.
- A la ligne du WSGIDaemonProcess, il faut changer le python-home par le chemin de votre environnement virtuel crée avec pipenv (vous obtenez ce chemin lorsque vous faite la commande ```pipenv shell``` au niveau du Pipfile).
- Tous les répertoires commençant par /home/ubuntu seront sûrement à remplacer par leur équivalent dans votre système
- SSLCertificateFile et SSLCertificateKeyFile sont générés à l'aide de [certbot](https://certbot.eff.org/) (sur le site, choisissez Apache comme software et Ubuntu suivi de la bonne version pour le System). Cela permet d'activer le SSL/TLS au niveau du serveur et que les clients puissent authentifier le serveur.
- SSLCACertificateFile, SSLVerifiClient, et SSLVerifiDepth correspondent à l'authentification des clients à l'aide d'un certificat self-signed, c'est à dire en créant notre propre Authorité de Certification à l'aide d'[OpenSSL](https://jamielinux.com/docs/openssl-certificate-authority/introduction.html)
- SSLOptions permet de transmettre à django toutes les informations SSL du client pour pouvoir l'identifier.

- Finalement, il faut activer les VirtualHosts que vous venez de créer : ```sudo a2ensite django_project``` ```sudo a2ensite django_project-le-ssl```





## Usage

Pour démarrer le serveur Apache2

```bash
sudo service apache2 start
```

Si vous changez des choses au niveau de ReactJS, il faut recompiler le fichier main.js à l'aide de la commande suivant au niveau de package.json, puis il faut dire a django que les fichiers statiques (html/css/js même si ici seul main.js change) :
```bash
npm run build
python3 ServeurHTTP/manage.py collectstatic
#Confirmez en écrivant yes
sudo service apache2 restart #Pour redémarrer le serveur
```

## Problèmes possibles

Si le serveur apache2 ou Django bloque un port :

```bash
sudo kill -9 $(sudo lsof -t -i:x)
```
où x est le numéro de port occupé permet de kill le processus en cours sur le port.

Pour Post et Get des messages, il faut absolument ajouter un / à la fin des urls dans la requête HTTP sinon cela ne fonctionne pas.

exemple : faire une requête POST à l'adresse https://paf-communications-bluetooth.online/messages/cam/ et non à :
https://paf-communications-bluetooth.online/messages/cam

## Serveur Voiture

Ce serveur est écrit en C. Il est conçu pour tourner sur un RaspberryPi ou sous Ubuntu. Il a pour rôle d'interfacer Vanetza avec l'aapplication mobile.

### Pré-requis

Afin de faire fonctionner ce serveur, il faut avoir installé et compilé [Vanetza](https://www.vanetza.org/). Avant la compilation, il faut remplacer les sources présentent dans `tools/socktap/` du dossier contenant les sources de Vanetza par celles contenues dans `RaspberryPi/Vanetza`.

De plus il faut compiler Vanetza en ajoutant l'argument `-DBUILD_SOCKTAP=ON`:
```bash
sudo cmake .. -DBUILD_SOCKTAP=ON
```

La compilation du serveur requiert également la présence les librairies libbluetooth-dev et libssl-dev.

### Installation

Il suffit de se placer dans le dossier RaspberryPi
```bash
cd bluetooth-auto/RaspberryPi/
```

Compilez le serveur

```bash
make uproxy
make proxy
```

### Utilisation

Lancez la simulation GPS
```bash
sudo gpsfake path/to/data.nmea
```

Placez vous dans le dossier contenant les binaires de Vanetza et lancez socktap
```bash
cd path/to/vnetza/bin/
sudo ./socktap-cam -i lo --gpsd-host localhost
```

Lancez le serveur

*Version non sécurisée*
```bash
cd bluetooth-auto/RaspberryPi/
sudo ./uproxy
```

*Version sécurisée*
```bash
cd bluetooth-auto/RaspberryPi/
sudo ./proxy
```

Vous n'avez alors plus qu'à vous connecter avec l'application.
