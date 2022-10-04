# O11y Tutorial

## Java

This is required only if you plan to run locally. If you plan to use gitpod, skip to the next section.

### JDK

Version 11 or greater JDK is required.

https://docs.oracle.com/en/java/javase/17/install/overview-jdk-installation.html

### Gradle

https://gradle.org/install/

## Signing up for Honeycomb

### Sign up for an Account

https://ui.honeycomb.io/signup

### Create a team

https://ui.honeycomb.io/teams

### Add API key to your environment

(Skip this stop if you are using gitpod)

```bash
```shell
# bash/zsh
export HONEYCOMB_API_KEY=<your-api-key>

# fish
set -gx HONEYCOMB_API_KEY <your-api-key>
```

## Signing up for Gitpod

Sign up for Gitpod using your GitHub account at https://gitpod.io

## Setting up Gitpod

Add the HONEYCOMB_API_KEY to https://gitpod.io/variables with scope */*

## Opening the repo in Gitpod

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/kaushald/o11y-tutorial)


## Running the examples

Navigate to the folders starting with 00* and run the bnd.sh script.

```shell
cd 001-basic
./bnd.sh

# To stop the services if needed
./stop.sh
```

