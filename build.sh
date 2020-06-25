#!/bin/bash

BUILD_NUMBER=${bamboo_buildNumber:-0}
BUILD_COMMIT=$(git rev-parse --short HEAD)
BUILD_REPO=zeebe-broker
BUILD_BRANCH=`git branch 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/\1/'`
BUILD_BRANCH_FOR_DOCKER=`echo $BUILD_BRANCH | tr '/' '_'`

if [ "${bamboo_DOCKER_PUSH_TARGET}" == "AWS" ]; then
	BUILD_REGISTRY=712823164894.dkr.ecr.us-east-2.amazonaws.com
else
	BUILD_REGISTRY="registry.ahanet.net:5000/ops"
fi

BUILD_IMAGE_TAG=${BUILD_NUMBER}-${BUILD_BRANCH_FOR_DOCKER}-${BUILD_COMMIT}
BUILD_IMAGE_NAME=$BUILD_REPO
BUILD_IMAGE_FULL_NAME=${BUILD_IMAGE_NAME}:${BUILD_IMAGE_TAG}
DEPLOY_IMAGE_FULL_NAME=${BUILD_REGISTRY}/${BUILD_IMAGE_FULL_NAME}
DEPLOY_TARGETS=${bamboo_DEPLOY_TARGETS}
DEPLOY_ACCESS_SECRET_KEY=${bamboo_DEPLOY_ACCESS_SECRET_KEY}
DEPLOY_ACCESS_FILE=~/.ssh/id_rsa
if [ -n "${bamboo_AWS_ACCESS_KEY_ID}" ]; then
    export AWS_ACCESS_KEY_ID=${bamboo_AWS_ACCESS_KEY_ID}
    export AWS_SECRET_ACCESS_KEY=${bamboo_AWS_SECRET_ACCESS_KEY}
fi
export AWS_REGION=us-east-2
set -x

if [ -n "$bamboo_buildNumber" ]; then
	export DEBIAN_FRONTEND=noninteractive
	echo 'debconf debconf/frontend select Noninteractive' | debconf-set-selections
	sudo add-apt-repository ppa:openjdk-r/ppa
	sudo apt update
	sudo apt-get -y install openjdk-11-jdk
	export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
	sudo apt-get -y upgrade maven
	unset M2_HOME
	export M3_HOME=/usr/share/maven
	export MAVEN_HOME=/usr/share/maven
	export PATH=/usr/bin:$PATH  # Override all /opt installations
	MVN_OPTS=-B
fi

function build {
	java -version
	mvn -version
	mvn $MVN_OPTS clean package -e -Dmaven.test.skip=true
}

function tagAndDeploy {
	if [ "${bamboo_DOCKER_PUSH_TARGET}" == "AWS" ]; then
		LOGINCMD=$(aws ecr get-login --region $AWS_REGION --no-include-email | sed 's|https://||')
		eval $LOGINCMD
	else
		docker login -u ${bamboo_AHANET_REGISTRY_USER} -p ${bamboo_AHANET_REGISTRY_PASSWORD} $BUILD_REGISTRY
	fi
	docker build --no-cache --build-arg DISTBALL=dist/target/zeebe-distribution-*.tar.gz -t $BUILD_IMAGE_FULL_NAME -t $DEPLOY_IMAGE_FULL_NAME . #--target app .
	docker push $DEPLOY_IMAGE_FULL_NAME
	echo $DEPLOY_IMAGE_FULL_NAME
}

echo "#### BUILD VARS ####"
( set -o posix ; set ) | egrep "^BUILD|^DEPLOY|^AWS" | grep -v SECRET

build || exit 1
tagAndDeploy
