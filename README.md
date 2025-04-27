# O11y Tutorial

## Signing up for Honeycomb

### Sign up for an Account

https://ui.honeycomb.io/signup

### Create a team

https://ui.honeycomb.io/teams

## Option 1 - Local Setup

This is required only if you plan to run locally. If you plan to use GitHub Codespaces, follow the instructions in the Codespaces section.

### JDK

Version 11 or greater JDK is required.

https://docs.oracle.com/en/java/javase/17/install/overview-jdk-installation.html

### Gradle

https://gradle.org/install/

### Go

Version 1.16 or greater is required.

Download and installation instructions can be found here:
https://golang.org/dl/

### Add API key to your environment

(Skip this step if you are not running locally)

```shell
# bash/zsh
export HONEYCOMB_API_KEY=<your-api-key>

# fish
set -gx HONEYCOMB_API_KEY <your-api-key>
```

## Option 2 - GitHub Codespaces

You can run this repository using [GitHub Codespaces](https://docs.github.com/en/codespaces).

1. Open the repository on GitHub.
2. Click the **Code** button and select **Open with Codespaces**.
3. If you don't have a Codespace already, create a new one.
4. Once your Codespace is ready, you'll have an environment pre-configured to run the examples.
5. Update the `HONEYCOMB_API_KEY` environment variable in the `.env` file with your Honeycomb API key you generated above.

## Running the examples

Navigate to the folders starting with 00\* and run the bnd.sh script.

```shell
cd 001-basic
./bnd.sh

# To stop the services press Ctrl+C
```
