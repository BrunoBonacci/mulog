#!/bin/bash

#
# Waits until a specified port becomes available.
# Usage:
#     wait_for DISPLAY_NAME HOST PORT
# like: wait_for service1 localhost 8080
function wait_for {
   echo "[WAIT_FOR] Checking if $1 is started."

   while [ "$(nc -z -w 5 $2 $3 || echo 1)" == "1" ] ; do
       echo "[WAIT_FOR] Waiting for $1 to start up..."
       sleep 3
   done

   sleep 1
   echo "[WAIT_FOR] Service $1 is now READY..."
}


wait_for "$@"
