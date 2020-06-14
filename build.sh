#!/bin/bash -x
COMMIT=`git rev-parse --short HEAD`
BUILD_BRANCH=`git branch 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/\1/'`
BUILD_BRANCH_FOR_DOCKER=`echo $BUILD_BRANCH | tr '/' '_'`
TAG=$BUILD_BRANCH_FOR_DOCKER-$COMMIT
FULL_NAME_REMOTE=712823164894.dkr.ecr.us-east-2.amazonaws.com/zeebe-broker:$TAG

sudo apt update
sudo apt-get -y install openjdk-11-jdk
sudo apt-get -y upgrade maven
java -version
unset M2_HOME
export M3_HOME=/usr/share/maven
which mvn
ls -la `which mvn`
/usr/bin/mvn -version

function build {
	java -version
	mvn -version
	echo "MAVEN__HOME=$MAVEN_HOME"
	mvn clean package -e -Dmaven.test.skip=true -Dmaven.repo.remote=https://repo1.maven.org/maven2/org/camunda/camunda-release-parent,https://app.camunda.com/nexus/content/repositories/camunda-bpm,https://app.camunda.com/nexus/content/repositories/zeebe-io,https://repo.maven.apache.org/maven2
}

function tagAndDeploy {
	LOGINCMD=$(aws ecr get-login --region $AWS_REGION --no-include-email | sed 's|https://||')
	eval $LOGINCMD
	docker build --no-cache --build-arg DISTBALL=dist/target/zeebe-distribution-*.tar.gz -t zeebe-broker:$TAG -t $FULL_NAME_REMOTE . #--target app .
	docker push $FULL_NAME_REMOTE
}

build || exit 1
tagAndDeploy
