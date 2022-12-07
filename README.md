# agena.ai Java API
This is a wrapper API for the AgenaRisk Core engine and puts the focus on:
- providing shortcuts for operations that previously used to be extremely complicated
- abstracting low level core data structures
- hiding most of the core functionality that was not self-explanatory or unsafe
- fully documented features

## Prerequisites
* JDK 8
<br>We recommend jdk1.8.0_192
<br>Note: versions of Java above 8 have not been tested but we have had user reports of successfully running on Java 11 and 17
* Maven
<br>Version >= 3.6.1
* Linux: net-tools, iproute2

# Usage
See [Example App](https://github.com/AgenaRisk/api-example-app) for usage example

# Licensing
Once cloned, you will need to perform mvn clean to trigger automatic download of non-Java dependencies into your project/lib directory.
~~~~
mvn clean compile
~~~~

Then, you can run the library with arguments to manage your license or override some configuration settings:
~~~~
mvn exec:java@activate '-Dexec.args="-h"'
~~~~
## Key Activation (Online)
~~~~
mvn exec:java@activate '-Dexec.args="--keyActivate --key 1234-ABCD-5678-EFGH"'
~~~~
## Key Activation (Offline)
First, you will need to generate an activation request file:
~~~~
mvn exec:java@activate '-Dexec.args="--offlineActivationRequest --key 1234-ABCD-5678-EFGH --oPath activation_request.txt"'
~~~~
Then, email this file to support@agena.ai. You will be provided an activation file in return, e.g. `activation_confirmation.dat`
~~~~
mvn exec:java@activate '-Dexec.args="--offlineActivate --key 1234-ABCD-5678-EFGH --oPath activation_confirmation.dat"'
~~~~
In order to deactivate a license (e.g. to move it to another machine), you will need to send the license release proof file to support@agena.ai
~~~~
mvn exec:java@activate '-Dexec.args="--offlineDeactivate --key 1234-ABCD-5678-EFGH --oPath deactivation_proof.dat"'
~~~~
Note that `--oPath` provided above is just for illustration and can be a path to any valid file location in the file system.
## Enterprise Activation
Note if you are seeing the error: `Only Enterprise version can run in a headless environment`, this could be because you are logged in via ssh or the OS is running in headless mode or genuinely does not have a GUI. You will either need to run it on OS with a GUI or get an enterprise license key.

If you do have an enterprise key, download Enterprise product files:
~~~~
wget -r -nd --accept-regex '.*dat|guid|aid' -R "index.html*" -P enterprise https://resources.agena.ai/products/enterprise/
~~~~

Then use a path override argument for product directory `--directoryProduct <path>`

~~~~
mvn exec:java@activate '-Dexec.args="--keyActivate --key 1234-ABCD-5678-EFGH --directoryProduct enterprise"'
~~~~

Note: you can also run this with e.g. `java -jar com.agenarisk.api-0.9-SNAPSHOT.jar`

# CLI Tools
## Calculation
You can use this library to run a calculation from CLI by invoking `mvn exec:java@calculate`
It requires the following `-Dexec.args`:
* `--model` - path to model file
* `--data` - path to data file  
Format of the output file will be a JSON array of dataset objects each containing id and observations array: `[{id, observations[]}, ...]`
* `--out` - path to output file  
Format of the output file will be a JSON array of dataset objects each containing id and results array: `[{id, results[]}, ...]`
* `--use-cache` - optional, skip data recalculation if found in results file [default: false]  
This option is useful when you want to be able to resume an interrupted batch calculation, as every dataset will be written to file as soon as it is calculated

Example command for Powershell:
```
 mvn exec:java@calculate "-Dexec.args=`"--model 'c:\\Car Costs.cmpx' --out 'c:\\out.json' --data 'c:\\Car Costs Scenarios.json' --use-cache`""
```

Example command for Linux bash:
```
mvn exec:java@calculate -Dexec.args="--model '~/agena/Car Costs.cmpx' --out '~/agena/out.json' --data '~/agena/Car Costs Scenarios.json' --use-cache"
```

# Resources
[JavaDoc](https://agenarisk.github.io/api/)
