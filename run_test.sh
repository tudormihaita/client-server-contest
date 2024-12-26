#!/bin/bash

echo "Current directory: $(pwd)"

CLIENT_CP="./client/build/classes/java/main:./client/build/libs/*"
SERVER_CP="./server/build/classes/java/main:./server/build/libs/*"

echo "Starting server..."
java -cp $SERVER_CP ppd.ContestServer &
SERVER_PID=$!

sleep 2

for i in {1..5}; do
    echo "Starting client $i..."
    java -cp $CLIENT_CP ppd.ContestClient "$i" &
done

wait $SERVER_PID