#!/bin/bash -e

export CUR=$(pwd)
export BASE=$(dirname $0)/..

function leinit(){
    prj=$1
    cd $BASE/$1
    shift
    echo "(*) --------------------------------------------------------------"
    echo "(*) running $prj : 'lein $*'"
    echo "(*) --------------------------------------------------------------"
    lein $*
    cd $CUR
}


leinit mulog-core $*
leinit mulog-elasticsearch $*
leinit mulog-kafka $*
