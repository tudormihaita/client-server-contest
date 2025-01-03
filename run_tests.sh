#!/bin/bash

#echo "Current directory: $(pwd)"

CLIENT_JAR="./client/build/libs/client-1.0-SNAPSHOT.jar"
#SERVER_JAR="./server/build/libs/server-1.0-SNAPSHOT.jar"

#echo "Starting server..."
#java -jar $SERVER_JAR &
#SERVER_PID=$!

#sleep 2

for i in {1..5}; do
    echo "Starting client $i..."
    java -jar $CLIENT_JAR "$i" &
done

#wait $SERVER_PID