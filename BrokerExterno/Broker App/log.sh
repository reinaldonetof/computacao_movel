#!/bin/env bash
while [ true ]; do
 sleep 2 #sleep 2 segundos
 /usr/bin/rsync -a /var/log/mosquitto/mosquitto.log /var/www/html/broker/m.log
 chown www-data:www-data /var/www/html/broker/m.log
 chmod 774 /var/www/html/broker/m.log
done
