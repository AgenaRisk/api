# AgenaRisk 10 API
Public AgenaRisk 10 API is a project that aims to make API programming with AgenaRisk easy.
AgenaRisk Java API v2 is ultimately a convenient front-end for the AgenaRisk Core engine and puts the focus on:
- providing shortcuts for operations that previously used to be extremely complicated
- abstracting low level core data structures
- hiding most of the core functionality that was not self-explanatory or unsafe
- fully documented features

## Prerequisites
* JDK 8
<br>We recommend jdk1.8.0_192
<br>Note: versions of Java above 8 have not been tested
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
Then, email this file to support@agenarisk.com. You will be provided an activation file in return, e.g. `activation_confirmation.dat`
~~~~
mvn exec:java@activate '-Dexec.args="--offlineActivate --key 1234-ABCD-5678-EFGH --oPath activation_confirmation.dat"'
~~~~
In order to deactivate a license (e.g. to move it to another machine), you will need to send the license release proof file to support@agenarisk.com
~~~~
mvn exec:java@activate '-Dexec.args="--offlineDeactivate --key 1234-ABCD-5678-EFGH --oPath deactivation_proof.dat"'
~~~~
Note that `--oPath` provided above is just for illustration and can be a path to any valid file location in the file system.
## Enterprise Activation
Note if you are seeing the error: `Only AgenaRisk Enterprise version can run in a headless environment`, this could be because you are logged in via ssh or the OS is running in headless mode or genuinely does not have a GUI. You will either need to run it on OS with a GUI or get an enterprise license key.

If you do have an enterprise key, download AgenaRisk 10 Enterprise product files:
~~~~
wget -r -nd --accept-regex '.*dat|guid|aid' -R "index.html*" -P enterprise https://resources.agenarisk.com/products/enterprise/
~~~~

Then use a path override argument for product directory `--directoryProduct <path>`

~~~~
mvn exec:java@activate '-Dexec.args="--keyActivate --key 1234-ABCD-5678-EFGH --directoryProduct enterprise"'
~~~~

Note: you can also run this with e.g. `java -jar com.agenarisk.api-0.9-SNAPSHOT.jar`

# Resources
[JavaDoc](https://agenarisk.github.io/api/)
