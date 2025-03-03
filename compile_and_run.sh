#!/bin/bash

# Set project directories
HEAP_DIR="HeapFile/src/heap"
GLOBAL_DIR="BufferManager/src/global"
BUF_DIR="BufferManager/src/bufmgr"
CHAINEXC_DIR="BufferManager/src/chainexception"
DISKMGR_DIR="BufferManager/src/diskmgr"
TEST_DIR="HeapFile/src/tests"

# Move to the tests directory
cd "$TEST_DIR" || exit

echo "Compiling all Java files..."
javac -cp .:"../heap":"../../$GLOBAL_DIR":"../../$BUF_DIR":"../../$CHAINEXC_DIR":"../../$DISKMGR_DIR" \
    ../heap/*.java \
    ../../$GLOBAL_DIR/*.java \
    ../../$BUF_DIR/*.java \
    ../../$CHAINEXC_DIR/*.java \
    ../../$DISKMGR_DIR/*.java \
    HFTest.java

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Running HFTest..."
    java -cp .:"../heap":"../../$GLOBAL_DIR":"../../$BUF_DIR":"../../$CHAINEXC_DIR":"../../$DISKMGR_DIR" tests.HFTest
else
    echo "Compilation failed. Please check for errors."
fi

