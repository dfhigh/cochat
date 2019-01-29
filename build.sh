#!/usr/bin/env bash
cur=$(cd `dirname $0`; pwd)
cd $cur

runCommand() {
    cmd="$1"
    echo "$cur: $cmd"
    $cmd
    code=$?
    if [[ $code != 0 ]] ; then
        echo "cmd $1 failed with code $code"
        exit $code
    fi
}

runCommandIgnoringResult() {
    cmd="$1"
    echo "$cur: $cmd"
    $cmd
    code=$?
    if [[ $code != 0 ]] ; then
        echo "cmd $1 failed with code $code, ignoring it..."
    fi
}

runCommandIgnoringResult "rm -rf release/*.tar.gz release/*.tar target"
runCommand "mvn clean package"
runCommand "mkdir -p target/cochat"
runCommand "mv target/lib target/cochat/"
runCommand "mv target/cochat-*.jar target/cochat/lib/"
runCommand "cp -fr src/main/resources/* target/cochat/"
runCommand "chmod +x target/cochat/bin/*"
runCommand "tar -C target -zcf release/cochat.tar.gz cochat"
#runCommand "docker build -t mib/cochat:1.0 ."
#runCommand "docker save -o release/cochat.tar mib/cochat:1.0"
runCommand "rm -rf target/cochat"
echo "done, package is available under release"
