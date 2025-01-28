# Automatic Document Classification

Automatically rename and move documents to a folder structure based on their content.

I PROMISE TO ADD MORE DETAILS SOON!

## Usage

Populate the [config.properties](src/main/resources/config.properties) file with the desired configuration.

Build using [maven](https://maven.apache.org/):

```shell
mvn clean package
```

Create a batch/shell script to run the application:

```shell
@echo off
java -jar I:\projects\document-sorting\target\automatic-document-classification-1.0-SNAPSHOT.jar %*
pause
```

```shell
#!/bin/bash
java -jar /home/user/projects/document-sorting/target/automatic-document-classification-1.0-SNAPSHOT.jar $@
```

Then simply drag and drop files or folders onto the script to start the classification process.

## How it works

![Process overview diagram](doc/classification-process.drawio.svg)
